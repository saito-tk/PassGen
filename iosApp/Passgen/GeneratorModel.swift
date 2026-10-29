import Foundation
import Observation
import PassgenCore

enum RecipeMode: String, CaseIterable, Identifiable, Sendable, Codable {
    case password, passphrase
    var id: Self { self }
    var title: String { switch self { case .password: "パスワード"; case .passphrase: "パスフレーズ" } }
    var core: GenerationMode { switch self { case .password: .password; case .passphrase: .passphrase } }
}

struct Recipe: Sendable, Codable, Equatable {
    var mode: RecipeMode = .password
    var lengthText = "24"
    var countText = "1"
    var words = 7
    var separator = " "
    var uppercase = true
    var lowercase = true
    var digits = true
    var symbolsEnabled = true
    var symbols = "!@#$%&*-_+=?"
    var excludeSimilar = true
    var requireEach = true
    var noAdjacent = false
    var uppercaseCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    var lowercaseCharacters = "abcdefghijklmnopqrstuvwxyz"
    var digitCharacters = "0123456789"
    var similarExclusions = "Oo0Il1"

    var length: Int { Int(InputPolicy.shared.normalizedInteger(text: lengthText, fallback: 24, minimum: 4, maximum: 999_999)) }
    var maximumCount: Int { mode == .passphrase ? 1000 : Int(InputPolicy.shared.maximumCount(length: Int32(length))) }
    var count: Int { Int(InputPolicy.shared.normalizedInteger(text: countText, fallback: 1, minimum: 1, maximum: Int32(maximumCount))) }
    mutating func normalize() { lengthText = String(length); countText = String(count) }
    func sanitized() -> Recipe {
        var value = self
        value.normalize()
        value.words = min(12, max(6, words))
        if ![" ", "-", "_", "."].contains(separator) { value.separator = " " }
        value.uppercaseCharacters = CharacterPolicy.shared.canonical(selected: uppercaseCharacters, alphabet: CharacterPolicy.shared.UPPERCASE)
        value.lowercaseCharacters = CharacterPolicy.shared.canonical(selected: lowercaseCharacters, alphabet: CharacterPolicy.shared.LOWERCASE)
        value.digitCharacters = CharacterPolicy.shared.canonical(selected: digitCharacters, alphabet: CharacterPolicy.shared.DIGITS)
        value.symbols = SymbolPolicy.shared.recognized(text: symbols)
        value.similarExclusions = CharacterPolicy.shared.canonical(selected: similarExclusions, alphabet: CharacterPolicy.shared.SIMILAR)
        return value
    }
    func request() -> GenerationRequest {
        GenerationRequest(mode: mode.core, length: Int32(length), wordCount: Int32(words), uppercase: uppercase,
                          lowercase: lowercase, digits: digits, symbolsEnabled: symbolsEnabled, symbols: symbols,
                          excludeSimilar: excludeSimilar, requireEach: requireEach, noAdjacent: noAdjacent, separator: separator,
                          uppercaseCharacters: uppercaseCharacters, lowercaseCharacters: lowercaseCharacters,
                          digitCharacters: digitCharacters, similarExclusions: similarExclusions)
    }
}

struct SecretItem: Identifiable, Sendable, CustomStringConvertible {
    let id: Int
    let value: String
    let bits: Double
    let length: Int
    var description: String { "SecretItem([REDACTED])" }
}

final class GenerationCancellation: NSObject, CancellationProbe, @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false
    func cancel() { lock.withLock { cancelled = true } }
    func isCancelled() -> Bool { lock.withLock { cancelled } }
}

enum GenerationState { case idle, running, cancelling }

struct SecuritySettings: Codable, Sendable {
    var allowUniversalClipboard = false
    var autoClearClipboard = true
    var clipboardSeconds = 30
    var protectCapture = true
    var protectAppSwitcher = true

    init() {}
    private enum CodingKeys: String, CodingKey {
        case allowUniversalClipboard, autoClearClipboard, clipboardSeconds, protectCapture, protectAppSwitcher
    }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        allowUniversalClipboard = try values.decodeIfPresent(Bool.self, forKey: .allowUniversalClipboard) ?? false
        autoClearClipboard = try values.decodeIfPresent(Bool.self, forKey: .autoClearClipboard) ?? true
        clipboardSeconds = try values.decodeIfPresent(Int.self, forKey: .clipboardSeconds) ?? 30
        protectCapture = try values.decodeIfPresent(Bool.self, forKey: .protectCapture) ?? true
        // Older preferences have no app-switcher option; preserve their protection.
        protectAppSwitcher = try values.decodeIfPresent(Bool.self, forKey: .protectAppSwitcher) ?? true
    }
    var sanitized: SecuritySettings {
        var value = self; value.clipboardSeconds = min(600, max(5, clipboardSeconds)); return value
    }
    var clipboardSummary: String { autoClearClipboard ? "コピーは\(clipboardSeconds)秒後に期限切れ" : "コピーの時間による自動消去はオフ" }
}

struct GeneratorPreset: Identifiable, Codable, Equatable {
    var id: UUID
    var name: String
    var recipe: Recipe
}

enum PresetError: LocalizedError {
    case name, duplicate, full, unavailable, busy, missing
    var errorDescription: String? {
        switch self {
        case .name: "名前は1〜40文字で入力してください。改行や制御文字は使用できません。"
        case .duplicate: "同じ名前のプリセットがあります。別の名前を入力してください。"
        case .full: "保存できるプリセットは50件までです。"
        case .unavailable: "保存済みプリセットを読み込めませんでした。元のデータは変更していません。"
        case .busy: "生成が終わってから操作してください。"
        case .missing: "プリセットが見つかりません。"
        }
    }
}

extension Recipe {
    var presetSummary: String {
        mode == .password ? "パスワード · \(length)文字 · \(count)件" :
            "パスフレーズ · \(words)単語 · \(count)件 · \(separator == " " ? "スペース区切り" : separator + "区切り")"
    }
}

@MainActor @Observable final class GeneratorModel {
    var recipe = Recipe() { didSet { persistPreferences() } }
    private(set) var results: [SecretItem] = []
    private var generatedRecipe: Recipe?
    private(set) var presets: [GeneratorPreset] = []
    private(set) var presetStorageIssue: String?
    private struct StoredPresets: Codable { let version: Int; let presets: [GeneratorPreset] }
    private static let presetsKey = "generation.presets.v1"
    private(set) var state: GenerationState = .idle
    private(set) var goal = 1
    var revealed = false { didSet { persistPreferences() } }
    var message: String?
    var copiedIDs: Set<Int> = []
    var security = SecuritySettings() { didSet { persistPreferences() } }
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var cancellation = GenerationCancellation()
    @ObservationIgnored private var epoch = 0
    @ObservationIgnored private let preferences: UserDefaults?
    private struct StoredPreferences: Codable {
        let version: Int
        let recipe: Recipe
        let revealed: Bool
        var security: SecuritySettings? = nil
    }
    init(preferences: UserDefaults? = nil) {
        self.preferences = preferences
        if let data = preferences?.data(forKey: "generation.preferences.v1"), data.count <= 16_384,
           let stored = try? JSONDecoder().decode(StoredPreferences.self, from: data), stored.version == 1 {
            recipe = stored.recipe.sanitized()
            revealed = stored.revealed
            security = stored.security?.sanitized ?? SecuritySettings()
        }
        loadPresets()
    }
    static func applicationModel() -> GeneratorModel {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--uitest-ephemeral-settings") { return GeneratorModel() }
        #endif
        return GeneratorModel(preferences: .standard)
    }
    private func persistPreferences() {
        guard let preferences,
              let data = try? JSONEncoder().encode(StoredPreferences(version: 1, recipe: recipe.sanitized(), revealed: revealed, security: security.sanitized)) else { return }
        preferences.set(data, forKey: "generation.preferences.v1")
    }
    private static func normalizedPresetName(_ value: String) throws -> String {
        let name = value.trimmingCharacters(in: .whitespacesAndNewlines).precomposedStringWithCanonicalMapping
        guard !name.isEmpty, name.utf16.count <= 40,
              !name.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { throw PresetError.name }
        return name
    }
    private static func nameKey(_ name: String) -> String { name.lowercased(with: Locale(identifier: "en_US_POSIX")) }
    func presetNameError(_ value: String, excluding id: UUID? = nil) -> String? {
        do {
            let name = try Self.normalizedPresetName(value)
            if presets.contains(where: { $0.id != id && Self.nameKey($0.name) == Self.nameKey(name) }) { return PresetError.duplicate.localizedDescription }
            return nil
        } catch { return error.localizedDescription }
    }
    private func loadPresets() {
        guard let storedValue = preferences?.object(forKey: Self.presetsKey) else { return }
        do {
            guard let data = storedValue as? Data, data.count <= 131_072 else { throw PresetError.unavailable }
            let stored = try JSONDecoder().decode(StoredPresets.self, from: data)
            guard stored.version == 1, stored.presets.count <= 50,
                  Set(stored.presets.map(\.id)).count == stored.presets.count else { throw PresetError.unavailable }
            var names: Set<String> = []
            var restored: [GeneratorPreset] = []
            for var preset in stored.presets {
                preset.name = try Self.normalizedPresetName(preset.name)
                guard names.insert(Self.nameKey(preset.name)).inserted else { throw PresetError.unavailable }
                preset.recipe = preset.recipe.sanitized(); restored.append(preset)
            }
            presets = restored
        } catch { presetStorageIssue = PresetError.unavailable.localizedDescription }
    }
    private func commitPresets(_ next: [GeneratorPreset]) throws {
        guard !busy else { throw PresetError.busy }
        guard presetStorageIssue == nil else { throw PresetError.unavailable }
        let data = try JSONEncoder().encode(StoredPresets(version: 1, presets: next))
        guard next.count <= 50, data.count <= 131_072 else { throw PresetError.full }
        preferences?.set(data, forKey: Self.presetsKey)
        presets = next
    }
    func savePreset(name value: String) throws {
        let name = try Self.normalizedPresetName(value)
        guard !presets.contains(where: { Self.nameKey($0.name) == Self.nameKey(name) }) else { throw PresetError.duplicate }
        try commitPresets([GeneratorPreset(id: UUID(), name: name, recipe: recipe.sanitized())] + presets)
    }
    func applyPreset(_ id: UUID) throws {
        guard !busy else { throw PresetError.busy }
        guard let preset = presets.first(where: { $0.id == id }) else { throw PresetError.missing }
        recipe = preset.recipe.sanitized()
    }
    func renamePreset(_ id: UUID, to value: String) throws {
        let name = try Self.normalizedPresetName(value)
        guard !presets.contains(where: { $0.id != id && Self.nameKey($0.name) == Self.nameKey(name) }) else { throw PresetError.duplicate }
        var next = presets
        guard let index = next.firstIndex(where: { $0.id == id }) else { throw PresetError.missing }
        next[index].name = name; try commitPresets(next)
    }
    func replacePreset(_ id: UUID) throws {
        var next = presets
        guard let index = next.firstIndex(where: { $0.id == id }) else { throw PresetError.missing }
        next[index].recipe = recipe.sanitized(); try commitPresets(next)
    }
    func deletePreset(_ id: UUID) throws {
        guard presets.contains(where: { $0.id == id }) else { throw PresetError.missing }
        try commitPresets(presets.filter { $0.id != id })
    }

    var busy: Bool { state != .idle }
    var generationActionTitle: String {
        if state == .cancelling { return "停止中…" }
        if busy { return "停止" }
        return !results.isEmpty && generatedRecipe == recipe.sanitized() ? "再生成" : "生成"
    }
    var isDigitsOnly: Bool { recipe.mode == .password && recipe.digits && !recipe.uppercase && !recipe.lowercase && !recipe.symbolsEnabled }
    var estimate: Double? { try? PasswordEngine().estimatedEntropy(request: recipe.request()) }

    func chooseMode(_ mode: RecipeMode) {
        guard !busy else { return }
        recipe.mode = mode
        recipe.normalize()
    }

    func generateOrStop() {
        guard state != .cancelling else { return }
        if state == .running {
            state = .cancelling; cancellation.cancel(); task?.cancel()
            return
        }
        recipe.normalize()
        let snapshot = recipe
        generatedRecipe = snapshot.sanitized()
        let token = GenerationCancellation()
        cancellation = token
        epoch += 1
        let run = epoch
        goal = snapshot.count
        results = []; copiedIDs = []; message = nil; state = .running
        task = Task.detached(priority: .userInitiated) { [weak self] in
            do {
                let engine = PasswordEngine()
                let request = snapshot.request()
                for index in 0..<snapshot.count {
                    try Task.checkCancellation()
                    let generated = try engine.generate(request: request, cancellation: token)
                    let item = SecretItem(id: index, value: generated.value, bits: generated.entropyBits, length: Int(generated.length))
                    guard !token.isCancelled() else { break }
                    await self?.append(item, run: run)
                    await Task.yield()
                }
            } catch {
                if !token.isCancelled() { await self?.failed(run: run) }
            }
            await self?.finished(run: run)
        }
    }

    private func append(_ item: SecretItem, run: Int) {
        guard run == epoch, state == .running else { return }
        results.append(item)
    }
    private func failed(run: Int) {
        guard run == epoch else { return }
        message = "安全に生成できませんでした。文字種・記号の選択と条件を確認してください。"
    }
    private func finished(run: Int) { if run == epoch { state = .idle } }

    func clear() {
        guard !busy else { return }
        results = []; copiedIDs = []; generatedRecipe = nil
    }
    func background() {
        epoch += 1; cancellation.cancel(); task?.cancel(); task = nil
        // Keep completed rows and copy marks in memory; invalidate late work.
        message = nil; state = .idle
    }
}
