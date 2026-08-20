package org.sovereignhq.sleepwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.sleepwave.alarm.PuzzleGenerator
import kotlin.random.Random

/**
 * The puzzle guards the alarm, so the properties that matter are not about difficulty.
 *
 * A puzzle with a negative answer reads as a typing mistake to a half-awake brain. A puzzle whose
 * answer is obvious without reading it (anything times zero) fails at its only job. And a puzzle
 * whose stated answer is wrong would lock someone out of switching their alarm off, which is the
 * worst outcome available here - so every generated puzzle is checked by evaluating it.
 */
class MathPuzzleTest {

    @Test
    fun `every generated answer is actually correct`() {
        val random = Random(4)
        for (digits in 1..3) {
            repeat(400) {
                val puzzle = PuzzleGenerator.generate(digits, random)
                assertEquals(
                    "Wrong answer for ${puzzle.question}",
                    evaluate(puzzle.question),
                    puzzle.answer
                )
            }
        }
    }

    @Test
    fun `answers are never negative`() {
        val random = Random(9)
        for (digits in 1..3) {
            repeat(400) {
                val puzzle = PuzzleGenerator.generate(digits, random)
                assertTrue(
                    "Negative answer would read as a mistake: ${puzzle.question} = ${puzzle.answer}",
                    puzzle.answer >= 0
                )
            }
        }
    }

    @Test
    fun `operands are never 0 or 1`() {
        val random = Random(11)
        repeat(600) {
            val puzzle = PuzzleGenerator.generate(2, random)
            operandsOf(puzzle.question).forEach { operand ->
                assertTrue("Trivial operand in ${puzzle.question}", operand >= 2)
            }
        }
    }

    @Test
    fun `digit setting controls the size of the numbers`() {
        val random = Random(13)
        repeat(200) {
            operandsOf(PuzzleGenerator.generate(1, random).question).forEach {
                assertTrue("Expected single digits, got $it", it <= 9)
            }
        }
        repeat(200) {
            // The larger operand carries the difficulty; multiplication keeps the other one small on
            // purpose, so only the maximum is checked.
            val operands = operandsOf(PuzzleGenerator.generate(3, random).question)
            assertTrue("Expected a three-digit operand in $operands", operands.max() >= 100)
        }
    }

    @Test
    fun `multiplication stays mental arithmetic`() {
        val random = Random(17)
        repeat(600) {
            val puzzle = PuzzleGenerator.generate(3, random)
            if ("×" !in puzzle.question) return@repeat
            val operands = operandsOf(puzzle.question)
            assertTrue(
                "Long multiplication at 7am is a step too far: ${puzzle.question}",
                operands.min() <= 9
            )
        }
    }

    @Test
    fun `a set has the requested size and no repeats`() {
        val set = PuzzleGenerator.generateSet(digits = 2, count = 3, random = Random(21))
        assertEquals(3, set.size)
        assertEquals("Solving the same sum twice should not count twice", 3, set.map { it.question }.distinct().size)
    }

    @Test
    fun `set size is clamped to something survivable`() {
        assertEquals(1, PuzzleGenerator.generateSet(1, 0, Random(1)).size)
        assertEquals(
            PuzzleGenerator.MAX_PUZZLES,
            PuzzleGenerator.generateSet(1, 99, Random(1)).size
        )
    }

    @Test
    fun `descriptions read like English`() {
        assertEquals("One single-digit sum", PuzzleGenerator.describe(1, 1))
        assertEquals("3 two-digit sums", PuzzleGenerator.describe(2, 3))
        assertEquals("2 three-digit sums", PuzzleGenerator.describe(3, 2))
    }

    // ---- helpers ----

    /** Evaluates the printed question independently, so a broken generator cannot mark its own work. */
    private fun evaluate(question: String): Int {
        val parts = question.split(" ")
        val a = parts[0].toInt()
        val b = parts[2].toInt()
        return when (parts[1]) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            else -> throw IllegalArgumentException("Unknown operator in: $question")
        }
    }

    private fun operandsOf(question: String): List<Int> =
        question.split(" ").mapNotNull { it.toIntOrNull() }
}
