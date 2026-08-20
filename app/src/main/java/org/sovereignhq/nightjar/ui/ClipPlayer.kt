package org.sovereignhq.nightjar.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sovereignhq.nightjar.data.SoundClip
import java.io.File

/**
 * Plays night recordings, one after another.
 *
 * A queue rather than a single-file player, because the way anyone actually listens to a night is
 * "play the loud ones back to back" - the highlights reel is the point, not an extra. Progress is
 * polled rather than derived, since a WAV played at 1.5x does not advance in wall-clock time.
 */
class ClipPlayer(
    private val context: Context,
    private val fileFor: (SoundClip) -> File
) {

    var current by mutableStateOf<SoundClip?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** True when more than one clip is queued, so the UI can show prev/next. */
    var queueSize by mutableStateOf(0)
        private set

    private var queue: List<SoundClip> = emptyList()
    private var index = 0
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    /** Starts [clip]. [alsoQueue] lets a tap on one row continue into the rest of the night. */
    fun play(clip: SoundClip, alsoQueue: List<SoundClip> = listOf(clip)) {
        queue = alsoQueue.ifEmpty { listOf(clip) }
        index = queue.indexOfFirst { it.fileName == clip.fileName }.coerceAtLeast(0)
        queueSize = queue.size
        openCurrent()
    }

    /** Tap the row that is already playing to stop it; tap any other row to switch. */
    fun toggle(clip: SoundClip, alsoQueue: List<SoundClip> = listOf(clip)) {
        if (current?.fileName == clip.fileName) {
            if (playing) pause() else resume()
        } else {
            play(clip, alsoQueue)
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        playing = false
        stopTicker()
    }

    fun resume() {
        val p = player ?: return
        runCatching {
            p.start()
            applySpeed()
        }
        playing = true
        startTicker()
    }

    fun stop() {
        stopTicker()
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
        playing = false
        current = null
        progress = 0f
        queue = emptyList()
        queueSize = 0
        index = 0
    }

    fun next(): Boolean {
        if (index + 1 >= queue.size) {
            stop()
            return false
        }
        index++
        openCurrent()
        return true
    }

    fun previous() {
        // Restart the current clip first, the way every audio player behaves.
        if (progress > 0.15f || index == 0) {
            seekTo(0f)
            return
        }
        index--
        openCurrent()
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        runCatching {
            p.seekTo((p.duration * clamped).toInt())
            progress = clamped
        }
    }

    /** 1x, 1.5x, 2x, then back. Slow mumbling is easier at 1x; a whole night is not. */
    fun cycleSpeed() {
        speed = when (speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        applySpeed()
    }

    private fun openCurrent() {
        val clip = queue.getOrNull(index) ?: return stop()
        val file = fileFor(clip)
        if (!file.exists()) {
            Log.w(TAG, "Clip file missing: ${clip.fileName}")
            if (!next()) stop()
            return
        }

        stopTicker()
        player?.let {
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        progress = 0f

        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { next() }
                setOnErrorListener { _, _, _ ->
                    Log.w(TAG, "Playback error on ${clip.fileName}")
                    next()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not play ${clip.fileName}", e)
            null
        }

        current = if (player != null) clip else null
        playing = player != null
        if (player != null) {
            applySpeed()
            startTicker()
        }
    }

    private fun applySpeed() {
        val p = player ?: return
        if (speed == 1f) return
        runCatching { p.playbackParams = PlaybackParams().setSpeed(speed) }
    }

    private fun startTicker() {
        stopTicker()
        val task = object : Runnable {
            override fun run() {
                val p = player
                if (p == null || !playing) return
                runCatching {
                    val duration = p.duration
                    if (duration > 0) progress = (p.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                }
                handler.postDelayed(this, TICK_MS)
            }
        }
        ticker = task
        handler.post(task)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private companion object {
        const val TAG = "ClipPlayer"
        const val TICK_MS = 60L
    }
}
