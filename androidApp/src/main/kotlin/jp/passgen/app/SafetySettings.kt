@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun PrivacySheet(model: PassgenModel, dismiss: () -> Unit, guide: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("設定と安全性", style = MaterialTheme.typography.headlineSmall)
            OutlinedCard(onClick = guide, modifier = Modifier.fillMaxWidth().semantics { testTag = "security-guide" }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("パスワードとアカウントを守る", style = MaterialTheme.typography.titleMedium)
                    Text("長さ・使い回し・管理アプリ・2要素認証・パスキー", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("コピー", style = MaterialTheme.typography.titleMedium)
            SettingsSwitch("時間が経ったら自動消去", model.autoClearClipboard, "clipboard-auto-clear") { model.autoClearClipboard = it }
            if (model.autoClearClipboard) {
                OutlinedTextField(model.clipboardSecondsText, { model.clipboardSecondsText = it.filter(Char::isDigit).take(3) },
                    label = { Text("自動消去までの秒数") }, supportingText = { Text("5〜600秒。現在 ${model.clipboardSeconds}秒。空欄は30秒。") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "clipboard-seconds" })
            }
            SettingsSwitch("背景へ移るとコピーを消去", model.clearClipboardOnBackground, "clipboard-background") { model.clearClipboardOnBackground = it }
            Text("他の内容に置き換わったコピーは消しません。他アプリへ移って貼り付ける場合は背景での消去をオフにしてください。設定変更は次のコピーから適用します。OSが先に消去する場合もあります。", style = MaterialTheme.typography.bodySmall)
            Text("端末間のクリップボード共有はOSや連携アプリ側の設定に従います。Androidにはアプリから端末内だけに限定する共通APIがありません。共有を使わない場合は端末側でも無効にしてください。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("画面の保護", style = MaterialTheme.typography.titleMedium)
            SettingsSwitch("スクリーンショット・画面収録を防ぐ", model.protectCapture, "protect-capture") { model.protectCapture = it }
            Text("オフにすると結果やアプリ切替時のプレビューが撮影・共有される可能性があります。背景へ移ると生成結果を消す処理は常に有効です。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("データの扱い", style = MaterialTheme.typography.titleMedium)
            Text("生成用の通信・アカウント・履歴はありません。保存するのは生成条件と表示・保護設定だけです。生成結果は保存・送信しません。✓はその生成一覧で一度コピーした印で、クリップボードに残っていることは示しません。", style = MaterialTheme.typography.bodyMedium)
            Text("OSの暗号学的乱数を使用します。推定エントロピーは生成候補数の目安です。侵害されたOSやコピー先アプリへの保護、メモリの完全消去は保証できません。", style = MaterialTheme.typography.bodySmall)
            Text("EFF Long Wordlist © Electronic Frontier Foundation / CC BY 4.0", style = MaterialTheme.typography.bodySmall)
            GuideLink("EFFの単語リスト", "https://www.eff.org/dice")
            GuideLink("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")
            TextButton(dismiss, Modifier.align(Alignment.End)) { Text("閉じる") }
        }
    }
}

@Composable private fun SettingsSwitch(title: String, value: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, change, modifier = Modifier.semantics { testTag = tag })
    }
}

@Composable fun SecurityGuideSheet(dismiss: () -> Unit, storageLesson: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("アカウントを守る", style = MaterialTheme.typography.headlineSmall)
            Text("長く、無作為に、サービスごとに", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("このアプリは生成専用です。作った値はサービスに登録し、管理アプリへ保存しましょう。")
            OutlinedCard(onClick = storageLesson, modifier = Modifier.fillMaxWidth().semantics { testTag = "password-storage-guide" }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("なぜ使い回しは危険？", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("平文・ハッシュ化の違いと、漏えいの広がりを図で見る", style = MaterialTheme.typography.bodySmall)
                }
            }
            GuideCard("強いパスワードの特徴", "長さがあり、推測できない無作為な文字や単語で構成されることが大切です。まず15文字以上を目安に、利用先が許す範囲で長くしましょう。初期値は24文字です。",
                "パスフレーズは無作為な単語の組み合わせです。名言や歌詞をそのまま使う方法とは異なります。数字だけの短い値は候補が少なく、推測されやすくなります。")
            GuideLink("NISTのパスワードガイド（英語）", "https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password")
            GuideCard("弱くなりやすい例", "名前・誕生日・単語だけ・連番・キーボード順・有名なフレーズは避けましょう。末尾に1や!を足すだけ、aを@に置き換えるだけでは推測を防ぎにくいです。",
                "使い回すと、ひとつのサービスから漏れた値で別のアカウントにも侵入されます。サービスごとに別の値を生成しましょう。長い値でも偽サイトへの入力やマルウェアは防げません。")
            GuideCard("管理アプリに保存", "Google パスワード マネージャー、Appleの「パスワード」、1Passwordなどは、サービスごとのパスワードやパスキーを保存・自動入力できます。使う端末や復旧方法、同期範囲を確認して選びましょう。",
                "管理アプリ自体のアカウントも保護し、復旧用情報を安全な場所へ。生成結果をスクリーンショットや平文メモで残すことは避けましょう。")
            GuideLink("Google パスワード マネージャー", "https://support.google.com/accounts/answer/6208650?hl=ja")
            GuideLink("Apple「パスワード」", "https://support.apple.com/ja-jp/120758")
            GuideLink("1Passwordの使い方", "https://support.1password.com/category/getting-started/")
            GuideCard("2要素認証を追加", "パスワードに加えて、認証アプリの一時コードやセキュリティキーなど、別の要素で本人を確認します。対応サービスのセキュリティ設定で有効にし、復旧コードも保管しましょう。",
                "SMSにも保護効果はありますが、認証アプリやセキュリティキーが選べるなら検討してください。一時コードも偽サイトに入力すると盗まれます。")
            GuideLink("Googleの2段階認証ガイド", "https://support.google.com/accounts/answer/185839?hl=ja")
            GuideCard("パスキーを使う", "端末や管理アプリに保存した暗号鍵でサインインする仕組みです。対応するサイト・アプリで作成し、指紋や顔認証、端末のロック解除などで使用します。登録したサイトに結び付くため、偽サイトに認証情報を入力する攻撃を防ぎやすくなります。",
                "同期・機種変更・復旧の方法も確認してください。このアプリはパスキー自体を作成しません。")
            GuideLink("Googleのパスキーガイド", "https://support.google.com/accounts/answer/13548313?hl=ja")
            GuideLink("Appleのパスキー解説", "https://support.apple.com/ja-jp/guide/iphone/iphf538ea8d0/ios")
            TextButton(dismiss, Modifier.align(Alignment.End)) { Text("閉じる") }
        }
    }
}

@Composable private fun GuideCard(title: String, vararg paragraphs: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable private fun GuideLink(title: String, url: String) {
    val uri = LocalUriHandler.current
    TextButton({ uri.openUri(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(title) }
}
