package jp.passgen.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by viewModels<PassgenModel>()
    private lateinit var clipboard: SecureClipboard
    private val fold = mutableStateOf<FoldingFeature?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true)
        clipboard = SecureClipboard(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    fold.value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating }
                }
            }
        }
        setContent { PassgenTheme { GeneratorScreen(model, fold.value, clipboard::copy) } }
    }

    override fun onStop() {
        if (!isChangingConfigurations) { model.background(); clipboard.clearOwned() }
        super.onStop()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if ((event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) return false
        if (Build.VERSION.SDK_INT >= 29 && (event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0) return false
        return super.dispatchTouchEvent(event)
    }
}
