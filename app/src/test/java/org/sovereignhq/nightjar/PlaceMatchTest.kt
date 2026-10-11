package org.sovereignhq.nightjar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.alarm.PlaceMatch

class PlaceMatchTest {

    private fun v(vararg x: Float) = x

    @Test
    fun aPhotoMatchesItselfCompletely() {
        assertEquals(1f, PlaceMatch.cosine(v(0.2f, 0.5f, 0.1f), v(0.2f, 0.5f, 0.1f)), 1e-5f)
    }

    @Test
    fun unrelatedPhotosScoreZero() {
        assertEquals(0f, PlaceMatch.cosine(v(1f, 0f), v(0f, 1f)), 1e-5f)
    }

    @Test
    fun mismatchedOrEmptyEmbeddingsNeverMatch() {
        assertEquals(0f, PlaceMatch.cosine(v(1f, 0f), v(1f, 0f, 0f)), 0f)
        assertEquals(0f, PlaceMatch.cosine(v(), v()), 0f)
    }

    @Test
    fun theBestReferenceWins() {
        val refs = listOf(v(1f, 0f), v(0f, 1f))
        assertEquals(1f, PlaceMatch.bestMatch(v(0f, 2f), refs), 1e-5f)
    }

    @Test
    fun theBarSitsJustUnderTheReferencesOwnAgreement() {
        // Three photos whose weakest pair agrees at about 0.90 put the bar near 0.80.
        val a = v(1f, 0f, 0f)
        val b = v(0.95f, 0.312f, 0f)
        val c = v(0.95f, 0f, 0.312f)
        val bar = PlaceMatch.thresholdFor(listOf(a, b, c))
        assertTrue("bar was $bar", bar in 0.80f..0.86f)
    }

    @Test
    fun theBarNeverDropsBelowTheFloor() {
        val bar = PlaceMatch.thresholdFor(listOf(v(1f, 0f), v(0f, 1f), v(1f, 1f)))
        assertEquals(0.60f, bar, 1e-5f)
    }

    @Test
    fun theBarNeverRisesAboveTheCeiling() {
        val same = v(0.3f, 0.4f)
        assertEquals(0.90f, PlaceMatch.thresholdFor(listOf(same, same, same)), 1e-5f)
    }

    @Test
    fun embeddingsSurviveStorage() {
        val refs = listOf(v(0.1f, -0.25f, 3.5f), v(1f, 2f, 3f))
        val back = PlaceMatch.decode(PlaceMatch.encode(refs))
        assertEquals(2, back.size)
        assertTrue(back[0].contentEquals(refs[0]))
        assertTrue(back[1].contentEquals(refs[1]))
        assertTrue(PlaceMatch.decode("").isEmpty())
    }
}
