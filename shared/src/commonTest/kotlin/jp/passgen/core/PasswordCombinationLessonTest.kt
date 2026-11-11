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
