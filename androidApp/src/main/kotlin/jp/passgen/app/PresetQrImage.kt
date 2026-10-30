package jp.passgen.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import jp.passgen.core.PresetQrCodec
import java.io.File

/** QR pixels only: never renders or shares the application window / password results. */
internal object PresetQrImage {
    const val ERROR = "プリセットのQRコードを読み取れませんでした。対応するQR画像を1つ選んでください。"
    fun encode(text: String): Bitmap {
        require(text.length <= PresetQrCodec.MAX_TEXT_LENGTH)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
    fun decode(bitmap: Bitmap): String {
        require(bitmap.width <= 2048 && bitmap.height <= 2048)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        val codes = QRCodeMultiReader().decodeMultiple(source, mapOf(DecodeHintType.TRY_HARDER to true))
            .map { it.text }.filter { it.startsWith("PG") }.distinct()
        require(codes.size == 1)
        return codes.single().also { PresetQrCodec.decode(it) }
    }
    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read; require(total <= 16 * 1024 * 1024)
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
            ?: throw IllegalArgumentException(ERROR)
        require(bytes.size <= 16 * 1024 * 1024)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (longest > 2048) decoder.setTargetSize(maxOf(1, (info.size.width.toLong() * 2048 / longest).toInt()), maxOf(1, (info.size.height.toLong() * 2048 / longest).toInt()))
        }
        return try { decode(bitmap) } finally { bitmap.recycle() }
    }
    fun share(context: Context, bitmap: Bitmap) {
        val directory = File(context.cacheDir, "preset-qr").apply { check(mkdirs() || isDirectory) }
        val file = File(directory, "preset.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.presetqr", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("プリセットQR", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "プリセットのQRコードを共有"))
    }
}
