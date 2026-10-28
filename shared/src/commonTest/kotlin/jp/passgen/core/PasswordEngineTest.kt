package jp.passgen.core

import kotlin.math.log2
import kotlin.test.*

private val neverCancel = object : CancellationProbe { override fun isCancelled() = false }

/** Test-only deterministic stream; production cannot inject it through the public constructor. */
private class TestEntropy : EntropySource {
    var state = 0x12345678
    override fun fill(destination: ByteArray) {
        for (i in destination.indices) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            destination[i] = state.toByte()
        }
    }
}

class PasswordEngineTest {
    private fun engine() = PasswordEngine(TestEntropy())

    @Test fun defaultContainsEveryCategoryAndExcludesSimilar() {
        val engine = engine()
        repeat(100) {
            val result = engine.generate(GenerationRequest(), neverCancel)
            assertEquals(24, result.value.length)
            assertTrue(result.value.any { it in 'A'..'Z' })
            assertTrue(result.value.any { it in 'a'..'z' })
            assertTrue(result.value.any { it in '0'..'9' })
            assertTrue(result.value.any { it in SymbolPolicy.SUPPORTED })
            assertFalse(result.value.any { it in "Oo0Il1" })
            assertTrue(result.entropyBits > 128)
            assertFalse(result.toString().contains(result.value))
        }
    }

    @Test fun symbolsAreExactlyTheAllowlist() {
        val result = engine().generate(GenerationRequest(length = 1000, uppercase = false, lowercase = false,
            digits = false, symbols = "!?!!", requireEach = false), neverCancel)
        assertEquals(setOf('!', '?'), result.value.toSet())
        assertEquals(1000.0, result.entropyBits, 0.00001)
    }

    @Test fun shortRequiredCategoryGenerationTerminates() {
        repeat(40) {
            val result = engine().generate(GenerationRequest(length = 4, symbols = "!"), neverCancel)
            assertTrue(result.value.any { it == '!' })
            assertTrue(result.value.any { it.isUpperCase() })
            assertTrue(result.value.any { it.isLowerCase() })
            assertTrue(result.value.any { it.isDigit() })
        }
    }

    @Test fun noAdjacentIsRespectedWithoutDiscardingCategories() {
        val result = engine().generate(GenerationRequest(length = 2000, noAdjacent = true), neverCancel)
        assertTrue(result.value.zipWithNext().all { (a, b) -> a != b })
    }

    @Test fun numericIncludesZeroAndHasNoHiddenCharacterRules() {
        val request = GenerationRequest(length = 1000, uppercase = false, lowercase = false, symbolsEnabled = false, excludeSimilar = false)
        val result = engine().generate(request, neverCancel)
        assertEquals(('0'..'9').toSet(), result.value.toSet())
        assertEquals(1000 * log2(10.0), result.entropyBits, 0.000001)
    }

    @Test fun phraseUsesFullUniqueEffListAndDoesNotCreditSeparator() {
        assertEquals(7776, EffWords.words.size)
        assertEquals(7776, EffWords.words.toSet().size)
        assertEquals("abacus", EffWords.words.first())
        assertEquals("zoom", EffWords.words.last())
        val result = engine().generate(GenerationRequest(mode = GenerationMode.PASSPHRASE, separator = "."), neverCancel)
        assertEquals(7, result.value.split('.').size)
        assertTrue(result.value.split('.').all { it in EffWords.words })
        assertEquals(7 * log2(7776.0), result.entropyBits, 0.00001)
    }

    @Test fun invalidConfigurationsFailClosed() {
        val cases = listOf(
            GenerationRequest(length = 3), GenerationRequest(length = 1_000_000),
            GenerationRequest(uppercase = false, lowercase = false, digits = false, symbolsEnabled = false),
            GenerationRequest(symbols = ""), GenerationRequest(symbols = "!😀"),
            GenerationRequest(uppercase = false, lowercase = false, digits = false, symbols = "!", noAdjacent = true),
            GenerationRequest(mode = GenerationMode.PASSPHRASE, wordCount = 5),
            GenerationRequest(mode = GenerationMode.PASSPHRASE, separator = ""),
        )
        cases.forEach { assertEquals("INVALID_CONFIGURATION", assertFailsWith<GenerationException> { engine().generate(it, neverCancel) }.code) }
    }

    @Test fun randomFailureNeverFallsBack() {
        val failing = PasswordEngine(object : EntropySource {
            override fun fill(destination: ByteArray) { error("secret internal failure") }
        })
        val error = assertFailsWith<GenerationException> { failing.generate(GenerationRequest(), neverCancel) }
        assertEquals("RANDOM_UNAVAILABLE", error.code)
        assertFalse(error.toString().contains("secret internal"))
    }

    @Test fun cancellationHappensBeforeEntropyAndDuringLargeWork() {
        val alreadyCancelled = object : CancellationProbe { override fun isCancelled() = true }
        assertEquals("CANCELLED", assertFailsWith<GenerationException> { engine().generate(GenerationRequest(), alreadyCancelled) }.code)
        var checks = 0
        val cancelLater = object : CancellationProbe { override fun isCancelled() = ++checks > 4 }
        assertEquals("CANCELLED", assertFailsWith<GenerationException> {
            engine().generate(GenerationRequest(length = 999_999), cancelLater)
        }.code)
        assertTrue(checks <= 6)
    }

    @Test fun maximumLengthIsSupported() {
        val result = engine().generate(GenerationRequest(length = 999_999), neverCancel)
        assertEquals(999_999, result.value.length)
    }

    @Test fun strictConditionsCannotCauseAnInfiniteLoop() {
        val zero = PasswordEngine(object : EntropySource { override fun fill(destination: ByteArray) { destination.fill(0) } })
        assertEquals("CONDITIONS_TOO_STRICT", assertFailsWith<GenerationException> {
            zero.generate(GenerationRequest(length = 4), neverCancel)
        }.code)
    }

    @Test fun entropyCountsMatchExhaustiveEnumeration() {
        val alphabet = "aBC!"
        for (length in 4..6) for (noAdjacent in listOf(false, true)) {
            var count = 0
            fun enumerate(prefix: String) {
                if (prefix.length == length) { if (prefix.contains('a') && prefix.contains('!') && prefix.any { it in "BC" }) count++; return }
                alphabet.forEach { if (!noAdjacent || prefix.lastOrNull() != it) enumerate(prefix + it) }
            }
            enumerate("")
            assertEquals(log2(count.toDouble()), entropyBits(listOf(1, 2, 1), length, true, noAdjacent), 0.0000001)
        }
    }

    @Test fun osEntropyIntegrationProducesDifferentResults() {
        val engine = PasswordEngine()
        val values = (1..20).map { engine.generate(GenerationRequest(), neverCancel).value }
        assertEquals(20, values.toSet().size)
    }

    @Test fun individualCharactersAreAnExactAllowlist() {
        val result = engine().generate(GenerationRequest(length = 1000, uppercaseCharacters = "AZA",
            lowercaseCharacters = "mn", digitCharacters = "79", symbols = "!?"), neverCancel)
        assertEquals("AZmn79!?".toSet(), result.value.toSet())
        assertTrue(result.entropyBits < 3000.001)
        assertTrue(result.entropyBits > 2999)
    }

    @Test fun emptyOrUnsupportedEnabledCharacterSelectionsFailClosed() {
        listOf(GenerationRequest(uppercaseCharacters = ""), GenerationRequest(lowercaseCharacters = "a😀"),
            GenerationRequest(digitCharacters = "１"), GenerationRequest(uppercaseCharacters = "OI"),
            GenerationRequest(similarExclusions = "A")).forEach { request ->
            assertEquals("INVALID_CONFIGURATION", assertFailsWith<GenerationException> { engine().generate(request, neverCancel) }.code)
        }
    }

    @Test fun exclusionsAreIndividuallyConfigurable() {
        val result = engine().generate(GenerationRequest(length = 1000, uppercaseCharacters = "OI",
            lowercase = false, digits = false, symbolsEnabled = false, similarExclusions = "I"), neverCancel)
        assertTrue(result.value.all { it == 'O' })
        assertEquals(0.0, result.entropyBits)
    }

    @Test fun disabledCharacterCategoriesDoNotAffectDistribution() {
        val result = engine().generate(GenerationRequest(length = 100, uppercase = false, uppercaseCharacters = "",
            lowercase = false, digitCharacters = "79", symbolsEnabled = false), neverCancel)
        assertEquals(setOf('7', '9'), result.value.toSet())
        assertEquals(100.0, result.entropyBits, 0.0001)
    }

    @Test fun phraseSeparatorsPreserveWordsAndEntropy() {
        for (separator in listOf(" ", "-", "_", ".")) {
            val result = engine().generate(GenerationRequest(mode = GenerationMode.PASSPHRASE, separator = separator), neverCancel)
            assertEquals(7 * log2(7776.0), result.entropyBits, 0.00001)
            if (separator != "-") assertEquals(7, result.value.split(separator).size)
            assertTrue(separator in result.value)
        }
        // EFF's four hyphenated words start with fragments that are not words in the list.
        // The hyphen-token sequences therefore form a prefix-free code, so joining is injective.
        val code = EffWords.words.map { it.split('-') }
        assertTrue(code.filter { it.size > 1 }.all { parts ->
            parts.first() !in EffWords.words && code.none { other -> other != parts && other.size <= parts.size && parts.take(other.size) == other }
        })
    }
}

class PolicyTest {
    @Test fun referenceSymbolPasteBehavior() {
        assertEquals(30, SymbolPolicy.SUPPORTED.length)
        assertEquals("!#:", SymbolPolicy.applyPasted("使用可能: ! # !", "@"))
    }

    @Test fun rawPasteIsLiteralAndUnsupportedTextPreservesSelection() {
        assertEquals("!?#", SymbolPolicy.applyPasted("! # ? !", "@"))
        assertEquals("@", SymbolPolicy.applyPasted("abc😀＼", "@"))
        assertEquals("@", SymbolPolicy.applyPasted("", "@"))
        assertEquals("!:", SymbolPolicy.recognized("Allowed: !"))
    }

    @Test fun modelCannotInventSymbols() {
        assertEquals("!", SymbolPolicy.confirmedCandidate("!?😀", "Allowed ! only"))
        assertEquals("", SymbolPolicy.confirmedCandidate("#", "Only letters"))
    }

    @Test fun fullwidthAndRangeNormalization() {
        assertEquals(24, InputPolicy.normalizedInteger("２４", 12, 4, 100))
        assertEquals(4, InputPolicy.normalizedInteger("0", 12, 4, 100))
        assertEquals(100, InputPolicy.normalizedInteger("999999999999999999999999", 12, 4, 100))
        assertEquals(12, InputPolicy.normalizedInteger("-3", 12, 4, 100))
        assertEquals(12, InputPolicy.normalizedInteger("", 12, 4, 100))
        assertEquals(10, InputPolicy.maximumCount(999_999))
        assertEquals(1000, InputPolicy.maximumCount(24))
    }

    @Test fun rejectionSamplingRejectsBiasedTail() {
        val entropy = object : EntropySource {
            override fun fill(destination: ByteArray) {
                destination.fill(0)
                destination[0] = 255.toByte(); destination[1] = 255.toByte()
                destination[2] = 255.toByte(); destination[3] = 254.toByte()
                destination[4] = 0; destination[5] = 23
            }
        }
        val sampler = SecureSampler(entropy, neverCancel)
        assertEquals(3, sampler.nextInt(10))
        sampler.close()
    }

    @Test fun everyAccepted16BitValueHasEqualBucketCount() {
        var next = 0
        val entropy = object : EntropySource {
            override fun fill(destination: ByteArray) {
                for (i in destination.indices step 2) {
                    destination[i] = (next ushr 8).toByte(); destination[i + 1] = next.toByte(); next = (next + 1) and 65535
                }
            }
        }
        val sampler = SecureSampler(entropy, neverCancel)
        val counts = IntArray(10)
        repeat(65_530) { counts[sampler.nextInt(10)]++ }
        assertTrue(counts.all { it == 6_553 })
        sampler.close()
    }
}
