package app.ember.client

/**
 * Invisible marker stored at the end of the user's bio. It is the same format
 * as in Ember Desktop, so both clients see each other's gifts.
 *
 *   [magic: 3 digits][payload: 12 digits][check: 1 digit]
 *
 * A digit is one of four zero-width characters (base 4). The payload is the
 * low 24 bits of the Telegram gift id.
 */
object EmberMarker {
    private val digits = charArrayOf('\u200B', '\u200C', '\u200D', '\u2060')
    private val magic = intArrayOf(2, 3, 2)
    private const val PAYLOAD = 12
    private const val LENGTH = 3 + PAYLOAD + 1
    const val TAIL_MASK: Long = (1L shl 24) - 1

    private fun digitOf(c: Char): Int = digits.indexOf(c)

    fun encode(giftId: Long): String {
        val sb = StringBuilder(LENGTH)
        for (d in magic) sb.append(digits[d])
        val tail = giftId and TAIL_MASK
        var sum = 0
        for (i in PAYLOAD - 1 downTo 0) {
            val d = ((tail shr (i * 2)) and 3L).toInt()
            sum += d
            sb.append(digits[d])
        }
        sb.append(digits[sum % 4])
        return sb.toString()
    }

    /** Returns the start index of a marker and its tail, or null. */
    private fun find(text: String): Pair<Int, Long>? {
        var from = 0
        while (from + LENGTH <= text.length) {
            var ok = true
            for (i in magic.indices) {
                if (digitOf(text[from + i]) != magic[i]) { ok = false; break }
            }
            if (ok) {
                var value = 0L
                var sum = 0
                for (i in 0 until PAYLOAD) {
                    val d = digitOf(text[from + magic.size + i])
                    if (d < 0) { ok = false; break }
                    value = (value shl 2) or d.toLong()
                    sum += d
                }
                if (ok && digitOf(text[from + LENGTH - 1]) == sum % 4) {
                    return from to value
                }
            }
            from++
        }
        return null
    }

    fun parse(about: String): Long? = find(about)?.second

    fun strip(about: String): String {
        var result = about
        while (true) {
            val (at, _) = find(result) ?: return result
            result = result.removeRange(at, at + LENGTH)
        }
    }
}
