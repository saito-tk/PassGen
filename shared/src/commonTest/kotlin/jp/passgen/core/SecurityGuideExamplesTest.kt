package jp.passgen.core

import kotlin.test.*

class SecurityGuideExamplesTest {
    @Test fun numericComparisonUsesOneConsistentAlphabetIncludingLeadingZero() {
        for (example in SecurityGuideExamples.numericExamples) {
            assertTrue(example.value.all { it in '0'..'9' })
            assertEquals((1..example.length).fold(1L) { count, _ -> count * 10 }, example.candidateCount)
        }
    }

    @Test fun publicShapeExamplesMatchTheDescribedPasswordAndEffPhrase() {
        val sample = SecurityGuideExamples.PASSWORD
        assertEquals(GenerationRequest().length, sample.length)
        assertTrue(sample.all { it in CharacterPolicy.UPPERCASE + CharacterPolicy.LOWERCASE + CharacterPolicy.DIGITS + SymbolPolicy.SUPPORTED })
        for (alphabet in listOf(CharacterPolicy.UPPERCASE, CharacterPolicy.LOWERCASE, CharacterPolicy.DIGITS, SymbolPolicy.SUPPORTED)) {
            assertTrue(sample.any { it in alphabet })
        }
        assertEquals(GenerationRequest().wordCount, SecurityGuideExamples.phraseWords.size)
        assertTrue(SecurityGuideExamples.phraseWords.all { it in EffWords.words })
        for (separator in listOf(" ", "-", "_")) {
            assertEquals(SecurityGuideExamples.phraseWords, SecurityGuideExamples.phrase(separator).split(separator))
        }
        assertFailsWith<IllegalArgumentException> { SecurityGuideExamples.phrase("/") }
    }

    @Test fun lookalikeUrlActuallyBelongsToADifferentReservedExampleDomain() {
        val expected = SecurityGuideExamples.EXPECTED_URL.removePrefix("https://")
        val lookalike = SecurityGuideExamples.LOOKALIKE_URL.removePrefix("https://")
        assertTrue(lookalike.startsWith(expected + "."))
        assertEquals("example.com", expected.split('.').takeLast(2).joinToString("."))
        assertEquals("example.net", lookalike.split('.').takeLast(2).joinToString("."))
    }
}
