@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import jp.passgen.core.SecurityGuideExamples

/** Static, public examples. No generator, clipboard or user credentials are used. */
@Composable fun PasswordExamplesLessonSheet(initialTopic: Int, fold: FoldingFeature?, back: () -> Unit) {
    var topic by remember { mutableIntStateOf(initialTopic) }
    val density = LocalDensity.current
    val topics = listOf("避けたい例", "長さ", "パスフレーズ")
    ModalBottomSheet(onDismissRequest = back, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = if (fold == null) Modifier else Modifier.padding(start = with(density) { fold.bounds.right.toDp() }),
        sheetMaxWidth = if (fold == null) 720.dp else Dp.Unspecified) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("パスワードを例で見る", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(back, Modifier.semantics { testTag = "password-examples-back" }) { Text("戻る") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(IntrinsicSize.Min)) {
                topics.forEachIndexed { index, title ->
                    SegmentedButton(topic == index, { topic = index }, shape = SegmentedButtonDefaults.itemShape(index, topics.size),
                        modifier = Modifier.fillMaxHeight().semantics { testTag = "password-topic-$index" }) { Text(title) }
                }
            }
            key(topic) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(SecurityGuideExamples.NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (topic) { 0 -> WeakPasswordExamples(); 1 -> LengthExamples(); else -> PhraseExamples() }
                }
            }
        }
    }
}

@Composable private fun WeakPasswordExamples() {
    SecurityGuideExamples.weakExamples.forEach { example ->
        LessonPanel(example.title) {
            GuideExampleValue("", example.value, "weak-value-${example.key}")
            Text(example.reason, style = MaterialTheme.typography.bodyMedium)
        }
    }
    LessonPanel("よくある置き換えも、推測されます") {
        SecurityGuideExamples.mutations.forEachIndexed { index, value ->
            if (index > 0) Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant)
            GuideExampleValue(if (index == 0) "元の単語" else "変形した例", value, "weak-mutation-$index")
        }
        Text("大文字にする・末尾に1!を足す・aを@に置き換える。この変形も攻撃で試される候補です。文字種が揃っていても、無作為とは限りません。", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun LengthExamples() {
    var numberIndex by remember { mutableIntStateOf(0) }
    val item = SecurityGuideExamples.numericExamples[numberIndex]
    LessonPanel("同じ数字でも、長さで変わる") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            SecurityGuideExamples.numericExamples.forEachIndexed { index, sample ->
                SegmentedButton(numberIndex == index, { numberIndex = index }, shape = SegmentedButtonDefaults.itemShape(index, 3),
                    modifier = Modifier.fillMaxHeight().semantics { testTag = "guide-length-$index" }) { Text("${sample.length}桁") }
            }
        }
        GuideExampleValue("数字だけの形の例", item.value, "guide-length-value")
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("候補数", style = MaterialTheme.typography.labelMedium)
                Text(item.candidateLabel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { testTag = "guide-length-count" })
            }
        }
        Text("各桁を0〜9から独立に無作為に選ぶ場合です。先頭の0も含みます。4桁から2桁増えるごとに、候補数は100倍になります。", style = MaterialTheme.typography.bodyMedium)
        Text("数字だけの短いパスワードは候補が限られます。候補数は安全性や解読時間の保証ではなく、文字種とルールでも変わります。", style = MaterialTheme.typography.bodySmall)
    }
    LessonPanel("24文字のパスワードの形") {
        GuideExampleValue("英大文字・小文字・数字・記号の見本", SecurityGuideExamples.PASSWORD, "guide-password-shape")
        Text("まず15文字以上を目安に、利用先が許す範囲で長く。このアプリの初期値は24文字です。長いだけ・複雑に見えるだけでは足りず、無作為に新しく作ることが大切です。", style = MaterialTheme.typography.bodyMedium)
        GuideLink("NISTのパスワードガイド（英語）", "https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password")
    }
}

@Composable private fun PhraseExamples() {
    var separator by remember { mutableStateOf(" ") }
    val separators = listOf(" ", "-", "_")
    LessonPanel("無作為に選んだ7単語の形") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            separators.forEachIndexed { index, value ->
                SegmentedButton(separator == value, { separator = value }, shape = SegmentedButtonDefaults.itemShape(index, 3),
                    modifier = Modifier.fillMaxHeight().semantics { testTag = "guide-separator-$index" }) { Text(if (index == 0) "半角スペース" else value) }
            }
        }
        GuideExampleValue("区切りを変えて見比べる", SecurityGuideExamples.phrase(separator), "guide-phrase-value")
        Text("このアプリはEFFの7,776語から単語ごとに無作為に選びます。区切りは入力しやすさや利用先の条件で選びます。固定の区切り自体が推測しにくさを増やすわけではありません。", style = MaterialTheme.typography.bodyMedium)
    }
    LessonPanel("長いフレーズでも、避けたい例") {
        GuideExampleValue("知られた言い回し", "iloveyouforever")
        Text("好きな文・歌詞・名言をそのままつなぐ方法は、無作為な単語選びと異なります。上の7単語も公開済みなので、そのまま使わず新しく生成しましょう。", style = MaterialTheme.typography.bodyMedium)
        GuideLink("EFF：パスフレーズの作り方（英語）", "https://ssd.eff.org/module/creating-strong-passwords")
    }
}

@Composable internal fun GuideExampleValue(caption: String, value: String, tag: String = "") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.semantics { testTag = tag })
    }
}

@Composable internal fun GuideJourneyStep(number: Int, title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(number.toString(), Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
