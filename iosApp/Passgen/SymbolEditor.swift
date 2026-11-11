import PhotosUI
import SwiftUI
import PassgenCore

struct SymbolEditor: View {
    @Bindable var model: GeneratorModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @Environment(ScreenProtectionState.self) private var protection
    @State private var pasted = ""
    @FocusState private var pasteFocused: Bool
    @State private var feedback: String?
    @State private var selection: PhotosPickerItem?
    @State private var session: ScreenshotImportSession = {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--uitest-screenshot-import") {
            return ScreenshotImportSession { try await ScreenshotImporter.analyze($0, useModel: false) }
        }
        #endif
        return ScreenshotImportSession()
    }()
    private let columns = [GridItem(.adaptive(minimum: 44), spacing: 6)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("\(model.effectiveSymbols.count) / \(SymbolPolicy.shared.SUPPORTED.count)種類").font(.headline)
                            Spacer()
                            Menu("選択") {
                                Button("すべて選択") { model.editSymbols(SymbolPolicy.shared.SUPPORTED) }
                                Button("選択を解除") { model.editSymbols("") }
                            }
                        }
                        LazyVGrid(columns: columns, spacing: 10) {
                            ForEach(Array(SymbolPolicy.shared.SUPPORTED), id: \.self) { symbol in
                                let blocked = model.blockedSymbols.contains(symbol)
                                let selected = model.effectiveSymbols.contains(symbol)
                                Button { toggle(symbol) } label: {
                                    Text(String(symbol)).font(.system(.title3, design: .monospaced, weight: .medium)).strikethrough(blocked)
                                        .frame(maxWidth: .infinity, minHeight: 48)
                                        .background(blocked ? Color(uiColor: .systemGray5) : selected ? Color.passgenAccent : Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                                        .foregroundStyle(blocked ? Color.secondary : selected ? Color.passgenOnAccent : Color.primary)
                                        .modifier(BlockedCharacterAppearance(blocked: blocked, cornerRadius: 14))
                                }.buttonStyle(.plain).disabled(blocked)
                                    .accessibilityValue(blocked ? "アプリの設定で使用しない記号" : selected ? "選択" : "未選択")
                                    .accessibilityLabel("記号 \(String(symbol))")
                                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                            }
                        }
                        if !model.blockedSymbols.isEmpty { Label("ロック付きの記号は、「アプリで使う記号」で使用不可に設定されています。", systemImage: "lock.fill").font(.caption).foregroundStyle(.secondary) }
                    }.padding(12).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                    VStack(alignment: .leading, spacing: 12) {
                        Label("記号一覧を貼り付け", systemImage: "doc.on.clipboard").font(.headline)
                        TextField("例: ! @ # $ %", text: $pasted)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .focused($pasteFocused).submitLabel(.done)
                            .onSubmit { pasteFocused = false }
                            .accessibilityIdentifier("symbol-paste-input")
                            .padding(12).background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(pasteFocused ? Color.passgenAccent : Color.secondary, lineWidth: pasteFocused ? 2 : 1)
                            }
                            .onChange(of: pasted) { _, _ in feedback = nil }
                            .redacted(reason: (protection.isCaptured && model.security.protectCapture) ? .privacy : []).disabled((protection.isCaptured && model.security.protectCapture))
                        Text("入力内の対応記号をそのまま反映します。説明文の句読点も選択対象です。対応記号がない場合は選択を維持します。")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("記号に反映") {
                            pasteFocused = false
                            let found = SymbolPolicy.shared.recognized(text: pasted)
                            model.editSymbols(SymbolPolicy.shared.applyPasted(text: pasted, current: model.recipe.symbols))
                            pasted = ""
                            feedback = found.isEmpty ? "対応する記号がないため、選択は変更していません。" : "\(SymbolPolicy.shared.effective(selected: found, blocked: model.blockedSymbols).count)種類を反映しました。使用しない記号は除外しています。"
                        }.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent).disabled(pasted.isEmpty)
                            .accessibilityIdentifier("apply-pasted-symbols")
                        if let feedback { Text(feedback).font(.caption).foregroundStyle(.secondary).accessibilityAddTraits(.updatesFrequently) }
                    }
                    Divider()
                    VStack(alignment: .leading, spacing: 14) {
                        Label("スクリーンショットから", systemImage: "viewfinder").font(.headline).accessibilityIdentifier("screenshot-import-section")
                        Text("文字種の表や記号の並びを端末内で読み取ります。候補は確認後に適用します。読み取り内容はこの画面を閉じるまで保持します。")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(ScreenshotImporter.modelStatus).font(.caption).foregroundStyle(.secondary)
                        PhotosPicker(selection: $selection, matching: .images, preferredItemEncoding: .current) {
                            Label("画像を選んで読み取る", systemImage: "photo")
                                .frame(maxWidth: .infinity).padding(.vertical, 8)
                        }.buttonStyle(.bordered).disabled((protection.isCaptured && model.security.protectCapture))
                        if session.suspended { Text("復帰後に読み取りを再開します。").font(.caption) }
                        if session.loading {
                            HStack { ProgressView(); Text("端末内で読み取り中…").font(.caption) }
                            Button("読み取りを中止") { selection = nil; session.clear() }.font(.caption)
                        }
                        if let importError = session.errorMessage { Text(importError).font(.caption).foregroundStyle(.red) }
                        if (protection.isCaptured && model.security.protectCapture) {
                            Text("画面収録中は読み取り内容を非表示にしています。").font(.caption).foregroundStyle(.secondary)
                        } else if let analysis = session.analysis {
                            Text(analysis.note).font(.subheadline).accessibilityIdentifier("screenshot-import-status")
                            if !analysis.evidence.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("読み取った根拠").font(.caption.weight(.semibold))
                                    Text(analysis.evidence).font(.caption).privacySensitive()
                                }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color.passgenAccent.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                            }
                            if let data = analysis.previewData, let image = UIImage(data: data) {
                                Image(uiImage: image).resizable().scaledToFit()
                                    .padding(8).background(.white, in: RoundedRectangle(cornerRadius: 12))
                                    .accessibilityLabel("元画像の記号部分。下の候補と照合してください。")
                                    .accessibilityIdentifier("screenshot-import-preview")
                                    .privacySensitive()
                            }
                            if analysis.previewData != nil {
                                Text("画像と照合 · \(SymbolPolicy.shared.effective(selected: session.candidate, blocked: model.blockedSymbols).count)種類を選択中").font(.subheadline.weight(.semibold))
                                Text("読み落とした記号は追加し、余分な記号は解除してください。").font(.caption).foregroundStyle(.secondary)
                                LazyVGrid(columns: columns, spacing: 8) {
                                    ForEach(Array(SymbolPolicy.shared.SUPPORTED), id: \.self) { symbol in
                                        let blocked = model.blockedSymbols.contains(symbol)
                                        let selected = session.candidate.contains(symbol) && !blocked
                                        Button {
                                            if session.candidate.contains(symbol) { session.candidate.removeAll { $0 == symbol } }
                                            else { session.candidate = SymbolPolicy.shared.recognized(text: session.candidate + String(symbol)) }
                                        } label: {
                                            Text(String(symbol)).font(.system(.title3, design: .monospaced, weight: .medium)).strikethrough(blocked)
                                                .frame(maxWidth: .infinity, minHeight: 44)
                                                .foregroundStyle(blocked ? Color.secondary : selected ? Color.passgenOnAccent : Color.primary)
                                                .background(blocked ? Color(uiColor: .systemGray5) : selected ? Color.passgenAccent : Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                                                .modifier(BlockedCharacterAppearance(blocked: blocked, cornerRadius: 12))
                                        }.buttonStyle(.plain).disabled(blocked)
                                            .accessibilityValue(blocked ? "アプリの設定で使用しない記号" : selected ? "選択" : "未選択")
                                            .accessibilityLabel("候補 \(String(symbol))")
                                            .accessibilityIdentifier("candidate-\(String(symbol))")
                                            .accessibilityAddTraits(selected ? [.isSelected] : [])
                                    }
                                }
                                Button("確認した候補を適用") {
                                    guard !session.candidate.isEmpty else { return }
                                    model.editSymbols(session.candidate)
                                    model.recipe.symbolsEnabled = true
                                    feedback = "確認した\(SymbolPolicy.shared.effective(selected: session.candidate, blocked: model.blockedSymbols).count)種類の記号を適用しました。"
                                    session.clear(); selection = nil
                                }.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent).disabled(session.candidate.isEmpty)
                                    .accessibilityIdentifier("apply-image-symbols")
                            }
                            DisclosureGroup("読み取りの詳細") {
                                Text(analysis.recognizedText).font(.caption.monospaced()).frame(maxWidth: .infinity, alignment: .leading).privacySensitive().padding(.top, 8)
                            }.font(.subheadline)
                        }
                    }
                }.padding(16)
            }
            .accessibilityIdentifier("symbol-editor-scroll")
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("使える記号").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完了", systemImage: "checkmark") { dismiss() } } }
            .disabled(model.busy)
        }
        .onAppear {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--uitest-screenshot-import"), session.analysis == nil, !session.loading {
                session.start {
                    let inline = ProcessInfo.processInfo.arguments.contains("--uitest-inline-permission")
                    let fixtureWidth: CGFloat = inline ? 2000 : 1400
                    let image = UIGraphicsImageRenderer(size: CGSize(width: fixtureWidth, height: 340)).image { context in
                        UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: fixtureWidth, height: 340))
                        let heading = inline ? "次の記号も任意で使用可能です。" :
                            ProcessInfo.processInfo.arguments.contains("--uitest-symbol-list") ? "記号の一覧" : "Allowed password symbols:"
                        let headingAttributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 40), .foregroundColor: UIColor.black]
                        (heading as NSString).draw(at: CGPoint(x: 30, y: 40), withAttributes: headingAttributes)
                        let upperX = inline ? 38 + (heading as NSString).size(withAttributes: headingAttributes).width : 30
                        (#"! " # $ % & ' ( ) * + , - . / : ;"# as NSString).draw(at: CGPoint(x: upperX, y: inline ? 40 : 130), withAttributes: [.font: UIFont.monospacedSystemFont(ofSize: 38, weight: .regular), .foregroundColor: UIColor.black])
                        (#"< = > ? @ [ ] ^ _ ` { } | ~"# as NSString).draw(at: CGPoint(x: 30, y: inline ? 120 : 210), withAttributes: [.font: UIFont.monospacedSystemFont(ofSize: 38, weight: .regular), .foregroundColor: UIColor.black])
                    }
                    return image.pngData()
                }
            }
            #endif
        }
        .onChange(of: selection) { _, item in
            guard let item else { return }
            session.start { try await item.loadTransferable(type: Data.self) }
        }
        .onChange(of: phase) { _, value in
            if value == .background { session.suspend() }
            else if value == .active { session.resume() }
        }
        .onDisappear { selection = nil; session.clear(); pasted = "" }
    }

    private func toggle(_ symbol: Character) {
        model.editSymbols(model.recipe.symbols.contains(symbol) ? String(model.recipe.symbols.filter { $0 != symbol }) : model.recipe.symbols + String(symbol))
    }
}
