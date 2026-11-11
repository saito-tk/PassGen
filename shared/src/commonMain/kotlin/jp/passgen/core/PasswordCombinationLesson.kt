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
    private val yearUnits = listOf("", "万", "億", "兆", "京", "垓", "秭", "穣", "溝", "澗")
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
        val (scaled, index) = yearScale(value)
        // The UI's largest case (94^24 at one guess/s) fits in 澗. Keep larger
        // shared-calculator values distinct with ordinary decimal notation too.
        if (index >= yearUnits.size) return plainYears(seconds)
        return "約 ${oneDecimal(scaled.coerceAtMost(9999.9))}${yearUnits[index]}年"
    }

    private fun yearScale(years: Double): Pair<Double, Int> {
        var scaled = years
        var index = 0
        while (scaled >= 10_000) { scaled /= 10_000; index++ }
        return scaled to index
    }

    internal fun yearUnitExplanation(seconds: Double): String? {
        val index = yearScale(seconds / SECONDS_PER_YEAR).second
        return if (index in 4 until yearUnits.size) {
            "1${yearUnits[index]}は、1${yearUnits[index - 1]}の1万倍です。"
        } else null
    }

    /** Three significant digits expanded to ordinary integers; not an exact forecast. */
    internal fun plainYears(seconds: Double): String {
        require(seconds >= SECONDS_PER_YEAR && seconds.isFinite())
        val parts = (seconds / SECONDS_PER_YEAR).toString().lowercase().split('e')
        val mantissa = parts[0]
        val digits = mantissa.replace(".", "")
        var positions = (mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length) +
            (parts.getOrNull(1)?.toInt() ?: 0)
        if (positions <= 3) return "約 ${oneDecimal(seconds / SECONDS_PER_YEAR)}年"
        val padded = digits.padEnd(4, '0')
        var leading = padded.take(3).toInt() + if (padded[3] >= '5') 1 else 0
        if (leading == 1000) { leading = 100; positions++ }
        val integer = leading.toString() + "0".repeat(positions - 3)
        return "約 ${integer.reversed().chunked(3).joinToString(",").reversed()}年"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        val whole = (tenths / 10).toString().reversed().chunked(3).joinToString(",").reversed()
        return if (tenths % 10L == 0L) whole else "$whole.${tenths % 10}"
    }
}

class LessonTrialRate(val attemptsPerSecond: Long, val label: String)
class BruteForceEstimate internal constructor(val averageSeconds: Double, val exhaustiveSeconds: Double) {
    val yearUnitExplanation: String? get() = listOfNotNull(
        BruteForceLesson.yearUnitExplanation(averageSeconds),
        BruteForceLesson.yearUnitExplanation(exhaustiveSeconds),
    ).distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")
    val showsYearNumbers: Boolean get() = exhaustiveSeconds / BruteForceLesson.SECONDS_PER_YEAR >= 1_000_000_000_000.0
    val averageYearNumber: String get() = BruteForceLesson.plainYears(averageSeconds)
    val exhaustiveYearNumber: String get() = BruteForceLesson.plainYears(exhaustiveSeconds)
    val averageLabel: String get() = BruteForceLesson.duration(averageSeconds)
    val exhaustiveLabel: String get() = BruteForceLesson.duration(exhaustiveSeconds)
}
