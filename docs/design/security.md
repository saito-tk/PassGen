# 安全性と構成

## 境界

- Kotlin Multiplatformで生成・入力検証・記号反映・エントロピー計算を共有する。
- AndroidはJetpack Compose / Material 3、iOSはSwiftUI。ComposeによるiOS画面の共有は行わない。
- オフラインの生成専用アプリ。アカウント、履歴、同期、広告、解析SDK、サーバーを持たない。
- パスワード・画像・OCRテキストをディスク、設定、ログ、バックアップへ保存しない。
- 生成条件・表示状態・保護設定だけをUserDefaults / アプリ専用SharedPreferencesに保存し、サイズ・バージョン・許可集合を検証して復元する。
- 生成した秘密とコピー済みの行IDはメモリ中だけに保持し、背景移行時に結果を破棄する。
- JVM / Swiftの文字列とOSクリップボードは完全なメモリ消去を保証できない。root化・脱獄・侵害されたOS、外部カメラによる撮影は防御範囲外。

## 生成

- Android: java.security.SecureRandom。iOS: Security.SecRandomCopyBytes。乱数取得失敗時は生成失敗として停止する。
- 独自PRNG、時刻シード、Kotlin Random、LLM生成パスワードは使用しない。
- 範囲化は16bit乱数のrejection sampling。剰余だけで範囲化しない。
- パスワードとEFF 7,776語のパスフレーズを選択可能にする。数字のみはパスワード側の文字種設定。形式は用途の違いであり、安全性の順位ではない。
- 必須文字種の条件は候補全体のrejection samplingで満たす。文字種ごとの先取り＋シャッフルによる偏りを避ける。
- 大文字・小文字・数字・記号は1文字ずつ許可集合を指定できる。重複を除去し、文字種外の文字と有効文字が空になる設定は拒否する。
- 似た文字の除外対象も `O o 0 I l 1` から個別選択できる。除外中の文字はUI上で明示し、許可集合との積で実効集合を決定する。
- 同一文字連続禁止は直前文字を除いた集合から一様選択する。必須文字種と併用しても全候補の棄却で一様性を維持する。
- 試行回数を制限し、条件が厳しくても安全性を落とす代替方式へ切り替えない。キャンセルを定期確認する。
- エントロピーは生成空間から計算する。固定区切り文字にエントロピーを加算しない。実際のサービスの安全性・解読時間を保証しない。
- 文字列長4〜999,999、件数1〜min(1,000, floor(9,999,990 / 長さ))。巨大な結果はプレビューを100文字に限定する。

## OS保護

- AndroidはINTERNET権限なし、backup無効、オーバーレイ対策、機密クリップボード印を使う。FLAG_SECUREは初期状態で有効。撮影・収録防止を設定で変更できる。
- iOSは非アクティブ時の画面を覆う。UIKitのsceneCaptureStateを開始・停止・再アクティブ化時に読み、収録中の保護が有効な場合は秘密と画像の表示・コピーを制限する。停止時に解除し、ユーザーの表示設定自体は変更しない。iOSの静止画スクリーンショットを完全に禁止できるとは表現しない。
- コピーは明示操作のみ。初期値は端末内限定（iOS localOnly）・30秒・背景消去。期限5〜600秒、自動消去と背景消去の無効化、iOSのUniversal Clipboard許可を選べる。設定変更は次のコピーから適用する。iOSはOS expiration、Androidは所有識別付きタイマーを使い、OSが先に消去し得ることを示す。Androidには共通のlocalOnly APIがないため端末側の共有設定を案内する。
- クリップボードの他アプリによる置換は削除しない。アクセシビリティは秘密を読み上げず、明示した表示操作だけで視覚表示する。

## スクリーンショットの記号取り込み

- iOS標準PhotosPickerで利用者が選択した画像だけを処理する。写真ライブラリ全体への権限を要求しない。
- Visionで許可文と記号行を特定。記号行だけを英語OCRで再読取し、連結した字形は元の字形を保った間隔追加でも認識する。iOS 27以降の対応端末でSystemLanguageModelを明示し、記号行の画像を直接入力する。日本語の誤認文字から括弧を機械的に推測しない。
- クラウドモデル・ネットワーク・ツール呼び出しへフォールバックしない。
- モデルは不確実な提案器。画像出力はASCII32記号と空白だけに制限し、明示された許可文を必須にする。OCRと画像モデルを併用しても誤認・欠落は残るため、元画像を確認画面に出し、全32記号から修正して明示適用する。標準ASCII並びからの穴埋めは禁止する。複数の許可ブロックは自動で合成しない。
- 読み取り内容は信頼しないデータとして扱う。ユーザーが根拠と記号を確認し、適用するまで生成条件を変更しない。
- 利用不可・失敗時は理由を示し、手入力と記号パレットを使えるようにする。

## 依存関係

Google / JetBrains / Appleの保守されている標準・公式ライブラリに限定する。
最新安定版を基本とし、Kotlinの公開互換範囲内でAGP / Gradleを固定する。
依存チェックサム、Gradle配布物チェックサム、バージョン固定で取得の再現性を確保する。
「最高峰」や「絶対安全」は検証可能な保証にならないため、脅威モデル・テスト・制約を明示する。

## 参照資料（2026-10-29確認）

- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://developer.android.com/kotlin/multiplatform/plugin
- https://developer.android.com/develop/ui/compose/bom
- https://developer.apple.com/documentation/security/secrandomcopybytes(_:_:_:)
- https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel
- https://www.eff.org/dice

## ガイドの出典

- NIST: https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password
- Apple: https://support.apple.com/ja-jp/120758 、 https://support.apple.com/ja-jp/guide/iphone/iphf538ea8d0/ios
- Google: https://support.google.com/accounts/answer/6208650 、 https://support.google.com/accounts/answer/185839 、 https://support.google.com/accounts/answer/13548313
- 1Password: https://support.1password.com/category/getting-started/
- Clipboard localOnly: https://developer.apple.com/documentation/uikit/uipasteboard/optionskey/localonly
- Foundation Models image attachment: https://developer.apple.com/documentation/foundationmodels/attachment
