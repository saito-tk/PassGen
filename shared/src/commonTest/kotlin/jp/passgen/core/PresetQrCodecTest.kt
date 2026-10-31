package jp.passgen.core

import kotlin.test.*

class PresetQrCodecTest {
    private val vector = "PG1:H4MACAP77777777777742DYAZAHUK6DBNVYGYZP3KHHRA"

    @Test fun cameraCandidatesIgnoreForeignBrokenAndFutureCodes() {
        assertNull(PresetQrCodec.selectCandidate(emptyList()))
        val ignored = listOf("https://example.com/", "WIFI:T:WPA;S:demo;P:example;;", "PG99:UNKNOWN", "PG1:BROKEN", vector.dropLast(1) + "B", "PG1:" + "A".repeat(10000))
        assertNull(PresetQrCodec.selectCandidate(ignored))
        assertEquals(vector, PresetQrCodec.selectCandidate(ignored + vector + vector))
        val other = PresetQrCodec.encode(SharedPreset("Other", PresetQrCodec.decode(vector).request, 1))
        assertNull(PresetQrCodec.selectCandidate(listOf(vector, other)))
        assertEquals(other, PresetQrCodec.selectCandidate(listOf(other, other)))
        assertNull(PresetQrCodec.selectCandidate(List(33) { vector }))
    }

    @Test fun independentKnownVectorAndCompactSize() {
        // Independently encoded using Python base64.b32encode / zlib.crc32, not this implementation.
        val p = SharedPreset("Example", GenerationRequest(symbols = SymbolPolicy.recognized(GenerationRequest().symbols)), 1)
        assertEquals(vector, PresetQrCodec.encode(p))
        val restored = PresetQrCodec.decode(vector)
        assertEquals("Example", restored.name)
        assertEquals(1, restored.count)
        assertEquals(vector, PresetQrCodec.encode(restored))
        assertTrue(vector.length < 60)
        // Non-default values, also independently encoded, freeze flag positions and variable integers.
        val legacy = "PG1:Y27YIPPIA4PACAAABJAAAQGI77776PYBJRSWOYLDPHYJ7FERCM5WVNA"
        val expected = SharedPreset("Legacy🔑", GenerationRequest(mode = GenerationMode.PASSPHRASE,
            length = 999999, wordCount = 12, separator = ".", uppercase = false, lowercase = true,
            digits = true, symbolsEnabled = false, excludeSimilar = false, requireEach = false,
            noAdjacent = true, uppercaseCharacters = "AZ", lowercaseCharacters = "bm", digitCharacters = "27",
            symbols = "-_@/*+.,!?#\u0024%&(){}[]~|:;\"'^><=`\\", similarExclusions = "0"), 1000)
        assertEquals(legacy, PresetQrCodec.encode(expected))
        assertEquals(legacy, PresetQrCodec.encode(PresetQrCodec.decode(legacy)))
    }

    @Test fun everyFlagAndAllSelectionsRoundTripIncludingInactiveSettings() {
        repeat(256) { flags ->
            fun flag(i: Int) = flags and (1 shl i) != 0
            val p = SharedPreset("設定🔑", GenerationRequest(
                mode = if (flag(7)) GenerationMode.PASSPHRASE else GenerationMode.PASSWORD,
                length = 999999, wordCount = 6 + flags % 7, separator = listOf(" ", "-", "_", ".")[flags % 4],
                uppercase = flag(0), lowercase = flag(1), digits = flag(2), symbolsEnabled = flag(3),
                excludeSimilar = flag(4), requireEach = flag(5), noAdjacent = flag(6),
                uppercaseCharacters = if (flag(1)) "AZ" else "", lowercaseCharacters = "az",
                digitCharacters = "09", symbols = if (flag(0)) SymbolPolicy.SUPPORTED else "",
                similarExclusions = if (flag(2)) "Ol1" else ""), if (flag(7)) 1000 else 10)
            val q = PresetQrCodec.decode(PresetQrCodec.encode(p)); val r = q.request
            assertEquals(p.name, q.name); assertEquals(p.count, q.count)
            assertEquals(p.request.mode, r.mode); assertEquals(999999, r.length)
            assertEquals(p.request.wordCount, r.wordCount); assertEquals(p.request.separator, r.separator)
            assertEquals(flag(0), r.uppercase); assertEquals(flag(1), r.lowercase)
            assertEquals(flag(2), r.digits); assertEquals(flag(3), r.symbolsEnabled)
            assertEquals(flag(4), r.excludeSimilar); assertEquals(flag(5), r.requireEach); assertEquals(flag(6), r.noAdjacent)
            assertEquals(p.request.uppercaseCharacters, r.uppercaseCharacters)
            assertEquals("az", r.lowercaseCharacters); assertEquals("09", r.digitCharacters)
            assertEquals(p.request.symbols, r.symbols); assertEquals(p.request.similarExclusions, r.similarExclusions)
        }
        val maximumName = SharedPreset("字".repeat(40), GenerationRequest(length = 999999, symbols = SymbolPolicy.SUPPORTED), 10)
        assertTrue(PresetQrCodec.encode(maximumName).length <= PresetQrCodec.MAX_TEXT_LENGTH)
        assertEquals(maximumName.name, PresetQrCodec.decode(PresetQrCodec.encode(maximumName)).name)
    }

    @Test fun damageUnknownVersionsAndUntrustedTextAreRejected() {
        for (i in 4 until vector.length) {
            val changed = vector.replaceRange(i, i + 1, if (vector[i] == 'A') "B" else "A")
            assertFailsWith<PresetTransferException> { PresetQrCodec.decode(changed) }
        }
        listOf("", "https://example.com", vector.replace("PG1:", "PG2:"), vector.lowercase(),
            vector + "=", vector + "A", vector.dropLast(1), "PG1:" + "A".repeat(253)).forEach {
            assertFailsWith<PresetTransferException> { PresetQrCodec.decode(it) }
        }
        // Correct CRC, invalid semantic values / reserved bits / UTF-8 / names.
        listOf(
            "PG1:H4BQCAP77777777777742DYAZAHUK6DBNVYGYZNMVXQWO",
            "PG1:H4MAAAP77777777777742DYAZAHUK6DBNVYGYZJ3GXTQO",
            "PG1:H4MACIH77777777777742DYAZAHUK6DBNVYGYZN567ORQ",
            "PG1:H4MACAP77777777777742DYAZD7UK6DBNVYGYZNRIWSO6",
            "PG1:H4MACAP77777777777742DYAZAHWPXCYWQ",
            "PG1:H4MACAP77777777777742DYAZAHWEYLEBJXGC3LF2QXURTY",
            "PG1:H4MACAP77777777777742DYAZAH76J5MMIWA",
        ).forEach { assertFailsWith<PresetTransferException> { PresetQrCodec.decode(it) } }
    }

    @Test fun exporterDoesNotSilentlyChangeInvalidSettings() {
        listOf(SharedPreset("", GenerationRequest(), 1), SharedPreset("a".repeat(41), GenerationRequest(), 1),
            SharedPreset("line\nname", GenerationRequest(), 1), SharedPreset(" name", GenerationRequest(), 1),
            SharedPreset("name", GenerationRequest(length = 3), 1),
            SharedPreset("name", GenerationRequest(length = 999999), 11),
            SharedPreset("name", GenerationRequest(symbols = "！"), 1),
            SharedPreset("name", GenerationRequest(uppercaseCharacters = "ZA"), 1),
            SharedPreset("name", GenerationRequest(separator = "/"), 1)).forEach {
            assertFailsWith<PresetTransferException> { PresetQrCodec.encode(it) }
        }
    }
}
