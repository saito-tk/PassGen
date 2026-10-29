@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.passgen.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import jp.passgen.core.*

enum class CharacterTab(val title: String, val alphabet: String) {
    UPPER("大文字", CharacterPolicy.UPPERCASE), LOWER("小文字", CharacterPolicy.LOWERCASE),
    DIGITS("数字", CharacterPolicy.DIGITS), SYMBOLS("記号", SymbolPolicy.SUPPORTED)
}

@Composable fun GeneratorScreen(model: PassgenModel, fold: FoldingFeature?, copy: (String) -> Boolean) {
    var showResults by remember { mutableStateOf(false) }
    var showSymbols by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showStorageLesson by remember { mutableStateOf(false) }
    var showAuthLesson by remember { mutableStateOf(false) }
    var examplesTopic by remember { mutableStateOf<Int?>(null) }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val verticalFold = fold?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
    fun generate() {
        focus.clearFocus()
        if (model.work == WorkState.IDLE) showResults = true
        model.generateOrStop()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Passgen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (model.results.isNotEmpty() || model.busy) TextButton(onClick = { showResults = true }) {
                    Text(if (model.busy) "${model.results.size}/${model.goal}件" else "結果 ${model.results.size}件")
                }
                TextButton(onClick = { showPrivacy = true }, modifier = Modifier.semantics { testTag = "safety-settings" }) { Text("設定") }
            }
        }, bottomBar = {
            if (!showResults) Surface(tonalElevation = 2.dp) {
                val left = if (verticalFold == null) 12.dp else with(density) { verticalFold.bounds.right.toDp() } + 12.dp
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = left, end = 12.dp, top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
                    Button(onClick = ::generate, enabled = model.work != WorkState.CANCELLING,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "generate" }) {
                        Text(model.generationActionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).onGloballyPositioned { origin = it.positionInWindow() }) {
            val horizontalFold = fold?.takeIf { it.orientation == FoldingFeature.Orientation.HORIZONTAL }
            val hingeX = with(density) { ((verticalFold?.bounds?.left ?: 0) - origin.x).toDp() }
            val hingeY = with(density) { ((horizontalFold?.bounds?.top ?: 0) - origin.y).toDp() }
            when {
                verticalFold != null && hingeX > 150.dp && hingeX < maxWidth - 150.dp -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(hingeX).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoreSettings(model); RuleCard(model)
                    }
                    Spacer(Modifier.width(with(density) { verticalFold.bounds.width().toDp() }.coerceAtLeast(12.dp)))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) { CharacterSettings(model) { showSymbols = true } }
                }
                horizontalFold != null && hingeY > 100.dp && hingeY < maxHeight - 100.dp -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.height(hingeY).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { CoreSettings(model); RuleCard(model) }
                    Spacer(Modifier.height(with(density) { horizontalFold.bounds.height().toDp() }.coerceAtLeast(12.dp)))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) { CharacterSettings(model) { showSymbols = true } }
                }
                maxWidth >= 840.dp -> Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(.9f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { CoreSettings(model); RuleCard(model) }
                    Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState())) { CharacterSettings(model) { showSymbols = true } }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreSettings(model)
                    CharacterSettings(model) { showSymbols = true }
                    RuleCard(model)
                }
            }
        }
    }
    if (showResults) ResultSheet(model, copy, verticalFold, { showResults = false }, ::generate)
    if (showSymbols) SymbolSheet(model) { showSymbols = false }
    if (showPrivacy) PrivacySheet(model, { showPrivacy = false }) { showPrivacy = false; showGuide = true }
    if (showGuide) SecurityGuideSheet(
        dismiss = { showGuide = false },
        storageLesson = { showGuide = false; showStorageLesson = true },
        authLesson = { showGuide = false; showAuthLesson = true },
        examplesLesson = { topic -> showGuide = false; examplesTopic = topic })
    if (showStorageLesson) PasswordStorageLessonSheet(verticalFold) { showStorageLesson = false; showGuide = true }
    if (showAuthLesson) AuthenticationFactorsLessonSheet(verticalFold) { showAuthLesson = false; showGuide = true }
    examplesTopic?.let { topic -> PasswordExamplesLessonSheet(topic, verticalFold) { examplesTopic = null; showGuide = true } }
}

@Composable private fun Modifier.normalizeOnBlur(normalize: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { if (focused && !it.isFocused) normalize(); focused = it.isFocused }
}

@Composable private fun CoreSettings(model: PassgenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("生成形式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            model.estimate()?.let { bits ->
                Text("推定 ${bits.toInt()} bits", style = MaterialTheme.typography.labelMedium,
                    color = if (bits < 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GenerationMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(selected = model.mode == mode, onClick = { model.chooseMode(mode) }, enabled = !model.busy,
                    shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                    Text(when (mode) { GenerationMode.PASSWORD -> "パスワード"; GenerationMode.PASSPHRASE -> "パスフレーズ" }, fontSize = 13.sp)
                }
            }
        }
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuantityField(model, true, Modifier.fillMaxWidth())
                QuantityField(model, false, Modifier.fillMaxWidth())
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuantityField(model, true, Modifier.weight(1f))
            QuantityField(model, false, Modifier.weight(1f))
        }
        if (model.mode == GenerationMode.PASSPHRASE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(" " to "スペース", "-" to "-", "_" to "_", "." to ".").forEach { (value, label) ->
                    FilterChip(model.separator == value, { model.separator = value }, enabled = !model.busy, label = { Text(label) })
                }
            }
        }
        if (model.isDigitsOnly) Text("数字は0–9を使用。短いコードは候補数が少なくなります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.estimate() == null) Text("有効な文字を各文字種で1つ以上選択してください。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun QuantityField(model: PassgenModel, lengthField: Boolean, modifier: Modifier) {
    if (lengthField && model.mode == GenerationMode.PASSPHRASE) {
        OutlinedCard(modifier) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("単語数", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { model.words-- }, enabled = !model.busy && model.words > 6) { Text("−") }
                    Text("${model.words}", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { model.words++ }, enabled = !model.busy && model.words < 12) { Text("＋") }
                }
            }
        }
    } else OutlinedTextField(if (lengthField) model.lengthText else model.countText,
        { if (lengthField) model.lengthText = it else model.countText = it }, enabled = !model.busy,
        label = { Text(if (lengthField) "文字数" else "生成件数") }, singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text(if (lengthField) "4–999,999" else "1–${model.maxCount}", fontSize = 11.sp) },
        modifier = modifier.normalizeOnBlur(model::normalize))
}

@Composable private fun CharacterSettings(model: PassgenModel, importSymbols: () -> Unit) {
    if (model.mode != GenerationMode.PASSWORD) return
    var tab by remember { mutableStateOf(CharacterTab.UPPER) }
    val selected = when (tab) { CharacterTab.UPPER -> model.uppercaseCharacters; CharacterTab.LOWER -> model.lowercaseCharacters; CharacterTab.DIGITS -> model.digitCharacters; CharacterTab.SYMBOLS -> model.symbols }
    val enabled = when (tab) { CharacterTab.UPPER -> model.uppercase; CharacterTab.LOWER -> model.lowercase; CharacterTab.DIGITS -> model.digits; CharacterTab.SYMBOLS -> model.symbolsEnabled }
    fun update(value: String) {
        val canonical = CharacterPolicy.canonical(value, tab.alphabet)
        when (tab) { CharacterTab.UPPER -> model.uppercaseCharacters = canonical; CharacterTab.LOWER -> model.lowercaseCharacters = canonical; CharacterTab.DIGITS -> model.digitCharacters = canonical; CharacterTab.SYMBOLS -> model.symbols = canonical }
    }
    fun toggleCategory(value: Boolean) {
        when (tab) { CharacterTab.UPPER -> model.uppercase = value; CharacterTab.LOWER -> model.lowercase = value; CharacterTab.DIGITS -> model.digits = value; CharacterTab.SYMBOLS -> model.symbolsEnabled = value }
    }
    val excluded = if (model.excludeSimilar && tab != CharacterTab.SYMBOLS) tab.alphabet.filter { it in model.similarExclusions } else ""
    Surface(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("文字セット", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("大${if (model.uppercase) model.uppercaseCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 小${if (model.lowercase) model.lowercaseCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 数${if (model.digits) model.digitCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 記${if (model.symbolsEnabled) model.symbols.length else 0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f).padding(start = 8.dp))
                TextButton(onClick = { model.useDigitsOnly(); tab = CharacterTab.DIGITS }, enabled = !model.busy) { Text("数字のみ", fontSize = 12.sp) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CharacterTab.entries.forEachIndexed { index, item ->
                    SegmentedButton(selected = tab == item, onClick = { tab = item }, enabled = !model.busy,
                        shape = SegmentedButtonDefaults.itemShape(index, 4), icon = {}) { Text(item.title, fontSize = 12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(enabled, ::toggleCategory, enabled = !model.busy)
                Text("${tab.title}を含める", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { update(tab.alphabet) }, enabled = enabled && !model.busy) { Text("全選択", fontSize = 12.sp) }
                TextButton(onClick = { update("") }, enabled = enabled && !model.busy) { Text("解除", fontSize = 12.sp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tab.alphabet.forEach { character ->
                    val isExcluded = character in excluded
                    val checked = character in selected && !isExcluded
                    Surface(onClick = { update(if (character in selected) selected.replace(character.toString(), "") else selected + character) },
                        enabled = enabled && !model.busy && !isExcluded,
                        modifier = Modifier.size(44.dp).semantics {
                            contentDescription = "${tab.title} $character"
                            stateDescription = if (isExcluded) "似た文字として除外中" else if (checked) "選択" else "未選択"
                            testTag = "character-$character"
                        }, shape = RoundedCornerShape(10.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f)) else null) {
                        Box(contentAlignment = Alignment.Center) { Text(character.toString(), fontSize = 18.sp, fontFamily = FontFamily.Monospace,
                            color = if (!enabled || isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = .3f) else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
            if (excluded.isNotEmpty()) Text("$excluded は「似た文字を除外」で除外中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tab == CharacterTab.SYMBOLS && enabled) TextButton(onClick = importSymbols, enabled = !model.busy) { Text("記号一覧を貼り付け", fontSize = 13.sp) }
        }
    }
    CategoryOverview(model)
}

@Composable private fun CategoryOverview(model: PassgenModel) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("現在選択中の文字", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CharacterTab.entries.forEach { tab ->
                    val enabled = when (tab) { CharacterTab.UPPER -> model.uppercase; CharacterTab.LOWER -> model.lowercase; CharacterTab.DIGITS -> model.digits; CharacterTab.SYMBOLS -> model.symbolsEnabled }
                    val selected = when (tab) { CharacterTab.UPPER -> model.uppercaseCharacters; CharacterTab.LOWER -> model.lowercaseCharacters; CharacterTab.DIGITS -> model.digitCharacters; CharacterTab.SYMBOLS -> model.symbols }
                    val effective = if (enabled) CharacterPolicy.effective(selected, tab.alphabet, model.excludeSimilar, model.similarExclusions) else ""
                    val description = "${tab.title}、生成対象: ${effective.toList().joinToString(" ").ifEmpty { "なし" }}"
                    val row = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description; testTag = "overview-${tab.name}" }
                    if (LocalDensity.current.fontScale >= 2f) {
                        Column(row, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tab.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CharacterPreviewGlyphs(tab.alphabet, effective, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(row, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(tab.title, modifier = Modifier.width(42.dp).padding(top = 2.dp), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CharacterPreviewGlyphs(tab.alphabet, effective, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CharacterPreviewGlyphs(alphabet: String, effective: String, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        alphabet.forEach { character ->
            val active = character in effective
            Text(character.toString(), modifier = Modifier.widthIn(min = 11.dp), fontSize = 14.sp, lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f))
        }
    }
}

@Composable private fun RuleCard(model: PassgenModel) {
    if (model.mode == GenerationMode.PASSPHRASE) return
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text("生成ルール", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text(if (expanded) "閉じる" else "設定", fontSize = 12.sp)
            }
            if (expanded) {
                if (model.mode == GenerationMode.PASSWORD) {
                    RuleToggle("各文字種を必ず含める", model.requireEach, !model.busy) { model.requireEach = it }
                    RuleToggle("似た文字を除外", model.excludeSimilar, !model.busy) { model.excludeSimilar = it }
                    if (model.excludeSimilar) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CharacterPolicy.SIMILAR.forEach { c ->
                            FilterChip(c in model.similarExclusions, {
                                model.similarExclusions = CharacterPolicy.canonical(if (c in model.similarExclusions) model.similarExclusions.replace(c.toString(), "") else model.similarExclusions + c, CharacterPolicy.SIMILAR)
                            }, enabled = !model.busy, label = { Text("$c", fontFamily = FontFamily.Monospace) })
                        }
                    }
                }
                RuleToggle("同じ文字を連続させない", model.noAdjacent, !model.busy) { model.noAdjacent = it }
            } else Text(buildList {
                if (model.mode == GenerationMode.PASSWORD && model.requireEach) add("各文字種必須")
                if (model.mode == GenerationMode.PASSWORD && model.excludeSimilar) add("似た文字を除外")
                if (model.noAdjacent) add("連続禁止")
            }.joinToString(" · ").ifEmpty { "追加の制約なし" }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
    }
}

@Composable private fun RuleToggle(label: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value, change, enabled = enabled)
    }
}

@Composable private fun ResultSheet(model: PassgenModel, copy: (String) -> Boolean, fold: FoldingFeature?, dismiss: () -> Unit, generate: () -> Unit) {
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = minOf(if (model.busy) model.goal else model.results.size, 6)
    val desiredHeight = (190 + maxOf(1, rows) * if (model.revealed) 110 else 80).dp
        .coerceAtMost(with(density) { (LocalWindowInfo.current.containerSize.height * .85f).toDp() })
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState,
        modifier = if (fold == null) Modifier else Modifier.padding(start = with(density) { fold.bounds.right.toDp() }),
        sheetMaxWidth = if (fold == null) 640.dp else androidx.compose.ui.unit.Dp.Unspecified) {
        Column(Modifier.fillMaxWidth().height(desiredHeight).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.revealed = !model.revealed }, enabled = model.results.isNotEmpty(),
                    modifier = Modifier.semantics { testTag = "results-visibility"; stateDescription = if (model.revealed) "表示中" else "非表示" }) { Text(if (model.revealed) "隠す" else "表示") }
                Text("生成結果 ${model.results.size}件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = generate, enabled = model.work != WorkState.CANCELLING, modifier = Modifier.semantics { testTag = "regenerate" }) {
                    Text(model.generationActionTitle)
                }
            }
            model.results.firstOrNull()?.let { Text("推定エントロピー ${it.bits.toInt()} bits · ${model.clipboardSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (model.busy) {
                LinearProgressIndicator(progress = { model.results.size.toFloat() / model.goal }, modifier = Modifier.fillMaxWidth())
                Text("${model.results.size} / ${model.goal}件", style = MaterialTheme.typography.labelSmall)
            }
            model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (model.results.isEmpty() && !model.busy) item { Text("生成結果はありません。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp)) }
                items(model.results, key = { it.index }) { row ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${row.index + 1} · ${row.value.length}文字${if (row.value.length > 100) " / 先頭100文字" else ""}", style = MaterialTheme.typography.labelSmall)
                                Text(resultPreview(row.value, model.revealed), fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clearAndSetSemantics { contentDescription = "パスワード。読み上げを保護しています"; testTag = "secret-preview-${row.index}" })
                            }
                            TextButton(onClick = {
                                if (copy(row.value)) model.copiedIndices = model.copiedIndices + row.index
                                else model.message = "コピーできませんでした。OSが長い値を扱えない場合があります。"
                            }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                                contentDescription = if (row.index in model.copiedIndices) "コピー済み。もう一度コピー" else "コピー"
                                testTag = "copy-${row.index}"
                            }) {
                                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_content_copy), null, Modifier.size(24.dp))
                                    if (row.index in model.copiedIndices) {
                                        Surface(Modifier.align(Alignment.TopEnd).size(14.dp), shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.surface) {
                                            Icon(painterResource(R.drawable.ic_check_circle), null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { model.clear(); dismiss() }, enabled = !model.busy && model.results.isNotEmpty(), modifier = Modifier.semantics { testTag = "clear-results" }) { Text("消去") }
        }
    }
}

@Composable private fun SymbolSheet(model: PassgenModel, dismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun finishInput() { focus.clearFocus(); keyboard?.hide() }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("記号一覧を反映", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(text, { text = it; info = null }, label = { Text("使える記号の一覧を貼り付け") },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "symbol-paste-input" }, singleLine = true, enabled = !model.busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishInput() }))
            Text("含まれる対応記号を選択します。説明文の句読点も対象です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                finishInput()
                val found = SymbolPolicy.recognized(text)
                model.symbols = SymbolPolicy.applyPasted(text, model.symbols)
                info = if (found.isEmpty()) "対応する記号がないため、選択は変更していません。" else "${found.length}種類を反映しました。"
                text = ""
            }, enabled = text.isNotEmpty() && !model.busy, modifier = Modifier.fillMaxWidth()) { Text("記号に反映") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("完了") }
        }
    }
}

internal fun resultPreview(value: String, revealed: Boolean): String =
    if (revealed) value.take(100).toList().joinToString("\u200B") else SecretPresentation.MASK
