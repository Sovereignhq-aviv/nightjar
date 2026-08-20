package org.sovereignhq.nightjar.audio

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import org.sovereignhq.nightjar.data.EventKind

/**
 * Names a recorded clip.
 *
 * Kept behind an interface so the recorder has no idea a machine-learning model exists, and so the
 * app still works when the model is missing: no verdict simply means the hand-written heuristic's
 * guess stands.
 */
fun interface ClipClassifier {
    /** Null means "no confident opinion" - the caller should keep its own label. */
    fun classify(pcm: ShortArray, count: Int): Verdict?
}

data class Verdict(
    val kind: EventKind,
    /** The model's own specific label, e.g. "Fart", "Snoring", "Air conditioning". */
    val label: String,
    val confidence: Float
)

/**
 * Maps AudioSet class names onto this app's five buckets.
 *
 * AudioSet is Google's two-million-clip sound ontology, and it already contains the exact classes
 * this app cares about - Snoring, Fart, Stomach rumble, Speech, Thump - which is why using a model
 * trained on it beats any amount of hand-tuning frequency thresholds.
 *
 * Anything absent from this table produces no verdict at all rather than a wrong one. Room tone,
 * "Silence" and "Inside, small room" therefore fall through to the heuristic, which is correct:
 * those are not events, they are the background YAMNet always hears.
 */
object AudioSetLabels {

    private val mapping: Map<String, EventKind> = buildMap {
        listOf("Snoring", "Snort", "Wheeze", "Breathing", "Gasp", "Pant")
            .forEach { put(it, EventKind.SNORE) }

        listOf(
            "Speech", "Child speech, kid speaking", "Conversation", "Narration, monologue",
            "Babbling", "Whispering", "Shout", "Bellow", "Yell", "Whoop", "Screaming",
            "Children shouting", "Laughter", "Giggle", "Chuckle, chortle", "Belly laugh",
            "Snicker", "Crying, sobbing", "Whimper", "Wail, moan", "Sigh", "Groan", "Grunt",
            "Singing", "Humming", "Whistling"
        ).forEach { put(it, EventKind.VOICE) }

        listOf("Fart", "Stomach rumble", "Burping, eructation", "Hiccup", "Gargling")
            .forEach { put(it, EventKind.RUMBLE) }

        listOf(
            "Thump, thud", "Bang", "Slam", "Knock", "Tap", "Whack, thwack", "Smash, crash",
            "Crack", "Clatter", "Squeak", "Creak", "Rustle", "Door"
        ).forEach { put(it, EventKind.THUMP) }

        // Recognisable but none of the above. Worth labelling precisely rather than hiding: being
        // told "Air conditioning" is how you find out why the list is full of rubbish.
        listOf(
            "Cough", "Sneeze", "Throat clearing", "Sniff", "Chewing, mastication", "Biting",
            "Mechanical fan", "Air conditioning", "Hum", "Alarm clock", "Clock", "Tick",
            "Tick-tock", "Telephone", "Television", "Music", "Radio",
            "Vehicle", "Car", "Car passing by", "Traffic noise, roadway noise", "Motorcycle",
            "Siren", "Aircraft", "Train",
            "Dog", "Bark", "Whimper (dog)", "Cat", "Meow", "Purr", "Bird", "Bird vocalization, bird call, bird song",
            "Rain", "Rain on surface", "Thunder", "Wind", "Wind noise (microphone)",
            "Water", "Toilet flush", "Sink (filling or washing)", "Frying (food)",
            "Footsteps", "Walk, footsteps"
        ).forEach { put(it, EventKind.OTHER) }
    }

    fun kindFor(audioSetLabel: String): EventKind? = mapping[audioSetLabel]

    /** Visible in Settings so the label vocabulary is not a black box. */
    val recognisedCount: Int get() = mapping.size
}

/**
 * YAMNet, running on the phone through MediaPipe.
 *
 * Deliberately used only on clips that have already been saved, not on the live microphone stream.
 * The cheap hand-written detector decides *that* something happened, which it can do continuously
 * for pennies of battery; the model then decides *what* it was, roughly a hundred times a night on
 * eight-second clips. Running a neural network on eight hours of continuous audio to answer a
 * question that only matters a hundred times would be a bad trade.
 */
class YamnetClassifier private constructor(
    private val classifier: AudioClassifier
) : ClipClassifier {

    override fun classify(pcm: ShortArray, count: Int): Verdict? {
        if (count <= 0) return null

        return try {
            val floats = FloatArray(count) { pcm[it] / 32768f }

            val format = AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(SAMPLE_RATE)
                .build()
            val audio = AudioData.create(format, count)
            audio.load(floats)

            val result = classifier.classify(audio)

            // YAMNet reports one set of scores per ~1 second window. Take each label's best moment
            // across the clip: a two-second fart inside an eight-second recording should not be
            // averaged away by six seconds of room tone.
            var bestKind: EventKind? = null
            var bestLabel = ""
            var bestScore = 0f

            result.classificationResults().forEach { classification ->
                classification.classifications().forEach { head ->
                    head.categories().forEach { category ->
                        val name = category.categoryName() ?: return@forEach
                        val kind = AudioSetLabels.kindFor(name) ?: return@forEach
                        if (category.score() > bestScore) {
                            bestScore = category.score()
                            bestLabel = name
                            bestKind = kind
                        }
                    }
                }
            }

            val kind = bestKind
            if (kind == null || bestScore < MIN_CONFIDENCE) null
            else Verdict(kind, bestLabel, bestScore)
        } catch (e: Exception) {
            Log.w(TAG, "Classification failed, keeping the heuristic label", e)
            null
        }
    }

    fun close() {
        runCatching { classifier.close() }
    }

    companion object {
        private const val TAG = "YamnetClassifier"
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val SAMPLE_RATE = NightRecorder.SAMPLE_RATE.toFloat()

        /** Below this the model is guessing, and the heuristic's opinion is worth as much. */
        private const val MIN_CONFIDENCE = 0.22f

        /**
         * Null when the model asset is absent - a local build without the download step, for
         * instance. The app then runs exactly as it did before the model existed.
         */
        fun createOrNull(context: Context): YamnetClassifier? = try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
                )
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_FLOOR)
                .build()
            YamnetClassifier(AudioClassifier.createFromOptions(context, options))
        } catch (e: Throwable) {
            Log.w(TAG, "YAMNet unavailable, falling back to the heuristic classifier", e)
            null
        }

        private const val MAX_RESULTS = 8
        private const val SCORE_FLOOR = 0.02f
    }
}
