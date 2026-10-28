@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package jp.passgen.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual class PlatformEntropy actual constructor() : EntropySource {
    actual override fun fill(destination: ByteArray) {
        if (destination.isEmpty()) return
        val status = destination.usePinned { SecRandomCopyBytes(kSecRandomDefault, destination.size.toULong(), it.addressOf(0)) }
        if (status != errSecSuccess) {
            destination.fill(0)
            throw GenerationException("RANDOM_UNAVAILABLE")
        }
    }
}
