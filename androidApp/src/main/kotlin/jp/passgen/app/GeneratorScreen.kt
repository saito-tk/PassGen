@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.passgen.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
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
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var resultsRequested by rememberSaveable { mutableStateOf(false) }
    var previousInline by rememberSaveable { mutableStateOf(false) }
    var showSymbols by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showStorageLesson by rememberSaveable { mutableStateOf(false) }
    var showAuthLesson by rememberSaveable { mutableStateOf(false) }
    var examplesTopic by rememberSaveable { mutableStateOf<Int?>(null) }
    var characterTab by rememberSaveable { mutableStateOf(CharacterTab.UPPER) }
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    val settingsScroll = rememberScrollState()
    val resultsScroll = rememberLazyListState()
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val verticalFold = fold?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
    val horizontalFold = fold?.takeIf { it.orientation == FoldingFeature.Orientation.HORIZONTAL }
    fun generate() {
        focus.clearFocus()
        if (model.work == WorkState.IDLE) resultsRequested = true
        model.generateOrStop()
    }
    fun clear() { if (!model.busy) { model.clear(); resultsRequested = false } }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .onGloballyPositioned { origin = it.positionInWindow() }) {
        val hingeStart = verticalFold?.let { with(density) { (it.bounds.left - origin.x).toDp().value } }
        val hingeEnd = verticalFold?.let { with(density) { (it.bounds.right - origin.x).toDp().value } }
        val region = generatorRegion(maxWidth.value, hingeStart, hingeEnd, 344f)
        val wholeWidth = region.length == maxWidth.value
        val split = generatorSplit(region.length, if (wholeWidth) hingeStart else null, if (wholeWidth) hingeEnd else null)
        val inline = split != null || horizontalFold != null
        val showResults = resultsRequested && !inline
        LaunchedEffect(inline) {
            if ((inline || previousInline) && (model.results.isNotEmpty() || model.busy)) resultsRequested = true
            previousInline = inline
        }
        LaunchedEffect(model.results.isEmpty()) { if (model.results.isEmpty()) resultsScroll.scrollToItem(0) }
        Scaffold(modifier = Modifier.padding(start = region.start.dp, end = (maxWidth.value - region.start - region.length).coerceAtLeast(0f).dp),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Passgen") },
                    navigationIcon = { IconAction(R.drawable.ic_bookmark, "プリセット", Modifier.semantics { testTag = "presets" }) { showPresets = true } },
                    actions = { IconAction(R.drawable.ic_settings, "設定と安全性", Modifier.semantics { testTag = "safety-settings" }) { showPrivacy = true } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, bottomBar = {
                if (!inline && !showResults) Surface(tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        ConfigurationIssue(model, Modifier.weight(1f))
                        if (model.results.isNotEmpty()) TextButton(onClick = { resultsRequested = true }) { ActionLabel(R.drawable.ic_expand_less, "結果 ${model.results.size}件") }
                        ResultGenerationAction(model, inline = true, generate = ::generate)
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .onGloballyPositioned { contentOrigin = it.positionInWindow() }) {
                val top = horizontalFold?.let { with(density) { (it.bounds.top - contentOrigin.y).toDp().value } }
                val bottom = horizontalFold?.let { with(density) { (it.bounds.bottom - contentOrigin.y).toDp().value } }
                val tabletop = generatorTabletop(maxHeight.value, top, bottom)
                val verticalRegion = generatorRegion(maxHeight.value, top, bottom, 180f)
                if (tabletop != null) {
                    Column(Modifier.fillMaxSize()) {
                        SettingsColumn(model, characterTab, { characterTab = it }, rulesExpanded, { rulesExpanded = it }, settingsScroll,
                            { showSymbols = true }, Modifier.fillMaxWidth().height(tabletop.settingsWidth.dp))
                        Spacer(Modifier.height(tabletop.gap.dp))
                        ResultsPanel(model, copy, resultsScroll, ::generate, ::clear, Modifier.weight(1f).fillMaxWidth())
                    }
                } else if (split != null) {
                    Row(Modifier.fillMaxSize().padding(top = verticalRegion.start.dp,
                        bottom = (maxHeight.value - verticalRegion.start - verticalRegion.length).coerceAtLeast(0f).dp)) {
                        SettingsColumn(model, characterTab, { characterTab = it }, rulesExpanded, { rulesExpanded = it }, settingsScroll,
                            { showSymbols = true }, Modifier.width(split.settingsWidth.dp).fillMaxHeight())
                        Spacer(Modifier.width(split.gap.dp))
                        ResultsPanel(model, copy, resultsScroll, ::generate, ::clear, Modifier.weight(1f).fillMaxHeight())
                    }
                } else if (horizontalFold != null) {
                    Column(Modifier.fillMaxSize().padding(top = verticalRegion.start.dp,
                        bottom = (maxHeight.value - verticalRegion.start - verticalRegion.length).coerceAtLeast(0f).dp)) {
                        SettingsColumn(model, characterTab, { characterTab = it }, rulesExpanded, { rulesExpanded = it }, settingsScroll,
                            { showSymbols = true }, Modifier.weight(1f).fillMaxWidth())
                        ResultsPanel(model, copy, resultsScroll, ::generate, ::clear, Modifier.weight(1f).fillMaxWidth())
                    }
                } else {
                    SettingsColumn(model, characterTab, { characterTab = it }, rulesExpanded, { rulesExpanded = it }, settingsScroll,
                        { showSymbols = true }, Modifier.fillMaxSize())
                }
            }
        }
        if (showPresets) PresetsSheet(model, fold) { model.editorSession.clearPresets(); showPresets = false }
        if (showResults) ResultSheet(model, fold, copy, resultsScroll, { resultsRequested = false }, ::generate, ::clear)
        if (showSymbols) SymbolSheet(model, fold) { model.editorSession.clearSymbols(); showSymbols = false }
        if (showPrivacy) PrivacySheet(model, fold, { showPrivacy = false }) { showPrivacy = false; showGuide = true }
        if (showGuide) SecurityGuideSheet(fold = fold,
            dismiss = { showGuide = false },
            storageLesson = { showGuide = false; showStorageLesson = true },
            authLesson = { showGuide = false; showAuthLesson = true },
            examplesLesson = { topic -> showGuide = false; examplesTopic = topic })
        if (showStorageLesson) PasswordStorageLessonSheet(fold) { showStorageLesson = false; showGuide = true }
        if (showAuthLesson) AuthenticationFactorsLessonSheet(fold) { showAuthLesson = false; showGuide = true }
        examplesTopic?.let { topic -> PasswordExamplesLessonSheet(topic, fold) { examplesTopic = null; showGuide = true } }
    }
}

@Composable private fun SettingsColumn(model: PassgenModel, tab: CharacterTab, selectTab: (CharacterTab) -> Unit,
    expanded: Boolean, expand: (Boolean) -> Unit, scroll: ScrollState, symbols: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(scroll).padding(12.dp).semantics { testTag = "settings-pane" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoreSettings(model)
        CharacterSettings(model, tab, selectTab, symbols)
        RuleCard(model, expanded, expand)
    }
}

@Composable private fun ResultsPanel(model: PassgenModel, copy: (String) -> Boolean, scroll: LazyListState,
    generate: () -> Unit, clear: () -> Unit, modifier: Modifier) {
    Surface(modifier.padding(12.dp).semantics { testTag = "results-pane" },
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultContent(model, copy, scroll, inline = true, generate = generate, clear = clear)
        }
    }
}

@Composable private fun Modifier.normalizeOnBlur(normalize: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { if (focused && !it.isFocused) normalize(); focused = it.isFocused }
}

@Composable private fun CoreSettings(model: PassgenModel) {
    Surface(modifier = Modifier.fillMaxWidth().semantics { testTag = "generation-settings" },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(when (mode) { GenerationMode.PASSWORD -> "パスワード"; GenerationMode.PASSPHRASE -> "パスフレーズ" }, style = MaterialTheme.typography.labelLarge)
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
                        FilterChip(model.separator == value, { model.separator = value }, enabled = !model.busy, label = { Text(label) },
                            leadingIcon = if (model.separator == value) { { AppIcon(R.drawable.ic_check, Modifier.size(16.dp)) } } else null)
                    }
                }
            }
            if (model.isDigitsOnly) Text("数字だけの短いパスワードは、候補数が少なくなります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable private fun QuantityField(model: PassgenModel, lengthField: Boolean, modifier: Modifier) {
    if (lengthField && model.mode == GenerationMode.PASSPHRASE) {
        OutlinedCard(modifier) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("単語数", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconAction(R.drawable.ic_remove, "単語数を減らす", enabled = !model.busy && model.words > 6) { model.words-- }
                    Text("${model.words}", style = MaterialTheme.typography.titleLarge)
                    IconAction(R.drawable.ic_add, "単語数を増やす", enabled = !model.busy && model.words < 12) { model.words++ }
                }
            }
        }
    } else {
        val focus = LocalFocusManager.current
        OutlinedTextField(if (lengthField) model.lengthText else model.countText,
            { if (lengthField) model.lengthText = it else model.countText = it }, enabled = !model.busy,
            label = { Text(if (lengthField) "文字数" else "生成件数") }, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            supportingText = { Text(if (lengthField) "4–999,999" else "1–${model.maxCount}", fontSize = 11.sp) },
            modifier = modifier.normalizeOnBlur(model::normalize))
    }
}

@Composable private fun CharacterSettings(model: PassgenModel, tab: CharacterTab, selectTab: (CharacterTab) -> Unit, importSymbols: () -> Unit) {
    if (model.mode != GenerationMode.PASSWORD) return
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
    var selectionMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("文字セット", style = MaterialTheme.typography.titleMedium)
            Text("大${if (model.uppercase) model.uppercaseCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 小${if (model.lowercase) model.lowercaseCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 数${if (model.digits) model.digitCharacters.count { !model.excludeSimilar || it !in model.similarExclusions } else 0} / 記${if (model.symbolsEnabled) model.symbols.length else 0}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (LocalDensity.current.fontScale > 1.3f) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterTab.entries.forEach { item ->
                        FilterChip(tab == item, { selectTab(item) }, enabled = !model.busy, label = { Text(item.title) },
                            leadingIcon = if (tab == item) { { AppIcon(R.drawable.ic_check, Modifier.size(16.dp)) } } else null)
                    }
                }
            } else SecondaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                CharacterTab.entries.forEach { item ->
                    Tab(selected = tab == item, onClick = { selectTab(item) }, enabled = !model.busy,
                        text = { Text(item.title, style = MaterialTheme.typography.labelLarge) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RuleToggle("${tab.title}を含める", enabled, !model.busy, Modifier.weight(1f), ::toggleCategory)
                Box {
                    IconAction(R.drawable.ic_more_vert, "${tab.title}の選択を変更", enabled = enabled && !model.busy) { selectionMenu = true }
                    DropdownMenu(selectionMenu, { selectionMenu = false }) {
                        DropdownMenuItem(text = { Text("全選択") }, leadingIcon = { AppIcon(R.drawable.ic_select_all) }, onClick = { update(tab.alphabet); selectionMenu = false })
                        DropdownMenuItem(text = { Text("選択を解除") }, leadingIcon = { AppIcon(R.drawable.ic_deselect) }, onClick = { update(""); selectionMenu = false })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tab.alphabet.forEach { character ->
                    val isExcluded = character in excluded
                    val checked = enabled && character in selected && !isExcluded
                    Surface(onClick = { update(if (character in selected) selected.replace(character.toString(), "") else selected + character) },
                        enabled = enabled && !model.busy && !isExcluded,
                        modifier = Modifier.size(maxOf(48f, 32f + 16f * LocalDensity.current.fontScale).dp).semantics {
                            toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off; role = Role.Checkbox
                            contentDescription = "${tab.title} $character"
                            stateDescription = if (isExcluded) "似た文字として除外中" else if (checked) "選択" else "未選択"
                            testTag = "character-$character"
                        }, shape = RoundedCornerShape(10.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(character.toString(), fontSize = 18.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace,
                                color = if (!enabled || isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else MaterialTheme.colorScheme.onSurface)
                            if (checked) AppIcon(R.drawable.ic_check, Modifier.align(Alignment.TopEnd).padding(3.dp).size(10.dp))
                        }
                    }
                }
            }
            if (excluded.isNotEmpty()) Text("$excluded は「似た文字を除外」で除外中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tab == CharacterTab.SYMBOLS && enabled) TextButton(onClick = importSymbols, enabled = !model.busy) { ActionLabel(R.drawable.ic_content_paste, "記号一覧を貼り付け") }
        }
    }
    CategoryOverview(model)
}

@Composable internal fun CategoryOverview(model: PassgenModel, identifierPrefix: String = "overview") {
    val labelSize = MaterialTheme.typography.labelMedium.fontSize
    val labelWidth = with(LocalDensity.current) { maxOf(42.dp, labelSize.toDp() * 3.5f) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("現在選択中の文字", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CharacterTab.entries.forEach { tab ->
                    val enabled = when (tab) { CharacterTab.UPPER -> model.uppercase; CharacterTab.LOWER -> model.lowercase; CharacterTab.DIGITS -> model.digits; CharacterTab.SYMBOLS -> model.symbolsEnabled }
                    val selected = when (tab) { CharacterTab.UPPER -> model.uppercaseCharacters; CharacterTab.LOWER -> model.lowercaseCharacters; CharacterTab.DIGITS -> model.digitCharacters; CharacterTab.SYMBOLS -> model.symbols }
                    val effective = if (enabled) CharacterPolicy.effective(selected, tab.alphabet, model.excludeSimilar, model.similarExclusions) else ""
                    val description = "${tab.title}、生成対象: ${effective.toList().joinToString(" ").ifEmpty { "なし" }}"
                    val row = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description; testTag = "$identifierPrefix-${tab.name}" }
                    if (LocalDensity.current.fontScale >= 2f) {
                        Column(row, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tab.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CharacterPreviewGlyphs(tab.alphabet, effective, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(row, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(tab.title, modifier = Modifier.width(labelWidth).padding(top = 2.dp), style = MaterialTheme.typography.labelMedium,
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
    val spacing = with(LocalDensity.current) { maxOf(3.dp, 3.sp.toDp()) }
    val glyphWidth = with(LocalDensity.current) { maxOf(11.dp, 11.sp.toDp()) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
        alphabet.forEach { character ->
            val active = character in effective
            Text(character.toString(), modifier = Modifier.widthIn(min = glyphWidth), fontSize = 14.sp, lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f))
        }
    }
}

@Composable private fun RuleCard(model: PassgenModel, expanded: Boolean, expand: (Boolean) -> Unit) {
    if (model.mode == GenerationMode.PASSPHRASE) return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expand(!expanded) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(R.drawable.ic_tune)
                Text("生成ルール", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                AppIcon(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
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

@Composable private fun RuleToggle(label: String, value: Boolean, enabled: Boolean, modifier: Modifier = Modifier, change: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value, onCheckedChange = null, enabled = enabled)
    }
}

@Composable private fun ResultSheet(model: PassgenModel, fold: FoldingFeature?, copy: (String) -> Boolean, scroll: LazyListState,
    dismiss: () -> Unit, generate: () -> Unit, clear: () -> Unit) {
    val density = LocalDensity.current
    val rows = minOf(if (model.busy) model.goal else model.results.size, 6)
    val desiredHeight = (190 + maxOf(1, rows) * if (model.revealed) 110 else 80).dp
        .coerceAtMost(with(density) { (LocalWindowInfo.current.containerSize.height * .85f).toDp() })
    PassgenSheet(dismiss, fold, modifier = Modifier.semantics { testTag = "results-sheet" }) {
        Column(Modifier.fillMaxWidth().height(desiredHeight).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultContent(model, copy, scroll, inline = false, generate = generate, clear = clear)
        }
    }
}

@Composable private fun ConfigurationIssue(model: PassgenModel, modifier: Modifier = Modifier) {
    val issue = if (model.busy) null else model.configurationIssue
    if (issue != null) Text(issue, modifier.semantics { testTag = "configuration-issue"; liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ResultVisibility(model: PassgenModel) {
    val revealed = model.revealed
    IconAction(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
        if (revealed) "生成結果を隠す" else "生成結果を表示", enabled = model.results.isNotEmpty(),
        modifier = Modifier.semantics { testTag = "results-visibility"; stateDescription = if (revealed) "表示中" else "非表示" }) {
        model.revealed = !model.revealed
    }
}

@Composable private fun ResultGenerationAction(model: PassgenModel, inline: Boolean, generate: () -> Unit) {
    Button(onClick = generate, enabled = model.generationActionEnabled, modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = if (inline) "generate" else "regenerate" }) {
        ActionLabel(if (model.busy) R.drawable.ic_stop else if (model.generationActionTitle == "再生成") R.drawable.ic_refresh else R.drawable.ic_key, model.generationActionTitle)
    }
}

@Composable private fun ColumnScope.ResultContent(model: PassgenModel, copy: (String) -> Boolean, scroll: LazyListState,
    inline: Boolean, generate: () -> Unit, clear: () -> Unit) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("生成結果 ${model.results.size}件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ResultVisibility(model)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ResultGenerationAction(model, inline, generate) }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResultVisibility(model)
            Text("生成結果 ${model.results.size}件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ResultGenerationAction(model, inline, generate)
        }
    }
    ConfigurationIssue(model)
    if (model.results.isNotEmpty()) {
        val shortest = model.results.minOf { it.value.length }
        val longest = model.results.maxOf { it.value.length }
        val length = if (shortest == longest) "${longest}文字" else "${shortest}〜${longest}文字"
        Text(length + if (longest > 100) " / 先頭100文字のみ表示" else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "results-summary" })
    }
    model.results.firstOrNull()?.let { Text("推定エントロピー ${it.bits.toInt()} bits · ${model.clipboardSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (model.busy) {
        LinearProgressIndicator(progress = { model.results.size.toFloat() / model.goal }, modifier = Modifier.fillMaxWidth())
        Text("${model.results.size} / ${model.goal}件", style = MaterialTheme.typography.labelSmall)
    }
    model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    LazyColumn(Modifier.weight(1f).semantics { testTag = "result-list" }, state = scroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (model.results.isEmpty() && !model.busy) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(R.drawable.ic_key)
                Text("生成結果はありません。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(model.results, key = { it.index }) { row ->
            // Observe during composition, not from callbacks on detached / prefetched lazy nodes.
            val copied = row.index in model.copiedIndices
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${row.index + 1}", style = MaterialTheme.typography.labelSmall)
                        Text(resultPreview(row.value, model.revealed), fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = "パスワード。読み上げを保護しています"; testTag = "secret-preview-${row.index}" })
                    }
                    IconAction(R.drawable.ic_content_copy, if (copied) "コピー済み。もう一度コピー" else "コピー",
                        Modifier.semantics { testTag = "copy-${row.index}" }, badge = copied) {
                        if (copy(row.value)) model.copiedIndices = model.copiedIndices + row.index
                        else model.message = "コピーできませんでした。OSが長い値を扱えない場合があります。"
                    }
                }
            }
        }
    }
    if (model.results.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { clear() }, enabled = !model.busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.semantics { testTag = "clear-results" }) { ActionLabel(R.drawable.ic_delete, "消去") }
    }
}

@Composable private fun SymbolSheet(model: PassgenModel, fold: FoldingFeature?, dismiss: () -> Unit) {
    var text by model.editorSession.symbolText
    var info by model.editorSession.symbolInfo
    PassgenSheet(dismiss, fold) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun finishInput() { focus.clearFocus(); keyboard?.hide() }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeading("記号一覧を反映", R.drawable.ic_content_paste)
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
