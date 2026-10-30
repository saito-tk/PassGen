package jp.passgen.core

/** Transfer whitelist: no results, random seeds, device identifiers or security preferences. */
class SharedPreset(val name: String, val request: GenerationRequest, val count: Int)

class PresetTransferException : Exception("INVALID_PRESET_QR")

/** App releases and wire versions are independent. Keep old decoders when adding a new writer. */
object PresetQrCodec {
    const val CURRENT_FORMAT_VERSION = 1
    const val MAX_TEXT_LENGTH = 256
    @Throws(PresetTransferException::class)
    fun encode(preset: SharedPreset): String = PresetQrV1.encode(preset)

    @Throws(PresetTransferException::class)
    fun decode(text: String): SharedPreset {
        if (text.length > MAX_TEXT_LENGTH) throw PresetTransferException()
        return when (text.substringBefore(':')) {
            "PG1" -> PresetQrV1.decode(text)
            else -> throw PresetTransferException()
        }
    }
}

/** Immutable v1 vocabulary and bounds. Never substitute the current UI's alphabets/defaults. */
internal object PresetQrV1 {
    private const val MAX_TEXT_LENGTH = 256
    private const val PREFIX = "PG1:"
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val separators = listOf(" ", "-", "_", ".")
    private val alphabets = listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz",
        "0123456789", "-_@/*+.,!?#\u0024%&(){}[]~|:;\"'^><=`\\", "Oo0Il1")
    private fun requireValid(value: Boolean) { if (!value) throw PresetTransferException() }

    private fun validate(p: SharedPreset) {
        val r = p.request
        requireValid(p.name.isNotEmpty() && p.name.length <= 40 && p.name.trim() == p.name)
        requireValid(p.name.none { it.category in setOf(CharCategory.CONTROL, CharCategory.FORMAT,
            CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR) })
        requireValid(r.mode in listOf(GenerationMode.PASSWORD, GenerationMode.PASSPHRASE))
        requireValid(r.length in 4..999_999 && r.wordCount in 6..12 && r.separator in separators)
        requireValid(p.count in 1..if (r.mode == GenerationMode.PASSPHRASE) 1000 else minOf(1000, 9_999_990 / r.length))
        val selections = listOf(r.uppercaseCharacters, r.lowercaseCharacters, r.digitCharacters, r.symbols, r.similarExclusions)
        selections.zip(alphabets).forEach { (s, a) -> requireValid(a.filter { it in s } == s) }
    }

    @Throws(PresetTransferException::class)
    fun encode(preset: SharedPreset): String {
        try {
            validate(preset)
            val r = preset.request
            val bytes = mutableListOf<Int>()
            val flags = listOf(r.uppercase, r.lowercase, r.digits, r.symbolsEnabled,
                r.excludeSimilar, r.requireEach, r.noAdjacent, r.mode == GenerationMode.PASSPHRASE)
            bytes += flags.withIndex().sumOf { (i, v) -> if (v) 1 shl i else 0 }
            fun integer(value: Int) {
                var n = value
                do { bytes += (n and 127) or if (n >= 128) 128 else 0; n = n ushr 7 } while (n > 0)
            }
            integer(r.length); integer(preset.count)
            bytes += (r.wordCount - 6) or (separators.indexOf(r.separator) shl 3)
            val masks = IntArray(13)
            var bit = 0
            listOf(r.uppercaseCharacters, r.lowercaseCharacters, r.digitCharacters, r.symbols, r.similarExclusions)
                .zip(alphabets).forEach { (selected, alphabet) ->
                    alphabet.forEach { if (it in selected) masks[bit / 8] = masks[bit / 8] or (1 shl (bit % 8)); bit++ }
                }
            bytes += masks.toList()
            bytes += preset.name.encodeToByteArray(throwOnInvalidSequence = true).map { it.toInt() and 255 }
            val checksum = crc(bytes)
            repeat(4) { bytes += (checksum ushr (it * 8)) and 255 }
            return (PREFIX + base32(bytes)).also { requireValid(it.length <= MAX_TEXT_LENGTH) }
        } catch (_: CharacterCodingException) { throw PresetTransferException() }
    }

    @Throws(PresetTransferException::class)
    fun decode(text: String): SharedPreset {
        try {
            requireValid(text.length <= MAX_TEXT_LENGTH && text.startsWith(PREFIX))
            val encoded = text.substring(PREFIX.length)
            val bytes = mutableListOf<Int>()
            var accumulator = 0; var bits = 0
            encoded.forEach {
                val value = BASE32.indexOf(it); requireValid(value >= 0)
                accumulator = (accumulator shl 5) or value; bits += 5
                if (bits >= 8) { bits -= 8; bytes += (accumulator ushr bits) and 255 }
            }
            requireValid(bytes.size >= 22 && base32(bytes) == encoded)
            val body = bytes.dropLast(4)
            val checksum = bytes.takeLast(4).withIndex().fold(0) { n, (i, v) -> n or (v shl (i * 8)) }
            requireValid(crc(body) == checksum)
            var cursor = 0
            fun next(): Int { requireValid(cursor < body.size); return body[cursor++] }
            fun integer(): Int {
                var value = 0
                repeat(3) { i ->
                    val byte = next(); value = value or ((byte and 127) shl (i * 7))
                    if (byte < 128) { requireValid(i == 0 || byte != 0); return value }
                }
                throw PresetTransferException()
            }
            val flags = next()
            fun flag(index: Int) = flags and (1 shl index) != 0
            val length = integer(); val count = integer(); val phrase = next()
            requireValid(phrase and 224 == 0 && phrase and 7 <= 6)
            val masks = List(13) { next() }
            requireValid(masks.last() and 240 == 0)
            var bit = 0
            val selected = alphabets.map { alphabet -> alphabet.filter {
                val included = masks[bit / 8] and (1 shl (bit % 8)) != 0; bit++; included
            } }
            val name = body.drop(cursor).map { it.toByte() }.toByteArray().decodeToString(throwOnInvalidSequence = true)
            val r = GenerationRequest(mode = if (flag(7)) GenerationMode.PASSPHRASE else GenerationMode.PASSWORD,
                length = length, wordCount = (phrase and 7) + 6, separator = separators[(phrase ushr 3) and 3],
                uppercase = flag(0), lowercase = flag(1), digits = flag(2), symbolsEnabled = flag(3),
                excludeSimilar = flag(4), requireEach = flag(5), noAdjacent = flag(6),
                uppercaseCharacters = selected[0], lowercaseCharacters = selected[1], digitCharacters = selected[2],
                symbols = selected[3], similarExclusions = selected[4])
            return SharedPreset(name, r, count).also { validate(it) }
        } catch (_: CharacterCodingException) { throw PresetTransferException() }
    }

    private fun base32(bytes: List<Int>): String = buildString {
        var accumulator = 0; var bits = 0
        bytes.forEach {
            accumulator = (accumulator shl 8) or it; bits += 8
            while (bits >= 5) { bits -= 5; append(BASE32[(accumulator ushr bits) and 31]) }
        }
        if (bits > 0) append(BASE32[(accumulator shl (5 - bits)) and 31])
    }

    private fun crc(bytes: List<Int>): Int {
        var value = -1
        bytes.forEach { byte ->
            value = value xor byte
            repeat(8) { value = (value ushr 1) xor if (value and 1 != 0) 0xEDB88320.toInt() else 0 }
        }
        return value.inv()
    }
}
