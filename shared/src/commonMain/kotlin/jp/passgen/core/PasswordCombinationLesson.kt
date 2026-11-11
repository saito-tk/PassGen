package jp.passgen.core

/** Teaching calculator, independent of generator preferences and device symbol restrictions. */
object PasswordCombinationLesson {
    const val MAX_LENGTH = 128
    val groups = listOf(
        LessonCharacterGroup(1, "英大文字", 26),
        LessonCharacterGroup(2, "英小文字", 26),
        LessonCharacterGroup(4, "数字", 10),
        LessonCharacterGroup(8, "記号", 32),
    )

    fun calculate(length: Int, selectionMask: Int): PasswordCombinationCount {
        require(length in 1..MAX_LENGTH && selectionMask in 0..15)
        val size = groups.filter { selectionMask and it.mask != 0 }.sumOf { it.size }
        // Decimal digits, least significant first. At most 253 digits for 94^128.
        val digits = mutableListOf(1)
        repeat(length) {
            var carry = 0
            for (i in digits.indices) {
                val product = digits[i] * size + carry
                digits[i] = product % 10; carry = product / 10
            }
            while (carry > 0) { digits += carry % 10; carry /= 10 }
        }
        return PasswordCombinationCount(size, length, digits.asReversed().joinToString(""))
    }
}

class LessonCharacterGroup(val mask: Int, val title: String, val size: Int)

class PasswordCombinationCount internal constructor(val alphabetSize: Int, val length: Int, val exactCount: String) {
    val formattedCount: String get() = exactCount.reversed().chunked(3).joinToString(",").reversed()
    val isAbbreviated: Boolean get() = exactCount.length > 16
    val summary: String get() = if (isAbbreviated) {
        "約 ${exactCount.first()}.${exactCount.substring(1, 4)} × 10${superscript(exactCount.length - 1)}通り"
    } else "$formattedCount 通り"
    val formula: String get() = "$alphabetSize${superscript(length)}"
    private fun superscript(value: Int): String = value.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
}
