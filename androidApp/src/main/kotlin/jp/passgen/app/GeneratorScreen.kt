@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.passgen.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import jp.passgen.core.*
import kotlin.math.roundToInt

@Composable fun GeneratorScreen(model: PassgenModel, fold: FoldingFeature?, copy: (String) -> Unit) {
    var symbolsSheet by remember { mutableStateOf(false) }
    var privacySheet by remember { mutableStateOf(false) }
    var resultsSheet by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var bodyOrigin by remember { mutableStateOf(Offset.Zero) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
                        Text("P", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, fontSize = 23.sp)
                    }
                    Text("Passgen", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { privacySheet = true }) { Text("端末内で完結") }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                val hinge = fold?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
                val leftPadding = if (hinge != null) with(density) { hinge.bounds.right.toDp() } + 16.dp else 16.dp
                Column(Modifier.navigationBarsPadding().imePadding().padding(start = leftPadding, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Button(onClick = { focus.clearFocus(); model.generateOrStop() },
                        enabled = model.work != WorkState.CANCELLING,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTagCompat("generate"),
                        shape = RoundedCornerShape(22.dp)) {
                        Text(when (model.work) { WorkState.IDLE -> "パスワードを生成  →"; WorkState.RUNNING -> "停止"; WorkState.CANCELLING -> "停止中…" },
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Text("保存しない。送信しない。あなたの手元で。", modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).onGloballyPositioned { bodyOrigin = it.positionInWindow() }) {
            val verticalHinge = fold?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
            val horizontalHinge = fold?.takeIf { it.orientation == FoldingFeature.Orientation.HORIZONTAL }
            val hingeLeft = with(density) { ((verticalHinge?.bounds?.left ?: 0) - bodyOrigin.x).toDp() }
            val hingeTop = with(density) { ((horizontalHinge?.bounds?.top ?: 0) - bodyOrigin.y).toDp() }
            when {
                verticalHinge != null && hingeLeft > 160.dp && hingeLeft < maxWidth - 160.dp -> {
                    Row(Modifier.fillMaxSize()) {
                        SettingsPanel(model, { symbolsSheet = true }, Modifier.width(hingeLeft).fillMaxHeight())
                        Spacer(Modifier.width(with(density) { verticalHinge.bounds.width().toDp() }.coerceAtLeast(16.dp)))
                        ResultsPanel(model, copy, { resultsSheet = true }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                horizontalHinge != null && hingeTop > 100.dp && hingeTop < maxHeight - 100.dp -> {
                    Column(Modifier.fillMaxSize()) {
                        ResultsPanel(model, copy, { resultsSheet = true }, Modifier.fillMaxWidth().height(hingeTop))
                        Spacer(Modifier.height(with(density) { horizontalHinge.bounds.height().toDp() }.coerceAtLeast(16.dp)))
                        SettingsPanel(model, { symbolsSheet = true }, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                maxWidth >= 840.dp -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPanel(model, { symbolsSheet = true }, Modifier.weight(1.1f).fillMaxHeight())
                    ResultsPanel(model, copy, { resultsSheet = true }, Modifier.weight(1f).fillMaxHeight())
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.height(2.dp))
                    Hero(model, copy, { resultsSheet = true })
                    SettingsContent(model) { symbolsSheet = true }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    if (symbolsSheet) SymbolSheet(model) { symbolsSheet = false }
    if (privacySheet) ModalBottomSheet(onDismissRequest = { privacySheet = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("秘密に、居場所をつくらない。", style = MaterialTheme.typography.titleLarge)
            Text("生成に通信は使いません。OSの暗号学的乱数から生成し、結果はアプリが背景へ移ると消去します。")
            Text("コピーは機密情報として扱い、30秒後または背景移行時に消去します。他の内容に置き換わっていた場合は消しません。")
            Text("画面キャプチャと最近使ったアプリのプレビューを保護します。コピー先のアプリや、侵害されたOSでの保護は保証できません。")
            Text("単語方式にはEFFの7,776語リストを使用します。数字コードは同じ長さの英数字より候補が少なくなります。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("エントロピーは生成空間の目安です。サービスの安全性や解読時間を保証する数値ではありません。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { privacySheet = false }, Modifier.align(Alignment.End)) { Text("閉じる") }
        }
    }
    if (resultsSheet) ModalBottomSheet(onDismissRequest = { resultsSheet = false }) {
        Text("今回の生成結果 · ${model.results.size}件", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxWidth().weightCompat().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(model.results, key = { it.index }) { row ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${row.index + 1} · ${row.value.length}文字", style = MaterialTheme.typography.labelMedium)
                            Text(if (model.revealed) row.value.take(100) else "•••• •••• ••••", fontFamily = FontFamily.Monospace,
                                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { contentDescription = "パスワード。読み上げを保護しています" })
                        }
                        TextButton(onClick = { copy(row.value); model.copiedIndex = row.index }) { Text(if (model.copiedIndex == row.index) "コピー済み" else "コピー") }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// Avoid saved instance state for any password-bearing UI.
private fun Modifier.testTagCompat(tag: String) = semantics { testTag = tag }
private fun Modifier.weightCompat() = heightIn(max = 600.dp)

@Composable private fun Modifier.normalizeOnBlur(normalize: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return onFocusChanged {
        if (hadFocus && !it.isFocused) normalize()
        hadFocus = it.isFocused
    }
}

@Composable private fun SettingsPanel(model: PassgenModel, symbols: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsContent(model, symbols)
    }
}
@Composable private fun ResultsPanel(model: PassgenModel, copy: (String) -> Unit, all: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("強さを、\n手のひらに。", style = MaterialTheme.typography.headlineLarge)
        Text("用途に合わせて選ぶ、あなただけの組み合わせ。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Hero(model, copy, all)
        Text("生成結果は今回限り。背景に移ると消去されます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Hero(model: PassgenModel, copy: (String) -> Unit, all: () -> Unit) {
    val row = model.results.firstOrNull()
    val bits = row?.bits ?: model.estimate()
    val ink = Color(0xFFE2F7EF)
    val mint = Color(0xFFA9E9C7)
    Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF173D48), contentColor = ink) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color(0xFF285260), radius = size.width * .45f, center = Offset(size.width, 0f), style = Stroke(1.dp.toPx()))
                drawCircle(Color(0xFF285260), radius = size.width * .63f, center = Offset(size.width, 0f), style = Stroke(1.dp.toPx()))
            }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (row == null) "YOUR NEXT SECRET" else "YOUR SECRET", fontSize = 11.sp, letterSpacing = 2.sp, color = mint, modifier = Modifier.weight(1f))
                    Text(if (model.busy) "${model.results.size} / ${model.goal}" else if (row == null) "未生成" else "${model.results.size}件生成", fontSize = 12.sp)
                }
                Text(when { row == null -> "まだ、あなただけの\n秘密はありません。"; model.revealed -> row.value.take(100); else -> "••••  ••••\n••••  ••••" },
                    style = MaterialTheme.typography.titleLarge, fontFamily = if (row != null) FontFamily.Monospace else FontFamily.Default,
                    lineHeight = 34.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clearAndSetSemantics { contentDescription = if (row == null) "未生成" else "生成されたパスワード。読み上げを保護しています" })
                if (row != null) Text("${row.value.length}文字${if (row.value.length > 100) " · 先頭100文字を表示 / コピーは全文" else ""}", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EntropyRing(bits, mint)
                    Column(Modifier.weight(1f)) {
                        Text(when { bits == null -> "条件を確認"; bits >= 128 -> "余裕のある強度"; bits >= 80 -> "十分な候補の広さ"; else -> "長さを増やすと安心" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (bits == null) "文字種を選んでください" else "${bits.toInt()} bits · ${if (row == null) "生成前の目安" else "生成時の条件"}", fontSize = 12.sp, color = Color(0xFFBDD5DB))
                    }
                }
                if (model.busy) LinearProgressIndicator(progress = { model.results.size.toFloat() / model.goal }, modifier = Modifier.fillMaxWidth(), color = mint, trackColor = Color(0xFF355761))
                if (row != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { copy(row.value); model.copiedIndex = row.index }, colors = ButtonDefaults.buttonColors(containerColor = mint, contentColor = Color(0xFF153E34))) {
                            Text(if (model.copiedIndex == row.index) "コピー済み ✓" else "コピー")
                        }
                        TextButton(onClick = { model.revealed = !model.revealed }, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(if (model.revealed) "隠す" else "表示") }
                        TextButton(onClick = model::clear, enabled = !model.busy, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text("消去") }
                    }
                    if (model.results.size > 1) TextButton(onClick = all, colors = ButtonDefaults.textButtonColors(contentColor = mint)) { Text("すべての結果を見る (${model.results.size})  →") }
                    Text("コピーは30秒で消去", fontSize = 11.sp, color = Color(0xFFBDD5DB))
                }
            }
        }
    }
}

@Composable private fun EntropyRing(bits: Double?, color: Color) {
    Box(Modifier.size(46.dp).clearAndSetSemantics { contentDescription = "推定エントロピー ${bits?.toInt() ?: 0}ビット" }, Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            drawCircle(color.copy(alpha = .18f), style = Stroke(3.dp.toPx()))
            drawArc(color, -90f, ((bits ?: 0.0) / 128).coerceIn(0.0, 1.0).toFloat() * 360, false, style = Stroke(3.dp.toPx()))
        }
        Text(if ((bits ?: 0.0) >= 128) "✓" else "+", color = color, fontSize = 21.sp)
    }
}

@Composable private fun SettingsContent(model: PassgenModel, openSymbols: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionLabel("01", "つくり方を選ぶ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(GenerationMode.RANDOM to ("Aa" to "ランダム"), GenerationMode.PASSPHRASE to ("abc" to "単語"), GenerationMode.NUMERIC to ("123" to "数字")).forEach { (mode, label) ->
                val selected = model.mode == mode
                Surface(onClick = { model.chooseMode(mode) }, enabled = !model.busy,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label.first, fontFamily = FontFamily.Monospace, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                        Text(label.second, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Text(when (model.mode) { GenerationMode.RANDOM -> "サイトの文字条件に合わせて、自由に。"; GenerationMode.PASSPHRASE -> "7,776語から無作為に。覚えやすさを大切に。"; else -> "数字だけのサービスに。短いコードは強度に注意。" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.weight(1.5f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (model.mode == GenerationMode.PASSPHRASE) "単語数" else "文字数", style = MaterialTheme.typography.labelLarge)
                    if (model.mode == GenerationMode.PASSPHRASE) {
                        Text("${model.words}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Slider(value = model.words.toFloat(), onValueChange = { model.words = it.roundToInt() }, valueRange = 6f..12f, steps = 5, enabled = !model.busy)
                    } else {
                        OutlinedTextField(model.lengthText, { model.lengthText = it }, enabled = !model.busy,
                            singleLine = true, textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().normalizeOnBlur(model::normalize),
                            supportingText = { Text("4–999,999文字", fontSize = 10.sp) })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(16, 24, 32, 48).forEach { length ->
                                FilterChip(selected = model.length == length, onClick = { model.lengthText = length.toString(); model.normalize() }, label = { Text("$length", fontSize = 12.sp) }, enabled = !model.busy)
                            }
                        }
                    }
                }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("まとめて生成", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(model.countText, { model.countText = it }, enabled = !model.busy, singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().normalizeOnBlur(model::normalize),
                        supportingText = { Text("1–${model.maxCount}件", fontSize = 10.sp) })
                    Text("必要な分だけ。\n履歴は残しません。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (model.mode == GenerationMode.RANDOM) {
            SectionLabel("02", "使える文字を整える")
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(8.dp)) {
                    if (LocalDensity.current.fontScale > 1.3f) {
                        CharacterToggle("大文字", "A–Z", model.uppercase, !model.busy, Modifier.fillMaxWidth()) { model.uppercase = it }
                        CharacterToggle("小文字", "a–z", model.lowercase, !model.busy, Modifier.fillMaxWidth()) { model.lowercase = it }
                        CharacterToggle("数字", "0–9", model.digits, !model.busy, Modifier.fillMaxWidth()) { model.digits = it }
                        CharacterToggle("記号", "!#?", model.symbolsEnabled, !model.busy, Modifier.fillMaxWidth()) { model.symbolsEnabled = it }
                    } else {
                    Row {
                        CharacterToggle("大文字", "A–Z", model.uppercase, !model.busy, Modifier.weight(1f)) { model.uppercase = it }
                        CharacterToggle("小文字", "a–z", model.lowercase, !model.busy, Modifier.weight(1f)) { model.lowercase = it }
                    }
                    Row {
                        CharacterToggle("数字", "0–9", model.digits, !model.busy, Modifier.weight(1f)) { model.digits = it }
                        CharacterToggle("記号", "!#?", model.symbolsEnabled, !model.busy, Modifier.weight(1f)) { model.symbolsEnabled = it }
                    }
                    }
                    if (model.symbolsEnabled) {
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        TextButton(onClick = openSymbols, enabled = !model.busy, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("使える記号を選ぶ  →")
                                Text("${model.symbols.length}種 · 貼り付けから反映できます", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    RuleToggle("似た文字を除外", "O o 0 / I l 1 を除く", model.excludeSimilar, !model.busy) { model.excludeSimilar = it }
                    RuleToggle("各文字種を必ず含める", "選択したすべての文字種を使用", model.requireEach, !model.busy) { model.requireEach = it }
                    RuleToggle("同じ文字を連続させない", "隣り合う文字の重複を防ぐ", model.noAdjacent, !model.busy) { model.noAdjacent = it }
                }
            }
        } else if (model.mode == GenerationMode.PASSPHRASE) {
            SectionLabel("02", "単語の区切り")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("." to "ドット", "_" to "アンダーバー", " " to "スペース").forEach { (value, label) ->
                    FilterChip(selected = model.separator == value, onClick = { model.separator = value }, enabled = !model.busy, label = { Text(label) })
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) { RuleToggle("同じ数字を連続させない", "数字は0–9を使用します", model.noAdjacent, !model.busy) { model.noAdjacent = it } }
            }
        }
    }
}

@Composable private fun SectionLabel(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun CharacterToggle(title: String, detail: String, checked: Boolean, enabled: Boolean, modifier: Modifier, change: (Boolean) -> Unit) {
    Row(modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(title, style = MaterialTheme.typography.labelLarge); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Checkbox(checked, change, enabled = enabled)
    }
}
@Composable private fun RuleToggle(title: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, change, enabled = enabled)
    }
}

@Composable private fun SymbolSheet(model: PassgenModel, dismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("サイトに合わせる、記号。", style = MaterialTheme.typography.titleLarge)
            Text("使える記号だけを選択。案内文の記号一覧を貼り付けて、一度に反映できます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { model.symbols = SymbolPolicy.SUPPORTED }, enabled = !model.busy) { Text("すべて選択") }
                TextButton(onClick = { model.symbols = "" }, enabled = !model.busy) { Text("選択を解除") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SymbolPolicy.SUPPORTED.forEach { symbol ->
                    FilterChip(selected = symbol in model.symbols, onClick = {
                        model.symbols = SymbolPolicy.recognized(if (symbol in model.symbols) model.symbols.replace(symbol.toString(), "") else model.symbols + symbol)
                    }, enabled = !model.busy, label = { Text(symbol.toString(), fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.widthIn(min = 22.dp)) })
                }
            }
            OutlinedTextField(text, { text = it; info = null }, label = { Text("使える記号の一覧を貼り付け") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !model.busy)
            Text("入力に含まれる対応記号をそのまま選択します。説明文の句読点も対象です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                val found = SymbolPolicy.recognized(text)
                model.symbols = SymbolPolicy.applyPasted(text, model.symbols)
                info = if (found.isEmpty()) "対応する記号がないため、選択は変更していません。" else "${found.length}種類を反映しました。"
                text = ""
            }, enabled = text.isNotEmpty() && !model.busy, modifier = Modifier.fillMaxWidth()) { Text("記号に反映") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (model.symbols.isEmpty()) Text("記号が未選択です。選択するか、記号を含める設定をオフにしてください。", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("完了") }
        }
    }
}
