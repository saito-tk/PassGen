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
import jp.passgen.core.StorageLessonExamples

/** Illustrations only: no user password is requested, processed, stored, or displayed. */
@Composable fun PasswordStorageLessonSheet(fold: FoldingFeature?, back: () -> Unit) {
    var topic by remember { mutableIntStateOf(0) }
    var reuse by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    PassgenSheet(back, fold, maxWidth = 720.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_arrow_back, "戻る", Modifier.semantics { testTag = "storage-back" }, action = back)
                Spacer(Modifier.width(8.dp))
                Text("保存方法と使い回し", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(IntrinsicSize.Min)) {
                listOf("保存の違い", "使い回し").forEachIndexed { index, title ->
                    SegmentedButton(topic == index, { topic = index }, shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.fillMaxHeight().semantics { testTag = "storage-topic-$index" }) { Text(title) }
                }
            }
            key(topic) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (topic == 0) {
                        Text("サーバーのDBには、何が残る？", style = MaterialTheme.typography.headlineSmall)
                        Text("DB（データベース）はサービス側の保存場所です。適切にハッシュ化すべきですが、実際にどう保存されているかは、利用者には外から確かめられません。", style = MaterialTheme.typography.bodyMedium)
                        Text("説明用の架空の例です。実際のパスワードには使わないでください。", style = MaterialTheme.typography.bodySmall)
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
                            BoxWithConstraints {
                                if (maxWidth < 560.dp || density.fontScale >= 1.5f) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ValueBox("今回の入力から計算", StorageLessonExamples.COMMON_SALT_HASH, Modifier.fillMaxWidth())
                                        Text("照合", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium)
                                        ValueBox("サーバーのDBに保存済み", StorageLessonExamples.COMMON_SALT_HASH, Modifier.fillMaxWidth())
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ValueBox("今回の入力から計算", StorageLessonExamples.COMMON_SALT_HASH, Modifier.weight(1f))
                                        Text("照合", style = MaterialTheme.typography.labelMedium)
                                        ValueBox("サーバーのDBに保存済み", StorageLessonExamples.COMMON_SALT_HASH, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        Text("HTTPSの鍵マークは通信の保護です。サービス内での保存方法までは示しません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HashDetails()
                        Button({ topic = 1 }, Modifier.fillMaxWidth()) { Text("使い回しで何が起きる？") }
                    } else {
                        Text("1か所で知られた値が、ほかの入口にも使われる。", style = MaterialTheme.typography.headlineSmall)
                        Text("説明用の架空の例です。実際のパスワードには使わないでください。", style = MaterialTheme.typography.bodySmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            listOf(true to "同じ値を使う", false to "別々にする").forEachIndexed { index, (same, title) ->
                                SegmentedButton(reuse == same, { reuse = same }, shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    modifier = Modifier.fillMaxHeight().semantics { testTag = if (same) "reuse-same" else "reuse-unique" }) { Text(title) }
                            }
                        }
                        LessonPanel("1  ショップAでパスワードが判明") {
                            Text("平文の漏えいや、漏れたハッシュからの推測などで知られた場合の図です。", style = MaterialTheme.typography.bodySmall)
                            ValueBox("攻撃者に知られた値", StorageLessonExamples.PASSWORD, Modifier.fillMaxWidth(), "reuse-password-a")
                        }
                        val tint = if (reuse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(26.dp).clearAndSetSemantics { }) {
                            drawLine(tint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height / 2), 2.dp.toPx())
                            drawLine(tint, Offset(size.width / 4, size.height / 2), Offset(size.width * .75f, size.height / 2), 2.dp.toPx())
                            for (x in listOf(.25f, .75f)) drawLine(tint, Offset(size.width * x, size.height / 2), Offset(size.width * x, size.height), 2.dp.toPx())
                        }
                        if (density.fontScale >= 2f) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReuseTarget("メールB", if (reuse) StorageLessonExamples.PASSWORD else StorageLessonExamples.PASSWORD_B, reuse, "reuse-password-b", Modifier.fillMaxWidth()); ReuseTarget("SNS C", if (reuse) StorageLessonExamples.PASSWORD else StorageLessonExamples.PASSWORD_C, reuse, "reuse-password-c", Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReuseTarget("メールB", if (reuse) StorageLessonExamples.PASSWORD else StorageLessonExamples.PASSWORD_B, reuse, "reuse-password-b", Modifier.weight(1f)); ReuseTarget("SNS C", if (reuse) StorageLessonExamples.PASSWORD else StorageLessonExamples.PASSWORD_C, reuse, "reuse-password-c", Modifier.weight(1f))
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
                        GuideLink("OWASP：パスワードの保存", "https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html")
                        GuideLink("NIST：漏えいしたハッシュへの攻撃", "https://pages.nist.gov/800-63-4/sp800-63b/passwords/")
                        GuideLink("OWASP：使い回しを狙う攻撃", "https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html")
                        GuideLink("OWASP：HTTPSによる通信の保護", "https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html")
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
            ValueBox("利用者が入力したパスワード", StorageLessonExamples.PASSWORD, Modifier.fillMaxWidth())
            Text(if (hashed) "↓ 一方向の計算" else "↓ そのまま保存", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium, color = color)
            ValueBox("サーバーのDBに保存される値", if (hashed) StorageLessonExamples.COMMON_SALT_HASH else StorageLessonExamples.PASSWORD, Modifier.fillMaxWidth(), if (hashed) "storage-hash-value" else "storage-plaintext-value")
            Text(if (hashed) "Argon2idで計算した保存形式の一例です。方式・設定・ソルトも含まれます。" else "保存先を見られると、パスワードもそのまま読まれます。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ValueBox(caption: String, value: String, modifier: Modifier, tag: String = "") {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, softWrap = true, modifier = Modifier.semantics { testTag = tag })
        }
    }
}

@Composable internal fun LessonPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
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
        SaltComparison()
        Text("個別ソルトがあっても、候補の計算による推測は可能です。サービス側は推測に時間やメモリがかかる専用の保存方式も使います。利用者は長く無作為な値を、サービスごとに別々にしましょう。", style = MaterialTheme.typography.bodySmall)
        Text("暗号化は鍵で元へ戻せる方式です。ハッシュ化は元へ戻す鍵を持たず、計算結果の照合に使います。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SaltComparison() {
    var salted by remember { mutableStateOf(false) }
    Text("同じパスワードを使う2人", style = MaterialTheme.typography.titleSmall)
    Text("同じArgon2idの設定と架空のパスワードで、ソルトだけを変えて計算した例です。", style = MaterialTheme.typography.bodySmall)
    ValueBox("2人が入力したパスワード", StorageLessonExamples.PASSWORD, Modifier.fillMaxWidth())
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        listOf(false to "共通ソルト", true to "個別ソルト").forEachIndexed { index, (selected, title) ->
            SegmentedButton(salted == selected, { salted = selected }, shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.fillMaxHeight().semantics { testTag = if (selected) "salt-unique" else "salt-common" }) { Text(title) }
        }
    }
    SaltAccount("利用者A", if (salted) "A専用のソルト" else "共通のソルト", if (salted) StorageLessonExamples.INDIVIDUAL_SALT_HASH_A else StorageLessonExamples.COMMON_SALT_HASH, "salt-hash-a")
    SaltAccount("利用者B", if (salted) "B専用のソルト" else "共通のソルト", if (salted) StorageLessonExamples.INDIVIDUAL_SALT_HASH_B else StorageLessonExamples.COMMON_SALT_HASH, "salt-hash-b")
    Text(if (salted) "同じパスワードでも、保存値は異なります。" else "保存値の一致が、同じパスワードの手掛かりに。",
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { testTag = "salt-outcome" })
    Text(if (salted) "利用者ごとに異なるランダムな「ソルト」を加えると、保存値の単純な一致比較を防げます。ソルトは通常ハッシュと一緒に保存され、秘密にする値ではありません。"
        else "一人のパスワードが判明すると、同じ保存値を持つほかの人のパスワードも推測できます。ソルトがない場合や全員で同じソルトを使う場合は、同じ方式・設定でこの問題が起こります。", style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun SaltAccount(name: String, salt: String, hash: String, tag: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$name · $salt", style = MaterialTheme.typography.labelMedium)
            Text(hash, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.semantics { testTag = tag })
        }
    }
}

@Composable private fun ReuseTarget(name: String, password: String, reuse: Boolean, tag: String, modifier: Modifier) {
    val tint = if (reuse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, tint.copy(alpha = .5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(password, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.semantics { testTag = tag })
            Text(if (reuse) "侵入を試される" else "漏れた値は不一致", style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}
