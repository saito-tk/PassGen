package jp.passgen.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import jp.passgen.core.PresetQrCodec
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only the Y plane is needed. Respect crop, buffer position, row padding and pixel stride. */
internal object PresetQrFrames {
    fun decode(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int, crop: Rect): String? {
        if (width !in 1..2048 || height !in 1..2048 || crop.isEmpty || crop.left < 0 || crop.top < 0 ||
            crop.right > width || crop.bottom > height || rowStride <= 0 || pixelStride <= 0) return null
        val w = crop.width(); val h = crop.height()
        if (w.toLong() * h > 2_097_152) return null
        val start = buffer.position().toLong()
        val last = start + (crop.bottom - 1L) * rowStride + (crop.right - 1L) * pixelStride
        if (last >= buffer.limit()) return null
        val y = ByteArray(w * h)
        return try {
            repeat(h) { row -> repeat(w) { col ->
                y[row * w + col] = buffer.get((start + (crop.top + row).toLong() * rowStride + (crop.left + col).toLong() * pixelStride).toInt())
            } }
            val source = PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false)
            fun read(luminance: LuminanceSource): List<String> = try {
                QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(luminance)),
                    mapOf(DecodeHintType.TRY_HARDER to true)).map { it.text }
            } catch (_: ReaderException) { emptyList() }
            PresetQrCodec.selectCandidate(read(source) + read(source.invert()))
        } finally { y.fill(0) }
    }
}

@Composable internal fun PresetQrCamera(back: () -> Unit, detected: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateAsState()
    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    var requested by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val onDetected by rememberUpdatedState(detected)
    LaunchedEffect(state) { if (state == Lifecycle.State.RESUMED) granted = allowed() }
    LaunchedEffect(Unit) { if (!granted && !requested) { requested = true; request.launch(Manifest.permission.CAMERA) } }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var surface by remember { mutableStateOf<SurfaceRequest?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) { onDispose { executor.shutdownNow() } }
    DisposableEffect(owner, granted, state, retry) {
        val active = AtomicBoolean(granted && state == Lifecycle.State.RESUMED)
        val delivered = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var observed: androidx.camera.core.CameraInfo? = null
        val observer = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { cameraState ->
            if (active.get()) {
                if (cameraState.error != null) failure = "カメラを利用できません。ほかのアプリの使用が終わってから再試行してください。"
                else if (cameraState.type == androidx.camera.core.CameraState.Type.OPEN) failure = null
            }
        }
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(
                android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build()
        if (active.get()) {
            failure = null
            preview.setSurfaceProvider { request -> if (active.get()) surface = request else request.willNotProvideSurface() }
            analysis.setAnalyzer(executor) { frame ->
                try {
                    if (active.get() && !delivered.get()) {
                        val plane = frame.planes.first()
                        val text = PresetQrFrames.decode(plane.buffer, plane.rowStride, plane.pixelStride,
                            frame.width, frame.height, frame.cropRect)
                        if (text != null) ContextCompat.getMainExecutor(context).execute {
                            if (active.get() && owner.lifecycle.currentState == Lifecycle.State.RESUMED && delivered.compareAndSet(false, true)) {
                                analysis.clearAnalyzer(); provider?.unbind(preview, analysis)
                                onDetected(text)
                            }
                        }
                    }
                } catch (_: Exception) { /* Ignore transient/bad frames without exposing their payloads. */ }
                finally { frame.close() }
            }
            providerFuture.addListener({
                if (active.get()) try {
                    val cameraProvider = providerFuture.get(); provider = cameraProvider
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                        else CameraSelector.DEFAULT_FRONT_CAMERA
                    observed = cameraProvider.bindToLifecycle(owner, selector, preview, analysis).cameraInfo
                    observed?.cameraState?.observe(owner, observer)
                } catch (_: Exception) { failure = "カメラを開始できませんでした。画像からの読み取りも利用できます。" }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            active.set(false); observed?.cameraState?.removeObserver(observer); analysis.clearAnalyzer(); provider?.unbind(preview, analysis)
            surface?.willNotProvideSurface(); surface = null
        }
    }
    Column(Modifier.fillMaxSize().semantics { testTag = "preset-qr-camera" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetHeading("カメラで読み取る", R.drawable.ic_qr_code_scanner) {
            IconAction(R.drawable.ic_close, "カメラを閉じる", action = back)
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (granted && failure == null) {
                surface?.let { CameraXViewfinder(it, modifier = Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED) }
            }
            else Text(if (!granted) "QRを読み取るには、カメラへのアクセスを許可してください。" else failure.orEmpty(),
                color = Color.White, modifier = Modifier.padding(24.dp))
        }
        Text("プリセットのQRコードをカメラに向けてください。", style = MaterialTheme.typography.titleSmall)
        Text("このアプリ用のQRだけを端末内で読み取ります。複数ある場合は1つずつ映してください。", style = MaterialTheme.typography.bodySmall)
        if (!granted) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri())) }) { Text("設定を開く") }
        if (failure != null) TextButton(onClick = { retry++ }) { Text("再試行") }
        Spacer(Modifier.height(8.dp))
    }
}
