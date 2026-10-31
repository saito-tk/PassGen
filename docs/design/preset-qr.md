# プリセットQRの形式と互換性

QRは名前と生成条件だけを運ぶ。生成済みパスワード、乱数やseed、コピー状態、画面保護、クリップボード設定、プリセットIDは含まない。同じQRを読み込んでも、生成結果はOS CSPRNGにより毎回独立する。暗号化・送信者認証は行わないため、共有元を確認してから登録する。

## アプリのバージョンとQRのバージョン

アプリの更新回数とQR形式の番号は独立している。現在の書き出しは `PG1:`。読み取り入口 `PresetQrCodec.decode` が形式番号で専用の `PresetQrV1` へ振り分ける。将来 `PG2:` を追加しても、`PG1:` の専用デコーダーと固定fixtureを残す。新しい設定へ移す処理はそのデコーダーの末尾で明示的に行う。

V1の文字の順番、区切り文字、数値範囲、ビット位置は凍結する。現在のUIのアルファベット・初期値・制限を参照しない。旧形式で指定されていない新設定には、その形式向けの互換値を明示する。現行UIの初期値をそのまま流用しない。意味が変わった項目は変換し、同じ条件を表せない場合は理由を示して取り込みを止める。保存済みプリセットや主画面を部分的に書き換えない。

設定追加時の手順:

1. V1のreaderと語彙を維持する。既存形式の意味やビットを再利用しない。
2. 新しい設定を表せない場合は新writer・専用readerを追加し、最新writerの呼び先と形式番号を更新する。
3. 各旧形式から現在のモデルへの変換で、欠ける項目の互換値を明記する。V1の単語生成は現在の固定EFF英語7,776語リストを前提にしている。将来辞書が選べる場合も、旧形式で辞書が指定されていないことを理由に別の辞書へ自動変更しない。
4. 固定テキストと両OS製のQR画像が引き続き読めること、全項目が期待どおり移行することをテストする。
5. 設定モデルのフィールド一覧を検査する互換性ガードを、移行処理とテストを追加した後に更新する。新しいデフォルト引数を追加しただけでQRから設定が抜けることを防ぐ。

未知の形式は推測で読まず拒否する。古いアプリが将来の未知形式を読めることは保証しない。新しいアプリが過去の形式を読めることを維持する。

## V1のバイト列（変更禁止）

接頭辞 `PG1:` の後ろはRFC 4648 Base32（英大文字・数字2〜7、paddingなし）。QRの英数字モードで表せる。ビット集合と可変長整数でJSONのキーや文字の重複を省き、汎用の圧縮・展開処理は使わない。

| 順序 | 内容 |
|---|---|
| 1 | フラグ1byte。bit0〜7: 大文字ON、小文字ON、数字ON、記号ON、似た文字除外、各文字種必須、同じ文字連続禁止、パスフレーズ形式 |
| 2 | 文字数、生成件数の順に、最短表現のunsigned LEB128。各最大3byte |
| 3 | 1byte。bit0〜2は単語数−6、bit3〜4は区切り（空白・`-`・`_`・`.`）、bit5〜7は予約0 |
| 4 | 文字選択100bitを13byteに格納。大文字26、小文字26、数字10、記号32、似た文字6の順。各byteの下位bitから格納、末尾の未使用4bitは0 |
| 5 | プリセット名のUTF-8。先頭末尾空白なし、1〜40 UTF-16 code units。制御文字等を拒否。残りの全byteを名前とし、後付けフィールドは認めない |
| 6 | 直前までのバイト列のCRC-32/ISO-HDLCを4byte little-endian。接頭辞は含まない |

選択順は、`ABCDEFGHIJKLMNOPQRSTUVWXYZ`、`abcdefghijklmnopqrstuvwxyz`、`0123456789`、以下の記号、`Oo0Il1`。

```text
-_@/*+.,!?#$%&(){}[]~|:;"'^><=`\
```

範囲: 文字数4〜999,999、単語数6〜12、生成件数1〜1,000。パスワード形式では件数は `min(1000, 9,999,990 / 文字数)` 以下。形式によらず全設定を保持し、OFFの文字種・現在使われていない形式の設定も捨てない。空の文字選択は有効な保存値として扱う。生成可能性は主画面で別途検証する。

V1の文字列は最大235文字、実装上の受け入れ上限は256文字。7文字のASCII名の標準設定は28byte、接頭辞込み49文字。CRCは破損検出であり、改ざん防止や送信者の証明にはならない。非最短整数、余分なBase32 padding、予約bit、非正規Base32、壊れたUTF-8、範囲外を拒否する。

## 画像・共有・登録

- iOSはCore ImageのQR generator / detector、PhotosPicker、ShareLinkを使用。SimulatorでVisionのQR検出を試すと推論コンテキスト失敗・候補0になったため、QR専用のCore Imageで生成と検出を統一する。
- AndroidはZXing core 3.5.4、Photo Picker、OSの共有画面を使用。ZXing Javaは新機能開発ではなくバグ修正を継続する保守版で、今回はcoreだけを利用する。公式Maven Centralの公開SHA-1と照合してSHA-256を固定し、strict依存検証を維持する。
- アプリ画面を撮影せず、QRだけの白背景画像を共有する。Mレベルの誤り訂正と四辺の余白を確保する。Androidのキャッシュは専用1ファイルに限定し、非公開FileProviderからそのディレクトリだけを読み取り共有する。生成結果や他のキャッシュを公開しない。
- 入力画像は16MiBまで、解析する画像は長辺2,048pxまで。複数の異なるPassgen QRがあれば選ばず拒否する。URLや外部アプリを自動で開かない。画像・内容をログ出力しない。
- 読み取り後は名前・文字選択・生成ルールを確認する。名前の重複は別名を求め、明示的な登録後に新しいIDで保存する。保存50件・不正な保存データ・処理中などの制約を維持し、現在の設定や結果には適用しない。
- 読取メニューで「カメラで読み取る」「画像から読み取る」を選ぶ。画像は従来どおりOSのPhoto Pickerを使い、写真全体の閲覧権限を要求しない。カメラ権限は利用時だけ要求し、拒否した場合も画像を使える。OSカメラからアプリを開くURL schemeは登録しない。
- カメラはiOSのAVFoundation / AndroidのCameraX 1.6.2（Compose viewfinder・Preview・ImageAnalysis）で端末内処理。録画・写真撮影・映像の保存/送信・マイクの取得は行わない。AndroidにはINTERNET権限を加えない。CameraXの録画/録音用モジュールは取り込まない。
- 画像とカメラは共通のselectCandidateを通す。32候補/256文字を上限に形式・CRC・全設定を検証し、URL・Wi-Fi・破損・未知形式は無視。同じコードの重複はまとめ、異なる有効コードが複数ある場合は選ばない。撮影範囲へ1つずつ映すよう案内する。
- Androidは最新フレームだけを解析し、Y平面のcrop/row stride/pixel stride/buffer位置を考慮する。使用後は必ずImageProxyをcloseし、一時輝度バッファを消去する。iOSはQR metadataだけを取得する。画面終了・背景移行でカメラを停止し、古い検出結果・重複通知を確認画面へ渡さない。
- 読取後も既存の確認画面を使い、自動登録・自動適用・既存項目の上書きは行わない。カメラ未搭載・拒否・起動失敗では閉じる/設定/再試行を案内する。

## 固定fixtureと検証

`test-fixtures/presets/android-pg1.png` はZXingの出力。名前Example、標準設定、件数1。Python標準ライブラリのBase32/CRCで別に求めた固定文字列:

```text
PG1:H4MACAP77777777777742DYAZAHUK6DBNVYGYZP3KHHRA
```

非標準のパスフレーズ設定にも、Pythonで独立に生成した固定文字列を共通テストへ残している。最大文字数・1,000件・12単語・ドット区切り・異なるフラグと個別選択を用い、標準設定だけでは見逃すビット位置の入れ替えや整数表現の変更も検出する。

`test-fixtures/presets/ios-pg1.png` はCore Imageの出力。名前「共有設定」、パスワード64文字×3件、7単語・空白区切りを保持。大文字AZ/OFF、小文字bm/ON、数字27/ON、記号 `!{}\` / OFF。似た文字除外OFF（選択0）、各種必須OFF、連続禁止ON。個人情報・生成結果は含まない。

共通テストは全256フラグ、文字選択、空の選択、最大値、UTF-8名、既知ベクトル、1文字の破損、未知形式、不正な範囲や予約bitを検証する。JVM・両ネイティブの設定フィールド一覧ガードも維持する。

Androidは両fixtureをassetsから読み取り、iOS製の全設定も照合する。iOS UIテスト `testPresetQrShareAndImageImport` の前に、専用SimulatorへAndroid製fixtureを入れる（最新の画像として表示されること）。実機には適用しない。

```sh
xcrun simctl addmedia <Simulator UUID> test-fixtures/presets/android-pg1.png
```

フォーマット更新時もfixtureを置き換えず、新しい形式のfixtureを追加する。

参考: [Apple Core Image](https://developer.apple.com/documentation/coreimage)、[Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)、[ZXing公式リリース](https://github.com/zxing/zxing/releases)、[Base32 RFC 4648](https://www.rfc-editor.org/rfc/rfc4648)。

カメラの実装根拠: [CameraX安定版](https://developer.android.com/jetpack/androidx/releases/camera)、[ImageAnalysisとバックプレッシャー](https://developer.android.com/media/camera/camerax/analyze)、[CameraXViewfinder](https://developer.android.com/reference/kotlin/androidx/camera/compose/CameraXViewfinder.composable)、[Apple AVCaptureMetadataOutput](https://developer.apple.com/documentation/avfoundation/avcapturemetadataoutput)。

### 仮想カメラの再現

`camera-pg1.png` は公開のAndroid製Example QRに余白を付けたAPI 36 Fold emulator用の撮像fixture。imagefileカメラの固定画角と90度の回転でQRが切れない位置へ配置している。元のAndroid/iOS相互運用fixtureは変更しない。実機・Macのwebcamには適用しない。

```sh
# 専用AVDを起動する際の追加引数。imagefileはホスト上の絶対パス。
-camera-back imagefile:$PWD/test-fixtures/presets/camera-pg1.png
# 検証用パッケージだけで実行する。カメラ使用を許可し、確認画面への遷移と自動適用しないことを照合。
adb -s <emulator> shell am instrument -w -e class jp.passgen.app.GeneratorUiTest#emulatorCameraReadsValidPresetIntoReviewOnly -e qrCameraFixture true <test-package>/androidx.test.runner.AndroidJUnitRunner
```

通常のテスト実行ではカメラfixture専用テストをskipする。画像の余白は読み取り機能の補正ではなく、仮想カメラの固定撮像範囲へQR全体を収めるためのテスト入力。実機の光学性能・AF・暗所・端末固有のカメラ挙動は別途検証が必要。

カメラ・背面カメラ・AFはいずれも必須featureにせず、前面カメラだけの端末やカメラなし端末も画像読取を利用できるようにする。[Androidの暗黙のfeature要件](https://developer.android.com/guide/topics/manifest/uses-feature-element)を参照。
