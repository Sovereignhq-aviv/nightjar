package org.sovereignhq.nightjar.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Plays the wake-up sound on the alarm stream, so it is heard even with the ringer silenced.
 *
 * Volume starts almost inaudible and climbs over [rampSeconds]. Waking to a sound that fades in
 * is the other half of a gentle alarm - catching light sleep only helps if the sound itself is
 * not a shock.
 */
class AlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = START_VOLUME
    private var vibrateRequested = false

    /** Silenced on request, but still ringing as far as the alarm is concerned. */
    @Volatile
    var isQuiet: Boolean = false
        private set
    private var step = 0f
    private var ramping: Runnable? = null

    val isPlaying: Boolean get() = player != null

    fun start(soundUri: String, vibrate: Boolean, rampSeconds: Int) {
        if (player != null) return

        val uri = resolveUri(soundUri)
        if (uri != null) {
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = true
                    setVolume(START_VOLUME, START_VOLUME)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not play $uri", e)
                null
            }
        }

        vibrateRequested = vibrate
        isQuiet = false
        volume = START_VOLUME
        step = if (rampSeconds > 0) (1f - START_VOLUME) / (rampSeconds * 1000f / RAMP_INTERVAL_MS) else 1f
        scheduleRamp()

        if (vibrate) startVibration()
    }

    /**
     * Drops the sound to nothing and stops the vibration without ending the alarm.
     *
     * For getting out of bed without waking whoever else is in it. Deliberately reversible and
     * deliberately not a dismissal: the service brings the sound back after a grace period, so
     * silencing the room can never be mistaken for switching the alarm off.
     */
    fun quieten() {
        isQuiet = true
        runCatching { player?.setVolume(0f, 0f) }
        runCatching { vibrator?.cancel() }
    }

    /** Back to a gentle ramp from near-silence, as if the alarm had just started. */
    fun restoreVolume() {
        if (!isQuiet) return
        isQuiet = false
        volume = START_VOLUME
        runCatching { player?.setVolume(volume, volume) }
        if (vibrateRequested) startVibration()
    }

    fun stop() {
        ramping?.let { handler.removeCallbacks(it) }
        ramping = null
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun scheduleRamp() {
        val task = object : Runnable {
            override fun run() {
                val p = player ?: return
                // Keep ticking while quietened, but do not creep the volume back up - that is the
                // service's decision to make, once the grace period is over.
                if (isQuiet) {
                    handler.postDelayed(this, RAMP_INTERVAL_MS)
                    return
                }
                volume = (volume + step).coerceAtMost(1f)
                runCatching { p.setVolume(volume, volume) }
                if (volume < 1f) handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }
        ramping = task
        handler.postDelayed(task, RAMP_INTERVAL_MS)
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // Soft double pulse, repeating from index 0.
        val timings = longArrayOf(0, 400, 900, 400, 1_800)
        val amplitudes = intArrayOf(0, 90, 0, 160, 0)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        }
    }

    private fun resolveUri(preferred: String): Uri? {
        if (preferred.isNotBlank()) {
            runCatching { return Uri.parse(preferred) }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        const val START_VOLUME = 0.04f
        const val RAMP_INTERVAL_MS = 500L
    }
}
