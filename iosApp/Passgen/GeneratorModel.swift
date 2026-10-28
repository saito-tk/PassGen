import Foundation
import Observation
import PassgenCore

enum RecipeMode: String, CaseIterable, Identifiable, Sendable {
    case password, passphrase
    var id: Self { self }
    var title: String { switch self { case .password: "パスワード"; case .passphrase: "パスフレーズ" } }
    var core: GenerationMode { switch self { case .password: .password; case .passphrase: .passphrase } }
}

struct Recipe: Sendable {
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

@MainActor @Observable final class GeneratorModel {
    var recipe = Recipe()
    private(set) var results: [SecretItem] = []
    private(set) var state: GenerationState = .idle
    private(set) var goal = 1
    var revealed = false
    var message: String?
    var copiedID: Int?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var cancellation = GenerationCancellation()
    @ObservationIgnored private var epoch = 0
    var busy: Bool { state != .idle }
    var isDigitsOnly: Bool { recipe.mode == .password && recipe.digits && !recipe.uppercase && !recipe.lowercase && !recipe.symbolsEnabled }
    var estimate: Double? { try? PasswordEngine().estimatedEntropy(request: recipe.request()) }

    func useDigitsOnly() {
        guard !busy else { return }
        recipe.mode = .password
        recipe.uppercase = false; recipe.lowercase = false; recipe.digits = true; recipe.symbolsEnabled = false
        recipe.digitCharacters = "0123456789"; recipe.excludeSimilar = false
        recipe.normalize()
    }

    func chooseMode(_ mode: RecipeMode) {
        guard !busy else { return }
        recipe.mode = mode
        if mode == .password { recipe.lengthText = "24" }
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
        let token = GenerationCancellation()
        cancellation = token
        epoch += 1
        let run = epoch
        goal = snapshot.count
        results = []; revealed = false; copiedID = nil; message = nil; state = .running
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
        results = []; revealed = false; copiedID = nil
    }
    func background() {
        epoch += 1; cancellation.cancel(); task?.cancel(); task = nil
        results = []; revealed = false; copiedID = nil; message = nil; state = .idle
    }
}
