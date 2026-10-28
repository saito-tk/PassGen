import SwiftUI
import PassgenCore

private enum CharacterGroup: String, CaseIterable, Identifiable {
    case upper, lower, digits, symbols
    var id: Self { self }
    var title: String { switch self { case .upper: "大文字"; case .lower: "小文字"; case .digits: "数字"; case .symbols: "記号" } }
    var alphabet: String {
        switch self {
        case .upper: CharacterPolicy.shared.UPPERCASE
        case .lower: CharacterPolicy.shared.LOWERCASE
        case .digits: CharacterPolicy.shared.DIGITS
        case .symbols: SymbolPolicy.shared.SUPPORTED
        }
    }
    func selected(in recipe: Recipe) -> String {
        switch self { case .upper: recipe.uppercaseCharacters; case .lower: recipe.lowercaseCharacters; case .digits: recipe.digitCharacters; case .symbols: recipe.symbols }
    }
    func enabled(in recipe: Recipe) -> Bool {
        switch self { case .upper: recipe.uppercase; case .lower: recipe.lowercase; case .digits: recipe.digits; case .symbols: recipe.symbolsEnabled }
    }
    func setSelected(_ value: String, in recipe: inout Recipe) {
        let value = CharacterPolicy.shared.canonical(selected: value, alphabet: alphabet)
        switch self { case .upper: recipe.uppercaseCharacters = value; case .lower: recipe.lowercaseCharacters = value; case .digits: recipe.digitCharacters = value; case .symbols: recipe.symbols = value }
    }
    func setEnabled(_ value: Bool, in recipe: inout Recipe) {
        switch self { case .upper: recipe.uppercase = value; case .lower: recipe.lowercase = value; case .digits: recipe.digits = value; case .symbols: recipe.symbolsEnabled = value }
    }
}

struct GeneratorView: View {
    @State private var model = GeneratorModel()
    @State private var clipboard = SecureClipboard()
    @State private var showSymbols = false
    @State private var showPrivacy = false
    @State private var showResults = false
    @Environment(\.scenePhase) private var phase
    @Environment(\.isSceneCaptured) private var captured
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                Group {
                    if geometry.size.width >= 760 && !typeSize.isAccessibilitySize {
                        HStack(alignment: .top, spacing: 12) {
                            ScrollView {
                                VStack(spacing: 12) { modePicker; QuantityEditor(model: model); RulesEditor(model: model) }
                                    .padding(12)
                            }.frame(width: min(360, geometry.size.width * 0.42))
                            ScrollView { CharacterEditor(model: model, showSymbols: $showSymbols).padding(12) }
                        }
                    } else {
                        ScrollView {
                            VStack(spacing: 12) {
                                modePicker
                                QuantityEditor(model: model)
                                CharacterEditor(model: model, showSymbols: $showSymbols)
                                RulesEditor(model: model)
                            }.padding(12)
                        }.scrollDismissesKeyboard(.interactively)
                    }
                }
                .background(Color(uiColor: .systemGroupedBackground))
                .safeAreaInset(edge: .bottom, spacing: 0) { generateDock }
            }
            .navigationTitle("Passgen").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !model.results.isEmpty || model.busy {
                        Button("結果 \(model.results.count)件") { showResults = true }.font(.subheadline)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showPrivacy = true } label: { Image(systemName: "lock.shield") }.accessibilityLabel("安全性とプライバシー")
                }
            }
            .sheet(isPresented: $showSymbols) { SymbolEditor(model: model).sensitiveScene() }
            .sheet(isPresented: $showPrivacy) { PrivacyView().sensitiveScene() }
            .sheet(isPresented: $showResults) {
                ResultsList(model: model, copy: copy).sensitiveScene()
                    .presentationDetents([.height(CGFloat(220 + min(model.busy ? model.goal : model.results.count, 5) * 76)), .large])
                    .presentationDragIndicator(.visible)
            }
            .sensitiveScene()
        }
        .tint(.passgenAccent)
        .onChange(of: phase) { _, value in
            if value != .active { model.revealed = false }
            if value == .background { model.background(); clipboard.clearOwned() }
        }
        .onChange(of: captured) { _, value in if value { model.revealed = false } }
    }

    private var modePicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("生成形式").font(.caption).foregroundStyle(.secondary)
            Picker("生成形式", selection: Binding(get: { model.recipe.mode }, set: { model.chooseMode($0) })) {
                ForEach(RecipeMode.allCases) { mode in Text(mode.title).tag(mode) }
            }.pickerStyle(.segmented).disabled(model.busy)
        }
    }
    private var generateDock: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("推定エントロピー").font(.caption2).foregroundStyle(.secondary)
                Text(model.estimate.map { "\(Int($0)) bits" } ?? "文字の選択を確認")
                    .font(.subheadline.monospacedDigit().weight(.medium))
                    .foregroundStyle((model.estimate ?? 0) < 80 ? Color.orange : Color.primary)
            }
            Spacer(minLength: 0)
            Button {
                if !model.busy { showResults = true }
                model.generateOrStop()
            } label: {
                Text(model.state == .cancelling ? "停止中…" : model.busy ? "停止" : "生成")
                    .font(.headline).frame(minWidth: 100, minHeight: 36)
            }.modifier(PrimaryActionStyle()).controlSize(.large)
                .disabled(model.state == .cancelling).accessibilityIdentifier("generate")
        }.padding(.horizontal, 16).padding(.vertical, 8).background(.bar)
    }
    private func copy(_ item: SecretItem) {
        guard !captured, phase == .active else { return }
        clipboard.copy(item.value); model.copiedID = item.id
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

private struct QuantityEditor: View {
    @Bindable var model: GeneratorModel
    @FocusState private var focus: Field?
    private enum Field { case length, count }
    var body: some View {
        VStack(spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.recipe.mode == .passphrase ? "単語数" : "文字数").font(.caption).foregroundStyle(.secondary)
                    if model.recipe.mode == .passphrase {
                        Stepper("\(model.recipe.words)", value: $model.recipe.words, in: 6...12)
                            .font(.title3.monospacedDigit())
                    } else {
                        TextField("24", text: $model.recipe.lengthText).keyboardType(.numberPad)
                            .font(.system(.title2, design: .rounded, weight: .semibold)).focused($focus, equals: .length)
                            .accessibilityIdentifier("length")
                        Text("4〜999,999").font(.caption2).foregroundStyle(.secondary)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Divider()
                VStack(alignment: .leading, spacing: 4) {
                    Text("生成件数").font(.caption).foregroundStyle(.secondary)
                    TextField("1", text: $model.recipe.countText).keyboardType(.numberPad)
                        .font(.system(.title2, design: .rounded, weight: .semibold)).focused($focus, equals: .count)
                        .accessibilityIdentifier("count")
                    Text("1〜\(model.recipe.maximumCount)").font(.caption2).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }.fixedSize(horizontal: false, vertical: true)
            if model.recipe.mode == .passphrase {
                Picker("区切り", selection: $model.recipe.separator) {
                    Text("スペース").tag(" "); Text("-").tag("-"); Text("_").tag("_"); Text(".").tag(".")
                }.pickerStyle(.segmented)
            }
            if model.isDigitsOnly { Text("数字は0–9を使用。短いコードは候補数が少なくなります。") .font(.caption).foregroundStyle(.secondary) }
        }.padding(14).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
            .disabled(model.busy)
            .onChange(of: focus) { previous, value in if previous != nil && previous != value { model.recipe.normalize() } }
            .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("完了") { focus = nil; model.recipe.normalize() } } }
    }
}

private struct CharacterEditor: View {
    @Bindable var model: GeneratorModel
    @Binding var showSymbols: Bool
    @State private var group: CharacterGroup = .upper
    private var selected: String { group.selected(in: model.recipe) }
    private var enabled: Bool { group.enabled(in: model.recipe) }
    private var excluded: String {
        guard model.recipe.excludeSimilar, group != .symbols else { return "" }
        return String(group.alphabet.filter { model.recipe.similarExclusions.contains($0) })
    }
    private var summary: String {
        CharacterGroup.allCases.map { item in
            let count = item.enabled(in: model.recipe) ? item.selected(in: model.recipe).filter {
                item == .symbols || !model.recipe.excludeSimilar || !model.recipe.similarExclusions.contains($0)
            }.count : 0
            return "\(item.title.prefix(1))\(count)"
        }.joined(separator: " / ")
    }
    var body: some View {
        if model.recipe.mode == .password {
            VStack(alignment: .leading, spacing: 8) {
                HStack { Text("文字セット").font(.subheadline.weight(.semibold)); Spacer(); Text(summary).font(.caption2).foregroundStyle(.secondary)
                    Button("数字のみ") { model.useDigitsOnly(); group = .digits }.font(.caption).frame(minHeight: 44)
                }
                Picker("文字種", selection: $group) {
                    ForEach(CharacterGroup.allCases) { item in Text(item.title).tag(item) }
                }.pickerStyle(.segmented)
                HStack {
                    Toggle("\(group.title)を含める", isOn: Binding(get: { enabled }, set: { group.setEnabled($0, in: &model.recipe) }))
                        .font(.subheadline)
                    Menu {
                        Button("すべて選択") { group.setSelected(group.alphabet, in: &model.recipe) }
                        Button("選択を解除") { group.setSelected("", in: &model.recipe) }
                        Button("選択を反転") { group.setSelected(String(group.alphabet.filter { !selected.contains($0) }), in: &model.recipe) }
                    } label: { Image(systemName: "ellipsis.circle").frame(width: 44, height: 44) }
                        .accessibilityLabel("文字の一括選択").disabled(!enabled)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 5)], spacing: 5) {
                    ForEach(Array(group.alphabet), id: \.self) { character in
                        let isExcluded = excluded.contains(character)
                        let checked = selected.contains(character) && !isExcluded
                        Button {
                            group.setSelected(selected.contains(character) ? String(selected.filter { $0 != character }) : selected + String(character), in: &model.recipe)
                        } label: {
                            Text(String(character)).font(.system(.body, design: .monospaced, weight: .medium))
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(checked ? Color.passgenAccent.opacity(0.15) : Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(checked ? Color.passgenAccent.opacity(0.5) : Color.clear))
                                .foregroundStyle(!enabled || isExcluded ? Color.secondary.opacity(0.4) : Color.primary)
                        }.buttonStyle(.plain).disabled(!enabled || isExcluded)
                            .accessibilityLabel("\(group.title) \(String(character))")
                            .accessibilityValue(isExcluded ? "似た文字として除外中" : checked ? "選択" : "未選択")
                            .accessibilityAddTraits(checked ? [.isSelected] : [])
                            .accessibilityIdentifier("character-\(character)")
                    }
                }
                if !excluded.isEmpty { Text("\(excluded) は「似た文字を除外」で除外中").font(.caption2).foregroundStyle(.secondary) }
                if group == .symbols && enabled {
                    Button("記号一覧を貼り付け・画像から読み取る", systemImage: "doc.viewfinder") { showSymbols = true }
                        .font(.subheadline).frame(minHeight: 44)
                }
                if model.estimate == nil { Text("有効な文字を各文字種で1つ以上選択してください。").font(.caption).foregroundStyle(.red) }
            }.padding(12).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                .disabled(model.busy)
        }
    }
}

private struct RulesEditor: View {
    @Bindable var model: GeneratorModel
    @State private var expanded = false
    var body: some View {
        if model.recipe.mode != .passphrase {
            DisclosureGroup("生成ルール", isExpanded: $expanded) {
                VStack(spacing: 12) {
                    if model.recipe.mode == .password {
                        Toggle("各文字種を必ず含める", isOn: $model.recipe.requireEach)
                        Toggle("似た文字を除外", isOn: $model.recipe.excludeSimilar)
                        if model.recipe.excludeSimilar {
                            HStack(spacing: 6) {
                                ForEach(Array("Oo0Il1"), id: \.self) { c in
                                    let selected = model.recipe.similarExclusions.contains(c)
                                    Button {
                                        model.recipe.similarExclusions = selected ? String(model.recipe.similarExclusions.filter { $0 != c }) : model.recipe.similarExclusions + String(c)
                                    } label: {
                                        Text(String(c)).font(.system(.body, design: .monospaced))
                                            .frame(maxWidth: .infinity, minHeight: 44)
                                            .background(selected ? Color.passgenAccent.opacity(0.15) : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                                    }.buttonStyle(.plain).accessibilityLabel("\(String(c)) を除外").accessibilityAddTraits(selected ? [.isSelected] : [])
                                }
                            }
                        }
                    }
                    Toggle("同じ文字を連続させない", isOn: $model.recipe.noAdjacent)
                }.font(.subheadline).padding(.top, 12).disabled(model.busy)
            }.font(.subheadline).padding(14)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
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
            List {
                if model.busy {
                    Section {
                        ProgressView(value: Double(model.results.count), total: Double(model.goal))
                        Text("\(model.results.count) / \(model.goal)件").font(.caption.monospacedDigit())
                    }
                }
                if let message = model.message { Text(message).font(.caption).foregroundStyle(.red) }
                if model.results.isEmpty && !model.busy { Text("生成結果はありません。").foregroundStyle(.secondary) }
                Section {
                    ForEach(model.results) { item in
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(item.id + 1) · \(item.length)文字\(item.length > 100 ? " / 先頭100文字" : "")").font(.caption2).foregroundStyle(.secondary)
                                Text(model.revealed && !captured ? String(item.value.prefix(100)) : "•••• •••• ••••")
                                    .font(.system(.body, design: .monospaced)).lineLimit(3).privacySensitive()
                                    .accessibilityLabel("パスワード。読み上げを保護しています").accessibilityIdentifier("secret")
                            }.frame(maxWidth: .infinity, alignment: .leading)
                            Button { copy(item) } label: {
                                Image(systemName: model.copiedID == item.id ? "checkmark" : "doc.on.doc").frame(width: 36, height: 36)
                            }.buttonStyle(.bordered).disabled(captured).accessibilityLabel(model.copiedID == item.id ? "コピー済み" : "コピー")
                        }.padding(.vertical, 3)
                    }
                } footer: {
                    if let first = model.results.first { Text("推定エントロピー \(Int(first.bits)) bits · コピーは30秒で消去") }
                }
            }
            .navigationTitle("生成結果 \(model.results.count)件").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { model.revealed.toggle() } label: { Image(systemName: model.revealed ? "eye.slash" : "eye") }
                        .disabled(captured || model.results.isEmpty).accessibilityLabel(model.revealed ? "隠す" : "表示")
                }
                ToolbarItem(placement: .confirmationAction) { Button("閉じる") { dismiss() } }
                ToolbarItem(placement: .bottomBar) {
                    Button("消去", role: .destructive, action: model.clear).disabled(model.busy || model.results.isEmpty)
                }
                ToolbarItem(placement: .bottomBar) { Spacer() }
                ToolbarItem(placement: .bottomBar) {
                    Button(model.state == .cancelling ? "停止中…" : model.busy ? "停止" : "再生成", action: model.generateOrStop)
                        .disabled(model.state == .cancelling)
                }
            }
        }
    }
}
struct PrivacyView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("データの扱い", systemImage: "lock.shield").font(.headline)
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
                    Text("パスワードとEFFの7,776語パスフレーズから選べます。数字のみの生成はパスワードの文字種設定で行います。数字は同じ長さの英数字より候補が少なくなります。")
                    Text("EFF Long Wordlist © Electronic Frontier Foundation / CC BY 4.0")
                    Link("EFFの単語リストについて", destination: URL(string: "https://www.eff.org/dice")!)
                    Link("CC BY 4.0 ライセンス", destination: URL(string: "https://creativecommons.org/licenses/by/4.0/")!)
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
