package jp.passgen.app

import jp.passgen.core.*
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Generation parameters only. Never includes results, reveal state or security settings. */
data class PresetRecipe(
    val mode: GenerationMode, val length: Int, val count: Int, val words: Int, val separator: String,
    val uppercase: Boolean, val lowercase: Boolean, val digits: Boolean, val symbolsEnabled: Boolean,
    val symbols: String, val excludeSimilar: Boolean, val requireEach: Boolean, val noAdjacent: Boolean,
    val uppercaseCharacters: String, val lowercaseCharacters: String, val digitCharacters: String,
    val similarExclusions: String,
) {
    val summary get() = if (mode == GenerationMode.PASSWORD) "パスワード · ${length}文字 · ${count}件"
        else "パスフレーズ · ${words}単語 · ${count}件 · ${if (separator == " ") "スペース" else separator}区切り"
    fun json() = JSONObject().apply {
        put("version", 1); put("mode", mode.name); put("length", length); put("count", count)
        put("words", words); put("separator", separator)
        put("uppercase", uppercase); put("lowercase", lowercase); put("digits", digits); put("symbolsEnabled", symbolsEnabled)
        put("symbols", symbols); put("excludeSimilar", excludeSimilar); put("requireEach", requireEach); put("noAdjacent", noAdjacent)
        put("uppercaseCharacters", uppercaseCharacters); put("lowercaseCharacters", lowercaseCharacters)
        put("digitCharacters", digitCharacters); put("similarExclusions", similarExclusions)
    }
    fun applyTo(model: PassgenModel) {
        model.mode = mode; model.lengthText = length.toString(); model.countText = count.toString()
        model.words = words; model.separator = separator
        model.uppercase = uppercase; model.lowercase = lowercase; model.digits = digits; model.symbolsEnabled = symbolsEnabled
        model.symbols = symbols; model.excludeSimilar = excludeSimilar; model.requireEach = requireEach; model.noAdjacent = noAdjacent
        model.uppercaseCharacters = uppercaseCharacters; model.lowercaseCharacters = lowercaseCharacters
        model.digitCharacters = digitCharacters; model.similarExclusions = similarExclusions
    }
    companion object {
        fun capture(model: PassgenModel): PresetRecipe = with(model) {
            PresetRecipe(mode, length, count, words.coerceIn(6, 12), separator.takeIf { it in listOf(" ", "-", "_", ".") } ?: " ",
                uppercase, lowercase, digits, symbolsEnabled, SymbolPolicy.recognized(symbols), excludeSimilar, requireEach, noAdjacent,
                CharacterPolicy.canonical(uppercaseCharacters, CharacterPolicy.UPPERCASE),
                CharacterPolicy.canonical(lowercaseCharacters, CharacterPolicy.LOWERCASE),
                CharacterPolicy.canonical(digitCharacters, CharacterPolicy.DIGITS),
                CharacterPolicy.canonical(similarExclusions, CharacterPolicy.SIMILAR))
        }
        fun decode(json: JSONObject): PresetRecipe {
            fun text(key: String, fallback: String) = json.opt(key) as? String ?: fallback
            fun flag(key: String, fallback: Boolean) = json.opt(key) as? Boolean ?: fallback
            val mode = GenerationMode.entries.firstOrNull { it.name == text("mode", "PASSWORD") } ?: GenerationMode.PASSWORD
            val length = InputPolicy.normalizedInteger(json.opt("length")?.toString() ?: "24", 24, 4, InputPolicy.MAX_LENGTH)
            val count = InputPolicy.normalizedInteger(json.opt("count")?.toString() ?: "1", 1, 1,
                if (mode == GenerationMode.PASSPHRASE) 1000 else InputPolicy.maximumCount(length))
            return PresetRecipe(mode, length, count, json.optInt("words", 7).coerceIn(6, 12),
                text("separator", " ").takeIf { it in listOf(" ", "-", "_", ".") } ?: " ",
                flag("uppercase", true), flag("lowercase", true), flag("digits", true), flag("symbolsEnabled", true),
                SymbolPolicy.recognized(text("symbols", "!@#\u0024%&*-_+=?")), flag("excludeSimilar", true), flag("requireEach", true), flag("noAdjacent", false),
                CharacterPolicy.canonical(text("uppercaseCharacters", CharacterPolicy.UPPERCASE), CharacterPolicy.UPPERCASE),
                CharacterPolicy.canonical(text("lowercaseCharacters", CharacterPolicy.LOWERCASE), CharacterPolicy.LOWERCASE),
                CharacterPolicy.canonical(text("digitCharacters", CharacterPolicy.DIGITS), CharacterPolicy.DIGITS),
                CharacterPolicy.canonical(text("similarExclusions", CharacterPolicy.SIMILAR), CharacterPolicy.SIMILAR))
        }
    }
}

data class GeneratorPreset(val id: String, val name: String, val recipe: PresetRecipe)
class PresetException(message: String) : Exception(message)
internal fun normalizedPresetName(value: String): String {
    val name = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
    if (name.isEmpty() || name.length > 40 || name.codePoints().anyMatch { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() })
        throw PresetException("名前は1〜40文字で入力してください。改行や制御文字は使用できません。")
    return name
}
internal fun presetNameKey(value: String) = value.lowercase(Locale.ROOT)
