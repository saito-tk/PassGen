package jp.passgen.core

import kotlin.test.*

class SymbolRuleSyntaxTest {
    private fun symbols(text: String): String? = SymbolRuleSyntax.parse(text)?.spans?.joinToString("") { it.symbols }?.let(SymbolPolicy::recognized)
    @Test fun commonPrefixSuffixAndQuotedFormatsSelectValuesNotFormatting() {
        val cases = listOf(
            "使用可能な記号: ! @ #" to "!@#", "使える記号は !@# です" to "!@#",
            "!@# の記号が使用できます" to "!@#", "記号は「!@#」が使用可能です。" to "!@#",
            "使用可能な記号: '!', '@', '#'" to "!@#", "使用可能な記号: !, @, #" to "!@#",
            "使用可能な記号（!@#）" to "!@#", "使用可能な記号（!@#()）" to "!@#()",
            "Symbols allowed: !@#" to "!@#", "Allowed symbols: !@#" to "!@#",
            "!@# symbols can be used" to "!@#", "Special characters: -_" to "-_",
            "記号: -_" to "-_", "使用可能な記号: ！＠＃" to "!@#",
            "次の記号以外は使用できません: !@#" to "!@#",
            "😀 使用可能な記号: !@#" to "!@#",
            "使用できる特殊文字は『!@#』です。" to "!@#",
            "使用可能な記号: !@# (8文字以上)" to "!@#",
            "使用可能な記号（半角）: !@#" to "!@#", "Special characters (ASCII): !@#" to "!@#",
            "使用可能な記号 !@#" to "!@#"
        )
        for ((text, expected) in cases) {
            assertEquals(SymbolPolicy.recognized(expected), symbols(text), text)
            for (span in assertNotNull(SymbolRuleSyntax.parse(text), text).spans) {
                assertTrue(span.start >= 0 && span.end <= text.length && span.start < span.end, text)
                assertTrue(span.symbols.all { it in SymbolRuleSyntax.narrowWidth(text.substring(span.start, span.end)) }, text)
            }
        }
    }
    @Test fun noisyGlyphRowsRetainTheEntireSourceWithoutSelectingDecoration() {
        for (text in listOf("）次の記号も任意で使用可能です。！\"#$%&'（）*+，ー/：", "次の記号も任意で使用可能です。!#ッー/:;", "使用可能な記号: !#ッー/:;")) {
            val parsed = assertNotNull(SymbolRuleSyntax.parse(text), text)
            val source = text.substring(parsed.spans.first().start, parsed.spans.first().end)
            assertFalse(source.contains("記号"), text)
            assertTrue('#' in SymbolRuleSyntax.narrowWidth(source), text)
            assertTrue(source.trimEnd().endsWith(":" ) || source.trimEnd().endsWith("：") || source.trimEnd().endsWith(";"), text)
            assertTrue('@' in parsed.modelVocabulary, text)
            assertFalse('ッ' in parsed.spans.joinToString("") { it.symbols })
        }
    }
    @Test fun modelCanCorrectAValueWithoutAddingQuotationOrSeparatorCharacters() {
        val parsed = assertNotNull(SymbolRuleSyntax.parse("使用可能な記号: '!', '.', '#'"))
        assertTrue('@' in parsed.modelVocabulary)
        assertFalse('\'' in parsed.modelVocabulary); assertFalse(',' in parsed.modelVocabulary)
        assertTrue(',' in assertNotNull(SymbolRuleSyntax.parse("使用可能な記号: ','")).modelVocabulary)
    }
    @Test fun aSuffixRuleRetainsEarlierMisreadGlyphsInThePixelSpan() {
        val text = "! 0 # の記号が使用できます"
        val parsed = assertNotNull(SymbolRuleSyntax.parse(text))
        assertEquals("! 0 # ", text.substring(parsed.spans.first().start, parsed.spans.first().end))
        assertTrue('@' in parsed.modelVocabulary)
        assertNull(SymbolRuleSyntax.parse("8-20 !@# の記号が使用できます"))
    }
    @Test fun wordingAndLiteralFormatsComposeWithoutChangingTheSetOrOffsets() {
        val values = listOf("! @ #", "!@#", "「!@#」", "'!', '@', '#'", "!, @, #", "！＠＃", "＇！＇，＇＠＇，＇＃＇")
        for (value in values) {
            val cases = listOf("使用可能な記号: $value", "使える記号は $value です", "$value の記号が使用できます", "Allowed symbols: $value", "$value symbols can be used", "次の記号以外は使用できません: $value", "使用可能な記号（半角）: $value", "記号: $value")
            for (text in cases) assertEquals(SymbolPolicy.recognized("!@#"), symbols(text), text)
        }
    }
    @Test fun punctuationUsedAsAQuotedValueIsPreserved() {
        assertEquals(setOf('"', '\'', ',', '\\'), symbols("使用可能な記号: '\"', \"'\", ',', '\\'")?.toSet())
    }
    @Test fun namesNeedAnAgreeingVisibleGlyph() {
        assertEquals(SymbolPolicy.recognized("-_"), symbols("使用可能な記号: ハイフン（-）、アンダースコア（_）"))
        assertEquals(SymbolPolicy.recognized("-_"), SymbolRuleSyntax.namedItems("・ハイフン（-）、アンダースコア（_）")?.spans?.joinToString("") { it.symbols }?.let(SymbolPolicy::recognized))
        assertNull(SymbolRuleSyntax.namedItems("ハイフン（_）"))
        assertNull(symbols("使用可能な記号: ハイフン、シャープ"))
    }
    @Test fun negativesRangesAndAddressesNeverBecomeAnInferredAllowlist() {
        for (text in listOf("使用不可の記号: !@#", "使える記号以外は禁止 !", "使用可能な記号: !@# ただし#は禁止", "使用可能な記号: regex [!-~]", "使用可能な記号: U+0021〜U+002F", "記号の入力例: Password!1", "使用可能な記号: https://a.b/?!#$%&", "使用可能な記号: a+b@x.io?!#$%", "使用可能な記号: ￥€", "使用可能な記号: ! '@'", "使用可能な記号: '!' @", "使用可能な記号: 'l', '#'", "使える記号は「!」「@」と#です", "使用可能な記号: ！ '@'", "使用可能な記号: ハイフン(-)、@", "記号: ********", "記号: --------", "記号: ''")) {
            assertNull(symbols(text), text)
        }
        assertFalse(SymbolRuleSyntax.isPermission("not allowed symbols: !@#"))
        assertFalse(SymbolRuleSyntax.isPermission("invalid special characters: !@#"))
        assertFalse(SymbolRuleSyntax.isPermission("次の記号は使用できません: !@#"))
        assertNull(SymbolRuleSyntax.parse("記号: " + "!".repeat(2000)))
    }
}
