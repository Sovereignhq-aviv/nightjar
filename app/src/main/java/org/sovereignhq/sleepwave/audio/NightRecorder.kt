package org.sovereignhq.sleepwave.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Owns the microphone for the whole night.
 *
 * Runs one thread that reads 32 ms frames, turns each into a handful of numbers (loudness plus
 * three frequency bands), and forwards them. Raw audio is kept only in a rolling in-memory ring
 * buffer; it reaches disk solely when a clip is requested. That is what makes "record everything
 * interesting" possible without recording eight hours of everything.
 *
 * Clips reach backwards in time. By the time a sound has been classified it is already over, so
 * the ring buffer is replayed from [LEAD_SECONDS] before the trigger - you hear the event start,
 * not its aftermath.
 */
class NightRecorder(
    private val clipsDir: File,
    private val listener: Listener,
    /** Optional. Absent means the caller's own guess at the kind is kept as-is. */
    private val classifier: ClipClassifier? = null
) {

    interface Listener {
        fun onFrame(frame: Frame)
        fun onClipSaved(
            fileName: String,
            startedAtMs: Long,
            durationMs: Long,
            kind: String,
            peakDb: Float,
            envelope: List<Int>,
            /** The model's specific label, or empty when no model weighed in. */
            detail: String,
            confidence: Float
        )
        fun onError(message: String)
    }

    data class Frame(
        val timeMs: Long,
        /** Loudness of this frame in dBFS, roughly -90 (silence) to 0 (clipping). */
        val rmsDb: Float,
        /** The room's own background level, tracked continuously. */
        val floorDb: Float,
        val lowEnergy: Float,
        val midEnergy: Float,
        val highEnergy: Float
    ) {
        val aboveFloorDb: Float get() = rmsDb - floorDb

        /** > 1 means the sound sits below 500 Hz: snores, rumbles, thumps through a mattress. */
        val lowRatio: Float get() = lowEnergy / (midEnergy + highEnergy + 1e-9f)

        /** > 1 means speech-shaped: energy in the vowel and consonant bands. */
        val voiceRatio: Float get() = (midEnergy + highEnergy * 0.5f) / (lowEnergy + 1e-9f)
    }

    @Volatile var lastLevel: Float = 0f
        private set

    /** Which input the phone actually gave us. Recorded so a silent night can be explained. */
    @Volatile var audioSource: String = "not started"
        private set

    /** Frames actually read. Zero after a whole night means the microphone was never really live. */
    @Volatile var framesRead: Long = 0L
        private set

    /** Quietest background level seen, in dBFS. Near -90 means the microphone heard nothing at all. */
    @Volatile var quietestFloorDb: Float = 0f
        private set

    private val fft = Fft(FRAME)
    private val mags = FloatArray(FRAME / 2)
    private val floatFrame = FloatArray(FRAME)
    private val shortFrame = ShortArray(FRAME)

    private val ring = ShortArray(RING_SECONDS * SAMPLE_RATE)
    private var written = 0L
    private var startedAtMs = 0L

    private var pendingTriggerSample = -1L
    private var pendingKind = ""
    private var pendingPeakDb = 0f

    private val diskWriter = Executors.newSingleThreadExecutor()
    private var thread: Thread? = null
    @Volatile private var running = false

    /** Fast to fall, very slow to rise, so a passing truck does not permanently raise the floor. */
    private var floorDb = -60f

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        startedAtMs = System.currentTimeMillis()
        thread = Thread({ loop() }, "sleepwave-mic").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(2_000)
        thread = null
        diskWriter.shutdown()
    }

    /** True while a clip is still collecting its tail, so callers can skip re-triggering. */
    val clipPending: Boolean get() = pendingTriggerSample >= 0

    fun requestClip(kind: String, peakDb: Float) {
        if (pendingTriggerSample >= 0) return
        pendingTriggerSample = written
        pendingKind = kind
        pendingPeakDb = peakDb
    }

    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            listener.onError("This phone reported no usable microphone buffer.")
            running = false
            return
        }
        // Two seconds of slack absorbs any hiccup while a clip is being copied out.
        val bufferSize = max(minBuf * 4, SAMPLE_RATE * 2 * 2)

        // Which input we ask for matters more than anything else in this file.
        //
        // AudioSource.MIC runs the phone's own noise suppression and automatic gain control, and
        // those are built to remove precisely what this app listens for: quiet, steady, non-speech
        // sound. Worse, several manufacturers switch DSP profiles when the screen goes off, which is
        // exactly how a night comes back empty while everything appears to be working.
        //
        // UNPROCESSED is the raw signal with no processing at all. VOICE_RECOGNITION at least turns
        // AGC and noise suppression off on most devices. MIC is the last resort.
        var opened: AudioRecord? = null
        var openedLabel = ""
        for ((source, label) in PREFERRED_SOURCES) {
            val candidate = try {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $label unavailable", e)
                null
            }
            if (candidate == null) continue

            if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { candidate.release() }
                continue
            }

            val started = try {
                candidate.startRecording()
                candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $label would not start", e)
                false
            }

            if (started) {
                opened = candidate
                openedLabel = label
                break
            }
            runCatching { candidate.stop() }
            runCatching { candidate.release() }
        }

        val record = opened
        if (record == null) {
            listener.onError("Could not open the microphone. Another app may be holding it.")
            running = false
            return
        }

        audioSource = openedLabel
        Log.i(TAG, "Recording from $openedLabel")

        try {
            while (running) {
                var got = 0
                while (got < FRAME && running) {
                    val n = record.read(shortFrame, got, FRAME - got)
                    if (n <= 0) {
                        if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_DEAD_OBJECT) {
                            listener.onError("The microphone stopped unexpectedly.")
                            return
                        }
                        break
                    }
                    got += n
                }
                if (got < FRAME) continue

                appendToRing(shortFrame, got)

                var sumSquares = 0.0
                for (i in 0 until FRAME) {
                    val v = shortFrame[i] / 32768f
                    floatFrame[i] = v
                    sumSquares += (v * v).toDouble()
                }
                val rms = sqrt(sumSquares / FRAME).toFloat()
                val db = if (rms <= 1e-7f) -90f else (20f * log10(rms)).coerceAtLeast(-90f)

                floorDb += if (db < floorDb) (db - floorDb) * 0.06f else (db - floorDb) * 0.0006f
                floorDb = floorDb.coerceIn(-90f, -20f)

                framesRead++
                if (framesRead == 1L || floorDb < quietestFloorDb) quietestFloorDb = floorDb

                fft.magnitudes(floatFrame, 0, mags)

                lastLevel = ((db - floorDb) / 30f).coerceIn(0f, 1f)

                listener.onFrame(
                    Frame(
                        timeMs = System.currentTimeMillis(),
                        rmsDb = db,
                        floorDb = floorDb,
                        lowEnergy = bandEnergy(LOW_BIN_FROM, LOW_BIN_TO),
                        midEnergy = bandEnergy(MID_BIN_FROM, MID_BIN_TO),
                        highEnergy = bandEnergy(HIGH_BIN_FROM, HIGH_BIN_TO)
                    )
                )

                maybeFlushClip()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recorder loop died", e)
            listener.onError("Recording stopped: ${e.message}")
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }

    private fun bandEnergy(fromBin: Int, toBin: Int): Float {
        var sum = 0f
        for (i in fromBin until toBin) sum += mags[i] * mags[i]
        return sum / (toBin - fromBin)
    }

    private fun appendToRing(src: ShortArray, count: Int) {
        val cap = ring.size
        var pos = (written % cap).toInt()
        var remaining = count
        var offset = 0
        while (remaining > 0) {
            val chunk = minOf(remaining, cap - pos)
            System.arraycopy(src, offset, ring, pos, chunk)
            pos = (pos + chunk) % cap
            offset += chunk
            remaining -= chunk
        }
        written += count
    }

    private fun maybeFlushClip() {
        val trigger = pendingTriggerSample
        if (trigger < 0) return
        val tailSamples = TAIL_SECONDS * SAMPLE_RATE
        if (written < trigger + tailSamples) return

        val leadSamples = LEAD_SECONDS * SAMPLE_RATE
        val oldestAvailable = max(0L, written - ring.size + FRAME.toLong())
        val from = max(oldestAvailable, trigger - leadSamples)
        val to = trigger + tailSamples
        val count = (to - from).toInt()

        val kind = pendingKind
        val peak = pendingPeakDb
        pendingTriggerSample = -1L

        if (count <= 0) return

        val copy = ShortArray(count)
        val cap = ring.size
        var pos = (from % cap).toInt()
        var done = 0
        while (done < count) {
            val chunk = minOf(count - done, cap - pos)
            System.arraycopy(ring, pos, copy, done, chunk)
            pos = (pos + chunk) % cap
            done += chunk
        }

        val clipStartMs = startedAtMs + (from * 1000L / SAMPLE_RATE)
        val durationMs = count * 1000L / SAMPLE_RATE

        if (diskWriter.isShutdown) return
        diskWriter.execute {
            try {
                // The model only ever sees clips that were already worth saving, and it runs on the
                // disk-writer thread so the microphone loop is never blocked by inference.
                val verdict = classifier?.classify(copy, count)
                val finalKind = verdict?.kind?.name ?: kind
                val name = "${finalKind.lowercase()}_$clipStartMs.wav"

                clipsDir.mkdirs()
                WavWriter.write(File(clipsDir, name), copy, count, SAMPLE_RATE)
                listener.onClipSaved(
                    fileName = name,
                    startedAtMs = clipStartMs,
                    durationMs = durationMs,
                    kind = finalKind,
                    peakDb = peak,
                    envelope = envelopeOf(copy, count),
                    detail = verdict?.label ?: "",
                    confidence = verdict?.confidence ?: 0f
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not write clip at $clipStartMs", e)
            }
        }
    }

    /**
     * A 0-100 loudness bucket per slice of the clip, computed once here so the UI can draw a real
     * waveform for a long list without touching a single audio file.
     *
     * Peak rather than RMS per bucket, because a waveform that shows the transients is what makes
     * a clip recognisable at a glance. Square-rooted so quiet clips are not drawn as flat lines.
     */
    private fun envelopeOf(pcm: ShortArray, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val buckets = ENVELOPE_BUCKETS
        val perBucket = max(1, count / buckets)
        return (0 until buckets).map { b ->
            val start = b * perBucket
            if (start >= count) return@map 0
            val end = minOf(count, start + perBucket)
            var peak = 0
            for (i in start until end) {
                val v = abs(pcm[i].toInt())
                if (v > peak) peak = v
            }
            (sqrt(peak / 32768f) * 100f).roundToInt().coerceIn(0, 100)
        }
    }

    companion object {
        /**
         * Tried in order. The first that opens and actually starts wins, and which one it was gets
         * reported so an empty night can be explained rather than guessed at.
         */
        private val PREFERRED_SOURCES = listOf(
            MediaRecorder.AudioSource.UNPROCESSED to "unprocessed",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "voice-recognition",
            MediaRecorder.AudioSource.MIC to "mic"
        )

        const val SAMPLE_RATE = 16_000
        const val FRAME = 512
        const val FRAME_MS = FRAME * 1000L / SAMPLE_RATE   // 32 ms
        const val ENVELOPE_BUCKETS = 120

        /** Audio kept before the trigger, so a clip opens with the sound that caused it. */
        private const val LEAD_SECONDS = 3
        private const val TAIL_SECONDS = 5
        private const val RING_SECONDS = LEAD_SECONDS + TAIL_SECONDS + 6
        private const val TAG = "NightRecorder"

        // 31.25 Hz per bin at 16 kHz / 512.
        private const val LOW_BIN_FROM = 2     // 62 Hz  - snore and rumble fundamentals
        private const val LOW_BIN_TO = 16      // 500 Hz
        private const val MID_BIN_FROM = 16    // 500 Hz - vowels
        private const val MID_BIN_TO = 48      // 1500 Hz
        private const val HIGH_BIN_FROM = 48   // 1500 Hz - consonants, rustling, taps
        private const val HIGH_BIN_TO = 128    // 4000 Hz
    }
}
