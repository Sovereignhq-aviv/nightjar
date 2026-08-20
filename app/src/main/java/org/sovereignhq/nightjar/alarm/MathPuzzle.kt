package org.sovereignhq.nightjar.alarm

import kotlin.random.Random

/**
 * An arithmetic puzzle standing between the alarm and being switched off.
 *
 * The point is not difficulty, it is wakefulness: something that cannot be solved by a thumb acting
 * on its own while the rest of you stays asleep. So the design constraints are narrow. Answers are
 * never negative, because a half-awake brain reading a minus result will assume it typed something
 * wrong. Multiplication keeps one operand small, since 384 x 7 wakes you up and 384 x 276 just makes
 * you give up and pull the battery out. And the numbers avoid 0 and 1 as operands, which produce
 * puzzles you can answer without reading them.
 */
data class MathPuzzle(val question: String, val answer: Int)

/** How hard the puzzle is, by how many digits the operands have. Zero means no puzzle. */
object PuzzleGenerator {

    private enum class Op(val symbol: String) { PLUS("+"), MINUS("−"), TIMES("×") }

    fun generate(digits: Int, random: Random = Random.Default): MathPuzzle {
        val safeDigits = digits.coerceIn(1, 3)
        val op = Op.entries[random.nextInt(Op.entries.size)]

        return when (op) {
            Op.PLUS -> {
                val a = operand(safeDigits, random)
                val b = operand(safeDigits, random)
                MathPuzzle("$a ${op.symbol} $b", a + b)
            }

            Op.MINUS -> {
                // Ordered so the answer is positive. A negative result reads as a typing mistake at
                // seven in the morning.
                val first = operand(safeDigits, random)
                val second = operand(safeDigits, random)
                val bigger = maxOf(first, second)
                val smaller = minOf(first, second)
                MathPuzzle("$bigger ${op.symbol} $smaller", bigger - smaller)
            }

            Op.TIMES -> {
                // One small operand always, so this stays mental arithmetic rather than long
                // multiplication on a bedside table.
                val a = operand(safeDigits, random)
                val b = if (safeDigits == 1) operand(1, random) else random.nextInt(3, 10)
                MathPuzzle("$a ${op.symbol} $b", a * b)
            }
        }
    }

    /** [count] puzzles, all distinct, so solving one twice does not count twice. */
    fun generateSet(digits: Int, count: Int, random: Random = Random.Default): List<MathPuzzle> {
        val wanted = count.coerceIn(1, MAX_PUZZLES)
        val out = mutableListOf<MathPuzzle>()
        var attempts = 0
        while (out.size < wanted && attempts < wanted * 20) {
            attempts++
            val next = generate(digits, random)
            if (out.none { it.question == next.question }) out += next
        }
        // A pathological run of duplicates should never leave the user with nothing to solve.
        while (out.size < wanted) out += generate(digits, random)
        return out
    }

    /** 2..9, 11..99 or 101..999. Never 0 or 1, which make a puzzle answerable without reading it. */
    private fun operand(digits: Int, random: Random): Int = when (digits) {
        1 -> random.nextInt(2, 10)
        2 -> random.nextInt(11, 100)
        else -> random.nextInt(101, 1000)
    }

    const val MAX_PUZZLES = 5

    fun describe(digits: Int, count: Int): String {
        val size = when (digits.coerceIn(1, 3)) {
            1 -> "single-digit"
            2 -> "two-digit"
            else -> "three-digit"
        }
        return if (count <= 1) "One $size sum" else "$count $size sums"
    }
}
