@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package jp.passgen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.*
import jp.passgen.core.SymbolPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun PrivacySheet(model: PassgenModel, fold: androidx.window.layout.FoldingFeature?, dismiss: () -> Unit, guide: () -> Unit) {
    var symbolsSettings by rememberSaveable { mutableStateOf(false) }
    if (symbolsSettings) {
        AppSymbolsSettingsSheet(model, fold) { symbolsSettings = false }
        return
    }
    val context = LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    PassgenSheet(dismiss, fold) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Box(Modifier.padding(horizontal = 20.dp)) { SheetHeading("設定と安全性", R.drawable.ic_verified_user) { IconAction(R.drawable.ic_close, "閉じる", action = dismiss) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideDestination("パスワードとアカウントを守る", "長さ・使い回し・管理アプリ・認証", "security-guide", guide)
                GuideDestination("アプリで使う記号", "すべての生成・プリセットに共通の使用設定", "app-symbol-settings") { symbolsSettings = true }
                SettingsSectionTitle("コピー", R.drawable.ic_content_copy)
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
                SettingsSectionTitle("画面の保護", R.drawable.ic_verified_user)
                SettingsSwitch("スクリーンショット・画面収録を防ぐ", model.protectCapture, "protect-capture") { model.protectCapture = it }
                Text("オフにすると結果やアプリ切替時のプレビューが撮影・共有される可能性があります。生成結果は背景へ移ってもメモリ内に保持し、消去・再生成・アプリの終了で破棄します。", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                SettingsSectionTitle("データの扱い", R.drawable.ic_tune)
                Text("パスワードとパスフレーズは、この端末内で生成します。このアプリが生成した値を外部のサーバーへ送信することはありません。生成にインターネット接続やアカウント登録は必要ありません。", style = MaterialTheme.typography.bodyMedium)
                Text("この端末に保存するのは、文字数・使う文字・名前付きプリセット・表示や保護などの設定です。生成したパスワードやパスフレーズを、履歴として保存することはありません。", style = MaterialTheme.typography.bodyMedium)
                Text("✓はその生成一覧で一度コピーした印で、クリップボードに残っていることは示しません。", style = MaterialTheme.typography.bodySmall)
                Text("OSの暗号学的乱数を使用します。推定エントロピーは生成候補数の目安です。侵害されたOSやコピー先アプリへの保護、メモリの完全消去は保証できません。", style = MaterialTheme.typography.bodySmall)
                Text("EFF Long Wordlist © Electronic Frontier Foundation / CC BY 4.0", style = MaterialTheme.typography.bodySmall)
                GuideLink("EFFの単語リスト", "https://www.eff.org/dice")
                GuideLink("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")
                Text("ZXing core 3.5.4 © ZXing authors", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { license = context.assets.open("licenses/zxing-LICENSE.txt").bufferedReader().use { it.readText() } }) {
                    Text("Apache License 2.0")
                }
                Text("Material Symbols © Google · Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { license = context.assets.open("licenses/material-symbols-LICENSE.txt").bufferedReader().use { it.readText() } }) { Text("Materialアイコンのライセンス") }
            }
        }
    }
    license?.let { text -> AlertDialog(onDismissRequest = { license = null }, title = { Text("Apache License 2.0") },
        text = { Text(text, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { license = null }) { Text("閉じる") } }) }
}

@Composable private fun AppSymbolsSettingsSheet(model: PassgenModel, fold: androidx.window.layout.FoldingFeature?, back: () -> Unit) {
    PassgenSheet(back, fold) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_arrow_back, "設定に戻る", Modifier.semantics { testTag = "app-symbol-settings-back" }, action = back)
                Text("アプリで使う記号", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("オンの記号だけを、このアプリで使用します。オフの記号は文字セットで選択できず、プリセット・QR・貼り付けから読み込んでも生成には使いません。", style = MaterialTheme.typography.bodyMedium)
                Text("使用する記号 ${SymbolPolicy.SUPPORTED.length - model.blockedSymbols.length} / 32", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { testTag = "allowed-symbol-count" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymbolPolicy.SUPPORTED.forEach { symbol ->
                        val allowed = symbol !in model.blockedSymbols
                        Surface(onClick = { model.setSymbolAllowed(symbol.toString(), !allowed) }, enabled = !model.busy,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (allowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp).semantics {
                                testTag = "app-symbol-$symbol"; contentDescription = "記号 $symbol"; role = Role.Checkbox
                                toggleableState = if (allowed) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
                                stateDescription = if (allowed) "使用する" else "使用しない"
                            }) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(symbol.toString(), style = MaterialTheme.typography.titleLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    textDecoration = if (allowed) TextDecoration.None else TextDecoration.LineThrough)
                                AppIcon(if (allowed) R.drawable.ic_check_circle else R.drawable.ic_remove, Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Text("パスフレーズの区切り記号にも適用します。変更は次の生成から反映されます。プリセットに保存された選択は保持され、再びオンにすると使用できるようになります。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun SettingsSectionTitle(title: String, icon: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(icon, Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun SettingsSwitch(title: String, value: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { testTag = tag }
        .toggleable(value, enabled = true, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onCheckedChange = null)
    }
}

@Composable fun SecurityGuideSheet(fold: androidx.window.layout.FoldingFeature? = null, dismiss: () -> Unit,
    storageLesson: () -> Unit, authLesson: (Boolean) -> Unit, examplesLesson: (Int) -> Unit, article: (GuideArticle) -> Unit) {
    PassgenSheet(dismiss, fold) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Box(Modifier.padding(horizontal = 20.dp)) { SheetHeading("アカウントを守る", R.drawable.ic_menu_book) { IconAction(R.drawable.ic_close, "閉じる", action = dismiss) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSectionTitle("パスワードを例で見る", R.drawable.ic_menu_book)
                GuideDestination("避けたいパスワード", "悪い例と、推測されやすい理由", "password-examples-guide") { examplesLesson(0) }
                GuideDestination("パスワードの長さ", "文字数による候補の増え方を比較", "password-length-guide") { examplesLesson(1) }
                GuideDestination("パスフレーズ", "単語の組み合わせと区切り方", "passphrase-examples-guide") { examplesLesson(2) }
                SettingsSectionTitle("生成時の設定", R.drawable.ic_tune)
                GuideDestination("生成ルールの選び方", "候補数・サービスの条件・入力のしやすさ", "generation-rules-guide") { article(GuideArticle.RULES) }
                SettingsSectionTitle("保存と使い回し", R.drawable.ic_content_copy)
                GuideDestination("なぜ使い回しは危険？", "平文・ハッシュ化と、漏えいの広がり", "password-storage-guide", storageLesson)
                GuideDestination("管理アプリに保存する", "生成から登録・保存・自動入力まで", "password-manager-guide") { article(GuideArticle.MANAGER) }
                SettingsSectionTitle("サインインを守る", R.drawable.ic_verified_user)
                GuideDestination("2要素認証と2段階認証", "確認の回数と要素の違いを比較", "authentication-guide") { authLesson(false) }
                GuideDestination("パスキー", "端末の鍵と本人確認でサインイン", "passkey-guide") { authLesson(true) }
                GuideDestination("偽サイトに入力しないために", "似たURLの例と、入力先の確認方法", "phishing-guide") { article(GuideArticle.PHISHING) }
            }
        }
    }
}

enum class GuideArticle(val title: String) {
    MANAGER("管理アプリに保存する"), PHISHING("入力先を確かめる"), RULES("生成ルールの選び方")
}

@Composable fun GuideArticleSheet(article: GuideArticle, fold: androidx.window.layout.FoldingFeature?, back: () -> Unit) {
    PassgenSheet(back, fold) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_arrow_back, "戻る", Modifier.semantics { testTag = "guide-article-back" }, action = back)
                Text(article.title, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (article) {
                    GuideArticle.RULES -> GenerationRulesGuideContent()
                    GuideArticle.MANAGER -> {
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
                    }
                    GuideArticle.PHISHING -> {
                        GuideCard("長いパスワードでも、入力先に注意") {
                            GuideExampleValue("登録先に見立てた架空URL", jp.passgen.core.SecurityGuideExamples.EXPECTED_URL)
                            GuideExampleValue("先頭を似せた、別のドメイン", jp.passgen.core.SecurityGuideExamples.LOOKALIKE_URL, "guide-lookalike-url")
                            Text("上はexample.com、下はexample.netの配下です。先頭の文字や鍵マークだけでは、正しい入力先か判断できません。メッセージのリンクから急いで入力せず、保存済みのブックマークや公式アプリから開きましょう。", style = MaterialTheme.typography.bodyMedium)
                            Text("URLも説明用の架空例です。長さや無作為さだけでは、偽サイトへの入力やマルウェアを防げません。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GenerationRulesGuideContent() {
    GuideCard("必要なルールを選ぶ") {
        Text("同じ文字数・同じ文字セットなら、3項目ともOFFのとき候補数が最大になります。サービスの条件や入力方法に合わせ、必要なルールだけONにしましょう。", style = MaterialTheme.typography.bodyMedium)
    }
    GuideCard("各文字種を必ず含める") {
        GuideExampleValue("サービスが指定する条件の例", "英字・数字・記号を各1文字以上")
        Text("使用する文字種を1文字以上ずつ含めます。このような条件がある場合にONにし、必要な文字種を文字セットでも選んでください。大文字と小文字は別の文字種として扱います。", style = MaterialTheme.typography.bodyMedium)
    }
    GuideCard("似た文字を除外") {
        GuideExampleValue("見間違えやすい文字の例", "O / 0    I / l / 1")
        Text("見ながら手入力するときに役立ちます。管理アプリの自動入力やコピーで使うなら、通常はOFFで構いません。ONでは、選んだ除外文字の分だけ候補が減ります。", style = MaterialTheme.typography.bodyMedium)
    }
    GuideCard("同じ文字を連続させない") {
        GuideExampleValue("除外される並びの例", "aa    11")
        Text("サービスが同じ文字の連続を禁止している場合にONにします。無作為な生成結果に偶然の連続があっても、それだけで弱いとは限りません。", style = MaterialTheme.typography.bodyMedium)
    }
    GuideCard("制約を加えると候補はどう変わる？") {
        Text("Aと1だけを使って、4文字を無作為に作る場合", style = MaterialTheme.typography.bodyMedium)
        GuideExampleValue("追加の制約なし", "16通り")
        GuideExampleValue("英字と数字を必ず含める", "14通り")
        Text("AAAAと1111の2通りが除かれます。", style = MaterialTheme.typography.bodySmall)
        GuideExampleValue("同じ文字を連続させない", "2通り")
        GuideExampleValue("連続禁止で残る候補", "A1A1    1A1A", "rules-candidate-example")
        Text("候補数の変化を示す説明用の例です。実際には十分な長さで無作為に生成してください。", style = MaterialTheme.typography.bodySmall)
    }
    GuideLink("NISTのパスワード指針（英語）", "https://pages.nist.gov/800-63-4/sp800-63b.html#passwords")
}

@Composable private fun GuideDestination(title: String, detail: String, tag: String, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth().semantics { testTag = tag }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(if (tag == "authentication-guide" || tag == "security-guide") R.drawable.ic_verified_user else R.drawable.ic_menu_book)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_right)
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
    TextButton({ uri.openUri(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(title, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp)); AppIcon(R.drawable.ic_open_in_new, Modifier.size(18.dp))
    }
}
