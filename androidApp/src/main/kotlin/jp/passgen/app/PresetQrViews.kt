package jp.passgen.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.passgen.core.GenerationMode
import kotlinx.coroutines.*

@Composable internal fun PresetQrShare(preset: GeneratorPreset, model: PassgenModel, back: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, preset) {
        try { value = withContext(Dispatchers.Default) { PresetQrImage.encode(model.presetQR(preset)) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "QRコードを作成できませんでした。" }
    }
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = back, modifier = Modifier.semantics { testTag = "preset-qr-back" }) { Text("戻る") }
        Text("QRコードで共有", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(preset.name, style = MaterialTheme.typography.headlineSmall)
        Text(preset.recipe.summary, style = MaterialTheme.typography.bodyMedium)
        bitmap?.let { image ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(image.asImageBitmap(), contentDescription = "${preset.name}のプリセットQRコード",
                    modifier = Modifier.sizeIn(maxWidth = 320.dp, maxHeight = 320.dp).aspectRatio(1f).semantics { testTag = "preset-qr-image" })
            }
            Button(onClick = {
                try { PresetQrImage.share(context, image) }
                catch (_: Exception) { error = "共有画面を開けませんでした。" }
            }, modifier = Modifier.semantics { testTag = "preset-qr-share" }) { Text("QR画像を共有") }
        } ?: run { if (error == null) CircularProgressIndicator() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("受け取った人は「QRを読み込む」から登録できます。QRにはプリセット名と生成条件だけが含まれます。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun PresetQrReview(preset: GeneratorPreset, model: PassgenModel, back: () -> Unit, saved: () -> Unit) {
    var name by remember(preset) { mutableStateOf(preset.name) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = back) { Text("戻る") }
        Button(onClick = {
            focus.clearFocus()
            try { model.importPreset(preset, name); saved() } catch (e: PresetException) { error = e.message }
        }, enabled = !model.busy && model.presetStorageIssue == null && model.presetNameError(name) == null,
            modifier = Modifier.semantics { testTag = "preset-qr-register" }) { Text("登録") }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("プリセットを確認", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("プリセット名") }, singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().semantics { testTag = "preset-qr-name" })
        model.presetNameError(name)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PresetQrDetails(preset.recipe)
        Text("共有元を確認してから登録してください。登録後、一覧から選ぶと主画面に適用できます。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun PresetQrDetails(recipe: PresetRecipe) {
    fun state(value: Boolean) = if (value) "ON" else "OFF"
    Text(recipe.summary, style = MaterialTheme.typography.titleMedium)
    if (recipe.mode == GenerationMode.PASSWORD) {
        listOf(Triple("大文字", recipe.uppercase, recipe.uppercaseCharacters), Triple("小文字", recipe.lowercase, recipe.lowercaseCharacters),
            Triple("数字", recipe.digits, recipe.digitCharacters), Triple("記号", recipe.symbolsEnabled, recipe.symbols)).forEach { (label, enabled, selected) ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$label · ${state(enabled)}", style = MaterialTheme.typography.labelMedium)
                    Text(selected.ifEmpty { "選択なし" }, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Text("各文字種を必ず含める：${state(recipe.requireEach)}")
        Text("似た文字を除外：${state(recipe.excludeSimilar)} · ${recipe.similarExclusions.ifEmpty { "選択なし" }}")
    }
    Text("同じ文字を連続させない：${state(recipe.noAdjacent)}")
}
