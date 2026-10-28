package jp.passgen.core

internal interface EntropySource { fun fill(destination: ByteArray) }
internal expect class PlatformEntropy() : EntropySource { override fun fill(destination: ByteArray) }

/** One short-lived buffer per generation; never shared across workers. */
internal class SecureSampler(private val source: EntropySource, private val cancellation: CancellationProbe) {
    private val bytes = ByteArray(512)
    private var position = bytes.size

    fun checkCancellation() {
        if (cancellation.isCancelled()) throw GenerationException("CANCELLED")
    }

    fun nextInt(bound: Int): Int {
        require(bound in 1..65_536)
        val limit = 65_536 - (65_536 % bound)
        repeat(4_096) {
            if (position >= bytes.size) {
                checkCancellation()
                try { source.fill(bytes) } catch (e: GenerationException) { throw e }
                catch (_: Exception) { throw GenerationException("RANDOM_UNAVAILABLE") }
                position = 0
            }
            val value = ((bytes[position++].toInt() and 255) shl 8) or (bytes[position++].toInt() and 255)
            if (value < limit) return value % bound
        }
        throw GenerationException("RANDOM_UNAVAILABLE")
    }

    fun close() { bytes.fill(0); position = bytes.size }
}
