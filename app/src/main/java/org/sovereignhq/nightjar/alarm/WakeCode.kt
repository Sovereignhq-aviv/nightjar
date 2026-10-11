package org.sovereignhq.nightjar.alarm

/**
 * The code that has to be scanned to switch the alarm off.
 *
 * Matching ignores surrounding whitespace only. Some printed codes and some scanners add a trailing
 * newline, and a mismatch over an invisible character at seven in the morning would be maddening -
 * but anything looser would let a different code count.
 */
object WakeCode {
    fun matches(scanned: String, registered: String): Boolean {
        val want = registered.trim()
        return want.isNotEmpty() && scanned.trim() == want
    }

    /** A short, readable stand-in for the registered code, for the Settings row. */
    fun preview(code: String): String {
        val clean = code.trim().replace(Regex("\s+"), " ")
        return if (clean.length <= 24) clean else clean.take(21) + "..."
    }
}
