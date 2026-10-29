package jp.passgen.core

/** UTF-16 offsets into the original OCR string. Only observed literal glyphs are returned. */
class SymbolRuleSpan(val start: Int, val end: Int, val symbols: String)
class SymbolRuleMatch(val spans: List<SymbolRuleSpan>, val excludedFormatting: String = "", val exactValues: Boolean = false) {
    /** Keep format delimiters out while allowing the image model to correct OCR. */
    val modelVocabulary: String get() = SymbolPolicy.SUPPORTED.filter { character ->
        spans.any { character in it.symbols } || (!exactValues && character !in excludedFormatting)
    }
}

/** Bounded syntax interpretation shared by the native clients; no OCR, I/O or generation. */
object SymbolRuleSyntax {
    private val negatives = listOf("禁止", "不可", "できません", "できない", "使えません", "使えない", "ありません", "notallowed", "notpermitted", "notsupported", "notvalid", "notaccepted", "cannot", "can't", "mustnot", "donot", "don't", "disallowed", "unsupported", "forbidden", "prohibited", "except", "以外", "除く", "使用しない", "利用しない", "含めない")
    private val positives = listOf("使用可能", "利用可能", "使用可", "利用可", "使える", "使えます", "使用できる", "使用できます", "利用できる", "利用できます", "使うことができます")
    private val englishPositive = Regex("\\b(?:allowed|supported|permitted|accepted|valid|can\\s+(?:be\\s+)?use[ds]?|may\\s+be\\s+used)\\b", RegexOption.IGNORE_CASE)
    private val referenceOnly = Regex("(?:以下|次|これら|上記)の(?:記号|特殊文字)以外は(?:使用|利用)(?:できません|不可|禁止)")
    private const val NOUN = "(?:特殊文字|使用可能な文字|使用できる文字|記号(?:類|一覧)?|シンボル|special\\s+characters?|symbols?|characters?)"
    private const val LABEL = "$NOUN(?:\\s*[（(](?:半角|半角ASCII|ASCII|half[- ]?width)[)）])?"
    private val symbolNoun = Regex("(?:特殊文字|記号|シンボル|使用可能な文字|使用できる文字)|\\b(?:symbols?|special\\s+characters?|characters?)\\b", RegexOption.IGNORE_CASE)
    private val heading = Regex("^\\s*(?:[【［\\[])?$LABEL(?:[】］\\]])?\\s*(?:[:：]|$)", RegexOption.IGNORE_CASE)
    private val colon = Regex("$LABEL(?:\\s+(?:allowed|permitted|accepted|supported|valid))?(?:\\s+(?:are|is))?\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
    private val particle = Regex("$LABEL(?:は|には|として)\\s*[、,:：]?\\s*", RegexOption.IGNORE_CASE)
    private val inputExample = Regex("入力(?:方法|するキー|例)|パスワード(?:の)?例|\\b(?:keyboard|typing|password\\s+example|example\\s+password)\\b", RegexOption.IGNORE_CASE)
    private val rangeNotation = Regex("正規表現|\\b(?:regex|ascii\\s+(?:range|code))\\b|U\\+[0-9a-f]{4}|0x[0-9a-f]{2}|\\[!-~\\]", RegexOption.IGNORE_CASE)
    private val names = mapOf("ハイフン" to "-", "アンダースコア" to "_", "アンダーバー" to "_", "ドット" to ".", "ピリオド" to ".", "アットマーク" to "@", "シャープ" to "#", "プラス" to "+", "スラッシュ" to "/", "バックスラッシュ" to "\\", "パーセント" to "%", "hyphen" to "-", "underscore" to "_", "dot" to ".", "period" to ".", "at sign" to "@", "hash" to "#", "plus" to "+", "slash" to "/", "backslash" to "\\", "percent" to "%")
    private val named = Regex("(${names.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})\\s*[（(]\\s*([^()（）]+)\\s*[)）]", RegexOption.IGNORE_CASE)
    private fun compact(text: String) = text.lowercase().filterNot(Char::isWhitespace)
    fun hasRestriction(text: String): Boolean = negatives.any { it in compact(text) }
    fun hasSymbolLabel(text: String): Boolean = text.length <= 1000 && symbolNoun.containsMatchIn(text)
    fun isPermission(text: String): Boolean {
        if (text.length > 1000) return false
        val value = compact(text)
        val reference = referenceOnly.find(value)
        if (reference != null && !hasRestriction(value.removeRange(reference.range))) return true
        return !hasRestriction(text) && (positives.any { it in value } || englishPositive.containsMatchIn(text))
    }
    fun isLiteralHeading(text: String): Boolean = text.length <= 1000 && !hasRestriction(text) && !inputExample.containsMatchIn(text) && heading.containsMatchIn(text)
    fun narrowWidth(text: String): String = text.map { if (it in '\uFF01'..'\uFF5E') (it.code - 0xFEE0).toChar() else it }.joinToString("")
    private fun address(text: String): Boolean = text.contains("://") || text.lowercase().contains("www.") || Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE).containsMatchIn(text)
    private fun literal(text: String): String? {
        val value = narrowWidth(text)
        if (value.length > 128 || value.none { it in SymbolPolicy.SUPPORTED } || value.any { !it.isWhitespace() && it !in SymbolPolicy.SUPPORTED && it !in "、・" }) return null
        return SymbolPolicy.recognized(value)
    }
    private fun span(text: String, start: Int, end: Int): SymbolRuleSpan? = literal(text.substring(start, end))?.let { SymbolRuleSpan(start, end, it) }
    fun reviewReason(text: String): String? {
        if (!hasSymbolLabel(text)) return null
        return when {
            rangeNotation.containsMatchIn(text) -> "範囲・正規表現の記号は展開していません。使用できる記号を手動で確認してください。"
            inputExample.containsMatchIn(text) -> "入力例・入力方法は、使用できる記号の一覧として取り込んでいません。"
            hasRestriction(text) && !isPermission(text) -> "禁止・除外条件を含むため、許可記号を推測して選択していません。"
            text.any { it in "￥¥€£" } -> "対応外の字形を別の記号へ変換していません。半角ASCIIの記号を確認してください。"
            valueScope(text)?.let { itemReviewReason(text.substring(it.start, it.end)) } != null -> valueScope(text)?.let { itemReviewReason(text.substring(it.start, it.end)) }
            valueScope(text)?.let { named.containsMatchIn(text.substring(it.start, it.end)) && namedItems(text.substring(it.start, it.end)) == null } == true -> "記号名と字形を確かめられませんでした。画像と照合して手動で選択してください。"
            else -> null
        }
    }
    /** A name is never enough: it must agree with its explicitly printed glyph. */
    fun namedItems(text: String): SymbolRuleMatch? {
        if (text.length > 1000 || hasRestriction(text) || inputExample.containsMatchIn(text)) return null
        val matches = named.findAll(text).toList()
        if (matches.isEmpty() || matches.size > 32) return null
        val remaining = named.replace(text, "").replace(Regex("\\b(?:and|or)\\b|および|及び|と", RegexOption.IGNORE_CASE), "")
            .trim { it.isWhitespace() || it in ",、・" }
        val prose = remaining == "です" || remaining == "です。" || isPermission(remaining)
        if (remaining.isNotEmpty() && (!prose || narrowWidth(remaining.removeSuffix(".")).any { it in SymbolPolicy.SUPPORTED })) return null
        val spans = matches.map { match ->
            val value = match.groups[2] ?: return null
            val expected = names[match.groupValues[1].lowercase()] ?: return null
            if (narrowWidth(value.value.trim()) != expected) return null
            span(text, value.range.first, value.range.last + 1) ?: return null
        }
        return SymbolRuleMatch(spans, "(),:.", exactValues = true)
    }
    private class ValueScope(val start: Int, val end: Int, val formatting: String = "")
    private fun valueScope(text: String): ValueScope? {
        val positions = text.indices.filterNot { text[it].isWhitespace() }
        val compactSource = positions.map { text[it] }.joinToString("")
        referenceOnly.find(compactSource)?.let { reference ->
            var start = positions[reference.range.last] + 1
            while (start < text.length && (text[start].isWhitespace() || text[start] in ":：")) start++
            return ValueScope(start, text.length, ":")
        }
        val prefix = colon.find(text) ?: particle.find(text)
        if (prefix != null) return ValueScope(prefix.range.last + 1, text.length, ":")
        val annotation = Regex("$NOUN\\s*[（(]", RegexOption.IGNORE_CASE).find(text)
        if (annotation != null) {
            val start = annotation.range.last + 1
            var depth = 1
            for (index in start until text.length) {
                if (text[index] in "（(") depth++
                if (text[index] in "）)") depth--
                if (depth == 0) return ValueScope(start, index, "():")
            }
            return null
        }
        val sentence = Regex("(?:です|ます)[。．.]\\s*").find(text)
        if (sentence != null && sentence.range.last + 1 < text.length) return ValueScope(sentence.range.last + 1, text.length, ":")
        val suffix = Regex("(?:の)?$NOUN(?:が|は|を|\\s+(?:can|may|are|is))", RegexOption.IGNORE_CASE).find(text)
        if (suffix != null && isPermission(text.substring(suffix.range.first))) {
            val before = text.substring(0, suffix.range.first)
            val clause = Regex("(?:では|には|として|は)\\s*").findAll(before).lastOrNull()
            return ValueScope(clause?.range?.last?.plus(1) ?: 0, suffix.range.first)
        }
        val noun = symbolNoun.findAll(text).lastOrNull() ?: return null
        return ValueScope(noun.range.last + 1, text.length, ":")
    }
    fun parse(text: String): SymbolRuleMatch? {
        if (text.length > 1000 || (!isPermission(text) && !isLiteralHeading(text)) || reviewReason(text) != null) return null
        val scope = valueScope(text) ?: return null
        val value = text.substring(scope.start, scope.end)
        if (address(narrowWidth(value)) || itemReviewReason(value) != null) return null
        val result = namedItems(value) ?: quotedSpans(value).takeIf { it.isNotEmpty() }?.let { SymbolRuleMatch(it, "\"',:.[]") }
            ?: plainPrefix(value, 0) ?: return null
        return SymbolRuleMatch(result.spans.map { SymbolRuleSpan(it.start + scope.start, it.end + scope.start, it.symbols) },
            result.excludedFormatting + scope.formatting, result.exactValues)
    }
    private fun observedSpan(text: String, start: Int, end: Int): SymbolRuleSpan? {
        span(text, start, end)?.let { return it }
        val value = narrowWidth(text.substring(start, end)).filterNot(Char::isWhitespace)
        if (value.isEmpty() || value.length > 96 || Regex("[0-9]+[-〜][0-9]+").containsMatchIn(value)) return null
        val known = value.count { it in SymbolPolicy.SUPPORTED }
        if (known < 1 || known * 2 < value.length || value.length - known > 3) return null
        return SymbolRuleSpan(start, end, SymbolPolicy.recognized(value))
    }
    private val quoted = Regex("(?<![A-Za-z0-9])(?:\"([^\"]*)\"|'([^']*)'|「([^」]*)」|『([^』]*)』|“([^”]*)”|‘([^’]*)’)")
    private fun quoteMatches(text: String) = quoted.findAll(narrowWidth(text)).toList()
    private fun quoteValues(text: String) = quoteMatches(text).map { match ->
        (1..6).mapNotNull { match.groups[it] }.first()
    }
    fun itemReviewReason(text: String): String? {
        if (text.length > 1000) return "記号の一覧が長すぎます。対象部分に絞ってください。"
        val compact = narrowWidth(text).filterNot(Char::isWhitespace)
        if (compact.length > 3 && compact.toSet().size == 1) return "伏せ字や装飾線を記号の一覧として取り込んでいません。"
        if (rangeNotation.containsMatchIn(text)) return "範囲表記を推測して展開していません。"
        if (text.any { it in "￥¥€£" }) return "対応外の記号を推測で変換していません。"
        val values = quoteValues(text)
        if (values.isNotEmpty() && values.all { it.value.isEmpty() } && quoted.replace(narrowWidth(text), "").isBlank())
            return "引用内に記号を確認できませんでした。画像と照合して選択してください。"
        if (values.any { it.value.isNotEmpty() } && values.any { literal(it.value) == null })
            return "引用内の記号を確かめられませんでした。書式の記号を推測して追加していません。"
        if (values.any { it.value.isNotEmpty() }) {
            val matches = quoteMatches(text)
            val before = text.substring(0, matches.first().range.first)
            if (narrowWidth(before).any { it in SymbolPolicy.SUPPORTED }) return "引用と直接列挙が混在しています。対象の記号を画像と照合して選択してください。"
            for (index in 1 until matches.size) {
                val between = narrowWidth(text.substring(matches[index - 1].range.last + 1, matches[index].range.first))
                    .replace(Regex("\\b(?:and|or)\\b|および|及び|と", RegexOption.IGNORE_CASE), "")
                if (between.any { !it.isWhitespace() && it !in ",、・/／" }) return "記号の区切りを確かめられませんでした。対象の一覧に絞ってください。"
            }
            val after = text.substring(matches.last().range.last + 1).trim()
            if (narrowWidth(after.removeSuffix(".")).any { it in SymbolPolicy.SUPPORTED })
                return "引用の外にも記号があります。対象の記号を手動で確認してください。"
        }
        return null
    }
    private fun quotedSpans(text: String): List<SymbolRuleSpan> {
        if (itemReviewReason(text) != null) return emptyList()
        return quoteValues(text).takeIf { it.size <= 32 }?.mapNotNull { value -> span(text, value.range.first, value.range.last + 1) } ?: emptyList()
    }
    /** Formatted body rows, usable only after the native client locates a rule. */
    fun items(text: String): SymbolRuleMatch? {
        if (text.length > 1000 || hasRestriction(text) || address(narrowWidth(text)) || itemReviewReason(text) != null) return null
        namedItems(text)?.let { return it }
        val spans = quotedSpans(text)
        if (spans.isNotEmpty()) {
            val without = quoted.replace(narrowWidth(text), "").replace(Regex("\\b(?:and|or)\\b", RegexOption.IGNORE_CASE), "")
            if (without.any { !it.isWhitespace() && it !in ",、・/／" }) return null
            return SymbolRuleMatch(spans, "\"',:.[]")
        }
        val parts = text.split(',')
        if (parts.size >= 3 && parts.all { narrowWidth(it.trim()).length == 1 && literal(it.trim()) != null }) return plainPrefix(text, 0)
        return null
    }
    private fun plainPrefix(text: String, from: Int): SymbolRuleMatch? {
        var start = from
        while (start < text.length && text[start].isWhitespace()) start++
        var end = start
        while (end < text.length && (narrowWidth(text[end].toString())[0] in SymbolPolicy.SUPPORTED || text[end].isWhitespace() || text[end] in "、・")) end++
        if (end == start) return null
        // Do not include the opening parenthesis of a following prose annotation.
        val annotation = text.indexOf('(', start).takeIf { it in start until end && text.indexOf(')', it + 1) >= end }
        if (annotation != null && text.substring(annotation, end).any { it == '(' } && end < text.length) end = annotation
        if (end < text.length) {
            val tail = text.substring(end).trimStart()
            val proseTail = tail.startsWith("です") || tail.startsWith("のみ") || tail.startsWith("だけ") || isPermission(tail) ||
                Regex("^[（(].*(?:文字|characters|optional).*[)）]", RegexOption.IGNORE_CASE).containsMatchIn(tail)
            if (!proseTail) return observedSpan(text, start, text.length)?.let { SymbolRuleMatch(listOf(it), ":") }
        }
        val value = text.substring(start, end).trim()
        val items = value.split(',').map(String::trim)
        if (items.size >= 3 && items.all { narrowWidth(it).length == 1 && literal(it) != null }) {
            val spans = mutableListOf<SymbolRuleSpan>(); var offset = start
            for (item in text.substring(start, end).split(',')) {
                val localStart = offset + item.indexOfFirst { !it.isWhitespace() }
                spans += span(text, localStart, localStart + item.trim().length) ?: return null
                offset += item.length + 1
            }
            return SymbolRuleMatch(spans, ",:")
        }
        return span(text, start, end)?.let { SymbolRuleMatch(listOf(it), ":") }
    }
}
