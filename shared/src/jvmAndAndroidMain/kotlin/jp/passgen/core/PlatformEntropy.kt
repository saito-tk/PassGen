package jp.passgen.core

import java.security.SecureRandom

internal actual class PlatformEntropy actual constructor() : EntropySource {
    private val random = SecureRandom()
    actual override fun fill(destination: ByteArray) {
        try { random.nextBytes(destination) }
        catch (_: Exception) { throw GenerationException("RANDOM_UNAVAILABLE") }
    }
}
