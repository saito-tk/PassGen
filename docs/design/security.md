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
- iOSは非アクティブ時の画面を初期状態で覆う。覆い全体は「アプリ切替時に画面を隠す」で選択でき、主画面・各シートに反映する。画面収録の保護とは独立した設定として保持し、既存データにこの項目がない場合はオンを引き継ぐ。オフ時はアプリ切替画面などに内容が映り得ることを案内する。UIKitのsceneCaptureStateを開始・停止・再アクティブ化時に読み、収録中の保護が有効な場合は秘密と画像の表示・コピーを制限する。停止時に解除し、ユーザーの表示設定自体は変更しない。iOSの静止画スクリーンショットを完全に禁止できるとは表現しない。
- コピーは明示操作のみ。初期値は端末内限定（iOS localOnly）・30秒。背景移行ではコピーを消去しない。期限5〜600秒、自動消去の有効/無効、iOSのUniversal Clipboard許可を選べる。設定変更は次のコピーから適用する。iOSはOS expiration、Androidは所有識別付きタイマーを使い、OSが先に消去し得ることを示す。Androidには共通のlocalOnly APIがないため端末側の共有設定を案内する。
- Androidで背景の所有確認がOSに拒否された場合は、所有トークンと単調時計の期限だけを保持し、window focusの復帰時に再確認する。別の内容であれば消去しない。プロセス終了や背景制限下での厳密な期限消去は保証しない。参照: [Androidのクリップボード制限](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data)。
- クリップボードの他アプリによる置換は削除しない。アクセシビリティは秘密を読み上げず、明示した表示操作だけで視覚表示する。

## スクリーンショットの記号取り込み

- iOS標準PhotosPickerで利用者が選択した画像だけを処理する。写真ライブラリ全体への権限を要求しない。
- Visionで許可文と記号行、文字種表の記号セル、または文字の大半が記号の一覧を候補領域として特定。文字種表では大文字・小文字・数字の行と列の整列、および直前のパスワード定義を確認する。候補領域の一次OCRと再OCRを統合し、全角ASCII相当のみ半角化する。記号行だけを英語OCRで再読取し、連結した字形は元の字形を保った間隔追加でも認識する。iOS 27以降の対応端末でSystemLanguageModelを明示し、記号行の元画像と間隔・拡大の異なる画像を直接入力する。プロンプトへ対応する半角32記号を出力語彙として渡し、画像の許可リストとは区別する。日本語の誤認文字から括弧を機械的に推測しない。
- クラウドモデル・ネットワーク・ツール呼び出しへフォールバックしない。
- 解析結果・元画像のプレビュー・候補の手修正は、編集画面を閉じるまでメモリ内に保持する。背景移行では覆いの設定に従い、処理中の読み取りは中断し、前景復帰で再開する。閉じる・中止・適用・画像変更では古いタスクを無効化し、遅れて返る結果を反映しない。生成パスワードの背景消去は維持する。
- モデルは不確実な提案器。画像出力はASCII32記号と空白だけに制限する。許可文がない記号の並びも候補化するが、サービスが使用可能と定めた記号だとは断定せず、利用先の条件を確認するよう表示する。OCRと画像モデルを併用しても誤認・欠落は残るため、元画像を確認画面に出し、全32記号から修正して明示適用する。標準ASCII並びからの穴埋めは禁止する。独立した複数の領域は自動で合成しない。記号一覧の近くの禁止・入力例の表現、URL、文章、同じ文字だけの伏せ字・罫線を除外する。近接した記号断片は位置関係でまとめ、間に文章がある場合は分離する。汎用一覧の探索は観測512件・記号断片96件までに制限する。
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

### 保存・使い回し・認証の図解（2026-10-29確認）

- OWASP Password Storage: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- NIST オフライン推測攻撃: https://pages.nist.gov/800-63-4/sp800-63b/passwords/
- OWASP Credential Stuffing: https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html
- OWASP TLS: https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html
- NIST 要素の分類・同種の重複・秘密の質問の扱い: https://pages.nist.gov/800-63-4/sp800-63/model/
- NCSC 2SV / 2FAの呼称: https://www.ncsc.gov.uk/guidance/setting-2-step-verification-2sv
- FIDO Passkeys FAQ（User Verificationを要求する場合の複数要素）: https://fidoalliance.org/passkeys/
- OWASP MFA（方式ごとの特徴とフィッシング）: https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html

ガイドはサービスの保存方式や認証方式を検査しない。模式図に本物のパスワードを入力させず、生成結果・設定値を教材へ渡さない。アプリから外部リンクを明示操作で開く以外に、教材の通信は行わない。秘密の質問の例は推奨として扱わない。


### 教材の具体値（タスク20）

- 平文・使い回しの例は公開の架空値のみで、生成エンジンの入力やユーザーの資格情報を使わない。アプリ内ではハッシュ計算を行わず、固定の共通教材データを表示する。
- ハッシュ例はArgon2idのPHC保存形式全文（バージョン・計算設定・16バイトのソルト・32バイトの計算結果を含む）を表示する。架空の同じ入力、同じメモリ/反復/並列設定で、共通ソルト・個別ソルトの違いを比較する。Argon2はソルトが必要なので、無ソルトのArgon2形式を創作しない。
- Node.js標準のArgon2idでオフライン計算し、RFC 9106のベクトルと3つの例を開発用スクリプトで検証する。モバイルアプリの依存ライブラリは追加しない。設定例はOWASPの最小構成（m=19456 KiB, t=2, p=1）に合わせる。例示の値を利用者のパスワードとして推奨しない。
- 参考: [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)、[RFC 9106 §5.3](https://www.rfc-editor.org/rfc/rfc9106.html#section-5.3)、[Node.js Argon2](https://nodejs.org/api/crypto.html#cryptoargon2syncalgorithm-parameters)。


## 説明用の公開例

`SecurityGuideExamples`は利用者の入力、生成器、クリップボードに接続しない読み取り用の教材。公開の24文字見本・7単語の形を安全な認証情報と評価しない。弱い例は攻撃で試されるパターンを示すためであり、例にない値の安全性を判定しない。数字の比較は0〜9を各位置で独立・一様に選ぶ条件（先頭0を含む）を固定する。候補数を解読時間に換算せず、生成ルールや利用先の攻撃制限による差も混同しない。固定の区切りに追加エントロピーは与えない。

パスキーの例は本人確認を求めるサービスでの流れと明記し、常に2要素となると一般化しない。認証アプリのコードは公開の架空値。復旧準備はサインインの追加段階として数えない。紛らわしいURLはIANAの予約ドメインだけを使い、クリック可能にしない。URLの見た目やHTTPSだけで正規性を保証しない。

教材見直し時の一次資料:
- [NIST: 長さ・推測されやすいパターン](https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password)
- [EFF: 無作為な単語と管理アプリ](https://ssd.eff.org/module/creating-strong-passwords)
- [Google: パスキーの操作と本人確認](https://support.google.com/accounts/answer/13548313?hl=ja)
- [IANA: 文書用の予約ドメイン](https://www.iana.org/help/example-domains)
- [FBI: 偽サイトと情報の入力](https://www.fbi.gov/how-we-can-help-you/common-frauds-and-scams/spoofing-and-phishing)
- [FBI: HTTPSを利用するフィッシング](https://www.ic3.gov/PSA/2019/PSA190610)
