# Passgen

端末内で完結する、Android / iOSのパスワード生成アプリ。
Kotlin Multiplatformの共通エンジンを、AndroidのJetpack Compose / Material 3と、iOSのSwiftUI / Liquid Glassから利用します。

## できること

- **パスワード**: 大文字・小文字・数字・32種類のASCII記号を、1文字ずつ選択して生成。
- **パスフレーズ**: EFFの7,776語から6〜12語を無作為に選択。区切りは半角スペース（初期値）・ハイフン・アンダーバー・ドット。
- **数字だけの生成**: 文字種の設定で大文字・小文字・記号を含めないように選択できます。誤操作を防ぐため一括切替ボタンは置きません。
- 4〜999,999文字、1〜1,000件。件数の上限は `min(1000, floor(9999990 / 文字数))`。
- 似た文字 `O o 0 I l 1` の個別除外、各文字種の必須化、同一文字の連続禁止。
- 記号パレット、一覧の貼り付け反映。対応記号がなければ選択を維持し、入力欄だけを空にします。
- iOSは許可文と記号行を切り出し、Vision OCRと対応端末のApple Intelligenceによる画像直接読取を併用。元画像と全記号のパレットで候補を修正して適用。
- 設定を中心にした凝縮UI。生成結果は下から開く一覧シートで、停止・進捗・表示切替・全文コピーを操作。プレビューは100文字まで。
- Androidはウィンドウ幅・縦横ヒンジに追従。iOSはiPhone向け編集シートとiPad向け2ペイン。
- システムのダークモード、文字サイズ拡大、VoiceOver / TalkBack向けラベルに対応。

起動時は自動生成しません。前回の生成条件・表示状態・保護設定を端末内に保存します。生成結果・コピー済みの印・画像・OCRテキストは保存しません。左上の「プリセット」から、生成条件を名前付きで保存・呼び出しできます。保存済み設定の更新・名前変更・削除にも対応します。履歴、同期、Safari拡張、Autofillサービスはこのモバイル版に含めていません。

## 安全性

生成用の乱数はAndroidの `SecureRandom`、iOSの `SecRandomCopyBytes` から取得します。
範囲化と文字種必須条件にはrejection samplingを使用し、無作為な候補の一様性を維持します。
乱数取得に失敗した場合や条件が厳しすぎる場合、弱い方式へ切り替えず停止します。

パスワードを保存・送信・ログ出力しません。AndroidにはINTERNET権限がありません。
生成結果は初回起動時にマスクします。選んだ表示状態を次回以降にも保持し、背景へ移ると処理中の生成を停止し、生成済みの結果はメモリ内に保持します。アプリの終了やOSによるプロセス終了後には復元しません。マスクに空白や単語区切りはありません。
コピーは背景へ移っても保持します。初期設定は30秒で、時間による消去の有効・無効と5〜600秒の期限を設定できます。他の内容へ置き換わっていた場合は消去しません。Androidで背景のアクセスをOSに制限された場合は復帰時にも期限を確認します。プロセス終了後の消去はOS側の動作に従います。
iOSは初期状態で端末内限定。設定でUniversal Clipboardへの共有を許可できます。Androidの端末間共有はOSや連携アプリ側で管理します。
OSが長い値のコピーを拒否した場合は、クラッシュせず失敗を表示します。

iOS 26以降は標準ツールバーと主要アクションにLiquid Glassを使用します。
「透明度を下げる」設定では不透明な操作面を使い、秘密を表示するカードは常に可読性を優先します。
iOSの非アクティブ画面は初期状態で覆います。「アプリ切替時に画面を隠す」で覆い全体をオン/オフにでき、選択を保持します。画面収録時は初期状態で秘密と読み取り内容を隠し、コピーを無効にします。保護は設定で変更可能で、停止後は選んでいた表示状態へ戻ります。Androidの撮影・収録防止も設定で切り替えられます。iOSの静止画スクリーンショットを完全に禁止することはできません。

JVM / Swift文字列やOSクリップボードの完全なメモリ消去、侵害されたOS、コピー先のアプリに対する保護は保証できません。
エントロピーは生成空間に基づく目安であり、解読時間やサービス全体の安全性を保証しません。
詳しくは [安全性の設計](docs/design/security.md) と [UI設計](docs/design/ui.md) を参照してください。

## iOSのスクリーンショット取り込み

文字セットの「記号」から「画像を選んで読み取る」を選びます。
写真ライブラリ全体への権限を要求せず、選んだ画像だけを処理します。
画像は最大20 MiB / 4,000万ピクセル。端末内で縮小してからVisionで読み取ります。

iOS 27以降でApple Intelligenceの端末内モデルが利用可能な場合だけ、`SystemLanguageModel` に記号行の画像を直接渡します。日本語OCRが角括弧や波括弧を誤読するため、記号部分を英語OCRで再読取し、画像モデルの候補と合わせます。
クラウドモデルへのフォールバックはありません。モデルを使えない場合はOCR結果と手動パレットを使えます。
iCloud上の写真を選んだ場合、写真アプリ側が元画像を取得することはあります。

候補は自動適用しません。許可文を特定し、モデル出力はASCII記号に制限しますが、画像モデルにも欠落や誤認があり得ます。許可文がない場合や複数の記号ルールがある場合は、案内部分に絞った画像を選び直してください。
元の記号行を表示する確認画面で、全32記号から追加・解除して「確認した候補を適用」を押してください。反映時に記号の文字種も有効になります。

## 設定と安全性

安全性ボタンから、コピーの共有（iOS）・自動消去・画面収録保護を変更できます。
コピーの期限・共有の変更は次のコピーから反映。別端末へ転送済みのコピーの消去までは制御しません。OSが先に消去する場合もあります。
コピー済みの✓は行ごとに残り、別の行をコピーしても維持します。現在のクリップボードに残っている印ではなく、その生成一覧で一度コピーした印です。再生成・結果消去・プロセス終了時に消えます。

「パスワードとアカウントを守る」ページで、強い・弱いパスワード、長さ、使い回しの危険、Apple / Google / 1Passwordの管理アプリ、2要素認証、パスキーを説明します。NISTと各社の公式ガイドへのリンクを用意しています。外部ページは標準ブラウザで開きます。

保存の図解は、サーバーのDBに残る平文とArgon2idの保存形式を、架空のパスワード例で比較します。ハッシュ値は全文表示し、使い回しの図にもA・B・Cの具体値を表示します。教材用の固定値のため、実際のパスワードには使用しないでください。

## 開発環境

| 要素 | 固定バージョン |
|---|---|
| Kotlin / Compose compiler | 2.4.21 |
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.7.0 |
| Compose BOM | 2026.09.00 |
| Activity / Lifecycle | 1.13.0 / 2.11.0 |
| Window | 1.5.1 |
| Android | min 28 / compile・target 37 |
| iOS | min 18 / Liquid Glass 26+ / ローカルLLM 27+ |
| Xcode（検証環境） | 27.0 / Apple Silicon |

2026-10-29時点の公式安定版と[Kotlin互換表](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)を確認しています。
AGP単体の最新9.5.0とGradle単体の最新9.8.2は、Kotlin 2.4.21の公開互換範囲を超えるため採用していません。
Kotlin公式表のXcode検証版は26.4ですが、このリポジトリではXcode 27.0でビルド・Simulatorテストを行います。
実行時の依存はGoogle / JetBrains / Appleの公式ライブラリと標準APIに限定しています。

## ビルド

AGP 9.3に対応するAndroid Studio（Quail 2 / 2026.1.2以降）、Android SDK 37、JDK 17以降（21推奨）が必要です。
Panda 4 / 2025.3.4はAGP 9.2までの対応なので、この構成でIDE同期する場合はStudioを更新してください（[公式互換表](https://developer.android.com/build/releases/about-agp#android_gradle_plugin_and_android_studio_compatibility)）。

```sh
./scripts/gradle-local.sh :shared:jvmTest :androidApp:assembleDebug :androidApp:lintDebug
```

補助スクリプトは、キャッシュをリポジトリ内 `.local-tools/` に置きます。
必要なら `JAVA_HOME` / `ANDROID_HOME` を指定してください。
生成されるAPKは `androidApp/build/outputs/apk/debug/androidApp-debug.apk` です。
リリースの署名鍵は含めていません。

iOSは `iosApp/Passgen.xcodeproj` を開き、`Passgen` schemeとSimulatorを選びます。
XcodeのビルドフェーズがKotlinフレームワークを生成・リンクします。
Apple Silicon Simulatorとarm64実機用のターゲットを定義しています。実機の署名Teamは開発者が設定してください。

```sh
xcodebuild -project iosApp/Passgen.xcodeproj -scheme Passgen \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .local-tools/DerivedData CODE_SIGNING_ALLOWED=NO build
```

Xcodeプロジェクトはコミット済みです。再生成が必要な場合だけ、`xcodeproj` gemを持つRubyで `ruby scripts/generate-xcode-project.rb` を使います。
アプリのビルド時にRubyや追加のSwift Packageは不要です。

## テスト

```sh
# 共通ロジック: JVM / iOSのOS乱数を含む同一テスト
./scripts/gradle-local.sh :shared:jvmTest
./scripts/gradle-local.sh --no-daemon :shared:iosSimulatorArm64Test

# 接続先を必ずemulatorに限定する
./scripts/test-android-emulator.sh emulator-5580

# 利用可能なSimulatorのIDを指定
xcodebuild -project iosApp/Passgen.xcodeproj -scheme Passgen \
  -destination 'platform=iOS Simulator,id=SIMULATOR_ID' \
  -derivedDataPath .local-tools/DerivedData -parallel-testing-enabled NO \
  CODE_SIGNING_ALLOWED=NO test
```

共通テストは範囲化の全走査、エントロピーの全列挙照合、最大長、文字種条件、キャンセル、乱数失敗を検証します。
ネイティブテストはコピーの所有判定、背景での結果保持、生成停止、OCR・候補検証、画面操作を検証します。
画面確認用のAndroidテストだけが一時的にFLAG_SECUREを解除し、未生成画面を撮影します。製品コードに解除経路はありません。

教材の計算値は、Node.js 24.7以上で `node scripts/verify-storage-examples.cjs` を実行して検証できます。アプリ内でハッシュ計算は行わず、新しいランタイム依存は追加していません。

依存物のSHA-256は `gradle/verification-metadata.xml`、Gradle配布物はwrapper設定で検証します。
依存更新時は取得元と差分をレビューし、検証メタデータを更新してください。初回取得の独立した監査・署名検証を代替するものではありません。

Android Studioの同期では、通常のビルドで取得しないKotlin・JUnit・Gradleのソースに加え、プラグインと推移的依存のソース、KMP共通ソース、Androidライブラリのソース・サンプル、JVM依存のJavadocも検証します。これらを含む固定チェックサムを登録しています。
`Dependency verification failed` が出たら、HTMLレポートで未登録か不一致かを確認してください。検証の無効化やソース全体の検証除外では対処しません（[Gradle公式資料](https://docs.gradle.org/current/userguide/dependency_verification.html#sec:troubleshooting-verification)）。
次のコマンドはGradle配布ソース、プラグインのソース（Gradle向けバリアントを含む）、KMP共通ソース、共有JVM・Android依存のソース・Javadocを取得・検証します。依存のバージョンは解決済みグラフから取得します。対象物が未公開の場合だけスキップし、チェックサム不足・不一致や通信エラーでは失敗します。サンプル等を含むIDE同期全体の代替ではないため、Studio上の同期も確認してください。

```sh
./scripts/gradle-local.sh --no-configuration-cache --dependency-verification strict \
  --init-script scripts/verify-ide-dependencies.init.gradle verifyIdeSources
```

Android Studio 2026.2.2で実際のGradle同期が完了することも確認しています。

## 構成

```text
shared/          Kotlinの生成・検証・記号ポリシー・テスト
androidApp/      Androidネイティブ画面・ライフサイクル・クリップボード
iosApp/          SwiftUI画面・Liquid Glass・Vision / Foundation Models
docs/design/     脅威モデルと設計上の境界
docs/tasks/      実装タスクと検証記録
third-party/     EFF単語リスト原本・出典・ライセンス
scripts/         ローカルビルド・Xcodeプロジェクト生成
```

参照元 `/Users/saitotk/github/safari-extension_passgen` は読み取りのみで使用しました。
モデルが利用可能な実機でのLLM抽出精度、ストア署名・配布、第三者セキュリティ監査は未実施です。
