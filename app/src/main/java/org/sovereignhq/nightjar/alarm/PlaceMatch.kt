package org.sovereignhq.nightjar.alarm

import kotlin.math.sqrt

/**
 * Deciding whether the camera is looking at the place that was registered.
 *
 * Each photo becomes an embedding - a list of numbers describing what is in the picture - and two
 * photos of the same spot produce embeddings that point the same way. Cosine similarity measures how
 * closely they do: 1 is identical, 0 is unrelated.
 *
 * The pass mark is not a fixed number, because "how similar is similar" depends on the room. A
 * bathroom with strong shapes gives near-identical embeddings from three angles; a plain corridor
 * gives looser ones. So registration takes three photos from slightly different spots, measures how
 * much they agree with each other, and sets the bar just under that. A live view only has to look as
 * much like the registered place as the registered photos looked like each other.
 */
object PlaceMatch {

    const val REFERENCE_PHOTOS = 3

    /**
     * How far under the photos' own agreement the bar sits. Too small and a change in lighting fails
     * a genuine match, leaving you standing in the bathroom with the alarm still going; too large and
     * the bedroom starts to pass.
     */
    private const val MARGIN = 0.10f

    /** Never easier than this, however loosely the reference photos agreed. */
    private const val FLOOR = 0.60f

    /** Never harder than this, however tightly they agreed. */
    private const val CEILING = 0.90f

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0f || nb == 0f) return 0f
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** The closest any reference photo comes to what the camera sees now. */
    fun bestMatch(live: FloatArray, references: List<FloatArray>): Float =
        references.maxOfOrNull { cosine(live, it) } ?: 0f

    fun thresholdFor(references: List<FloatArray>): Float {
        val agreement = buildList {
            for (i in references.indices) {
                for (j in i + 1 until references.size) add(cosine(references[i], references[j]))
            }
        }.minOrNull() ?: return CEILING
        return (agreement - MARGIN).coerceIn(FLOOR, CEILING)
    }

    /** Plain text for SharedPreferences: photos separated by ';', numbers by ','. */
    fun encode(references: List<FloatArray>): String =
        references.joinToString(";") { photo -> photo.joinToString(",") }

    fun decode(text: String): List<FloatArray> =
        if (text.isBlank()) {
            emptyList()
        } else {
            text.split(";").mapNotNull { photo ->
                val values = photo.split(",").mapNotNull { it.toFloatOrNull() }
                if (values.isEmpty()) null else values.toFloatArray()
            }
        }
}
