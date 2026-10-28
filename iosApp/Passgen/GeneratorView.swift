import SwiftUI
import PassgenCore

struct GeneratorView: View {
    @State private var model = GeneratorModel()
    @State private var clipboard = SecureClipboard()
    @State private var showRecipe = false
    @State private var showSymbols = false
    @State private var showPrivacy = false
    @State private var showResults = false
    @Environment(\.scenePhase) private var phase
    @Environment(\.isSceneCaptured) private var captured
    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .largeTitle) private var headlineSize = 40.0

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ZStack {
                    AppBackdrop()
                    if geometry.size.width >= 760 && !typeSize.isAccessibilitySize {
                        HStack(spacing: 0) {
                            RecipeForm(model: model, showSymbols: $showSymbols).frame(width: min(380, geometry.size.width * 0.42))
                            ScrollView { mainContent(wide: true).padding(36).frame(maxWidth: 700).frame(maxWidth: .infinity) }
                        }
                    } else {
                        ScrollView { mainContent(wide: false).padding(.horizontal, 24).padding(.top, 16).padding(.bottom, 24) }
                            .scrollDismissesKeyboard(.interactively)
                    }
                }
                .safeAreaInset(edge: .bottom, spacing: 0) { generateDock }
            }
            .navigationTitle("Passgen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Image(systemName: "key.horizontal.fill").foregroundStyle(Color.passgenAccent).accessibilityHidden(true)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showPrivacy = true } label: { Image(systemName: "lock.shield") }
                        .accessibilityLabel("プライバシーと安全性")
                }
            }
            .sheet(isPresented: $showRecipe) {
                RecipeSheet(model: model).sensitiveScene().presentationDetents([.large])
            }
            .sheet(isPresented: $showSymbols) { SymbolEditor(model: model).sensitiveScene() }
            .sheet(isPresented: $showPrivacy) { PrivacyView().sensitiveScene() }
            .sheet(isPresented: $showResults) { ResultsList(model: model, copy: copy).sensitiveScene() }
            .sensitiveScene()
        }
        .tint(.passgenAccent)
        .onChange(of: phase) { _, value in
            if value != .active { model.revealed = false }
            if value == .background { model.background(); clipboard.clearOwned() }
        }
        .onChange(of: captured) { _, value in if value { model.revealed = false } }
    }

    @ViewBuilder private func mainContent(wide: Bool) -> some View {
        VStack(alignment: .leading, spacing: 26) {
            VStack(alignment: .leading, spacing: 10) {
                Label("PRIVATE BY DESIGN", systemImage: "circle.inset.filled")
                    .font(.system(.caption2, design: .monospaced, weight: .medium)).tracking(2).foregroundStyle(Color.passgenAccent)
                Text("強さを、\n手のひらに。")
                    .font(.system(size: wide ? headlineSize * 1.2 : headlineSize, weight: .semibold, design: .serif)).tracking(-1)
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                Text("あなたの秘密は、あなたの端末で。")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            SecretCard(model: model, copy: copy, all: { showResults = true })
            if !wide {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text("生成のレシピ").font(.headline)
                        Spacer()
                        Button("カスタマイズ", systemImage: "slider.horizontal.3") { showRecipe = true }.font(.subheadline)
                            .disabled(model.busy)
                    }
                    Picker("生成方式", selection: Binding(get: { model.recipe.mode }, set: { model.chooseMode($0) })) {
                        ForEach(RecipeMode.allCases) { mode in Text(mode.title).tag(mode) }
                    }.pickerStyle(.segmented).disabled(model.busy)
                    Text(model.recipe.mode.subtitle).font(.caption).foregroundStyle(.secondary)
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 12) { lengthSummary; countSummary }
                        VStack(spacing: 12) { lengthSummary; countSummary }
                    }
                    if model.recipe.mode == .random && model.recipe.symbolsEnabled {
                        Button { showSymbols = true } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "character.cursor.ibeam").font(.title2).frame(width: 38, height: 44)
                                    .foregroundStyle(Color.passgenAccent)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("サイトに合わせる記号").font(.subheadline.weight(.semibold))
                                    Text("\(model.recipe.symbols.count)種類 · 貼り付け / 画像から読み取る").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            }.padding(16).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
                        }.buttonStyle(.plain).disabled(model.busy)
                    }
                }
            }
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "iphone.gen3.radiowaves.left.and.right.slash").foregroundStyle(Color.passgenAccent)
                Text("通信も、履歴も、ありません。\n生成結果は背景へ移ると消去されます。")
                    .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }.padding(.horizontal, 4)
        }
    }

    private var lengthSummary: some View {
        Button { showRecipe = true } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text(model.recipe.mode == .passphrase ? "単語数" : "文字数").font(.caption).foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text("\(model.recipe.mode == .passphrase ? model.recipe.words : model.recipe.length)")
                        .font(.system(.largeTitle, design: .rounded, weight: .semibold)).monospacedDigit()
                    Text(model.recipe.mode == .passphrase ? "words" : "characters").font(.caption2).foregroundStyle(.secondary)
                }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(18)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
        }.buttonStyle(.plain).disabled(model.busy)
    }
    private var countSummary: some View {
        Button { showRecipe = true } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text("まとめて生成").font(.caption).foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text("\(model.recipe.count)").font(.system(.largeTitle, design: .rounded, weight: .semibold)).monospacedDigit()
                    Text("passwords").font(.caption2).foregroundStyle(.secondary)
                }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(18)
                .background(Color.passgenAccent.opacity(0.09), in: RoundedRectangle(cornerRadius: 22))
        }.buttonStyle(.plain).disabled(model.busy)
    }

    private var generateDock: some View {
        VStack(spacing: 8) {
            if let message = model.message { Text(message).font(.caption).foregroundStyle(.red).accessibilityAddTraits(.updatesFrequently) }
            Button(action: model.generateOrStop) {
                HStack(spacing: 12) {
                    Image(systemName: model.busy ? "stop.fill" : "sparkles")
                    Text(model.state == .cancelling ? "停止中…" : model.busy ? "停止" : "パスワードを生成")
                        .font(.headline)
                    if !model.busy { Image(systemName: "arrow.right") }
                }.frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .controlSize(.large).modifier(PrimaryActionStyle())
            .disabled(model.state == .cancelling).accessibilityIdentifier("generate")
            Text("端末内で生成 · 保存しない · 送信しない").font(.caption2).foregroundStyle(.secondary)
        }.frame(maxWidth: 600).padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 10)
            .frame(maxWidth: .infinity).background(.bar)
    }

    private func copy(_ item: SecretItem) {
        guard !captured, phase == .active else { return }
        clipboard.copy(item.value); model.copiedID = item.id
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

struct SecretCard: View {
    @Bindable var model: GeneratorModel
    var copy: (SecretItem) -> Void
    var all: () -> Void
    @Environment(\.isSceneCaptured) private var captured
    @Environment(\.colorScheme) private var scheme
    private var first: SecretItem? { model.results.first }
    private var bits: Double? { first?.bits ?? model.estimate }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack {
                Label(first == nil ? "まだ生成されていません" : "今回だけの秘密", systemImage: "lock.fill")
                    .font(.caption.weight(.medium)).foregroundStyle(.secondary)
                Spacer()
                if let first { Text("\(first.value.count)文字").font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
            }
            ZStack(alignment: .leading) {
                if let first {
                    Text(model.revealed && !captured ? String(first.value.prefix(100)) : "••••  ••••\n••••  ••••")
                        .font(.system(.title2, design: .monospaced, weight: .medium)).lineSpacing(8).lineLimit(4)
                        .privacySensitive().accessibilityLabel("生成されたパスワード。読み上げを保護しています")
                        .accessibilityIdentifier("secret")
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        Image(systemName: "key.horizontal").font(.system(size: 32, weight: .light))
                            .foregroundStyle(Color.passgenAccent).accessibilityHidden(true)
                        Text("新しい秘密を、\nここから。")
                            .font(.system(.title2, design: .serif, weight: .medium)).lineSpacing(5)
                    }
                }
            }.frame(maxWidth: .infinity, minHeight: 100, alignment: .leading)
            if let first, first.value.count > 100 { Text("先頭100文字のプレビュー。コピーは全文です。").font(.caption2).foregroundStyle(.secondary) }
            Divider()
            HStack(spacing: 14) {
                ZStack {
                    Circle().stroke(Color.passgenAccent.opacity(0.12), lineWidth: 4)
                    Circle().trim(from: 0, to: min(1, (bits ?? 0) / 128)).stroke(Color.passgenAccent,
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                    Image(systemName: (bits ?? 0) >= 128 ? "checkmark.shield" : "shield.lefthalf.filled")
                        .font(.title3).foregroundStyle(Color.passgenAccent)
                }.frame(width: 47, height: 47).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(bits == nil ? "条件を確認してください" : (bits ?? 0) >= 128 ? "余裕のある強度" : (bits ?? 0) >= 80 ? "十分な候補の広さ" : "長さを増やすと安心")
                        .font(.subheadline.weight(.semibold))
                    Text(bits.map { "\(Int($0)) bits · \(first == nil ? "生成前の目安" : "生成時の条件")" } ?? "文字種を選んでください")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if model.busy {
                ProgressView(value: Double(model.results.count), total: Double(model.goal))
                Text("\(model.results.count) / \(model.goal)件").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            if let first {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { resultButtons(first) }
                    VStack(alignment: .leading, spacing: 12) { resultButtons(first) }
                }
                if model.results.count > 1 { Button("すべての結果を見る (\(model.results.count))", action: all).font(.subheadline) }
                Text("コピーは30秒で消去されます").font(.caption2).foregroundStyle(.secondary)
            }
        }.padding(24).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 28))
            .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(Color.passgenAccent.opacity(scheme == .dark ? 0.25 : 0.10)))
            .shadow(color: Color.passgenAccent.opacity(0.05), radius: 20, y: 10)
            .accessibilityElement(children: .contain).accessibilityIdentifier("secret-card")
    }

    @ViewBuilder private func resultButtons(_ item: SecretItem) -> some View {
        Button { copy(item) } label: { Label(model.copiedID == item.id ? "コピー済み" : "コピー", systemImage: model.copiedID == item.id ? "checkmark" : "doc.on.doc") }
            .foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent).disabled(captured)
        Button { model.revealed.toggle() } label: { Label(model.revealed ? "隠す" : "表示", systemImage: model.revealed ? "eye.slash" : "eye") }
            .labelStyle(.iconOnly).accessibilityLabel(model.revealed ? "パスワードを隠す" : "パスワードを表示").disabled(captured)
        Button(role: .destructive, action: model.clear) { Label("消去", systemImage: "trash") }
            .labelStyle(.iconOnly).disabled(model.busy)
    }
}

struct RecipeForm: View {
    @Bindable var model: GeneratorModel
    @Binding var showSymbols: Bool
    @FocusState private var fieldFocused: Bool

    var body: some View {
        Form {
            Section {
                Picker("つくり方", selection: Binding(get: { model.recipe.mode }, set: { model.chooseMode($0) })) {
                    ForEach(RecipeMode.allCases) { mode in Label(mode.title, systemImage: mode.symbol).tag(mode) }
                }
                Text(model.recipe.mode.subtitle).font(.caption).foregroundStyle(.secondary)
            } header: { Text("生成方式") }
            Section {
                if model.recipe.mode == .passphrase {
                    Stepper("\(model.recipe.words)単語", value: $model.recipe.words, in: 6...12)
                    Picker("区切り", selection: $model.recipe.separator) {
                        Text("ドット").tag("."); Text("アンダーバー").tag("_"); Text("スペース").tag(" ")
                    }
                } else {
                    LabeledContent("文字数") {
                        TextField("24", text: $model.recipe.lengthText).multilineTextAlignment(.trailing)
                            .keyboardType(.numberPad).focused($fieldFocused).accessibilityIdentifier("length")
                    }
                    HStack {
                        ForEach([16, 24, 32, 48], id: \.self) { length in
                            Button("\(length)") { model.recipe.lengthText = String(length); model.recipe.normalize(); fieldFocused = false }
                                .buttonStyle(.bordered).frame(maxWidth: .infinity)
                        }
                    }
                }
                LabeledContent("生成件数") {
                    TextField("1", text: $model.recipe.countText).multilineTextAlignment(.trailing).keyboardType(.numberPad).focused($fieldFocused)
                }
            } header: { Text("ボリューム") } footer: {
                Text("\(model.recipe.mode == .passphrase ? "6〜12単語" : "4〜999,999文字") / 1〜\(model.recipe.maximumCount)件。数値は入力完了時に調整します。")
            }
            if model.recipe.mode == .random {
                Section("文字セット") {
                    Toggle("大文字 A–Z", isOn: $model.recipe.uppercase)
                    Toggle("小文字 a–z", isOn: $model.recipe.lowercase)
                    Toggle("数字 0–9", isOn: $model.recipe.digits)
                    Toggle("記号を含める", isOn: $model.recipe.symbolsEnabled)
                    if model.recipe.symbolsEnabled {
                        Button { showSymbols = true } label: { LabeledContent("使える記号", value: "\(model.recipe.symbols.count)種類  ›") }
                    }
                }
                Section("生成ルール") {
                    Toggle("似た文字を除外する", isOn: $model.recipe.excludeSimilar)
                    Text("O o 0 / I l 1 を候補から除きます。").font(.caption).foregroundStyle(.secondary)
                    Toggle("各文字種を必ず含める", isOn: $model.recipe.requireEach)
                    Toggle("同じ文字を連続させない", isOn: $model.recipe.noAdjacent)
                }
            } else if model.recipe.mode == .numeric {
                Section { Toggle("同じ数字を連続させない", isOn: $model.recipe.noAdjacent) }
            }
            Section {
                LabeledContent("推定エントロピー", value: model.estimate.map { "\(Int($0)) bits" } ?? "条件を確認")
                Text("候補の広さを示す目安です。サービスの安全性や解読時間を保証するものではありません。")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .scrollContentBackground(.hidden).background(Color(uiColor: .systemGroupedBackground))
        .disabled(model.busy)
        .onChange(of: fieldFocused) { _, focused in if !focused { model.recipe.normalize() } }
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("入力完了") { fieldFocused = false; model.recipe.normalize() } } }
    }
}

private struct RecipeSheet: View {
    @Bindable var model: GeneratorModel
    @State private var showSymbols = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            RecipeForm(model: model, showSymbols: $showSymbols)
                .navigationTitle("生成のレシピ").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完了") { model.recipe.normalize(); dismiss() } } }
                .sheet(isPresented: $showSymbols) { SymbolEditor(model: model).sensitiveScene() }
        }
    }
}

struct ResultsList: View {
    @Bindable var model: GeneratorModel
    var copy: (SecretItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.isSceneCaptured) private var captured
    var body: some View {
        NavigationStack {
            List(model.results) { item in
                VStack(alignment: .leading, spacing: 12) {
                    Text("\(item.id + 1) · \(item.value.count)文字").font(.caption).foregroundStyle(.secondary)
                    Text(model.revealed && !captured ? String(item.value.prefix(100)) : "•••• •••• ••••")
                        .font(.system(.body, design: .monospaced)).lineLimit(3).privacySensitive()
                        .accessibilityLabel("パスワード。読み上げを保護しています")
                    Button { copy(item) } label: { Label(model.copiedID == item.id ? "コピー済み" : "コピー", systemImage: "doc.on.doc") }
                        .disabled(captured)
                }.padding(.vertical, 8)
            }.navigationTitle("今回の\(model.results.count)件").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完了") { dismiss() } } }
        }
    }
}

struct PrivacyView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("秘密に、居場所をつくらない。", systemImage: "lock.shield").font(.headline)
                    Text("OSの暗号学的乱数から生成します。通信・アカウント・パスワードの保存はありません。")
                    Text("生成結果は背景へ移ると消去します。コピーはこの端末に限定し、30秒で期限切れになります。")
                }
                Section("画面の保護") {
                    Text("非アクティブ時の画面を覆い、画面収録中は秘密を表示しません。iOSの静止画スクリーンショットを完全に禁止することはできません。")
                    Text("侵害されたOSやコピー先のアプリ、外部カメラの撮影に対する保護は保証できません。")
                }
                Section("画像からの記号取り込み") {
                    Text("選択した画像をVisionで文字認識します。iOS 27以降の対応端末ではApple Intelligenceの端末内モデルで候補を抽出します。画像とテキストは保存・送信しません。")
                    Text("写真にiCloud上の項目を選んだ場合、写真アプリが元画像を取得することがあります。アプリのOCR・モデル処理は端末内です。")
                    Text("読み取りは誤ることがあります。記号の根拠を確認してから適用してください。")
                }
                Section("生成方式") {
                    Text("ランダム文字列、EFFの7,776語パスフレーズ、数字コードから選べます。数字は同じ長さの英数字より候補が少なくなります。")
                    Text("EFF Long Wordlist © Electronic Frontier Foundation / CC BY 3.0 US")
                    Link("EFFの単語リストについて", destination: URL(string: "https://www.eff.org/dice")!)
                }
            }.navigationTitle("プライバシー").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("閉じる") { dismiss() } } }
        }
    }
}

private struct SensitiveScene: ViewModifier {
    @Environment(\.scenePhase) private var phase
    @Environment(\.isSceneCaptured) private var captured
    func body(content: Content) -> some View {
        content.overlay {
            if phase != .active || captured {
                ZStack {
                    Color(uiColor: .systemBackground).ignoresSafeArea()
                    VStack(spacing: 16) {
                        Image(systemName: "lock.shield").font(.largeTitle).foregroundStyle(Color.passgenAccent)
                        Text("Passgen").font(.title2.weight(.semibold))
                        Text(captured ? "画面収録中は秘密を保護しています" : "秘密を保護しています").font(.caption).foregroundStyle(.secondary)
                    }
                }.transition(.identity).accessibilityElement(children: .combine)
            }
        }
    }
}
extension View { func sensitiveScene() -> some View { modifier(SensitiveScene()) } }
