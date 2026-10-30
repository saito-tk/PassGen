import SwiftUI
import PassgenCore
import UIKit
import Observation

@MainActor @Observable final class ScreenProtectionState {
    private(set) var isCaptured = false
    func update(_ value: Bool) { if isCaptured != value { isCaptured = value } }
    func allowsSecret(revealed: Bool, protectCapture: Bool = true) -> Bool { revealed && !(isCaptured && protectCapture) }
}

@MainActor final class CaptureStatusView: UIView {
    var onChange: ((Bool) -> Void)?
    override init(frame: CGRect) {
        super.init(frame: frame)
        registerForTraitChanges([UITraitSceneCaptureState.self]) { (view: CaptureStatusView, _: UITraitCollection) in view.refresh() }
        NotificationCenter.default.addObserver(self, selector: #selector(refresh), name: UIScene.didActivateNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(refresh), name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
    override func didMoveToWindow() { super.didMoveToWindow(); refresh() }
    @objc func refresh() { updateTraitsIfNeeded(); onChange?(traitCollection.sceneCaptureState == .active) }
    deinit { NotificationCenter.default.removeObserver(self) }
}

private struct CaptureStateReader: UIViewRepresentable {
    var onChange: @MainActor (Bool) -> Void
    func makeUIView(context: Context) -> CaptureStatusView {
        let view = CaptureStatusView(frame: .zero)
        view.onChange = { value in Task { @MainActor in onChange(value) } }
        return view
    }
    func updateUIView(_ view: CaptureStatusView, context: Context) {
        view.onChange = { value in Task { @MainActor in onChange(value) } }
        view.refresh()
    }
}

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

private enum GeneratorSheet: Identifiable { case results, symbols, privacy, presets; var id: Self { self } }

struct GeneratorView: View {
    @State private var model = GeneratorModel.applicationModel()
    @State private var clipboard = SecureClipboard()
    @State private var activeSheet: GeneratorSheet?
    @State private var resultDetent: PresentationDetent = .height(88)
    @Environment(\.verticalSizeClass) private var verticalSize
    private let peekHeight: CGFloat = 88
    private var usesPersistentResults: Bool { UIDevice.current.userInterfaceIdiom == .phone && verticalSize != .compact }
    private var symbolPresentation: Binding<Bool> {
        Binding(get: { activeSheet == .symbols }, set: { if $0 { activeSheet = .symbols } })
    }
    @State private var protection = ScreenProtectionState()
    @Environment(\.scenePhase) private var phase
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
                            ScrollView { VStack(spacing: 12) { CharacterEditor(model: model, showSymbols: symbolPresentation); CharacterOverview(model: model) }.padding(12) }
                        }
                    } else {
                        ScrollView {
                            VStack(spacing: 12) {
                                modePicker
                                QuantityEditor(model: model)
                                CharacterEditor(model: model, showSymbols: symbolPresentation)
                                CharacterOverview(model: model)
                                RulesEditor(model: model)
                            }.padding(12)
                        }.scrollDismissesKeyboard(.interactively)
                    }
                }
                .background(Color(uiColor: .systemGroupedBackground))
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if usesPersistentResults { Color.clear.frame(height: peekHeight) }
                    else if activeSheet != .results { generateDock }
                }
            }
            .navigationTitle("Passgen").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("プリセット", systemImage: "bookmark") { activeSheet = .presets }
                        .labelStyle(.iconOnly).accessibilityIdentifier("presets")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .privacy } label: { Image(systemName: "lock.shield") }.accessibilityLabel("安全性とプライバシー")
                }
            }
            .sheet(item: $activeSheet, onDismiss: restoreResultDock) { sheet in
                switch sheet {
                case .presets: PresetsView(model: model, done: { activeSheet = nil }).sensitiveScene(enabled: model.security.protectAppSwitcher).presentationDetents([.large]).presentationDragIndicator(.visible)
                case .symbols: SymbolEditor(model: model).sensitiveScene(enabled: model.security.protectAppSwitcher)
                case .privacy: PrivacyView(model: model).sensitiveScene(enabled: model.security.protectAppSwitcher)
                case .results:
                    ResultsList(model: model, copy: copy,
                        collapsed: usesPersistentResults && resultDetent == .height(peekHeight), generate: generate, clear: clearResults)
                        .sensitiveScene(enabled: model.security.protectAppSwitcher)
                        .presentationDetents(usesPersistentResults ? [.height(peekHeight), .medium, .large] : [.medium, .large], selection: $resultDetent)
                        .presentationBackgroundInteraction(usesPersistentResults ? .enabled(upThrough: .height(peekHeight)) : .disabled)
                        .presentationDragIndicator(.visible)
                        .interactiveDismissDisabled(usesPersistentResults)
                }
            }
            .sensitiveScene(enabled: model.security.protectAppSwitcher)
        }
        .environment(protection)
        .background { CaptureStateReader(onChange: protection.update).frame(width: 0, height: 0).allowsHitTesting(false).accessibilityHidden(true) }
        .tint(.passgenAccent)
        .onChange(of: phase) { _, value in
            if value == .background {
                model.background()
            } else if value == .active { restoreResultDock() }
        }
        .onAppear { restoreResultDock() }
        .onChange(of: usesPersistentResults) { _, persistent in
            resultDetent = persistent ? .height(peekHeight) : .large
            restoreResultDock()
        }
        .onChange(of: protection.isCaptured) { _, value in if value && model.security.protectCapture { clipboard.clearOwned() } }
        .onChange(of: model.security.protectCapture) { _, value in if value && protection.isCaptured { clipboard.clearOwned() } }
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
            if !model.results.isEmpty {
                Button("結果を表示") { resultDetent = .large; activeSheet = .results }
                    .font(.subheadline).accessibilityIdentifier("show-retained-results")
            }
            Button(action: generate) {
                Text(model.generationActionTitle)
                    .font(.headline)
            }.modifier(PrimaryActionStyle()).controlSize(.regular)
                .disabled(model.state == .cancelling).accessibilityIdentifier("generate")
        }.padding(.horizontal, 16).padding(.vertical, 8).background(.bar)
    }
    private func restoreResultDock() {
        if usesPersistentResults, activeSheet == nil, phase == .active {
            resultDetent = .height(peekHeight); activeSheet = .results
        }
    }
    private func generate() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        if !model.busy {
            resultDetent = usesPersistentResults && model.recipe.count <= 3 && !typeSize.isAccessibilitySize ? .medium : .large
            activeSheet = .results
        }
        model.generateOrStop()
    }
    private func clearResults() {
        guard !model.busy else { return }
        model.clear()
        if usesPersistentResults { resultDetent = .height(peekHeight) }
        else { activeSheet = nil }
    }
    private func copy(_ item: SecretItem) {
        guard !(protection.isCaptured && model.security.protectCapture), phase == .active else { return }
        clipboard.copy(item.value, settings: model.security); model.copiedIDs.insert(item.id)
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
            if model.isDigitsOnly { Text("数字だけの短いパスワードは、候補数が少なくなります。") .font(.caption).foregroundStyle(.secondary) }
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

private struct CharacterOverview: View {
    let model: GeneratorModel
    var identifierPrefix = "overview"
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        if model.recipe.mode == .password {
            VStack(alignment: .leading, spacing: 8) {
                Text("現在選択中の文字").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(CharacterGroup.allCases) { item in
                        let effective = item.enabled(in: model.recipe) ? CharacterPolicy.shared.effective(
                            selected: item.selected(in: model.recipe), alphabet: item.alphabet,
                            excludeSimilar: model.recipe.excludeSimilar, exclusions: model.recipe.similarExclusions) : ""
                        Group {
                            if typeSize.isAccessibilitySize {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                    glyphs(item.alphabet, effective: effective)
                                }
                            } else {
                                HStack(alignment: .top, spacing: 8) {
                                    Text(item.title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                        .frame(width: 42, alignment: .leading).padding(.top, 2)
                                    glyphs(item.alphabet, effective: effective)
                                }
                            }
                        }.accessibilityElement(children: .ignore)
                            .accessibilityLabel(item.title)
                            .accessibilityValue("生成対象: \(effective.isEmpty ? "なし" : effective.map(String.init).joined(separator: " "))")
                            .accessibilityIdentifier("\(identifierPrefix)-\(item.rawValue)")
                    }
                }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.secondary.opacity(0.18)))
            }
        }
    }
    private func glyphs(_ alphabet: String, effective: String) -> some View {
        CharacterPreviewLayout(spacing: 3) {
            ForEach(Array(alphabet), id: \.self) { character in
                let active = effective.contains(character)
                Text(String(character)).font(.system(.caption, design: .monospaced, weight: active ? .semibold : .regular))
                    .foregroundStyle(active ? Color.primary : Color.secondary.opacity(0.35))
                    .frame(minWidth: 11)
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A read-only character list with natural wrapping, including at large Dynamic Type sizes.
private struct CharacterPreviewLayout: Layout {
    var spacing: CGFloat
    private func arrangement(width: CGFloat, subviews: Subviews) -> (positions: [CGPoint], height: CGFloat) {
        var positions: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width { x = 0; y += lineHeight + spacing; lineHeight = 0 }
            positions.append(CGPoint(x: x, y: y))
            x += size.width + spacing; lineHeight = max(lineHeight, size.height)
        }
        return (positions, y + lineHeight)
    }
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let naturalWidth = subviews.reduce(CGFloat.zero) { $0 + $1.sizeThatFits(.unspecified).width + spacing }
        let proposedWidth = proposal.width ?? naturalWidth
        let width = max(0, proposedWidth.isFinite ? proposedWidth : naturalWidth)
        return CGSize(width: width, height: arrangement(width: width, subviews: subviews).height)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let positions = arrangement(width: bounds.width, subviews: subviews).positions
        for (view, point) in zip(subviews, positions) {
            view.place(at: CGPoint(x: bounds.minX + point.x, y: bounds.minY + point.y), anchor: .topLeading, proposal: .unspecified)
        }
    }
}

private struct RuleSwitch: View {
    let title: String
    @Binding var value: Bool
    let identifier: String
    var body: some View {
        Toggle(isOn: $value) {
            Text(title).fixedSize(horizontal: false, vertical: true)
        }.toggleStyle(.switch).fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, minHeight: 44)
            .accessibilityIdentifier(identifier)
    }
}

private struct RulesEditor: View {
    @Bindable var model: GeneratorModel
    @State private var expanded = false
    var body: some View {
        if model.recipe.mode != .passphrase {
            VStack(spacing: 8) {
                Button { expanded.toggle() } label: {
                    HStack { Text("生成ルール"); Spacer(); Image(systemName: expanded ? "chevron.down" : "chevron.right").font(.caption.weight(.semibold)) }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("generation-rules")
                if expanded {
                    VStack(spacing: 8) {
                        RuleSwitch(title: "各文字種を必ず含める", value: $model.recipe.requireEach, identifier: "rule-requireEach")
                        RuleSwitch(title: "似た文字を除外", value: $model.recipe.excludeSimilar, identifier: "rule-excludeSimilar")
                        if model.recipe.excludeSimilar {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 6)], spacing: 6) {
                                ForEach(Array("Oo0Il1"), id: \.self) { c in
                                    let selected = model.recipe.similarExclusions.contains(c)
                                    Button {
                                        model.recipe.similarExclusions = selected ? String(model.recipe.similarExclusions.filter { $0 != c }) : model.recipe.similarExclusions + String(c)
                                    } label: {
                                        Text(String(c)).font(.system(.body, design: .monospaced)).frame(maxWidth: .infinity, minHeight: 44)
                                            .background(selected ? Color.passgenAccent.opacity(0.15) : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                                    }.buttonStyle(.plain).accessibilityLabel("\(String(c)) を除外").accessibilityAddTraits(selected ? [.isSelected] : [])
                                }
                            }
                        }
                        RuleSwitch(title: "同じ文字を連続させない", value: $model.recipe.noAdjacent, identifier: "rule-noAdjacent")
                    }.disabled(model.busy)
                }
            }.font(.subheadline).padding(12)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                .accessibilityElement(children: .contain)
        }
    }
}

final class SecretPreviewLabel: UILabel {
    override init(frame: CGRect) {
        super.init(frame: frame)
        numberOfLines = 0; lineBreakMode = .byCharWrapping
        backgroundColor = .clear; textColor = .label
        font = UIFontMetrics(forTextStyle: .body).scaledFont(for: UIFont.monospacedSystemFont(ofSize: UIFont.labelFontSize, weight: .regular))
        adjustsFontForContentSizeCategory = true
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        setContentHuggingPriority(.defaultLow, for: .horizontal)
        isAccessibilityElement = true
        accessibilityLabel = "パスワード。読み上げを保護しています"; accessibilityIdentifier = "secret"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
}

private struct SecretPreview: UIViewRepresentable {
    let value: String
    func makeUIView(context: Context) -> SecretPreviewLabel { SecretPreviewLabel() }
    func updateUIView(_ view: SecretPreviewLabel, context: Context) {
        view.text = value
        view.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: UIFont.monospacedSystemFont(ofSize: UIFont.labelFontSize, weight: .regular))
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: SecretPreviewLabel, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0 else { return nil }
        let size = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(size.height))
    }
}

struct ResultsList: View {
    @Bindable var model: GeneratorModel
    var copy: (SecretItem) -> Void
    var collapsed = false
    var generate: () -> Void
    var clear: () -> Void
    @Environment(ScreenProtectionState.self) private var protection
    private var resultSummary: String? {
        let lengths = model.results.map(\.length)
        guard let shortest = lengths.min(), let longest = lengths.max() else { return nil }
        let length = shortest == longest ? "\(longest)文字" : "\(shortest)〜\(longest)文字"
        return length + (longest > 100 ? " / 先頭100文字のみ表示" : "")
    }
    var body: some View {
        NavigationStack {
            Group {
                if collapsed { Color.clear }
                else {
                    List {
                        if protection.isCaptured && model.security.protectCapture {
                            Label("画面収録中は結果の表示・コピーを制限します。停止すると復帰します。", systemImage: "record.circle")
                                .font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("capture-protection")
                        }
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
                                        Text("\(item.id + 1)").font(.caption2).foregroundStyle(.secondary)
                                        SecretPreview(value: protection.allowsSecret(revealed: model.revealed, protectCapture: model.security.protectCapture) ? String(item.value.prefix(100)) : SecretPresentation.shared.MASK)
                                            .privacySensitive()
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    Button { copy(item) } label: {
                                        Image(systemName: "doc.on.doc").frame(width: 36, height: 36)
                                            .overlay(alignment: .topTrailing) {
                                                if model.copiedIDs.contains(item.id) {
                                                    Image(systemName: "checkmark.circle.fill").font(.caption.weight(.bold))
                                                        .foregroundStyle(Color.passgenAccent).background(.background, in: Circle())
                                                }
                                            }
                                    }.buttonStyle(.bordered).disabled(protection.isCaptured && model.security.protectCapture).accessibilityLabel(model.copiedIDs.contains(item.id) ? "コピー済み。もう一度コピー" : "コピー").accessibilityIdentifier("copy-\(item.id)")
                                }.padding(.vertical, 3)
                            }
                        } header: {
                            if let resultSummary {
                                Text(resultSummary).textCase(nil).accessibilityIdentifier("results-summary")
                            }
                        } footer: {
                            if let first = model.results.first { Text("推定エントロピー \(Int(first.bits)) bits · \(model.security.clipboardSummary)") }
                        }
                    }
                }
            }
            .navigationTitle("生成結果 \(model.results.count)件").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !collapsed {
                    ToolbarItem(placement: .topBarLeading) {
                        let visible = protection.allowsSecret(revealed: model.revealed, protectCapture: model.security.protectCapture)
                        Button { model.revealed.toggle() } label: { Image(systemName: visible ? "eye" : "eye.slash") }
                            .disabled((protection.isCaptured && model.security.protectCapture) || model.results.isEmpty)
                            .accessibilityLabel(model.revealed ? "隠す" : "表示")
                            .accessibilityValue(visible ? "表示中" : "非表示").accessibilityIdentifier("results-visibility")
                    }
                    ToolbarItem(placement: .bottomBar) {
                        Button("消去", role: .destructive, action: clear).disabled(model.busy || model.results.isEmpty).accessibilityIdentifier("clear-results")
                    }
                    ToolbarItem(placement: .bottomBar) { Spacer() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(model.generationActionTitle, action: generate)
                        .disabled(model.state == .cancelling).accessibilityIdentifier("generate")
                }
            }
        }
    }
}
struct PresetsView: View {
    @Bindable var model: GeneratorModel
    var done: () -> Void
    @State private var name = ""
    @State private var status: String?
    @State private var renaming: GeneratorPreset?
    @State private var renameText = ""
    @State private var replacing: GeneratorPreset?
    @State private var deleting: GeneratorPreset?
    @State private var showReplaceConfirmation = false
    @State private var showDeleteConfirmation = false
    @FocusState private var nameFocused: Bool
    @FocusState private var renameFocused: Bool
    private func perform(_ operation: () throws -> Void, success: String? = nil) {
        do { try operation(); status = success } catch { status = error.localizedDescription }
    }
    var body: some View {
        NavigationStack {
            List { saveSection; savedSection }
            .navigationTitle("プリセット").navigationBarTitleDisplayMode(.inline)
            .accessibilityAction(.escape, done)
            .confirmationDialog("現在の設定で上書きしますか？", isPresented: $showReplaceConfirmation, titleVisibility: .visible, presenting: replacing) { preset in
                Button("「\(preset.name)」を上書き", role: .destructive) { perform({ try model.replacePreset(preset.id) }, success: "更新しました。") }
            }
            .confirmationDialog("プリセットを削除しますか？", isPresented: $showDeleteConfirmation, titleVisibility: .visible, presenting: deleting) { preset in
                Button("「\(preset.name)」を削除", role: .destructive) { perform({ try model.deletePreset(preset.id) }, success: "削除しました。") }
            }
        }
    }
    private var saveSection: some View {
        Section {
            if model.recipe.mode == .password { CharacterOverview(model: model, identifierPrefix: "preset-overview") }
            Text(model.recipe.presetSummary).font(.caption).foregroundStyle(.secondary)
            TextField("プリセット名", text: $name).textInputAutocapitalization(.never).autocorrectionDisabled().focused($nameFocused).submitLabel(.done)
                .onSubmit { nameFocused = false }.accessibilityIdentifier("preset-name")
            if !name.isEmpty, let error = model.presetNameError(name) { Text(error).font(.caption).foregroundStyle(.red) }
            if let status { Text(status).font(.caption).accessibilityIdentifier("preset-status") }
            if let issue = model.presetStorageIssue { Text(issue).font(.caption).foregroundStyle(.red) }
        } header: {
            HStack {
                Text("現在の設定を保存").font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                Spacer()
                Button("保存") {
                    nameFocused = false
                    perform({ try model.savePreset(name: name); name = "" }, success: "保存しました。")
                }.modifier(PrimaryActionStyle()).controlSize(.regular).fixedSize()
                    .disabled(model.busy || model.presetNameError(name) != nil || model.presetStorageIssue != nil)
                    .accessibilityIdentifier("preset-save")
            }.textCase(nil)
        } footer: { Text("生成条件をこの端末に保存します。パスワード・表示・コピー・画面保護の設定は含みません。") }
    }
    private var savedSection: some View {
        Section("保存済み · \(model.presets.count)件") {
            if model.presets.isEmpty { Text("名前を付けて保存すると、ここから呼び出せます。").font(.subheadline).foregroundStyle(.secondary) }
            ForEach(model.presets) { preset in
                presetRow(preset)
            }
    }
    }
    @ViewBuilder private func presetRow(_ preset: GeneratorPreset) -> some View {
        if renaming?.id == preset.id {
            VStack(alignment: .leading, spacing: 8) {
                Text(preset.name).font(.caption).foregroundStyle(.secondary)
                TextField("新しい名前", text: $renameText).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($renameFocused).submitLabel(.done).onSubmit { renameFocused = false }
                    .accessibilityIdentifier("preset-rename-name")
                if let error = model.presetNameError(renameText, excluding: preset.id) { Text(error).font(.caption).foregroundStyle(.red) }
                HStack {
                    Button("キャンセル") { renaming = nil; renameFocused = false }
                    Spacer()
                    Button("保存") {
                        perform({ try model.renamePreset(preset.id, to: renameText); renaming = nil; renameFocused = false }, success: "名前を変更しました。")
                    }.disabled(model.busy || model.presetNameError(renameText, excluding: preset.id) != nil)
                        .accessibilityIdentifier("preset-rename-save")
                }.buttonStyle(.borderless)
            }
        } else {
            HStack {
                Button {
                    perform({
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                        try model.applyPreset(preset.id); done()
                    })
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(preset.name).foregroundStyle(.primary)
                        Text(preset.recipe.presetSummary).font(.caption).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }.buttonStyle(.borderless).accessibilityLabel(preset.name + "を読み込む").accessibilityHint(preset.recipe.presetSummary)
                if preset.recipe == model.recipe.sanitized() { Image(systemName: "checkmark").foregroundStyle(Color.passgenAccent).accessibilityLabel("現在の設定と一致") }
                Menu {
                    Button("名前を変更") { renameText = preset.name; renaming = preset; renameFocused = true }
                    Button("現在の設定で上書き") { replacing = preset; showReplaceConfirmation = true }
                    Button("削除", role: .destructive) { deleting = preset; showDeleteConfirmation = true }
                } label: { Image(systemName: "ellipsis.circle").frame(width: 44, height: 44) }
                    .accessibilityLabel(preset.name + "の編集")
            }.disabled(model.busy)
        }
    }

}

struct PrivacyView: View {
    @Bindable var model: GeneratorModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink { SecurityGuideView() } label: {
                        Label("パスワードとアカウントを守る", systemImage: "book.closed")
                    }.accessibilityIdentifier("security-guide")
                } footer: { Text("長さ・使い回し・管理アプリ・2要素認証・パスキーを知る") }
                Section {
                    Toggle("ほかのApple端末へのコピーを許可", isOn: $model.security.allowUniversalClipboard)
                        .accessibilityIdentifier("allow-universal-clipboard")
                } header: { Label("コピーの共有", systemImage: "doc.on.clipboard") }
                footer: { Text(model.security.allowUniversalClipboard ? "ユニバーサルクリップボードで共有します。Apple AccountやHandoffなどOS側の設定も必要です。コピー先の消去までは制御できません。" : "この端末のクリップボードだけにコピーします。") }
                Section {
                    Toggle("時間が経ったら自動消去", isOn: $model.security.autoClearClipboard)
                    if model.security.autoClearClipboard {
                        Stepper("\(model.security.clipboardSeconds)秒後", value: $model.security.clipboardSeconds, in: 5...600, step: 5)
                    }
                } header: { Label("コピーの消去", systemImage: "timer") }
                footer: { Text("他の内容に置き換わったコピーは消しません。背景へ移ってもコピーは保持し、設定した期限で消去します。設定変更は次のコピーから適用します。✓はこの一覧でコピーした印です。") }
                Section {
                    Toggle("アプリ切替時に画面を隠す", isOn: $model.security.protectAppSwitcher)
                        .accessibilityIdentifier("protect-app-switcher")
                    Toggle("画面収録中は結果を保護", isOn: $model.security.protectCapture)
                        .accessibilityIdentifier("protect-capture")
                    Text("iOSでは静止画のスクリーンショットを禁止できません。収録の停止後は選んでいた表示状態に戻ります。")
                        .font(.footnote).foregroundStyle(.secondary)
                } header: { Label("画面の保護", systemImage: "record.circle") }
                footer: { Text("画面を隠す設定をオフにすると、アプリ切替画面などに内容が映る場合があります。収録中の保護は別に設定できます。生成結果は背景へ移ってもメモリ内に保持し、消去・再生成・アプリの終了で破棄します。") }
                Section("データの扱い") {
                    Text("生成用の通信・アカウント・履歴はありません。保存するのは生成条件・名前付きプリセット・表示・保護設定だけです。例：24文字、単語数、使う記号、コピーの消去秒数。パスワード・画像・読み取りテキストは保存しません。")
                    Text("画像はVisionと、対応するiOS 27端末ではApple Intelligenceの画像モデルで端末内処理します。読み取り候補は画像と照合して適用してください。iCloud写真を選ぶ際はOSが元画像を取得することがあります。")
                    Text("侵害されたOS・コピー先のアプリ・外部カメラへの保護や、メモリの完全消去は保証できません。")
                }
                Section("生成方式と出典") {
                    Text("OSの暗号学的乱数を使います。パスワードは選んだ文字から、パスフレーズはEFFの7,776語から無作為に生成します。推定エントロピーは候補数の目安です。")
                    Text("EFF Long Wordlist © Electronic Frontier Foundation / CC BY 4.0").font(.footnote)
                    Link("EFFの単語リスト", destination: URL(string: "https://www.eff.org/dice")!)
                    Link("CC BY 4.0 ライセンス", destination: URL(string: "https://creativecommons.org/licenses/by/4.0/")!)
                }
            }.navigationTitle("設定と安全性").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("閉じる") { dismiss() } } }
        }
    }
}

struct SecurityGuideView: View {
    private let examples = SecurityGuideExamples.shared
    var body: some View {
        List {
            Section {
                Label("長く、無作為に、サービスごとに", systemImage: "key.horizontal")
                    .font(.headline).foregroundStyle(Color.passgenAccent)
                Text("このアプリは生成専用です。作った値は利用先に登録し、管理アプリへ保存しましょう。")
                    .font(.subheadline)
            }
            Section {
                NavigationLink { PasswordStorageLessonView() } label: {
                    GuideDestination(title: "なぜ使い回しは危険？", detail: "平文・ハッシュ化と、漏えいの広がり")
                }.accessibilityIdentifier("password-storage-guide")
                NavigationLink { AuthenticationFactorsLessonView() } label: {
                    GuideDestination(title: "2要素と2段階、何が違う？", detail: "手順と要素を、3つの例で見比べる")
                }.accessibilityIdentifier("authentication-guide")
            }
            Section {
                GuideExampleValue(caption: "連番", value: "12345678")
                GuideExampleValue(caption: "単語に文字を足しただけ", value: examples.mutations[2])
                NavigationLink { PasswordExamplesLessonView(initialTopic: 0) } label: {
                    GuideDestination(title: "悪い例と、その理由を見る", detail: "単語・名前と日付・繰り返し・置き換え")
                }.accessibilityIdentifier("password-examples-guide")
            } header: { Text("避けたいパスワード") } footer: { Text(examples.NOTICE) }
            Section("長さとパスフレーズ") {
                NavigationLink { PasswordExamplesLessonView(initialTopic: 1) } label: {
                    GuideDestination(title: "文字数で候補はどう変わる？", detail: "数字4・6・8桁を切り替えて比較")
                }.accessibilityIdentifier("password-length-guide")
                NavigationLink { PasswordExamplesLessonView(initialTopic: 2) } label: {
                    GuideDestination(title: "7単語をつないでみる", detail: "半角スペース・ハイフン・アンダースコア")
                }.accessibilityIdentifier("passphrase-examples-guide")
            }
            Section("管理アプリへの保存例") {
                GuideExampleValue(caption: "サービス", value: examples.SERVICE)
                GuideExampleValue(caption: "ユーザー名", value: examples.USERNAME)
                GuideExampleValue(caption: "パスワード（公開の24文字見本）", value: examples.PASSWORD, identifier: "guide-manager-password")
                GuideJourneyStep(number: 1, title: "このアプリで生成", value: "サービスごとに、新しい値を作る")
                GuideJourneyStep(number: 2, title: "利用先で登録・変更", value: "作った値を、そのサービスに設定する")
                GuideJourneyStep(number: 3, title: "管理アプリへ保存", value: "次回は保存した項目から自動入力")
                Text("Appleの「パスワード」、Google パスワード マネージャー、1Passwordなどで管理できます。同期する端末と復旧方法を確認しましょう。公開の見本は使わず、新しく生成してください。")
                    .font(.footnote).foregroundStyle(.secondary)
                Text("管理アプリのアカウントも保護し、復旧情報を保管。生成結果を平文メモやスクリーンショットに残すことは避けましょう。")
                    .font(.footnote).foregroundStyle(.secondary)
                Link("Apple「パスワード」の使い方", destination: URL(string: "https://support.apple.com/ja-jp/120758")!)
                Link("Google パスワード マネージャー", destination: URL(string: "https://support.google.com/accounts/answer/6208650?hl=ja")!)
                Link("1Passwordの使い方", destination: URL(string: "https://support.1password.com/category/getting-started/")!)
            }
            Section("認証アプリを加えたサインイン例") {
                GuideJourneyStep(number: 1, title: "パスワードを入力", value: examples.PASSWORD)
                GuideJourneyStep(number: 2, title: "認証アプリの一時コードを入力", value: "\(examples.CODE)（説明用）")
                Text("知っているパスワード ＋ アプリの秘密鍵を使えること。この例は2段階・2要素です。一時コードも偽サイトへ入力すると盗まれます。")
                    .font(.footnote).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 4) {
                    Text("機種変更の前に").font(.subheadline.weight(.semibold))
                    Text("サービスの復旧コードなどを保管し、新端末での利用を確認").font(.footnote).foregroundStyle(.secondary)
                }
                Link("Googleの2段階認証ガイド", destination: URL(string: "https://support.google.com/accounts/answer/185839?hl=ja")!)
            }
            Section("パスキーでのサインイン例") {
                GuideJourneyStep(number: 1, title: "対応サービスでパスキーを選ぶ", value: "事前に登録した鍵を使用")
                GuideJourneyStep(number: 2, title: "端末で本人確認", value: "Face ID・Touch ID・端末のロック解除など")
                    .accessibilityIdentifier("guide-passkey-unlock")
                GuideJourneyStep(number: 3, title: "サインイン", value: "パスワードや一時コードを入力せずに認証")
                Text("本人確認を求めるサービスの例です。顔や指紋の情報をサイトへ送る仕組みではありません。登録したサイトに結び付くため、偽サイトへの認証情報の入力を防ぎやすくなります。")
                    .font(.footnote).foregroundStyle(.secondary)
                Text("同期・機種変更・復旧の方法も確認しましょう。このアプリはパスキー自体を作成しません。")
                    .font(.footnote).foregroundStyle(.secondary)
                Link("Appleのパスキー解説", destination: URL(string: "https://support.apple.com/ja-jp/guide/iphone/iphf538ea8d0/ios")!)
                Link("Googleのパスキーガイド", destination: URL(string: "https://support.google.com/accounts/answer/13548313?hl=ja")!)
            }
            Section {
                GuideExampleValue(caption: "登録先に見立てた架空URL", value: examples.EXPECTED_URL)
                GuideExampleValue(caption: "先頭を似せた、別のドメイン", value: examples.LOOKALIKE_URL, identifier: "guide-lookalike-url")
                Text("上はexample.com、下はexample.netの配下です。先頭の文字や鍵マークだけでは、正しい入力先か判断できません。メッセージのリンクから急いで入力せず、保存済みのブックマークや公式アプリから開きましょう。")
                    .font(.footnote).foregroundStyle(.secondary)
            } header: { Text("長いパスワードでも、入力先に注意") } footer: { Text("URLも説明用の架空例です。長さや無作為さだけでは、偽サイトへの入力やマルウェアを防げません。") }
        }.navigationTitle("アカウントを守る").navigationBarTitleDisplayMode(.inline)
            .accessibilityIdentifier("security-guide-list")
    }
}

private struct GuideDestination: View {
    let title: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline).foregroundStyle(Color.passgenAccent)
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical, 4)
    }
}

private struct GuideExampleValue: View {
    let caption: String
    let value: String
    var identifier = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !caption.isEmpty { Text(caption).font(.caption).foregroundStyle(.secondary) }
            Text(value).font(.system(.body, design: .monospaced, weight: .semibold))
                .fixedSize(horizontal: false, vertical: true).accessibilityIdentifier(identifier)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct GuideJourneyStep: View {
    let number: Int
    let title: String
    let value: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(number)").font(.caption.bold()).foregroundStyle(Color.passgenAccent)
                .padding(8).background(Color.passgenAccent.opacity(0.1), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(value).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            }.frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct PasswordExamplesLessonView: View {
    @State private var topic: Int
    @State private var numberIndex = 0
    @State private var separator = " "
    @Environment(\.dynamicTypeSize) private var typeSize
    private let examples = SecurityGuideExamples.shared
    private let topics = ["避けたい例", "長さ", "パスフレーズ"]
    init(initialTopic: Int) { _topic = State(initialValue: initialTopic) }
    var body: some View {
        VStack(spacing: 0) {
            Group {
                if typeSize.isAccessibilitySize { topicPicker.pickerStyle(.menu) }
                else { topicPicker.pickerStyle(.segmented) }
            }.padding(.horizontal, 16).padding(.vertical, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(examples.NOTICE).font(.caption).foregroundStyle(.secondary)
                    if topic == 0 { weakContent }
                    else if topic == 1 { lengthContent }
                    else { phraseContent }
                }.padding(16).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }.id(topic).accessibilityIdentifier("password-examples-scroll")
        }.background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("パスワードを例で見る").navigationBarTitleDisplayMode(.inline)
    }
    private var topicPicker: some View {
        Picker("例のテーマ", selection: $topic) {
            ForEach(topics.indices, id: \.self) { Text(topics[$0]).tag($0) }
        }.accessibilityIdentifier("password-example-topic")
    }
    private var weakContent: some View {
        Group {
            ForEach(examples.weakExamples, id: \.key) { example in
                LessonPanel(title: example.title) {
                    GuideExampleValue(caption: "", value: example.value, identifier: "weak-value-\(example.key)")
                    Text(example.reason).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            LessonPanel(title: "よくある置き換えも、推測されます") {
                ForEach(Array(examples.mutations.enumerated()), id: \.offset) { index, value in
                    if index > 0 { Text("↓").foregroundStyle(.secondary) }
                    Text(value).font(.system(.body, design: .monospaced, weight: .semibold))
                        .accessibilityIdentifier("weak-mutation-\(index)")
                }
                Text("大文字にする・末尾に1!を足す・aを@に置き換える。この変形も攻撃で試される候補です。文字種が揃っていても、無作為とは限りません。")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
    private var lengthContent: some View {
        let item = examples.numericExamples[numberIndex]
        return Group {
            LessonPanel(title: "同じ数字でも、長さで変わる") {
                Picker("数字の桁数", selection: $numberIndex) {
                    ForEach(examples.numericExamples.indices, id: \.self) { index in
                        Text("\(examples.numericExamples[index].length)桁").tag(index)
                    }
                }.pickerStyle(.segmented)
                GuideExampleValue(caption: "数字だけの形の例", value: item.value, identifier: "guide-length-value")
                Text(item.candidateLabel).font(.title2.bold()).foregroundStyle(Color.passgenAccent)
                    .accessibilityIdentifier("guide-length-count")
                Text("各桁を0〜9から独立に無作為に選ぶ場合です。先頭の0も含みます。4桁から2桁増えるごとに、候補数は100倍になります。")
                    .font(.subheadline).foregroundStyle(.secondary)
                Text("数字だけの短いパスワードは候補が限られます。候補数は安全性や解読時間の保証ではなく、文字種とルールでも変わります。")
                    .font(.caption).foregroundStyle(.secondary)
            }
            LessonPanel(title: "24文字のパスワードの形") {
                GuideExampleValue(caption: "英大文字・小文字・数字・記号の見本", value: examples.PASSWORD, identifier: "guide-password-shape")
                Text("まず15文字以上を目安に、利用先が許す範囲で長く。このアプリの初期値は24文字です。長いだけ・複雑に見えるだけでは足りず、無作為に新しく作ることが大切です。")
                    .font(.subheadline).foregroundStyle(.secondary)
                Link("NISTのパスワードガイド（英語）", destination: URL(string: "https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password")!)
                    .font(.footnote)
            }
        }
    }
    private var phraseContent: some View {
        Group {
            LessonPanel(title: "無作為に選んだ7単語の形") {
                Picker("単語の区切り", selection: $separator) {
                    Text("半角スペース").tag(" "); Text("-").tag("-"); Text("_").tag("_")
                }.pickerStyle(.segmented)
                GuideExampleValue(caption: "区切りを変えて見比べる", value: examples.phrase(separator: separator), identifier: "guide-phrase-value")
                Text("このアプリはEFFの7,776語から単語ごとに無作為に選びます。区切りは入力しやすさや利用先の条件で選びます。固定の区切り自体が推測しにくさを増やすわけではありません。")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            LessonPanel(title: "長いフレーズでも、避けたい例") {
                GuideExampleValue(caption: "知られた言い回し", value: "iloveyouforever")
                Text("好きな文・歌詞・名言をそのままつなぐ方法は、無作為な単語選びと異なります。上の7単語も公開済みなので、そのまま使わず新しく生成しましょう。")
                    .font(.subheadline).foregroundStyle(.secondary)
                Link("EFF：パスフレーズの作り方（英語）", destination: URL(string: "https://ssd.eff.org/module/creating-strong-passwords")!)
                    .font(.footnote)
            }
        }
    }
}

private struct PasswordStorageLessonView: View {
    @State private var topic = 0
    @State private var reuse = true
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.horizontalSizeClass) private var horizontalSize
    var body: some View {
        VStack(spacing: 0) {
            Picker("図解のテーマ", selection: $topic) {
                Text("保存の違い").tag(0); Text("使い回し").tag(1)
            }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if topic == 0 { storageContent } else { reuseContent }
                    Divider()
                    DisclosureGroup("根拠を読む（英語）") {
                        VStack(alignment: .leading, spacing: 12) {
                            Link("OWASP：パスワードの保存", destination: URL(string: "https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html")!)
                            Link("NIST：漏えいしたハッシュへの攻撃", destination: URL(string: "https://pages.nist.gov/800-63-4/sp800-63b/passwords/")!)
                            Link("OWASP：使い回しを狙う攻撃", destination: URL(string: "https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html")!)
                            Link("OWASP：HTTPSによる通信の保護", destination: URL(string: "https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html")!)
                        }.font(.subheadline).padding(.top, 10)
                    }.font(.caption.weight(.semibold))
                }.padding(16).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }.id(topic).accessibilityIdentifier("storage-lesson-scroll")
        }.background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("保存方法と使い回し").navigationBarTitleDisplayMode(.inline)
    }
    private var storageContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("サーバーのDBには、何が残る？").font(.title2.weight(.semibold))
            Text("DB（データベース）はサービス側の保存場所です。適切にハッシュ化すべきですが、実際にどう保存されているかは、利用者には外から確かめられません。").font(.subheadline)
            Text("説明用の架空の例です。実際のパスワードには使わないでください。").font(.caption).foregroundStyle(.secondary)
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 12) {
                    StorageComparisonCard(hashed: false).frame(minWidth: 280)
                    StorageComparisonCard(hashed: true).frame(minWidth: 280)
                }
                VStack(spacing: 12) { StorageComparisonCard(hashed: false); StorageComparisonCard(hashed: true) }
            }
            LessonPanel(title: "ログイン時は、計算して照合") {
                Text("入力したパスワードを、保存時と同じソルト・設定で計算します。保存済みの値と一致するかで確認します。").font(.subheadline)
                if typeSize.isAccessibilitySize || horizontalSize != .regular {
                    LessonValueBox(caption: "今回の入力から計算", value: StorageLessonExamples.shared.COMMON_SALT_HASH)
                    Text("照合").font(.headline).foregroundStyle(Color.passgenAccent)
                    LessonValueBox(caption: "サーバーのDBに保存済み", value: StorageLessonExamples.shared.COMMON_SALT_HASH)
                } else {
                    HStack(spacing: 8) {
                        LessonValueBox(caption: "今回の入力から計算", value: StorageLessonExamples.shared.COMMON_SALT_HASH)
                        Text("照合").font(.caption.weight(.semibold))
                        LessonValueBox(caption: "サーバーのDBに保存済み", value: StorageLessonExamples.shared.COMMON_SALT_HASH)
                    }
                }
            }
            Text("HTTPSの鍵マークは通信の保護です。サービス内での保存方法までは示しません。").font(.caption).foregroundStyle(.secondary)
            DisclosureGroup {
                VStack(alignment: .leading, spacing: 10) {
                    Text("攻撃者は候補を次々に計算し、漏れた値と比べて元のパスワードを探せます。短い値・よくある値は特に推測されやすくなります。")
                        .accessibilityIdentifier("hash-guessing")
                    SaltComparisonView()
                    Text("個別ソルトがあっても、候補の計算による推測は可能です。サービス側は推測に時間やメモリがかかる専用の保存方式も使います。利用者は長く無作為な値を、サービスごとに別々にしましょう。")
                    Text("暗号化は鍵で元へ戻せる方式です。ハッシュ化は元へ戻す鍵を持たず、計算結果の照合に使います。")
                }.font(.subheadline).padding(.top, 10)
            } label: { Text("ハッシュ化なら、漏れても安全？").font(.subheadline.weight(.semibold)) }
                .padding(16).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
            Button("使い回しで何が起きる？") { topic = 1 }
                .frame(maxWidth: .infinity, minHeight: 44).buttonStyle(.borderedProminent)
        }
    }
    private var reuseContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("1か所で知られた値が、ほかの入口にも使われる。").font(.title2.weight(.semibold))
            Text("説明用の架空の例です。実際のパスワードには使わないでください。").font(.caption).foregroundStyle(.secondary)
            Picker("パスワードの使い方", selection: $reuse) {
                Text("同じ値を使う").tag(true); Text("別々にする").tag(false)
            }.pickerStyle(.segmented).accessibilityIdentifier("reuse-choice")
            LessonPanel(title: "1  ショップAでパスワードが判明") {
                Text("平文の漏えいや、漏れたハッシュからの推測などで知られた場合の図です。").font(.caption).foregroundStyle(.secondary)
                LessonValueBox(caption: "攻撃者に知られた値", value: StorageLessonExamples.shared.PASSWORD, identifier: "reuse-password-a")
            }
            LessonBranch().stroke(reuse ? Color.red.opacity(0.6) : Color.passgenAccent.opacity(0.6), lineWidth: 1.5)
                .frame(height: 24).accessibilityHidden(true)
            if typeSize.isAccessibilitySize {
                VStack(spacing: 10) { ReuseTargetCard(name: "メールB", password: reuse ? StorageLessonExamples.shared.PASSWORD : StorageLessonExamples.shared.PASSWORD_B, reuse: reuse, identifier: "reuse-password-b"); ReuseTargetCard(name: "SNS C", password: reuse ? StorageLessonExamples.shared.PASSWORD : StorageLessonExamples.shared.PASSWORD_C, reuse: reuse, identifier: "reuse-password-c") }
            } else {
                HStack(alignment: .top, spacing: 10) { ReuseTargetCard(name: "メールB", password: reuse ? StorageLessonExamples.shared.PASSWORD : StorageLessonExamples.shared.PASSWORD_B, reuse: reuse, identifier: "reuse-password-b"); ReuseTargetCard(name: "SNS C", password: reuse ? StorageLessonExamples.shared.PASSWORD : StorageLessonExamples.shared.PASSWORD_C, reuse: reuse, identifier: "reuse-password-c") }
            }
            Text(reuse ? "同じ値が別のサービスでも試されます。" : "Aで漏れた値は、BとCのパスワードには一致しません。")
                .font(.headline).foregroundStyle(reuse ? Color.red : Color.passgenAccent).accessibilityIdentifier("reuse-outcome")
            Text("同じメールアドレスなどのログインIDと組み合わせ、別のサービスにも入ろうとする攻撃です。相手側が適切にハッシュ化していても、正しいパスワードでのログインを試される点は変わりません。").font(.subheadline)
            Text("別々の値でも、そのサービス自身の漏えいや偽サイトへの入力などは防げません。").font(.caption).foregroundStyle(.secondary)
            LessonPanel(title: "自分でできる、3つの備え") {
                Text("① サービスごとに長くランダムな別の値を使う")
                Text("② 管理アプリに保存して、自動入力する")
                Text("③ パスキーや2要素認証を追加する")
            }.font(.subheadline)
            DisclosureGroup("漏えいに気づいたら？") {
                Text("使い回している全サービスで別々の値に変更し、不審なログインや有効なセッションを確認してください。変更は正規アプリや公式サイトから行いましょう。")
                    .font(.subheadline).padding(.top, 8)
            }.font(.subheadline).padding(16)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        }
    }
}

private struct LessonPanel<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline); content()
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}

private struct LessonValueBox: View {
    let caption: String
    let value: String
    var identifier = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(caption).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.system(.subheadline, design: .monospaced, weight: .medium))
                .lineLimit(nil).fixedSize(horizontal: false, vertical: true)
        }.padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
            .accessibilityElement(children: .combine).accessibilityIdentifier(identifier)
    }
}

private struct StorageComparisonCard: View {
    let hashed: Bool
    var body: some View {
        LessonPanel(title: hashed ? "ハッシュ化" : "平文（ひらぶん）") {
            Text(hashed ? "照合に使う値へ変換して保存" : "そのまま読める形で保存").font(.subheadline)
            LessonValueBox(caption: "利用者が入力したパスワード", value: StorageLessonExamples.shared.PASSWORD)
            Text(hashed ? "↓ 一方向の計算" : "↓ そのまま保存").font(.caption.weight(.semibold))
                .foregroundStyle(hashed ? Color.passgenAccent : Color.red).frame(maxWidth: .infinity)
            LessonValueBox(caption: "サーバーのDBに保存される値", value: hashed ? StorageLessonExamples.shared.COMMON_SALT_HASH : StorageLessonExamples.shared.PASSWORD, identifier: hashed ? "storage-hash-value" : "storage-plaintext-value")
            Text(hashed ? "Argon2idで計算した保存形式の一例です。方式・設定・ソルトも含まれます。" : "保存先を見られると、パスワードもそのまま読まれます。")
                .font(.caption).foregroundStyle(.secondary)
        }
    }
}

private struct SaltComparisonView: View {
    @State private var salted = false
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("同じパスワードを使う2人").font(.subheadline.weight(.semibold))
            Text("同じArgon2idの設定と架空のパスワードで、ソルトだけを変えて計算した例です。").font(.caption).foregroundStyle(.secondary)
            LessonValueBox(caption: "2人が入力したパスワード", value: StorageLessonExamples.shared.PASSWORD)
            Picker("ソルトの比較", selection: $salted) {
                Text("共通ソルト").tag(false); Text("個別ソルト").tag(true)
            }.pickerStyle(.segmented)
            account("利用者A", salted ? "A専用のソルト" : "共通のソルト", salted ? StorageLessonExamples.shared.INDIVIDUAL_SALT_HASH_A : StorageLessonExamples.shared.COMMON_SALT_HASH, "salt-hash-a")
            account("利用者B", salted ? "B専用のソルト" : "共通のソルト", salted ? StorageLessonExamples.shared.INDIVIDUAL_SALT_HASH_B : StorageLessonExamples.shared.COMMON_SALT_HASH, "salt-hash-b")
            Text(salted ? "同じパスワードでも、保存値は異なります。" : "保存値の一致が、同じパスワードの手掛かりに。")
                .font(.subheadline.weight(.semibold)).foregroundStyle(Color.passgenAccent).accessibilityIdentifier("salt-outcome")
            Text(salted ? "利用者ごとに異なるランダムな「ソルト」を加えると、保存値の単純な一致比較を防げます。ソルトは通常ハッシュと一緒に保存され、秘密にする値ではありません。"
                 : "一人のパスワードが判明すると、同じ保存値を持つほかの人のパスワードも推測できます。ソルトがない場合や全員で同じソルトを使う場合は、同じ方式・設定でこの問題が起こります。")
                .font(.subheadline)
        }.padding(.vertical, 8)
    }
    private func account(_ name: String, _ salt: String, _ hash: String, _ identifier: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("\(name) · \(salt)").font(.caption.weight(.medium)).foregroundStyle(.secondary)
            Text(hash).font(.system(.body, design: .monospaced)).lineLimit(nil).fixedSize(horizontal: false, vertical: true).accessibilityIdentifier(identifier)
        }.padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct ReuseTargetCard: View {
    let name: String
    let password: String
    let reuse: Bool
    let identifier: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(name).font(.subheadline.weight(.semibold))
            Text(password).font(.system(.caption, design: .monospaced)).lineLimit(nil).fixedSize(horizontal: false, vertical: true)
            Text(reuse ? "侵入を試される" : "漏れた値は不一致").font(.caption.weight(.semibold))
                .foregroundStyle(reuse ? Color.red : Color.passgenAccent)
        }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(reuse ? Color.red.opacity(0.4) : Color.passgenAccent.opacity(0.4)))
            .accessibilityElement(children: .combine).accessibilityIdentifier(identifier)
    }
}

private struct LessonBranch: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY)); path.addLine(to: CGPoint(x: rect.midX, y: rect.midY))
        path.move(to: CGPoint(x: rect.width * 0.25, y: rect.maxY)); path.addLine(to: CGPoint(x: rect.width * 0.25, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.width * 0.75, y: rect.midY)); path.addLine(to: CGPoint(x: rect.width * 0.75, y: rect.maxY))
        return path
    }
}

private enum AuthFactor: String, CaseIterable {
    case knowledge = "知識", possession = "所持", biometric = "生体"
    var color: Color {
        switch self { case .knowledge: .primary; case .possession: .passgenAccent; case .biometric: .primary }
    }
    var meaning: String {
        switch self { case .knowledge: "知っていること"; case .possession: "機器や鍵を使えること"; case .biometric: "自分の身体の特徴" }
    }
    var examples: String {
        switch self { case .knowledge: "パスワード・PIN"; case .possession: "認証アプリ・セキュリティキー"; case .biometric: "指紋・顔" }
    }
}

private enum AuthExample: String, CaseIterable {
    case question = "2段階", app = "2要素", passkey = "パスキー"
    var title: String {
        switch self { case .question: "パスワード ＋ 秘密の質問"; case .app: "パスワード ＋ 認証アプリ"; case .passkey: "指紋で本人確認するパスキー" }
    }
    var first: String { self == .passkey ? "端末にある暗号鍵" : "パスワード" }
    var second: String {
        switch self { case .question: "秘密の質問の答え"; case .app: "アプリの一時コード"; case .passkey: "指紋で鍵を使用" }
    }
    var firstFactor: AuthFactor { self == .passkey ? .possession : .knowledge }
    var secondFactor: AuthFactor {
        switch self { case .question: .knowledge; case .app: .possession; case .passkey: .biometric }
    }
    var steps: String { self == .passkey ? "一連の操作" : "2段階" }
    var factors: String { self == .question ? "1種類" : "2種類" }
    var result: String {
        switch self {
        case .question: "2回確認しても、2要素にはなりません。"
        case .app: "2段階で、異なる2つの要素を確認します。"
        case .passkey: "一連の操作で、複数の要素を確認できます。"
        }
    }
    var explanation: String {
        switch self {
        case .question: "どちらも「知っていること」です。これは違いを説明するための例です。秘密の質問は推測や調査で知られやすく、NISTでも認証手段として認められていません。"
        case .app: "パスワードを知っていることと、認証アプリの秘密鍵を使えることを確かめます。コードを入力する操作でも、ここでは「所持」の証明です。"
        case .passkey: "端末の暗号鍵と、鍵を使うための指紋確認を組み合わせる例です。指紋の情報そのものをサービスへ送る仕組みではありません。"
        }
    }
}

private struct AuthenticationFactorsLessonView: View {
    @State private var example = AuthExample.app
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        VStack(spacing: 0) {
            Text("2段階は「手順」、2要素は「種類」。").font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.top, 8)
            Picker("比較する例", selection: $example) {
                ForEach(AuthExample.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 10)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(example.title).font(.title3.weight(.semibold))
                    HStack(alignment: .top, spacing: 10) {
                        authMetric("確認の流れ", example.steps)
                        authMetric("要素の種類", example.factors)
                    }
                    LessonPanel(title: example == .passkey ? "鍵を使うときに、本人確認" : "確認を順番に重ねる") {
                        if typeSize.isAccessibilitySize {
                            AuthStepCard(title: example.first, factor: example.firstFactor)
                            connector.frame(maxWidth: .infinity)
                            AuthStepCard(title: example.second, factor: example.secondFactor)
                        } else {
                            HStack(spacing: 8) {
                                AuthStepCard(title: example.first, factor: example.firstFactor)
                                connector
                                AuthStepCard(title: example.second, factor: example.secondFactor)
                            }
                        }
                        Text(example.result).font(.headline).foregroundStyle(Color.passgenAccent).accessibilityIdentifier("auth-outcome")
                    }
                    Text(example.explanation).font(.subheadline)
                    if example == .passkey {
                        Text("この例は利用時の本人確認が必須の構成です。PINなら「所持＋知識」になります。すべてのパスキーが常に2要素として扱われるわけではなく、サービスの要求と実装によります。").font(.caption)
                    }
                    LessonPanel(title: "名前だけで、強さは決まりません") {
                        Text("「2段階認証」という名前で2要素を使うサービスもあります。何を組み合わせ、どの攻撃を防げるかを確認しましょう。").font(.subheadline)
                    }
                    DisclosureGroup("3つの要素とは？") {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(AuthFactor.allCases, id: \.self) { factor in
                                HStack(alignment: .top, spacing: 12) {
                                    Text(factor.rawValue).font(.subheadline.weight(.semibold)).foregroundStyle(factor.color)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(factor.meaning).font(.subheadline)
                                        Text(factor.examples).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            Text("生体情報は通常、機器の鍵を使うための本人確認に使います。顔や指紋だけを単独でネットの相手へ証明するものではありません。2種類以上なら「多要素認証（MFA）」と呼びます。").font(.caption)
                        }.padding(.top, 12)
                    }.font(.subheadline.weight(.semibold))
                    DisclosureGroup("どれを設定したらいい？") {
                        VStack(alignment: .leading, spacing: 12) {
                            authMethod("パスキー・FIDOセキュリティキー", "正規のサイトと結び付き、偽サイトへ認証情報を渡す攻撃に強い方式です。同期先・復旧方法も確認しましょう。")
                            authMethod("認証アプリの一時コード", "パスワードだけの認証に保護を追加できます。ただし偽サイトへのコード入力で盗まれることがあります。")
                            authMethod("SMSの一時コード", "追加の保護になりますが、電話番号の乗っ取りや偽サイトに弱い面があります。ほかの方法も選べるか確認しましょう。")
                            Divider()
                            Text("心当たりのない認証要求は承認せず、コードを人に教えないでください。復旧コードは安全に保管し、機種変更前に引き継ぎ方法を確認しましょう。").font(.caption)
                        }.padding(.top, 12)
                    }.font(.subheadline.weight(.semibold))
                    Divider()
                    DisclosureGroup("根拠を読む（英語）") {
                        VStack(alignment: .leading, spacing: 12) {
                            Link("NIST：要素の分類", destination: URL(string: "https://pages.nist.gov/800-63-4/sp800-63/model/")!)
                            Link("NCSC：2段階認証の呼び方", destination: URL(string: "https://www.ncsc.gov.uk/guidance/setting-2-step-verification-2sv")!)
                            Link("FIDO：パスキーと本人確認", destination: URL(string: "https://fidoalliance.org/passkeys/")!)
                            Link("OWASP：認証方式ごとの特徴", destination: URL(string: "https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html")!)
                        }.font(.subheadline).padding(.top, 10)
                    }.font(.caption.weight(.semibold))
                }.padding(16).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }.id(example)
        }.background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("2要素と2段階").navigationBarTitleDisplayMode(.inline)
    }
    private var connector: some View { Text(example == .passkey ? "＋" : typeSize.isAccessibilitySize ? "↓" : "→").font(.headline).accessibilityHidden(true) }
    private func authMetric(_ caption: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(caption).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.headline).accessibilityIdentifier(caption == "確認の流れ" ? "auth-steps" : "auth-count")
        }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.passgenAccent.opacity(0.09), in: RoundedRectangle(cornerRadius: 12))
    }
    private func authMethod(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) { Text(title).font(.subheadline.weight(.semibold)); Text(detail).font(.subheadline) }
    }
}

private struct AuthStepCard: View {
    let title: String
    let factor: AuthFactor
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(factor.rawValue).font(.caption.weight(.semibold)).foregroundStyle(factor.color)
            Text(title).font(.subheadline)
        }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(factor.color.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
            .accessibilityElement(children: .combine)
    }
}

private struct SensitiveScene: ViewModifier {
    let enabled: Bool
    @Environment(\.scenePhase) private var phase
    func body(content: Content) -> some View {
        content.overlay {
            if enabled && phase != .active {
                ZStack {
                    Color(uiColor: .systemBackground).ignoresSafeArea()
                    VStack(spacing: 16) {
                        Image(systemName: "lock.shield").font(.largeTitle).foregroundStyle(Color.passgenAccent)
                        Text("Passgen").font(.title2.weight(.semibold))
                        Text("秘密を保護しています").font(.caption).foregroundStyle(.secondary)
                    }
                }.transition(.identity).accessibilityElement(children: .combine).allowsHitTesting(false)
                    .accessibilityIdentifier("background-privacy-cover")
            }
        }
    }
}
extension View { func sensitiveScene(enabled: Bool) -> some View { modifier(SensitiveScene(enabled: enabled)) } }
