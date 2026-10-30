package jp.passgen.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max

/** No state, IO, secret persistence, third-party crypto, or caller-provided seeds. */
class PasswordEngine internal constructor(private val entropy: EntropySource) {
    constructor() : this(platformEntropy())

    @Throws(GenerationException::class)
    fun generate(request: GenerationRequest, cancellation: CancellationProbe): GeneratedSecret {
        val sampler = SecureSampler(entropy, cancellation)
        try {
            sampler.checkCancellation()
            validate(request)
            return when (request.mode) {
                GenerationMode.PASSPHRASE -> phrase(request, sampler)
                GenerationMode.PASSWORD -> characters(request, pools(request), sampler, request.requireEach)
            }
        } finally { sampler.close() }
    }

    @Throws(GenerationException::class)
    fun estimatedEntropy(request: GenerationRequest): Double {
        validate(request)
        return when (request.mode) {
            GenerationMode.PASSPHRASE -> request.wordCount * log2(EffWords.words.size.toDouble())
            GenerationMode.PASSWORD -> entropyBits(pools(request).map { it.length }, request.length, request.requireEach, request.noAdjacent)
        }
    }

    private fun validate(r: GenerationRequest) {
        if (r.mode == GenerationMode.PASSPHRASE) {
            if (r.wordCount !in 6..12 || r.separator !in listOf(".", "_", " ", "-")) invalid()
            return
        }
        if (r.length !in 4..InputPolicy.MAX_LENGTH) invalid()
        if (r.mode == GenerationMode.PASSWORD) {
            if (r.similarExclusions.any { it !in CharacterPolicy.SIMILAR }) invalid()
            if (r.uppercase && r.uppercaseCharacters.any { it !in CharacterPolicy.UPPERCASE }) invalid()
            if (r.lowercase && r.lowercaseCharacters.any { it !in CharacterPolicy.LOWERCASE }) invalid()
            if (r.digits && r.digitCharacters.any { it !in CharacterPolicy.DIGITS }) invalid()
            if (r.symbolsEnabled && r.symbols.any { it !in SymbolPolicy.SUPPORTED }) invalid()
            val pools = pools(r)
            if (pools.isEmpty() || (r.requireEach && r.length < pools.size)) invalid()
            if (r.noAdjacent && pools.sumOf { it.length } < 2) invalid()
        }
    }

    private fun pools(r: GenerationRequest): List<String> = CharacterPolicy.passwordPools(r)

    private fun characters(r: GenerationRequest, pools: List<String>, random: SecureSampler, requireEach: Boolean): GeneratedSecret {
        val alphabet = pools.joinToString("")
        val output = CharArray(r.length)
        val categories = IntArray(alphabet.length)
        var offset = 0
        pools.forEachIndexed { index, pool -> repeat(pool.length) { categories[offset++] = 1 shl index } }
        val all = (1 shl pools.size) - 1
        try {
            repeat(4_096) {
                random.checkCancellation()
                var seen = 0
                var previous = -1
                for (i in output.indices) {
                    // Mapping skips the previous character and preserves equal probability.
                    var index = random.nextInt(alphabet.length - if (r.noAdjacent && i > 0) 1 else 0)
                    if (r.noAdjacent && i > 0 && index >= previous) index++
                    output[i] = alphabet[index]
                    previous = index
                    seen = seen or categories[index]
                }
                if (!requireEach || seen == all) {
                    random.checkCancellation()
                    return GeneratedSecret(output.concatToString(), entropyBits(pools.map { it.length }, r.length, requireEach, r.noAdjacent))
                }
                output.fill('\u0000')
            }
            throw GenerationException("CONDITIONS_TOO_STRICT")
        } finally { output.fill('\u0000') }
    }

    private fun phrase(r: GenerationRequest, random: SecureSampler): GeneratedSecret {
        // Independent words with replacement; a fixed separator adds no entropy.
        val words = Array(r.wordCount) { EffWords.words[random.nextInt(EffWords.words.size)] }
        random.checkCancellation()
        return GeneratedSecret(words.joinToString(r.separator), r.wordCount * log2(EffWords.words.size.toDouble()))
    }

    private fun invalid(): Nothing = throw GenerationException("INVALID_CONFIGURATION")
}

/** Inclusion-exclusion counts valid strings, including the no-adjacent constraint. */
internal fun entropyBits(sizes: List<Int>, length: Int, requireEach: Boolean, noAdjacent: Boolean): Double {
    val n = sizes.sum()
    fun logCount(count: Int): Double = when {
        count <= 0 || (noAdjacent && count == 1 && length > 1) -> Double.NEGATIVE_INFINITY
        noAdjacent -> ln(count.toDouble()) + (length - 1) * ln((count - 1).toDouble())
        else -> length * ln(count.toDouble())
    }
    val base = logCount(n)
    if (!requireEach) return max(0.0, base / ln(2.0))
    var fraction = 0.0
    for (mask in 0 until (1 shl sizes.size)) {
        var removed = 0
        var parity = 0
        sizes.forEachIndexed { i, size -> if (mask and (1 shl i) != 0) { removed += size; parity++ } }
        val term = exp(logCount(n - removed) - base)
        fraction += if (parity % 2 == 0) term else -term
    }
    return if (fraction <= 0.0) 0.0 else max(0.0, (base + ln(fraction.coerceAtMost(1.0))) / ln(2.0))
}
