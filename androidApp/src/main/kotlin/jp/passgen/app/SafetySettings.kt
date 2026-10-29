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
            Text("他の内容に置き換わったコピーは消しません。背景へ移ってもコピーは保持します。OSの制限で期限時に消せない場合は、復帰時に消去します。設定変更は次のコピーから適用します。OSが先に消去する場合もあります。", style = MaterialTheme.typography.bodySmall)
            Text("端末間のクリップボード共有はOSや連携アプリ側の設定に従います。Androidにはアプリから端末内だけに限定する共通APIがありません。共有を使わない場合は端末側でも無効にしてください。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("画面の保護", style = MaterialTheme.typography.titleMedium)
            SettingsSwitch("スクリーンショット・画面収録を防ぐ", model.protectCapture, "protect-capture") { model.protectCapture = it }
            Text("オフにすると結果やアプリ切替時のプレビューが撮影・共有される可能性があります。生成結果は背景へ移ってもメモリ内に保持し、消去・再生成・アプリの終了で破棄します。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("データの扱い", style = MaterialTheme.typography.titleMedium)
            Text("生成用の通信・アカウント・履歴はありません。保存するのは生成条件と表示・保護設定だけです。例：24文字、単語数、使う記号、コピーの消去秒数。生成結果は保存・送信しません。✓はその生成一覧で一度コピーした印で、クリップボードに残っていることは示しません。", style = MaterialTheme.typography.bodyMedium)
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

@Composable fun SecurityGuideSheet(dismiss: () -> Unit, storageLesson: () -> Unit, authLesson: () -> Unit, examplesLesson: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("アカウントを守る", style = MaterialTheme.typography.headlineSmall)
            Text("長く、無作為に、サービスごとに", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("このアプリは生成専用です。作った値は利用先に登録し、管理アプリへ保存しましょう。", style = MaterialTheme.typography.bodyMedium)
            GuideDestination("なぜ使い回しは危険？", "平文・ハッシュ化と、漏えいの広がり", "password-storage-guide", storageLesson)
            GuideDestination("2要素と2段階、何が違う？", "手順と要素を、3つの例で見比べる", "authentication-guide", authLesson)
            GuideCard("避けたいパスワード") {
                GuideExampleValue("連番", "12345678")
                GuideExampleValue("単語に文字を足しただけ", jp.passgen.core.SecurityGuideExamples.mutations[2])
                TextButton({ examplesLesson(0) }, Modifier.semantics { testTag = "password-examples-guide" }) { Text("悪い例と、その理由を見る") }
                Text(jp.passgen.core.SecurityGuideExamples.NOTICE, style = MaterialTheme.typography.bodySmall)
            }
            GuideDestination("文字数で候補はどう変わる？", "数字4・6・8桁を切り替えて比較", "password-length-guide") { examplesLesson(1) }
            GuideDestination("7単語をつないでみる", "半角スペース・ハイフン・アンダースコア", "passphrase-examples-guide") { examplesLesson(2) }
            GuideCard("管理アプリへの保存例") {
                GuideExampleValue("サービス", jp.passgen.core.SecurityGuideExamples.SERVICE)
                GuideExampleValue("ユーザー名", jp.passgen.core.SecurityGuideExamples.USERNAME)
                GuideExampleValue("パスワード（公開の24文字見本）", jp.passgen.core.SecurityGuideExamples.PASSWORD, "guide-manager-password")
                GuideJourneyStep(1, "このアプリで生成", "サービスごとに、新しい値を作る")
                GuideJourneyStep(2, "利用先で登録・変更", "作った値を、そのサービスに設定する")
                GuideJourneyStep(3, "管理アプリへ保存", "次回は保存した項目から自動入力")
                Text("Google パスワード マネージャー、Appleの「パスワード」、1Passwordなどで管理できます。同期する端末と復旧方法を確認しましょう。公開の見本は使わず、新しく生成してください。", style = MaterialTheme.typography.bodySmall)
                Text("管理アプリのアカウントも保護し、復旧情報を保管。生成結果を平文メモやスクリーンショットに残すことは避けましょう。", style = MaterialTheme.typography.bodySmall)
                GuideLink("Google パスワード マネージャー", "https://support.google.com/accounts/answer/6208650?hl=ja")
                GuideLink("Apple「パスワード」", "https://support.apple.com/ja-jp/120758")
                GuideLink("1Passwordの使い方", "https://support.1password.com/category/getting-started/")
            }
            GuideCard("認証アプリを加えたサインイン例") {
                GuideJourneyStep(1, "パスワードを入力", jp.passgen.core.SecurityGuideExamples.PASSWORD)
                GuideJourneyStep(2, "認証アプリの一時コードを入力", "${jp.passgen.core.SecurityGuideExamples.CODE}（説明用）")
                Text("知っているパスワード ＋ アプリの秘密鍵を使えること。この例は2段階・2要素です。一時コードも偽サイトへ入力すると盗まれます。", style = MaterialTheme.typography.bodySmall)
                Text("機種変更の前に", style = MaterialTheme.typography.titleSmall)
                Text("サービスの復旧コードなどを保管し、新端末での利用を確認", style = MaterialTheme.typography.bodySmall)
                GuideLink("Googleの2段階認証ガイド", "https://support.google.com/accounts/answer/185839?hl=ja")
            }
            GuideCard("パスキーでのサインイン例") {
                GuideJourneyStep(1, "対応サービスでパスキーを選ぶ", "事前に登録した鍵を使用")
                GuideJourneyStep(2, "端末で本人確認", "指紋・顔認証・端末のロック解除など")
                GuideJourneyStep(3, "サインイン", "パスワードや一時コードを入力せずに認証")
                Text("本人確認を求めるサービスの例です。顔や指紋の情報をサイトへ送る仕組みではありません。登録したサイトに結び付くため、偽サイトへの認証情報の入力を防ぎやすくなります。", style = MaterialTheme.typography.bodySmall)
                Text("同期・機種変更・復旧の方法も確認しましょう。このアプリはパスキー自体を作成しません。", style = MaterialTheme.typography.bodySmall)
                GuideLink("Googleのパスキーガイド", "https://support.google.com/accounts/answer/13548313?hl=ja")
                GuideLink("Appleのパスキー解説", "https://support.apple.com/ja-jp/guide/iphone/iphf538ea8d0/ios")
            }
            GuideCard("長いパスワードでも、入力先に注意") {
                GuideExampleValue("登録先に見立てた架空URL", jp.passgen.core.SecurityGuideExamples.EXPECTED_URL)
                GuideExampleValue("先頭を似せた、別のドメイン", jp.passgen.core.SecurityGuideExamples.LOOKALIKE_URL, "guide-lookalike-url")
                Text("上はexample.com、下はexample.netの配下です。先頭の文字や鍵マークだけでは、正しい入力先か判断できません。メッセージのリンクから急いで入力せず、保存済みのブックマークや公式アプリから開きましょう。", style = MaterialTheme.typography.bodyMedium)
                Text("URLも説明用の架空例です。長さや無作為さだけでは、偽サイトへの入力やマルウェアを防げません。", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(dismiss, Modifier.align(Alignment.End)) { Text("閉じる") }
        }
    }
}

@Composable private fun GuideDestination(title: String, detail: String, tag: String, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth().semantics { testTag = tag }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun GuideCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable internal fun GuideLink(title: String, url: String) {
    val uri = LocalUriHandler.current
    TextButton({ uri.openUri(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(title) }
}
