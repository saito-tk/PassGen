@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature

/** Illustrations only: no user password is requested, processed, stored, or displayed. */
@Composable fun PasswordStorageLessonSheet(fold: FoldingFeature?, back: () -> Unit) {
    var topic by remember { mutableIntStateOf(0) }
    var reuse by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    ModalBottomSheet(onDismissRequest = back, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = if (fold == null) Modifier else Modifier.padding(start = with(density) { fold.bounds.right.toDp() }),
        sheetMaxWidth = if (fold == null) 720.dp else Dp.Unspecified) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("保存方法と使い回し", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(back, modifier = Modifier.semantics { testTag = "storage-back" }) { Text("戻る") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf("保存の違い", "使い回し").forEachIndexed { index, title ->
                    SegmentedButton(topic == index, { topic = index }, shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.semantics { testTag = "storage-topic-$index" }) { Text(title) }
                }
            }
            key(topic) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (topic == 0) {
                        Text("預けた先の保存方法で、漏れた後の危険が変わる。", style = MaterialTheme.typography.headlineSmall)
                        Text("本来は適切にハッシュ化して保存すべきですが、平文で保存する実装が残っている可能性もあります。利用者には外から確かめにくい部分です。", style = MaterialTheme.typography.bodyMedium)
                        BoxWithConstraints {
                            if (maxWidth >= 560.dp && density.fontScale < 2f) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StorageComparison(false, Modifier.weight(1f)); StorageComparison(true, Modifier.weight(1f))
                                }
                            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StorageComparison(false, Modifier.fillMaxWidth()); StorageComparison(true, Modifier.fillMaxWidth())
                            }
                        }
                        LessonPanel("ログイン時は、計算して照合") {
                            Text("入力したパスワードを、保存時と同じソルト・設定で計算します。保存済みの値と一致するかで確認します。", style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ValueBox("今回の入力から計算", "今回の計算結果", Modifier.weight(1f))
                                Text("照合", style = MaterialTheme.typography.labelMedium)
                                ValueBox("サービスに保存済み", "保存済みの値", Modifier.weight(1f))
                            }
                        }
                        Text("HTTPSの鍵マークは通信の保護です。サービス内での保存方法までは示しません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HashDetails()
                        Button({ topic = 1 }, Modifier.fillMaxWidth()) { Text("使い回しで何が起きる？") }
                    } else {
                        Text("1か所で知られた値が、ほかの入口にも使われる。", style = MaterialTheme.typography.headlineSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(true to "同じ値を使う", false to "別々にする").forEachIndexed { index, (same, title) ->
                                SegmentedButton(reuse == same, { reuse = same }, shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    modifier = Modifier.semantics { testTag = if (same) "reuse-same" else "reuse-unique" }) { Text(title) }
                            }
                        }
                        LessonPanel("1  ショップAでパスワードが判明") {
                            Text("平文の漏えいや、漏れたハッシュからの推測などで知られた場合の図です。", style = MaterialTheme.typography.bodySmall)
                            ValueBox("攻撃者に知られた値", "Aのパスワード", Modifier.fillMaxWidth())
                        }
                        val tint = if (reuse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(26.dp).clearAndSetSemantics { }) {
                            drawLine(tint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height / 2), 2.dp.toPx())
                            drawLine(tint, Offset(size.width / 4, size.height / 2), Offset(size.width * .75f, size.height / 2), 2.dp.toPx())
                            for (x in listOf(.25f, .75f)) drawLine(tint, Offset(size.width * x, size.height / 2), Offset(size.width * x, size.height), 2.dp.toPx())
                        }
                        if (density.fontScale >= 2f) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReuseTarget("メールB", reuse, Modifier.fillMaxWidth()); ReuseTarget("SNS C", reuse, Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReuseTarget("メールB", reuse, Modifier.weight(1f)); ReuseTarget("SNS C", reuse, Modifier.weight(1f))
                        }
                        Text(if (reuse) "同じ値が別のサービスでも試されます。" else "Aで漏れた値は、BとCのパスワードには一致しません。",
                            style = MaterialTheme.typography.titleMedium, color = tint, modifier = Modifier.semantics { testTag = "reuse-outcome" })
                        Text("同じメールアドレスなどのログインIDと組み合わせ、別のサービスにも入ろうとする攻撃です。相手側が適切にハッシュ化していても、正しいパスワードでのログインを試される点は変わりません。", style = MaterialTheme.typography.bodyMedium)
                        Text("別々の値でも、そのサービス自身の漏えいや偽サイトへの入力などは防げません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LessonPanel("自分でできる、3つの備え") {
                            Text("① サービスごとに長くランダムな別の値を使う")
                            Text("② 管理アプリに保存して、自動入力する")
                            Text("③ パスキーや2要素認証を追加する")
                        }
                        var recovery by remember { mutableStateOf(false) }
                        OutlinedButton({ recovery = !recovery }, Modifier.fillMaxWidth()) { Text(if (recovery) "漏えい時の対処を閉じる" else "漏えいに気づいたら？") }
                        if (recovery) Text("使い回している全サービスで別々の値に変更し、不審なログインや有効なセッションを確認してください。変更は正規アプリや公式サイトから行いましょう。")
                    }
                    val uri = LocalUriHandler.current
                    var sources by remember { mutableStateOf(false) }
                    HorizontalDivider()
                    TextButton({ sources = !sources }) { Text(if (sources) "根拠を閉じる" else "根拠を読む（英語）") }
                    if (sources) {
                        TextButton({ uri.openUri("https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html") }) { Text("OWASP：パスワードの保存") }
                        TextButton({ uri.openUri("https://pages.nist.gov/800-63-4/sp800-63b/passwords/") }) { Text("NIST：漏えいしたハッシュへの攻撃") }
                        TextButton({ uri.openUri("https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html") }) { Text("OWASP：使い回しを狙う攻撃") }
                        TextButton({ uri.openUri("https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html") }) { Text("OWASP：HTTPSによる通信の保護") }
                    }
                }
            }
        }
    }
}

@Composable private fun StorageComparison(hashed: Boolean, modifier: Modifier) {
    val color = if (hashed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    OutlinedCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (hashed) "ハッシュ化" else "平文（ひらぶん）", style = MaterialTheme.typography.titleMedium, color = color)
            Text(if (hashed) "照合に使う値へ変換して保存" else "そのまま読める形で保存", style = MaterialTheme.typography.bodyMedium)
            ValueBox("入力", "入力した文字列", Modifier.fillMaxWidth())
            Text(if (hashed) "↓ 一方向の計算" else "↓ そのまま保存", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium, color = color)
            ValueBox("サービス内に保存", if (hashed) "a7f0…9c2e" else "入力した文字列", Modifier.fillMaxWidth())
            Text(if (hashed) "値の表示は模式図です。元の文字列へ読み戻すための仕組みではありません。" else "保存先を見られると、パスワードもそのまま読まれます。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ValueBox(caption: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable private fun LessonPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium); content()
        }
    }
}

@Composable private fun HashDetails() {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().semantics { testTag = "hash-details" }) {
        Text(if (expanded) "ハッシュ化の詳しい説明を閉じる" else "ハッシュ化なら、漏れても安全？")
    }
    if (expanded) LessonPanel("推測で見つかる可能性があります") {
        Text("攻撃者は候補を次々に計算し、漏れた値と比べて元のパスワードを探せます。短い値・よくある値は特に推測されやすくなります。", Modifier.semantics { testTag = "hash-guessing" })
        Text("サービス側はパスワードごとにランダムな「ソルト」を加え、推測の計算に時間やメモリがかかる専用の保存方式を使います。ソルトだけでは短い値や使い回しを守れません。", style = MaterialTheme.typography.bodySmall)
        Text("暗号化は鍵で元へ戻せる方式です。ハッシュ化は元へ戻す鍵を持たず、計算結果の照合に使います。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ReuseTarget(name: String, reuse: Boolean, modifier: Modifier) {
    val tint = if (reuse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, tint.copy(alpha = .5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(if (reuse) "Aと同じパスワード" else "Aとは別のパスワード", style = MaterialTheme.typography.bodySmall)
            Text(if (reuse) "侵入を試される" else "漏れた値は不一致", style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}
