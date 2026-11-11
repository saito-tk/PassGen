@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import jp.passgen.core.PasswordCombinationLesson
import jp.passgen.core.PasswordCombinationCount
import jp.passgen.core.BruteForceLesson
import androidx.compose.runtime.saveable.rememberSaveable

/** Static, public examples. No generator, clipboard or user credentials are used. */
@Composable fun PasswordExamplesLessonSheet(initialTopic: Int, fold: FoldingFeature?, back: () -> Unit) {
    var topic by rememberSaveable { mutableIntStateOf(initialTopic) }
    var lessonLength by rememberSaveable { mutableIntStateOf(4) }
    var lessonCharacters by rememberSaveable { mutableIntStateOf(4) }
    var lessonRate by rememberSaveable { mutableIntStateOf(2) }
    val density = LocalDensity.current
    val topics = listOf("避けたい例", "長さ", "パスフレーズ")
    PassgenSheet(back, fold, maxWidth = 720.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_arrow_back, "戻る", Modifier.semantics { testTag = "password-examples-back" }, action = back)
                Spacer(Modifier.width(8.dp))
                Text("パスワードを例で見る", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                    when (topic) { 0 -> WeakPasswordExamples(); 1 -> LengthExamples(lessonLength, { lessonLength = it }, lessonCharacters, { lessonCharacters = it }, lessonRate, { lessonRate = it }); else -> PhraseExamples() }
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

@Composable private fun LengthExamples(length: Int, changeLength: (Int) -> Unit, selection: Int, changeSelection: (Int) -> Unit, rateIndex: Int, changeRate: (Int) -> Unit) {
    var showExact by rememberSaveable { mutableStateOf(false) }
    LessonPanel("文字種と長さで、何通り？") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordCombinationLesson.groups.forEach { group ->
                val selected = selection and group.mask != 0
                FilterChip(selected, { changeSelection(selection xor group.mask) },
                    label = { Text("${group.title} ${group.size}") },
                    leadingIcon = if (selected) { { AppIcon(R.drawable.ic_check, Modifier.size(18.dp)) } } else null,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "guide-alphabet-${group.mask}" })
            }
        }
        Text("文字数", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4, 8, 12, 16, 24).forEach { value ->
                FilterChip(length == value, { changeLength(value) }, label = { Text("${value}文字") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "guide-length-preset-$value" })
            }
        }
        run {
            val result = remember(length, selection) { PasswordCombinationLesson.calculate(length, selection) }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1文字に使える候補：${result.alphabetSize}種類", style = MaterialTheme.typography.bodyMedium)
                    Text(result.summary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { testTag = "guide-length-count" })
                    if (selection != 0) {
                        Text("${result.formula}（${result.alphabetSize}を${length}回掛ける）", style = MaterialTheme.typography.bodyMedium)
                        Text("1文字増やすと ${result.alphabetSize}倍", style = MaterialTheme.typography.titleSmall)
                        if (result.isAbbreviated) {
                            TextButton(onClick = { showExact = !showExact }) {
                                Text("正確な数を見る")
                                AppIcon(if (showExact) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
                            }
                            if (showExact) Text(result.formattedCount + " 通り", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { testTag = "guide-length-exact" })
                        }
                    } else Text("文字種を1つ以上選んでください。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (selection != 0) BruteForceTimeLesson(result, rateIndex, changeRate)
        }
        Text("選んだ文字種は全文字を使います。記号は半角32種類。各位置で独立に選び、同じ文字の繰り返しや先頭の0も含めた計算です。各文字種を必ず含む条件は付けません。", style = MaterialTheme.typography.bodySmall)
        Text("説明用の試算です。生成設定・使用不可記号の設定とは連動しません。候補数は安全性や解読時間の保証ではなく、人が選ぶ単語や規則的な並びの推測しにくさを表すものでもありません。", style = MaterialTheme.typography.bodySmall)
    }
    LessonPanel("24文字のパスワードの形") {
        GuideExampleValue("英大文字・小文字・数字・記号の見本", SecurityGuideExamples.PASSWORD, "guide-password-shape")
        Text("まず15文字以上を目安に、利用先が許す範囲で長く。このアプリの初期値は24文字です。長いだけ・複雑に見えるだけでは足りず、無作為に新しく作ることが大切です。", style = MaterialTheme.typography.bodyMedium)
        GuideLink("NISTのパスワードガイド（英語）", "https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password")
    }
}

@Composable private fun BruteForceTimeLesson(combinations: PasswordCombinationCount, rateIndex: Int, changeRate: (Int) -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    Text("総当たりで探す時間の目安", style = MaterialTheme.typography.titleMedium)
    Text("候補を1つずつ試すと仮定します。1秒に試せる回数を変えて比べてください。", style = MaterialTheme.typography.bodySmall)
    Text("仮定する試行速度", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BruteForceLesson.rates.forEachIndexed { index, rate ->
            FilterChip(rateIndex == index, { changeRate(index) }, label = { Text(rate.label) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "guide-trial-rate-$index" })
        }
    }
    BruteForceLesson.estimate(combinations, rateIndex)?.let { estimate ->
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("見つかるまでの平均", style = MaterialTheme.typography.labelMedium)
                Text(estimate.averageLabel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { testTag = "guide-time-average" })
                HorizontalDivider()
                Text("全候補を試す時間", style = MaterialTheme.typography.labelMedium)
                Text(estimate.exhaustiveLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { testTag = "guide-time-exhaustive" })
            }
        }
    }
    Text("均等に無作為に選ばれた1つの値を、重複なく同じ速さで試す計算です。平均では約半分の候補を試す計算です。最初の1回で当たる場合もあり、表示時間まで安全という意味ではありません。1年は365.25日で換算します。", style = MaterialTheme.typography.bodySmall)
    Text(BruteForceLesson.DISPLAY_LIMIT_NOTE, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { details = !details }) {
        Text("実際の速さは何で変わる？")
        AppIcon(if (details) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
    }
    if (details) {
        Text("ログイン画面に試す場合：回数制限や待ち時間の影響を受けます。", style = MaterialTheme.typography.bodySmall)
        Text("漏えいしたハッシュに試す場合：サイトへのログインを介さず試せるため、その回数制限は効きません。ハッシュ方式・計算コストの設定・攻撃側の機材で速さが変わります。", style = MaterialTheme.typography.bodySmall)
        Text("ここで選ぶ速度は説明用の仮定で、特定のサービスや機材の測定値ではありません。辞書攻撃・使い回し・フィッシングなどは、この試算の対象外です。", style = MaterialTheme.typography.bodySmall)
        Text("10億回/秒は普通？", style = MaterialTheme.typography.titleSmall)
        Text("SHA-256などの高速なハッシュでは、対応する機材で毎秒数十億回の計算も可能です。一方、Argon2idなどは1回の照合に時間やメモリを使う設計です。10億回/秒は、どのパソコン・サービスにも当てはまる速度ではありません。", style = MaterialTheme.typography.bodySmall)
        Text("スーパーコンピュータなら？", style = MaterialTheme.typography.titleSmall)
        Text("大量の計算機で分担できる場合もありますが、保存方式やメモリ量で速さは変わります。公表される計算性能は別の計算を測った値なので、そのままパスワードの試行回数には換算できません。", style = MaterialTheme.typography.bodySmall)
        Text("量子コンピュータなら？", style = MaterialTheme.typography.titleSmall)
        Text("グローバー法という探索方法で、必要な手順を理論上減らせます。ただし、1手順の時間や必要な装置が通常の計算とは違います。この画面の「毎秒何回」の計算で量子コンピュータの所要時間は表せません。", style = MaterialTheme.typography.bodySmall)
        GuideLink("Spring Security：計算速度と保存方式（英語）", "https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html")
        GuideLink("TOP500：計算性能の測り方（英語）", "https://www.top500.org/project/linpack/")
        GuideLink("NIST：量子探索の限界（英語）", "https://csrc.nist.gov/Projects/Post-Quantum-Cryptography/faqs")
        GuideLink("NIST：パスワードの保護（英語）", "https://pages.nist.gov/800-63-4/sp800-63b.html#passwords")
        GuideLink("OWASP：パスワードの保存（英語）", "https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html")
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
