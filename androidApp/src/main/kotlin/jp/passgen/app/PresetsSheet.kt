@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import jp.passgen.core.GenerationMode

@Composable fun PresetsSheet(model: PassgenModel, fold: FoldingFeature?, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<GeneratorPreset?>(null) }
    var action by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf<GeneratorPreset?>(null) }
    var incoming by remember { mutableStateOf<GeneratorPreset?>(null) }
    var reading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            reading = true; status = null
            try {
                val text = withContext(Dispatchers.IO) { PresetQrImage.read(context, uri) }
                incoming = model.decodedPresetQR(text)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = PresetQrImage.ERROR }
            finally { reading = false }
        }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    fun perform(success: String? = null, operation: () -> Unit): Boolean = try {
        operation(); status = success; true
    } catch (e: PresetException) { status = e.message; false }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = if (fold == null) Modifier else Modifier.padding(start = with(density) { fold.bounds.right.toDp() }),
        sheetMaxWidth = if (fold == null) 640.dp else androidx.compose.ui.unit.Dp.Unspecified) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val exported = sharing
            val imported = incoming
            if (exported != null) {
                PresetQrShare(exported, model, back = { sharing = null })
            } else if (imported != null) {
                PresetQrReview(imported, model, back = { incoming = null }, saved = { incoming = null; status = "プリセットを登録しました。" })
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("プリセット", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !reading && !model.busy && model.presetStorageIssue == null,
                        modifier = Modifier.semantics { testTag = "preset-qr-read" }) { Text("QRを読み込む") }
                }
                if (reading) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Text("端末内で読み取り中…", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("現在の設定を保存", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        focus.clearFocus(); keyboard?.hide()
                        perform("保存しました。") { model.savePreset(name); name = "" }
                    }, enabled = !model.busy && model.presetNameError(name) == null && model.presetStorageIssue == null,
                        modifier = Modifier.semantics { testTag = "preset-save" }) { Text("保存") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (model.mode == GenerationMode.PASSWORD) CategoryOverview(model, identifierPrefix = "preset-overview")
                            Text(PresetRecipe.capture(model).summary, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(name, { name = it; status = null }, label = { Text("プリセット名") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().semantics { testTag = "preset-name" }, enabled = !model.busy,
                                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
                            if (name.isNotEmpty()) model.presetNameError(name)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            Text("生成条件をこの端末に保存します。パスワード・表示・コピー・画面保護の設定は含みません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { testTag = "preset-status"; liveRegion = LiveRegionMode.Polite }) }
                            model.presetStorageIssue?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Text("保存済み · ${model.presets.size}件", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (model.presets.isEmpty()) item { Text("名前を付けて保存すると、ここから呼び出せます。", style = MaterialTheme.typography.bodyMedium) }
                    items(model.presets, key = { it.id }) { preset ->
                        var menu by remember { mutableStateOf(false) }
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { perform { model.applyPreset(preset.id); dismiss() } }, enabled = !model.busy,
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "${preset.name}を読み込む"; stateDescription = preset.recipe.summary }) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                        Text(preset.recipe.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (preset.recipe == PresetRecipe.capture(model)) Text("現在の設定", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Box {
                                    TextButton(onClick = { menu = true }, enabled = !model.busy,
                                        modifier = Modifier.semantics { contentDescription = "${preset.name}の編集" }) { Text("編集") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(text = { Text("QRコードで共有") }, onClick = { menu = false; sharing = preset })
                                        DropdownMenuItem(text = { Text("名前を変更") }, onClick = { menu = false; editing = preset; newName = preset.name; action = "rename" })
                                        DropdownMenuItem(text = { Text("現在の設定で上書き") }, onClick = { menu = false; editing = preset; action = "replace" })
                                        DropdownMenuItem(text = { Text("削除") }, onClick = { menu = false; editing = preset; action = "delete" })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { preset ->
        AlertDialog(onDismissRequest = { editing = null },
            title = { Text(when (action) { "rename" -> "名前を変更"; "replace" -> "現在の設定で上書きしますか？"; else -> "プリセットを削除しますか？" }) },
            text = {
                if (action == "rename") Column {
                    OutlinedTextField(newName, { newName = it }, label = { Text("プリセット名") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                        modifier = Modifier.semantics { testTag = "preset-rename-name" })
                    model.presetNameError(newName, preset.id)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } else Text("「${preset.name}」${if (action == "replace") "を現在の設定に置き換えます。" else "を削除します。"}")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (perform("変更しました。") {
                        when (action) { "rename" -> model.renamePreset(preset.id, newName); "replace" -> model.replacePreset(preset.id); else -> model.deletePreset(preset.id) }
                    }) editing = null
                }, enabled = !model.busy && (action != "rename" || model.presetNameError(newName, preset.id) == null),
                    modifier = Modifier.semantics { testTag = "preset-confirm" }) {
                    Text(when (action) { "rename" -> "保存"; "replace" -> "上書き"; else -> "削除" })
                }
            }, dismissButton = { TextButton(onClick = { editing = null }) { Text("キャンセル") } })
    }
}
