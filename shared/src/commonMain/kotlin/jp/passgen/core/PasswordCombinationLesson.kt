package jp.passgen.core

import kotlin.math.pow

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

/** Illustrative rates, not measurements of a service, hash algorithm or device. */
object BruteForceLesson {
    val rates = listOf(
        LessonTrialRate(1, "1回/秒"), LessonTrialRate(1_000, "1,000回/秒"),
        LessonTrialRate(1_000_000, "100万回/秒"), LessonTrialRate(1_000_000_000, "10億回/秒"),
    )
    const val SECONDS_PER_YEAR = 31_557_600.0 // 365.25 days; a display conversion, not a prediction.

    fun estimate(combinations: PasswordCombinationCount, rateIndex: Int): BruteForceEstimate? {
        require(rateIndex in rates.indices)
        if (combinations.alphabetSize == 0) return null
        // 94^128 is below Double.MAX_VALUE. Time labels are deliberately approximate.
        val candidates = combinations.exactCount.toDouble()
        val rate = rates[rateIndex].attemptsPerSecond.toDouble()
        return BruteForceEstimate((candidates + 1.0) / (2.0 * rate), candidates / rate)
    }

    internal fun duration(seconds: Double): String {
        require(seconds > 0 && seconds.isFinite())
        if (seconds < 0.001) return "1ミリ秒未満"
        val (value, unit) = when {
            seconds < 1 -> seconds * 1000 to "ミリ秒"
            seconds < 60 -> seconds to "秒"
            seconds < 3600 -> seconds / 60 to "分"
            seconds < 86400 -> seconds / 3600 to "時間"
            seconds < SECONDS_PER_YEAR -> seconds / 86400 to "日"
            else -> seconds / SECONDS_PER_YEAR to "年"
        }
        if (value < 10_000) return "約 ${oneDecimal(value)}$unit"
        var exponent = kotlin.math.floor(kotlin.math.log10(value)).toInt()
        var mantissa = value / 10.0.pow(exponent)
        if (kotlin.math.round(mantissa * 10) >= 100) { exponent++; mantissa = 1.0 }
        val power = exponent.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
        return "約 ${oneDecimal(mantissa)} × 10$power$unit"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        return if (tenths % 10L == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }
}

class LessonTrialRate(val attemptsPerSecond: Long, val label: String)
class BruteForceEstimate internal constructor(val averageSeconds: Double, val exhaustiveSeconds: Double) {
    val averageLabel: String get() = BruteForceLesson.duration(averageSeconds)
    val exhaustiveLabel: String get() = BruteForceLesson.duration(exhaustiveSeconds)
}
