package jp.passgen.core

import kotlin.test.*

class SimilarExclusionTest {
    @Test fun groupsAreDistinctAndLegacyDefaultsStayUnchanged() {
        assertEquals("Oo0Il1", GenerationRequest().similarExclusions)
        assertEquals(CharacterPolicy.SIMILAR, CharacterPolicy.SIMILAR_GROUPS.joinToString(""))
        assertEquals(CharacterPolicy.SIMILAR.length, CharacterPolicy.SIMILAR.toSet().size)
        assertEquals(9, CharacterPolicy.SIMILAR_GROUPS.size)
    }

    @Test fun bulkAndIndividualEditsPreserveBlockedPreferences() {
        val policy = CharacterPolicy
        var selected = policy.editedSimilarExclusions("|", policy.SIMILAR, true, "|;")
        assertEquals(policy.SIMILAR.filterNot { it == ';' }, selected)
        selected = policy.editedSimilarExclusions(selected, policy.SIMILAR, false, "|;")
        assertEquals("|", selected)
        selected = policy.editedSimilarExclusions(selected, "Il1|", true, "|")
        assertEquals("Il1|", selected)
        selected = policy.editedSimilarExclusions(selected, "l", false, "|")
        assertEquals("I1|", selected)
        assertEquals("I1", policy.editedSimilarExclusions(selected, "|", false, ""))
        assertEquals("I1|", policy.editedSimilarExclusions(selected, "A！", true, ""))
    }

    @Test fun generatorAndEntropyApplyBothPoliciesToAllCharacterTypes() {
        val r = GenerationRequest(length = 64, symbols = SymbolPolicy.SUPPORTED,
            similarExclusions = CharacterPolicy.SIMILAR, blockedSymbols = "!@")
        val alphabet = CharacterPolicy.effectivePasswordAlphabet(r)
        assertTrue(alphabet.none { it in CharacterPolicy.SIMILAR || it in "!@" })
        val engine = PasswordEngine()
        val cancel = object : CancellationProbe { override fun isCancelled() = false }
        repeat(20) {
            val secret = engine.generate(r, cancel)
            assertEquals(64, secret.length)
            assertTrue(secret.value.all { it in alphabet })
        }
        val off = GenerationRequest(uppercase = false, lowercase = false, digits = false,
            symbols = "|;!?", excludeSimilar = false, blockedSymbols = "!")
        assertEquals(SymbolPolicy.recognized("|;?"), CharacterPolicy.effectivePasswordAlphabet(off))
        val empty = GenerationRequest(uppercase = false, lowercase = false, digits = false,
            symbols = "|;!", similarExclusions = "|;", blockedSymbols = "!")
        assertEquals("", CharacterPolicy.effectivePasswordAlphabet(empty))
        assertFailsWith<GenerationException> { engine.generate(empty, cancel) }
    }

    @Test fun pg2HasIndependentFixtureAndPreservesEveryAdditionalBit() {
        val fixture = "PG2:H4MACAP7777777777777777777776H2HOJXXK4DTSEZWCAQ"
        val request = GenerationRequest(symbols = SymbolPolicy.SUPPORTED, similarExclusions = CharacterPolicy.SIMILAR, blockedSymbols = "|;")
        assertEquals(fixture, PresetQrCodec.encode(SharedPreset("Groups", request, 1)))
        val decoded = PresetQrCodec.decode(fixture)
        assertEquals(CharacterPolicy.SIMILAR, decoded.request.similarExclusions)
        assertEquals("", decoded.request.blockedSymbols)
        assertEquals(fixture, PresetQrCodec.encode(decoded))
        assertEquals(fixture, PresetQrCodec.selectCandidate(listOf("https://example.com", fixture)))
        CharacterPolicy.SIMILAR.forEach { c ->
            val preset = SharedPreset("字".repeat(40), GenerationRequest(symbols = SymbolPolicy.SUPPORTED, similarExclusions = c.toString()), 1)
            val qr = PresetQrCodec.encode(preset)
            assertEquals(c.toString(), PresetQrCodec.decode(qr).request.similarExclusions)
            assertEquals(c !in "Oo0Il1", qr.startsWith("PG2:"))
        }
        for (i in 4 until fixture.length) {
            assertFailsWith<PresetTransferException> { PresetQrCodec.decode(fixture.replaceRange(i, i + 1, if (fixture[i] == 'A') "B" else "A")) }
        }
    }
}
