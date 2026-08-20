package org.sovereignhq.nightjar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.sleep.SnoreTrend

/**
 * The snoring alert exists to be believed, so most of these tests are about staying quiet.
 *
 * An app that announces a dramatic weekly change every week trains you to ignore it, and the easiest
 * way to produce that is a percentage on a tiny base: two minutes becoming six is "up 200%" and means
 * nothing whatsoever. Half of what follows checks that nothing is reported.
 */
class SnoreTrendTest {

    @Test
    fun `says nothing until there are enough nights on both sides`() {
        assertNull(SnoreTrend.compare(recent = listOf(30, 30), previous = listOf(10, 10, 10)))
        assertNull(SnoreTrend.compare(recent = listOf(30, 30, 30), previous = listOf(10, 10)))
        assertNull(SnoreTrend.compare(recent = emptyList(), previous = emptyList()))
    }

    @Test
    fun `a small change is steady, not news`() {
        val verdict = SnoreTrend.compare(
            recent = List(5) { 33 },
            previous = List(5) { 30 }
        )!!
        assertEquals(SnoreTrend.Direction.STEADY, verdict.direction)
    }

    @Test
    fun `a real increase on a solid base is reported as a percentage`() {
        val verdict = SnoreTrend.compare(
            recent = List(5) { 40 },
            previous = List(5) { 20 }
        )!!
        assertEquals(SnoreTrend.Direction.UP, verdict.direction)
        assertTrue("Expected a percentage, got: ${verdict.headline}", verdict.headline.contains("100%"))
    }

    @Test
    fun `a decrease is reported and reads as good news`() {
        val verdict = SnoreTrend.compare(
            recent = List(6) { 30 },
            previous = List(6) { 60 }
        )!!
        assertEquals(SnoreTrend.Direction.DOWN, verdict.direction)
        assertTrue("Expected a down headline, got: ${verdict.headline}", verdict.headline.contains("down"))
    }

    @Test
    fun `a tiny baseline never produces a percentage`() {
        // Two minutes becoming twelve is technically "up 500%". Saying so would be theatre.
        val verdict = SnoreTrend.compare(
            recent = List(5) { 12 },
            previous = List(5) { 2 }
        )!!
        assertEquals(SnoreTrend.Direction.UP, verdict.direction)
        assertTrue(
            "A percentage on a 2-minute base is meaningless: ${verdict.headline}",
            !verdict.headline.contains("%")
        )
        assertTrue("Expected plain minutes, got: ${verdict.headline}", verdict.headline.contains("min"))
    }

    @Test
    fun `a big absolute change that is proportionally small stays steady`() {
        // Ten minutes more on top of a hundred clears the absolute bar but is not a real shift.
        val verdict = SnoreTrend.compare(
            recent = List(5) { 110 },
            previous = List(5) { 100 }
        )!!
        assertEquals(SnoreTrend.Direction.STEADY, verdict.direction)
    }

    @Test
    fun `a thin sample is flagged as a hint rather than a finding`() {
        val verdict = SnoreTrend.compare(
            recent = List(3) { 45 },
            previous = List(3) { 15 }
        )!!
        assertEquals(SnoreTrend.Direction.UP, verdict.direction)
        assertTrue("Thin samples should be hedged: ${verdict.detail}", verdict.detail.contains("hint"))
    }

    @Test
    fun `a comfortable sample is stated without hedging`() {
        val verdict = SnoreTrend.compare(
            recent = List(7) { 45 },
            previous = List(7) { 15 }
        )!!
        assertTrue("Should not hedge a full fortnight: ${verdict.detail}", !verdict.detail.contains("hint"))
    }

    @Test
    fun `no snoring at all is not framed as a change`() {
        val verdict = SnoreTrend.compare(recent = List(5) { 0 }, previous = List(5) { 0 })!!
        assertEquals(SnoreTrend.Direction.STEADY, verdict.direction)
        assertTrue(
            "Zero nights should read plainly: ${verdict.headline}",
            verdict.headline.contains("No snoring")
        )
    }

    @Test
    fun `windows split a history into this week and last week`() {
        val history = (1..20).toList()   // newest first
        val (recent, previous) = SnoreTrend.windows(history, windowNights = 7)

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), recent)
        assertEquals(listOf(8, 9, 10, 11, 12, 13, 14), previous)
    }

    @Test
    fun `windows cope with a short history`() {
        val (recent, previous) = SnoreTrend.windows(listOf(5, 4, 3), windowNights = 7)
        assertEquals(listOf(5, 4, 3), recent)
        assertTrue("Nothing to compare against yet", previous.isEmpty())
        assertNull(SnoreTrend.compare(recent, previous))
    }
}
