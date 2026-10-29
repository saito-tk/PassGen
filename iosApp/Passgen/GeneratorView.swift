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

struct GeneratorView: View {
    @State private var model = GeneratorModel.applicationModel()
    @State private var clipboard = SecureClipboard()
    @State private var showSymbols = false
    @State private var showPrivacy = false
    @State private var showResults = false
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
                            ScrollView { VStack(spacing: 12) { CharacterEditor(model: model, showSymbols: $showSymbols); CharacterOverview(model: model) }.padding(12) }
                        }
                    } else {
                        ScrollView {
                            VStack(spacing: 12) {
                                modePicker
                                QuantityEditor(model: model)
                                CharacterEditor(model: model, showSymbols: $showSymbols)
                                CharacterOverview(model: model)
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
            .sheet(isPresented: $showPrivacy) { PrivacyView(model: model).sensitiveScene() }
            .sheet(isPresented: $showResults) {
                ResultsList(model: model, copy: copy).sensitiveScene()
                    .presentationDetents([.height(CGFloat(220 + min(model.busy ? model.goal : model.results.count, 5) * 76)), .large])
                    .presentationDragIndicator(.visible)
            }
            .sensitiveScene()
        }
        .environment(protection)
        .background { CaptureStateReader(onChange: protection.update).frame(width: 0, height: 0).allowsHitTesting(false).accessibilityHidden(true) }
        .tint(.passgenAccent)
        .onChange(of: phase) { _, value in
            if value == .background { model.background(); if model.security.clearClipboardOnBackground { clipboard.clearOwned() } }
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

private struct CharacterOverview: View {
    @Bindable var model: GeneratorModel
    var body: some View {
        if model.recipe.mode == .password {
            VStack(alignment: .leading, spacing: 8) {
                Text("文字種の使用状況").font(.caption).foregroundStyle(.secondary)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], spacing: 8) {
                    ForEach(CharacterGroup.allCases) { item in
                        let enabled = item.enabled(in: model.recipe)
                        let count = item.selected(in: model.recipe).filter { item == .symbols || !model.recipe.excludeSimilar || !model.recipe.similarExclusions.contains($0) }.count
                        Button { item.setEnabled(!enabled, in: &model.recipe) } label: {
                            HStack(spacing: 8) {
                                Image(systemName: enabled ? "checkmark.circle.fill" : "circle").foregroundStyle(enabled ? Color.passgenAccent : Color.secondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.subheadline)
                                    Text(enabled ? "使用中 · \(count)文字" : "未使用").font(.caption2).foregroundStyle(.secondary)
                                }.fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).padding(.horizontal, 8)
                                .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain).accessibilityLabel("\(item.title)を使用")
                            .accessibilityValue(enabled ? "オン、\(count)文字" : "オフ")
                            .accessibilityAddTraits(enabled ? [.isSelected] : [])
                            .accessibilityIdentifier("category-\(item.rawValue)")
                    }
                }
            }.padding(12).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                .disabled(model.busy)
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

struct ResultsList: View {
    @Bindable var model: GeneratorModel
    var copy: (SecretItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(ScreenProtectionState.self) private var protection
    var body: some View {
        NavigationStack {
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
                                Text("\(item.id + 1) · \(item.length)文字\(item.length > 100 ? " / 先頭100文字" : "")").font(.caption2).foregroundStyle(.secondary)
                                Text(protection.allowsSecret(revealed: model.revealed, protectCapture: model.security.protectCapture) ? String(item.value.prefix(100)) : SecretPresentation.shared.MASK)
                                    .font(.system(.body, design: .monospaced)).lineLimit(3).privacySensitive()
                                    .accessibilityLabel("パスワード。読み上げを保護しています").accessibilityIdentifier("secret")
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
                } footer: {
                    if let first = model.results.first { Text("推定エントロピー \(Int(first.bits)) bits · \(model.security.clipboardSummary)") }
                }
            }
            .navigationTitle("生成結果 \(model.results.count)件").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { model.revealed.toggle() } label: { Image(systemName: model.revealed ? "eye.slash" : "eye") }
                        .disabled((protection.isCaptured && model.security.protectCapture) || model.results.isEmpty).accessibilityLabel(model.revealed ? "隠す" : "表示")
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
                    Toggle("背景へ移るとコピーを消去", isOn: $model.security.clearClipboardOnBackground)
                } header: { Label("コピーの消去", systemImage: "timer") }
                footer: { Text("他の内容に置き換わったコピーは消しません。他アプリや別端末へ移って貼り付けたい場合は、背景での消去をオフにしてください。設定変更は次のコピーから適用します。✓はこの一覧でコピーした印です。") }
                Section {
                    Toggle("画面収録中は結果を保護", isOn: $model.security.protectCapture)
                        .accessibilityIdentifier("protect-capture")
                    Text("iOSでは静止画のスクリーンショットを禁止できません。収録の停止後は選んでいた表示状態に戻ります。")
                        .font(.footnote).foregroundStyle(.secondary)
                } header: { Label("画面の保護", systemImage: "record.circle") }
                footer: { Text("オフにすると収録や画面共有に結果・読み取り画像が映ります。非アクティブ時の覆いと背景移行時の結果消去は常に有効です。") }
                Section("データの扱い") {
                    Text("生成用の通信・アカウント・履歴はありません。保存するのは生成条件と表示・保護設定だけです。パスワード・画像・読み取りテキストは保存しません。")
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
    var body: some View {
        List {
            Section {
                Label("長く、無作為に、サービスごとに", systemImage: "key.horizontal")
                    .font(.headline).foregroundStyle(Color.passgenAccent)
                Text("このアプリは生成専用です。作った値はサービスに登録し、管理アプリへ保存してください。サービスの条件を満たす長さでも、安全性が同じとは限りません。")
            }
            Section("強いパスワードの特徴") {
                Text("長さがあり、推測できない無作為な文字や単語で構成されることが大切です。まず15文字以上を目安に、利用先が許す範囲で長くしましょう。このアプリの初期値は24文字です。")
                Text("パスフレーズは無作為な単語を複数組み合わせます。好きな名言や歌詞をそのまま使う方法とは異なります。数字だけの短い値は候補が少なく、英数字の長いパスワードより推測されやすくなります。")
                Link("NISTのパスワードガイド（英語）", destination: URL(string: "https://www.nist.gov/cybersecurity-and-privacy/how-do-i-create-good-password")!)
            }
            Section("弱くなりやすい例") {
                Text("名前・誕生日・辞書の単語だけ・連番・キーボード順・有名なフレーズは避けましょう。末尾に1や!を足すだけ、aを@に置き換えるだけでは推測を防ぎにくいです。")
                Text("使い回すと、ひとつのサービスから漏れた値で別のアカウントにも侵入されます。サービスごとに別の値を生成しましょう。長いパスワードでも、偽サイトへの入力やマルウェアは防げません。")
            }
            Section("管理アプリへ保存する") {
                Text("Appleの「パスワード」、Google パスワード マネージャー、1Passwordなどは、サービスごとのパスワードやパスキーを保存・自動入力できます。使う端末や復旧方法、同期範囲を確認して選びましょう。")
                Text("管理アプリ自体のアカウントも保護し、復旧用情報を安全な場所へ保存してください。生成結果をスクリーンショットや平文のメモで残すことは避けましょう。")
                Link("Apple「パスワード」の使い方", destination: URL(string: "https://support.apple.com/ja-jp/120758")!)
                Link("Google パスワード マネージャー", destination: URL(string: "https://support.google.com/accounts/answer/6208650?hl=ja")!)
                Link("1Passwordの使い方", destination: URL(string: "https://support.1password.com/category/getting-started/")!)
            }
            Section("2要素認証を追加する") {
                Text("パスワードに加え、認証アプリの一時コードやセキュリティキーなど、別の要素で本人を確認します。対応サービスのセキュリティ設定から有効にし、復旧コードも保管しましょう。")
                Text("SMSにも保護効果はありますが、認証アプリやセキュリティキーが選べるなら検討してください。一時コードも偽サイトに入力すると盗まれます。")
                Link("Googleの2段階認証ガイド", destination: URL(string: "https://support.google.com/accounts/answer/185839?hl=ja")!)
            }
            Section("パスキーを使う") {
                Text("パスキーは、端末や管理アプリに保存した暗号鍵でサインインする仕組みです。対応するサイト・アプリで作成し、Face IDや指紋、端末のロック解除などで使用します。")
                Text("登録したサイトに結び付くため、偽サイトへの認証情報の入力を防ぎやすくなります。同期・機種変更・復旧の方法も確認しましょう。このアプリはパスキー自体を作成しません。")
                Link("Appleのパスキー解説", destination: URL(string: "https://support.apple.com/ja-jp/guide/iphone/iphf538ea8d0/ios")!)
                Link("Googleのパスキーガイド", destination: URL(string: "https://support.google.com/accounts/answer/13548313?hl=ja")!)
            }
        }.navigationTitle("アカウントを守る").navigationBarTitleDisplayMode(.inline)
    }
}

private struct SensitiveScene: ViewModifier {
    @Environment(\.scenePhase) private var phase
    func body(content: Content) -> some View {
        content.overlay {
            if phase != .active {
                ZStack {
                    Color(uiColor: .systemBackground).ignoresSafeArea()
                    VStack(spacing: 16) {
                        Image(systemName: "lock.shield").font(.largeTitle).foregroundStyle(Color.passgenAccent)
                        Text("Passgen").font(.title2.weight(.semibold))
                        Text("秘密を保護しています").font(.caption).foregroundStyle(.secondary)
                    }
                }.transition(.identity).accessibilityElement(children: .combine).allowsHitTesting(false)
            }
        }
    }
}
extension View { func sensitiveScene() -> some View { modifier(SensitiveScene()) } }
