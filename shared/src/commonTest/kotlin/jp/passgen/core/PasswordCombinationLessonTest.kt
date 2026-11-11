package jp.passgen.core

import kotlin.test.*

class PasswordCombinationLessonTest {
    @Test fun allSixteenAlphabetCombinationsMatchIndependentIntegerPowers() {
        // Python integer powers, independent of the decimal multiplication implementation.
        val expected = listOf("0", "95428956661682176", "95428956661682176", "390877006486250192896", "1000000000000", "4738381338321616896", "4738381338321616896", "3226266762397899821056", "1152921504606846976", "1449225352009601191936", "1449225352009601191936", "123410307017276135571456", "30129469486639681536", "9774779120406941925376", "9774779120406941925376", "475920314814253376475136")
        expected.forEachIndexed { mask, count ->
            assertEquals(count, PasswordCombinationLesson.calculate(12, mask).exactCount)
        }
        assertEquals(94, PasswordCombinationLesson.calculate(12, 15).alphabetSize)
        assertEquals("94¹²", PasswordCombinationLesson.calculate(12, 15).formula)
    }
    @Test fun bruteForceTimesUseUniformWithoutReplacementModelAndSelectedRate() {
        val count = PasswordCombinationLesson.calculate(4, 4)
        val slow = BruteForceLesson.estimate(count, 0)!!
        assertEquals(5000.5, slow.averageSeconds)
        assertEquals(10000.0, slow.exhaustiveSeconds)
        assertEquals("約 1.4時間", slow.averageLabel)
        assertEquals("約 2.8時間", slow.exhaustiveLabel)
        BruteForceLesson.rates.forEachIndexed { i, rate ->
            val estimate = BruteForceLesson.estimate(count, i)!!
            assertEquals(slow.averageSeconds / rate.attemptsPerSecond, estimate.averageSeconds)
            assertEquals(slow.exhaustiveSeconds / rate.attemptsPerSecond, estimate.exhaustiveSeconds)
        }
        val fast = BruteForceLesson.estimate(count, 2)!!
        assertEquals("約 5ミリ秒", fast.averageLabel)
        assertEquals("約 10ミリ秒", fast.exhaustiveLabel)
        assertNull(BruteForceLesson.estimate(PasswordCombinationLesson.calculate(4, 0), 0))
        assertFailsWith<IllegalArgumentException> { BruteForceLesson.estimate(count, -1) }
        assertFailsWith<IllegalArgumentException> { BruteForceLesson.estimate(count, 4) }
        val huge = BruteForceLesson.estimate(PasswordCombinationLesson.calculate(128, 15), 0)!!
        assertTrue(huge.averageSeconds.isFinite())
        assertEquals("1兆年以上", huge.averageLabel)
        assertEquals("1兆年以上", huge.exhaustiveLabel)
    }

    @Test fun longDurationsUseJapaneseUnitsAndAnExplicitDisplayCap() {
        val year = BruteForceLesson.SECONDS_PER_YEAR
        val cases = listOf(9_999.0 to "約 9,999年", 10_000.0 to "約 1万年",
            25_000.0 to "約 2.5万年", 99_999_999.0 to "約 9,999.9万年",
            100_000_000.0 to "約 1億年", 350_000_000.0 to "約 3.5億年",
            999_999_999_999.0 to "約 9,999.9億年", 1_000_000_000_000.0 to "1兆年以上",
            1e100 to "1兆年以上")
        cases.forEach { (years, label) -> assertEquals(label, BruteForceLesson.duration(years * year)) }
        for (length in listOf(4, 8, 12, 16, 24)) for (mask in 1..15) for (rate in 0..3) {
            val result = BruteForceLesson.estimate(PasswordCombinationLesson.calculate(length, mask), rate)!!
            for (label in listOf(result.averageLabel, result.exhaustiveLabel)) {
                assertFalse(label.contains("×")); assertFalse(label.contains("E")); assertFalse(label.contains("Infinity"))
            }
        }
    }

    @Test fun durationUnitsCoverSmallAndLargeTimesWithoutZeroOrInfinity() {
        val cases = listOf(0.00001 to "1ミリ秒未満", 0.001 to "約 1ミリ秒", 1.0 to "約 1秒",
            60.0 to "約 1分", 3600.0 to "約 1時間", 86400.0 to "約 1日", BruteForceLesson.SECONDS_PER_YEAR to "約 1年")
        cases.forEach { (seconds, label) -> assertEquals(label, BruteForceLesson.duration(seconds)) }
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { BruteForceLesson.duration(bad) }
        }
    }

    @Test fun boundsEmptyAlphabetAndHugePowersRemainExact() {
        assertEquals("0", PasswordCombinationLesson.calculate(128, 0).exactCount)
        assertEquals("94", PasswordCombinationLesson.calculate(1, 15).exactCount)
        assertEquals("3633835478324481201225242625978813329407953758811975090418839126886926337041641709674462355789814144604420213345525420742933253111929523700011348060401654959739572669026479573347579342843495353819729154917174466884698510060100716571486301461610198204416", PasswordCombinationLesson.calculate(128, 15).exactCount)
        for (n in listOf(-1, 0, 129, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { PasswordCombinationLesson.calculate(n, 4) }
        for (mask in listOf(-1, 16, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { PasswordCombinationLesson.calculate(4, mask) }
    }
    @Test fun displayLabelsDistinguishExactFromApproximateNumbers() {
        val small = PasswordCombinationLesson.calculate(4, 4)
        assertEquals("10,000 通り", small.summary)
        assertFalse(small.isAbbreviated)
        val large = PasswordCombinationLesson.calculate(12, 15)
        assertEquals("475,920,314,814,253,376,475,136", large.formattedCount)
        assertEquals("約 4.759 × 10²³通り", large.summary)
        assertTrue(large.isAbbreviated)
        assertEquals(large.exactCount, large.formattedCount.replace(",", ""))
    }
}
