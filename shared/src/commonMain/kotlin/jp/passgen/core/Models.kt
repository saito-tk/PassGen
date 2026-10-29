package jp.passgen.core

enum class GenerationMode { PASSWORD, PASSPHRASE }

object SecretPresentation {
    const val MASK = "••••••••••••"
}

/** Configuration contains no secret and may be used by either native UI. */
class GenerationRequest(
    val mode: GenerationMode = GenerationMode.PASSWORD,
    val length: Int = 24,
    val wordCount: Int = 7,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbolsEnabled: Boolean = true,
    val symbols: String = "!@#\u0024%&*-_+=?",
    val excludeSimilar: Boolean = true,
    val requireEach: Boolean = true,
    val noAdjacent: Boolean = false,
    val separator: String = " ",
    val uppercaseCharacters: String = CharacterPolicy.UPPERCASE,
    val lowercaseCharacters: String = CharacterPolicy.LOWERCASE,
    val digitCharacters: String = CharacterPolicy.DIGITS,
    val similarExclusions: String = CharacterPolicy.SIMILAR,
)

interface CancellationProbe { fun isCancelled(): Boolean }

class GenerationException(val code: String) : Exception(code)

/** Deliberately not a data class: toString must never reveal a secret. */
class GeneratedSecret internal constructor(val value: String, val entropyBits: Double) {
    val length: Int get() = value.length
    override fun toString(): String = "GeneratedSecret([REDACTED])"
}

object InputPolicy {
    const val MAX_LENGTH = 999_999
    const val MAX_TOTAL_CHARACTERS = 9_999_990
    fun maximumCount(length: Int): Int =
        if (length in 4..MAX_LENGTH) minOf(1_000, MAX_TOTAL_CHARACTERS / length) else 1

    /** Called on editing completion, never on each keystroke. */
    fun normalizedInteger(text: String, fallback: Int, minimum: Int, maximum: Int): Int {
        val ascii = text.trim().map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
        if (ascii.isEmpty() || ascii.any { it !in '0'..'9' }) return fallback.coerceIn(minimum, maximum)
        return (ascii.toLongOrNull() ?: Long.MAX_VALUE).coerceIn(minimum.toLong(), maximum.toLong()).toInt()
    }
}

/** All 32 ASCII punctuation marks; preserves the reference app's literal paste behavior. */
object SymbolPolicy {
    const val SUPPORTED = "-_@/*+.,!?#\u0024%&(){}[]~|:;\"'^><=`\\"
    fun recognized(text: String): String = SUPPORTED.filter { it in text }
    fun applyPasted(text: String, current: String): String = recognized(text).ifEmpty { current }

    /** OCR / LLM candidates can only narrow explicitly observed, supported symbols. */
    fun confirmedCandidate(proposed: String, evidence: String): String =
        recognized(proposed).filter { it in evidence }
}

object CharacterPolicy {
    const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SIMILAR = "Oo0Il1"
    fun canonical(selected: String, alphabet: String): String = alphabet.filter { it in selected }
    fun effective(selected: String, alphabet: String, excludeSimilar: Boolean, exclusions: String): String =
        canonical(selected, alphabet).filterNot { excludeSimilar && it in exclusions }
}
