package jp.passgen.core

import java.security.SecureRandom

internal actual fun platformEntropy(): EntropySource = JvmEntropy()

private class JvmEntropy : EntropySource {
    private val random = try { SecureRandom() } catch (_: RuntimeException) { throw GenerationException("RANDOM_UNAVAILABLE") }
    override fun fill(destination: ByteArray) {
        try { random.nextBytes(destination) }
        catch (_: Exception) { throw GenerationException("RANDOM_UNAVAILABLE") }
    }
}
