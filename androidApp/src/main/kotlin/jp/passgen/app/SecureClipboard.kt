package jp.passgen.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import java.util.UUID

/** Labels are opaque ownership tokens; never read another application's clipboard text. */
class SecureClipboard(context: Context) {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)
    // Ownership survives Activity recreation (rotation / folding), without retaining an Activity.
    private companion object {
        val handler = Handler(Looper.getMainLooper())
        var owner: String? = null
        var pending: Runnable? = null
        var expiresAt: Long? = null
    }

    fun copy(secret: String, clearAfterSeconds: Int? = 30): Boolean {
        val id = "passgen-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(id, secret)
        clip.description.extras = PersistableBundle().apply {
            // Official compatibility key, also safe before API 33 introduced the constant.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        try { manager.setPrimaryClip(clip) }
        catch (_: RuntimeException) { return false }
        pending?.let(handler::removeCallbacks)
        owner = id
        val delay = clearAfterSeconds?.coerceIn(5, 600)?.times(1000L)
        expiresAt = delay?.let { SystemClock.elapsedRealtime() + it }
        pending = delay?.let { milliseconds ->
            Runnable { clearExpired() }.also { handler.postDelayed(it, milliseconds) }
        }
        return true
    }

    fun clearExpired() {
        val deadline = expiresAt ?: return
        if (SystemClock.elapsedRealtime() >= deadline) clearOwned()
    }

    fun clearOwned() {
        pending?.let(handler::removeCallbacks)
        pending = null
        val expected = owner ?: return
        try {
            // Background access may return null. Keep only the opaque ownership
            // token and deadline, then retry when this app regains window focus.
            val description = manager.primaryClipDescription ?: return
            if (description.label?.toString() == expected) manager.clearPrimaryClip()
            owner = null; expiresAt = null
        } catch (_: RuntimeException) { /* Retry on focus; never log clip contents. */ }
    }
}
