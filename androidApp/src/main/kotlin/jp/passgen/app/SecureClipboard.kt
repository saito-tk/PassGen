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
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var owner: String? = null
    private val expire = Runnable { clearOwned() }

    fun copy(secret: String) {
        handler.removeCallbacks(expire)
        val id = "passgen-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(id, secret)
        clip.description.extras = PersistableBundle().apply {
            // Official compatibility key, also safe before API 33 introduced the constant.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        manager.setPrimaryClip(clip)
        owner = id
        handler.postDelayed(expire, 30_000)
    }

    fun clearOwned() {
        handler.removeCallbacks(expire)
        if (owner != null && manager.primaryClipDescription?.label?.toString() == owner) manager.clearPrimaryClip()
        owner = null
    }
}
