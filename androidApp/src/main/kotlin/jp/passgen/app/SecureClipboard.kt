package jp.passgen.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/** Labels are opaque ownership tokens; never read another application's clipboard text. */
class SecureClipboard(context: Context) {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)
    // Ownership survives Activity recreation (rotation / folding), without retaining an Activity.
    private companion object {
        val handler = Handler(Looper.getMainLooper())
        var owner: String? = null
        var pending: Runnable? = null
    }

    fun copy(secret: String): Boolean {
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
        pending = Runnable { clearOwned() }.also { handler.postDelayed(it, 30_000) }
        return true
    }

    fun clearOwned() {
        pending?.let(handler::removeCallbacks)
        try {
            if (owner != null && manager.primaryClipDescription?.label?.toString() == owner) manager.clearPrimaryClip()
        } catch (_: RuntimeException) { /* OS clipboard service unavailable; never log clip contents. */ }
        owner = null
        pending = null
    }
}
