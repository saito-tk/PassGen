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

/** Illustrative rates, not measurements of a service, hash algorithm or device. */
object BruteForceLesson {
    val rates = listOf(
        LessonTrialRate(1, "1回/秒"), LessonTrialRate(1_000, "1,000回/秒"),
        LessonTrialRate(1_000_000, "100万回/秒"), LessonTrialRate(1_000_000_000, "10億回/秒"),
    )
    const val DISPLAY_YEAR_LIMIT = 1_000_000_000_000.0
    const val DISPLAY_LIMIT_NOTE = "1兆年以上は、細かい年数を省いた表示です。計算上の時間であり、安全を保証するものではありません。"
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
        if (unit != "年" || value < 10_000) return "約 ${oneDecimal(value)}$unit"
        if (value >= DISPLAY_YEAR_LIMIT) return "1兆年以上"
        val scale = if (value >= 100_000_000) 100_000_000.0 else 10_000.0
        val yearUnit = if (value >= 100_000_000) "億年" else "万年"
        // Keep rounding below the next named unit, especially the explicit display cap.
        val scaled = (value / scale).coerceAtMost(9999.9)
        return "約 ${oneDecimal(scaled)}$yearUnit"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        val whole = (tenths / 10).toString().reversed().chunked(3).joinToString(",").reversed()
        return if (tenths % 10L == 0L) whole else "$whole.${tenths % 10}"
    }
}

class LessonTrialRate(val attemptsPerSecond: Long, val label: String)
class BruteForceEstimate internal constructor(val averageSeconds: Double, val exhaustiveSeconds: Double) {
    val averageLabel: String get() = BruteForceLesson.duration(averageSeconds)
    val exhaustiveLabel: String get() = BruteForceLesson.duration(exhaustiveSeconds)
}
