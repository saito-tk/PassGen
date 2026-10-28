import PhotosUI
import SwiftUI
import PassgenCore

struct SymbolEditor: View {
    @Bindable var model: GeneratorModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @State private var pasted = ""
    @State private var feedback: String?
    @State private var selection: PhotosPickerItem?
    @State private var analysis: ScreenshotAnalysis?
    @State private var candidate = ""
    @State private var loading = false
    @State private var importError: String?
    private let columns = [GridItem(.adaptive(minimum: 48), spacing: 10)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("サイトに合わせる、\n記号。").font(.system(.largeTitle, design: .serif, weight: .semibold))
                        Text("使える記号だけを、あなたのレシピに。")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("\(model.recipe.symbols.count) / 30種類").font(.headline)
                            Spacer()
                            Menu("選択") {
                                Button("すべて選択") { model.recipe.symbols = SymbolPolicy.shared.SUPPORTED }
                                Button("選択を解除") { model.recipe.symbols = "" }
                            }
                        }
                        LazyVGrid(columns: columns, spacing: 10) {
                            ForEach(Array(SymbolPolicy.shared.SUPPORTED), id: \.self) { symbol in
                                let selected = model.recipe.symbols.contains(symbol)
                                Button { toggle(symbol) } label: {
                                    Text(String(symbol)).font(.system(.title3, design: .monospaced, weight: .medium))
                                        .frame(maxWidth: .infinity, minHeight: 48)
                                        .background(selected ? Color.passgenAccent : Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                                        .foregroundStyle(selected ? Color.passgenOnAccent : Color.primary)
                                }.buttonStyle(.plain).accessibilityLabel("記号 \(String(symbol))")
                                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                            }
                        }
                        if model.recipe.symbols.isEmpty {
                            Text("記号を選択するか「記号を含める」をオフにしてください。")
                                .font(.caption).foregroundStyle(.red)
                        }
                    }.padding(20).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
                    VStack(alignment: .leading, spacing: 12) {
                        Label("記号一覧を貼り付け", systemImage: "doc.on.clipboard").font(.headline)
                        TextField("例: ! @ # $ %", text: $pasted, axis: .vertical)
                            .lineLimit(2...5).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .padding(16).background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                            .onChange(of: pasted) { _, _ in feedback = nil }
                        Text("入力内の対応記号をそのまま反映します。説明文の句読点も選択対象です。対応記号がない場合は選択を維持します。")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("記号に反映") {
                            let found = SymbolPolicy.shared.recognized(text: pasted)
                            model.recipe.symbols = SymbolPolicy.shared.applyPasted(text: pasted, current: model.recipe.symbols)
                            pasted = ""
                            feedback = found.isEmpty ? "対応する記号がないため、選択は変更していません。" : "\(found.count)種類を反映しました。"
                        }.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent).disabled(pasted.isEmpty)
                        if let feedback { Text(feedback).font(.caption).foregroundStyle(.secondary).accessibilityAddTraits(.updatesFrequently) }
                    }
                    Divider()
                    VStack(alignment: .leading, spacing: 14) {
                        Label("スクリーンショットから", systemImage: "viewfinder").font(.headline)
                        Text("選んだ画像だけを端末内で読み取ります。許可記号の案内部分を切り取ると読み取りやすくなります。")
                            .font(.subheadline).foregroundStyle(.secondary)
                        Text(ScreenshotImporter.modelStatus).font(.caption).foregroundStyle(.secondary)
                        PhotosPicker(selection: $selection, matching: .images, preferredItemEncoding: .current) {
                            Label("画像を選んで読み取る", systemImage: "photo")
                                .frame(maxWidth: .infinity).padding(.vertical, 8)
                        }.buttonStyle(.bordered)
                        if loading {
                            HStack { ProgressView(); Text("端末内で読み取り中…").font(.caption) }
                            Button("読み取りを中止") { selection = nil; loading = false; analysis = nil; candidate = "" }.font(.caption)
                        }
                        if let importError { Text(importError).font(.caption).foregroundStyle(.red) }
                        if let analysis {
                            Text(analysis.note).font(.subheadline)
                            if !analysis.evidence.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("読み取った根拠").font(.caption.weight(.semibold))
                                    Text(analysis.evidence).font(.caption).privacySensitive()
                                }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color.passgenAccent.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                            }
                            if !analysis.proposedSymbols.isEmpty {
                                Text("記号の候補 · \(candidate.count)種類").font(.subheadline.weight(.semibold))
                                LazyVGrid(columns: columns, spacing: 8) {
                                    ForEach(Array(analysis.proposedSymbols), id: \.self) { symbol in
                                        Button {
                                            if candidate.contains(symbol) { candidate.removeAll { $0 == symbol } }
                                            else { candidate = SymbolPolicy.shared.recognized(text: candidate + String(symbol)) }
                                        } label: {
                                            Text(String(symbol)).font(.system(.body, design: .monospaced)).frame(minWidth: 44, minHeight: 44)
                                                .background(candidate.contains(symbol) ? Color.passgenAccent.opacity(0.15) : Color.clear, in: RoundedRectangle(cornerRadius: 12))
                                        }.buttonStyle(.bordered).accessibilityAddTraits(candidate.contains(symbol) ? [.isSelected] : [])
                                    }
                                }
                                Button("確認した候補を適用") {
                                    guard !candidate.isEmpty else { return }
                                    model.recipe.symbols = candidate
                                    feedback = "確認した\(candidate.count)種類の記号を適用しました。"
                                    self.analysis = nil; selection = nil; candidate = ""
                                }.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent).disabled(candidate.isEmpty)
                            }
                            DisclosureGroup("文字認識の結果を確認") {
                                Text(analysis.recognizedText).font(.caption.monospaced()).frame(maxWidth: .infinity, alignment: .leading).privacySensitive().padding(.top, 8)
                            }.font(.subheadline)
                        }
                    }
                }.padding(24)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("使える記号").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完了") { dismiss() } } }
            .disabled(model.busy)
        }
        .task(id: selection) {
            guard let selection else { return }
            analysis = nil; candidate = ""; importError = nil; loading = true
            do {
                guard let data = try await selection.loadTransferable(type: Data.self) else { throw ScreenshotImportError.invalidImage }
                try Task.checkCancellation()
                let result = try await ScreenshotImporter.analyze(data)
                try Task.checkCancellation()
                analysis = result; candidate = result.proposedSymbols; loading = false
            } catch is CancellationError { }
            catch {
                guard !Task.isCancelled else { return }
                importError = (error as? ScreenshotImportError)?.errorDescription ?? "画像を読み込めませんでした。別の画像を選んでください。"
                loading = false
            }
        }
        .onChange(of: phase) { _, value in
            if value == .background { selection = nil; analysis = nil; candidate = ""; pasted = ""; loading = false; importError = nil; feedback = nil }
        }
        .onDisappear { selection = nil; analysis = nil; candidate = ""; pasted = "" }
    }

    private func toggle(_ symbol: Character) {
        if model.recipe.symbols.contains(symbol) { model.recipe.symbols.removeAll { $0 == symbol } }
        else { model.recipe.symbols = SymbolPolicy.shared.recognized(text: model.recipe.symbols + String(symbol)) }
    }
}
