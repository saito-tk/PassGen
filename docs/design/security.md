# 安全性と構成

## 境界

- Kotlin Multiplatformで生成・入力検証・記号反映・エントロピー計算を共有する。
- AndroidはJetpack Compose / Material 3、iOSはSwiftUI。ComposeによるiOS画面の共有は行わない。
- オフラインの生成専用アプリ。アカウント、履歴、同期、広告、解析SDK、サーバーを持たない。
- パスワード・画像・OCRテキストをディスク、設定、ログ、バックアップへ保存しない。
- 生成した秘密はメモリ中だけに保持し、背景移行時に結果を破棄する。
- JVM / Swiftの文字列とOSクリップボードは完全なメモリ消去を保証できない。root化・脱獄・侵害されたOS、外部カメラによる撮影は防御範囲外。

## 生成

- Android: java.security.SecureRandom。iOS: Security.SecRandomCopyBytes。乱数取得失敗時は生成失敗として停止する。
- 独自PRNG、時刻シード、Kotlin Random、LLM生成パスワードは使用しない。
- 範囲化は16bit乱数のrejection sampling。剰余だけで範囲化しない。
- ランダム文字列、EFF 7,776語のパスフレーズ、数字コードを選択可能にする。方式は用途の違いであり、安全性の順位ではない。
- 必須文字種の条件は候補全体のrejection samplingで満たす。文字種ごとの先取り＋シャッフルによる偏りを避ける。
- 同一文字連続禁止は直前文字を除いた集合から一様選択する。必須文字種と併用しても全候補の棄却で一様性を維持する。
- 試行回数を制限し、条件が厳しくても安全性を落とす代替方式へ切り替えない。キャンセルを定期確認する。
- エントロピーは生成空間から計算する。固定区切り文字にエントロピーを加算しない。実際のサービスの安全性・解読時間を保証しない。
- 文字列長4〜999,999、件数1〜min(1,000, floor(9,999,990 / 長さ))。巨大な結果はプレビューを100文字に限定する。

## OS保護

- AndroidはINTERNET権限なし、backup無効、FLAG_SECURE、オーバーレイ対策、機密クリップボード印を使う。
- iOSは非アクティブ時の画面を覆い、画面収録時に秘密を隠す。iOSの静止画スクリーンショットを完全に禁止できるとは表現しない。
- コピーは明示操作のみ。Androidは所有を識別し、前景中30秒で消去・背景移行時消去。iOSはlocalOnlyと30秒のOS expirationを指定する。
- クリップボードの他アプリによる置換は削除しない。アクセシビリティは秘密を読み上げず、明示した表示操作だけで視覚表示する。

## スクリーンショットの記号取り込み

- iOS標準PhotosPickerで利用者が選択した画像だけを処理する。写真ライブラリ全体への権限を要求しない。
- VisionでOCR。iOS 27以降の対応端末でFoundation ModelsのSystemLanguageModelを明示し、ローカルモデルで許可記号の候補を抽出する。
- クラウドモデル・ネットワーク・ツール呼び出しへフォールバックしない。
- モデルは不確実な提案器。OCRに存在しない記号、未対応記号、禁止/許可の曖昧さは適用しない。
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
