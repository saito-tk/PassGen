@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature

private enum class AuthFactor(val title: String, val meaning: String, val examples: String) {
    KNOWLEDGE("知識", "知っていること", "パスワード・PIN"),
    POSSESSION("所持", "機器や鍵を使えること", "認証アプリ・セキュリティキー"),
    BIOMETRIC("生体", "自分の身体の特徴", "指紋・顔")
}

private enum class AuthExample(val tab: String, val title: String, val first: String, val second: String,
    val firstFactor: AuthFactor, val secondFactor: AuthFactor, val steps: String, val factors: String,
    val result: String, val explanation: String) {
    QUESTION("2段階", "パスワード ＋ 秘密の質問", "パスワード", "秘密の質問の答え",
        AuthFactor.KNOWLEDGE, AuthFactor.KNOWLEDGE, "2段階", "1種類",
        "2回確認しても、2要素にはなりません。",
        "どちらも「知っていること」です。これは違いを説明するための例です。秘密の質問は推測や調査で知られやすく、NISTでも認証手段として認められていません。"),
    APP("2要素", "パスワード ＋ 認証アプリ", "パスワード", "アプリの一時コード",
        AuthFactor.KNOWLEDGE, AuthFactor.POSSESSION, "2段階", "2種類",
        "2段階で、異なる2つの要素を確認します。",
        "パスワードを知っていることと、認証アプリの秘密鍵を使えることを確かめます。コードを入力する操作でも、ここでは「所持」の証明です。"),
    PASSKEY("パスキー", "指紋で本人確認するパスキー", "端末にある暗号鍵", "指紋で鍵を使用",
        AuthFactor.POSSESSION, AuthFactor.BIOMETRIC, "一連の操作", "2種類",
        "一連の操作で、複数の要素を確認できます。",
        "端末の暗号鍵と、鍵を使うための指紋確認を組み合わせる例です。指紋の情報そのものをサービスへ送る仕組みではありません。")
}

/** Local educational diagrams. No credentials or authentication are handled here. */
@Composable fun AuthenticationFactorsLessonSheet(fold: FoldingFeature?, back: () -> Unit) {
    var example by remember { mutableStateOf(AuthExample.APP) }
    val density = LocalDensity.current
    PassgenSheet(back, fold, maxWidth = 720.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_arrow_back, "戻る", Modifier.semantics { testTag = "auth-back" }, action = back)
                Spacer(Modifier.width(8.dp))
                Text("2要素と2段階", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text("2段階は「手順」、2要素は「種類」。", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(IntrinsicSize.Min)) {
                AuthExample.entries.forEachIndexed { index, item ->
                    SegmentedButton(example == item, { example = item }, shape = SegmentedButtonDefaults.itemShape(index, 3),
                        modifier = Modifier.fillMaxHeight().semantics { testTag = "auth-example-$index" }) { Text(item.tab) }
                }
            }
            key(example) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(example.title, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AuthMetric("確認の流れ", example.steps, Modifier.weight(1f))
                        AuthMetric("要素の種類", example.factors, Modifier.weight(1f))
                    }
                    LessonPanel(if (example == AuthExample.PASSKEY) "鍵を使うときに、本人確認" else "確認を順番に重ねる") {
                        if (density.fontScale >= 1.5f) {
                            AuthStep(example.first, example.firstFactor, Modifier.fillMaxWidth())
                            Text(if (example == AuthExample.PASSKEY) "＋" else "↓", Modifier.align(Alignment.CenterHorizontally))
                            AuthStep(example.second, example.secondFactor, Modifier.fillMaxWidth())
                        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AuthStep(example.first, example.firstFactor, Modifier.weight(1f))
                            Text(if (example == AuthExample.PASSKEY) "＋" else "→")
                            AuthStep(example.second, example.secondFactor, Modifier.weight(1f))
                        }
                        Text(example.result, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { testTag = "auth-outcome" })
                    }
                    Text(example.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (example == AuthExample.PASSKEY) Text("この例は利用時の本人確認が必須の構成です。PINなら「所持＋知識」になります。すべてのパスキーが常に2要素として扱われるわけではなく、サービスの要求と実装によります。", style = MaterialTheme.typography.bodySmall)
                    LessonPanel("名前だけで、強さは決まりません") {
                        Text("「2段階認証」という名前で2要素を使うサービスもあります。何を組み合わせ、どの攻撃を防げるかを確認しましょう。", style = MaterialTheme.typography.bodyMedium)
                    }
                    AuthDetails()
                }
            }
        }
    }
}

@Composable private fun AuthMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { testTag = if (label == "確認の流れ") "auth-steps" else "auth-count" })
        }
    }
}

@Composable private fun factorColor(factor: AuthFactor): Color = when (factor) {
    AuthFactor.KNOWLEDGE -> MaterialTheme.colorScheme.tertiary
    AuthFactor.POSSESSION -> MaterialTheme.colorScheme.primary
    AuthFactor.BIOMETRIC -> MaterialTheme.colorScheme.secondary
}

@Composable private fun AuthStep(title: String, factor: AuthFactor, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = factorColor(factor).copy(alpha = .08f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(factor.title, style = MaterialTheme.typography.labelLarge, color = factorColor(factor))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun AuthDetails() {
    var factors by remember { mutableStateOf(false) }
    var methods by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    OutlinedButton({ factors = !factors }, Modifier.fillMaxWidth().semantics { testTag = "auth-factors" }) { Text("3つの要素とは？") }
    if (factors) LessonPanel("異なる種類を組み合わせる") {
        AuthFactor.entries.forEach { factor ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(factor.title, style = MaterialTheme.typography.titleSmall, color = factorColor(factor))
                Column { Text(factor.meaning, style = MaterialTheme.typography.bodyMedium); Text(factor.examples, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Text("生体情報は通常、機器の鍵を使うための本人確認に使います。顔や指紋だけを単独でネットの相手へ証明するものではありません。2種類以上なら「多要素認証（MFA）」と呼びます。", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton({ methods = !methods }, Modifier.fillMaxWidth().semantics { testTag = "auth-methods" }) { Text("どれを設定したらいい？") }
    if (methods) LessonPanel("対応している方法から選ぶ") {
        Text("パスキー・FIDOセキュリティキー", style = MaterialTheme.typography.titleSmall)
        Text("正規のサイトと結び付き、偽サイトへ認証情報を渡す攻撃に強い方式です。同期先・復旧方法も確認しましょう。", style = MaterialTheme.typography.bodyMedium)
        Text("認証アプリの一時コード", style = MaterialTheme.typography.titleSmall)
        Text("パスワードだけの認証に保護を追加できます。ただし偽サイトへのコード入力で盗まれることがあります。", style = MaterialTheme.typography.bodyMedium)
        Text("SMSの一時コード", style = MaterialTheme.typography.titleSmall)
        Text("追加の保護になりますが、電話番号の乗っ取りや偽サイトに弱い面があります。ほかの方法も選べるか確認しましょう。", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("心当たりのない認証要求は承認せず、コードを人に教えないでください。復旧コードは安全に保管し、機種変更前に引き継ぎ方法を確認しましょう。", style = MaterialTheme.typography.bodySmall)
    }
    val uri = LocalUriHandler.current
    TextButton({ sources = !sources }) { Text("根拠を読む（英語）") }
    if (sources) {
        listOf("NIST：要素の分類" to "https://pages.nist.gov/800-63-4/sp800-63/model/",
            "NCSC：2段階認証の呼び方" to "https://www.ncsc.gov.uk/guidance/setting-2-step-verification-2sv",
            "FIDO：パスキーと本人確認" to "https://fidoalliance.org/passkeys/",
            "OWASP：認証方式ごとの特徴" to "https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html").forEach { (title, link) ->
            GuideLink(title, link)
        }
    }
}
