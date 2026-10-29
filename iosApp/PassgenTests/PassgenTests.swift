import XCTest
import UIKit
@testable import Passgen

final class SymbolEvidenceTests: XCTestCase {
    func testOnlyExplicitObservedAllowlistIsAccepted() {
        let evidence = "使用可能な記号 ! @ #"
        XCTAssertEqual(SymbolEvidenceValidator.candidate(symbols: "!@#?", evidence: evidence, fullText: evidence, explicit: true), "@!#")
    }
    func testForbiddenAndInventedEvidenceCannotBeApplied() {
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "使用可能 !", fullText: "使用禁止 !", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "使用可能な記号以外は禁止 !", fullText: "使用可能な記号以外は禁止 !", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "Example !", fullText: "Example !", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "Allowed !", fullText: "Allowed !", explicit: false).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "#", evidence: "Allowed !", fullText: "Allowed !", explicit: true).isEmpty)
    }
    func testInvalidImageFailsWithoutCallingModel() async {
        do { _ = try await ScreenshotImporter.analyze(Data([0, 1, 2])); XCTFail("Invalid image accepted") }
        catch { XCTAssertTrue(error is ScreenshotImportError) }
    }
    func testPermissionIsScopedAndVisualCandidatesIncludeBacktickWithoutInventingBackslash() {
        let expected = "!\"#$%&'()*+,-./:;<=>?@[]^_`{}|~"
        let candidate = SymbolEvidenceValidator.visualCandidate(symbols: expected,
            permission: "次の記号も任意で使用可能です。", explicit: true)
        XCTAssertEqual(Set(candidate), Set(expected)); XCTAssertEqual(candidate.count, 31)
        XCTAssertFalse(candidate.contains("\\"))
        XCTAssertTrue(SymbolEvidenceValidator.visualCandidate(symbols: "[]口", permission: "使用可能", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.visualCandidate(symbols: "[]", permission: "使用不可", explicit: true).isEmpty)
        XCTAssertEqual(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "使用可能 !", fullText: "使用可能 !\n一部ブラウザからはアクセスできません。", explicit: true), "!")
    }
    func testOriginalAllowedSymbolParagraphProducesEditableLocalOCRCandidates() async throws {
        // Only the supplied public symbol-rule paragraph, no account or surrounding form data.
        let data = Data(base64Encoded: Self.symbolRulePNG, options: .ignoreUnknownCharacters)!
        let result = try await ScreenshotImporter.analyze(data, useModel: false)
        XCTAssertNotNil(result.previewData)
        XCTAssertTrue(result.evidence.contains("使用可能"))
        let expected = Set("!\"#$%&'()*+,-./:;<=>?@[]^_`{}|~")
        XCTAssertTrue(Set(result.proposedSymbols).isSubset(of: expected))
        // Vision revisions differ. Verify both lines contribute, not perfect OCR without the image model.
        XCTAssertGreaterThanOrEqual(result.proposedSymbols.count, 20)
        XCTAssertTrue(result.recognizedText.contains("記号行の再読取:"))
    }

    func testRecipeNormalizationUsesSharedPolicy() {
        var recipe = Recipe(); recipe.lengthText = "９９９９９９"; recipe.countText = "1000"; recipe.normalize()
        XCTAssertEqual(recipe.length, 999999); XCTAssertEqual(recipe.count, 10)
    }
    @MainActor func testOCRReadsLocalScreenshotFixture() async throws {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1200, height: 300)).image { context in
            UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 1200, height: 300))
            ("Allowed password symbols: ! @ # $ %" as NSString).draw(at: CGPoint(x: 40, y: 100),
                withAttributes: [.font: UIFont.systemFont(ofSize: 42), .foregroundColor: UIColor.black])
        }
        let result = try await ScreenshotImporter.analyze(image.pngData()!, useModel: false)
        XCTAssertTrue(result.recognizedText.lowercased().contains("allowed"))
        XCTAssertEqual(Set(result.proposedSymbols), Set("!@#$%")); XCTAssertNotNil(result.previewData)
    }
    private static let symbolRulePNG = """
    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
    d/7j8N+vf/3rE5zgBCc60YlOdrKTnf/857/YxS528pOffLBhE5FK++Uvf9kXOOEJT3iKU5ziv//7v7/0pS/1U8Wc6UxnOtrRjjaY
    1I98+9vf/m//9m93u9vdjnSkI/VTty/mRS960de//vX73Oc+W68C2rD64x//eNzjHne//fYLWI5znOOc+tSnXvexfvvb3/7Vr36l
    SRCG89bbViUUAoVAIVAIFAKFwN6NAJ5XvxaBH/zgB7e+9a2PetSjTjzXs5zlLC95yUv+9Kc/tRnnhPHp613veic5yUkmCo+ks53t
    bE960pOwwDnFhsy1rnWtwWJvfvObE/jWt741mCryi1/84sxaKALBIJ/xjGfMzLKI2Ote97qjHOUwPfCf//mfNy4QmPe+972R4MTh
    oIMOamE5+tGPTrGhPMyv4u/+7u+itEMOOWR+rpIsBAqBQqAQKAQKgX0Vgc2Nr0lQ9qXAox/96P333x9PYuXN+2LTPdWpTtVaNJHR
    6173uhe+8IV/85vfpNh04G1ve9vf/u3fHnjggS9+8Yt//OMfEz7WsY6lhKtf/eqo/D3vec9b3vKW17jGNc55znOGPfjzn//87W9/
    +7/6q796zGMeo/NNF75jqXe5y11+9rOfqe7ud7/7j370o52p9zOf+cy1r33t//mf/1HdAx7wgPve974b1Ptf//Vf5zvf+R7ykId8
    //vfH8v++9///mMf+9hlLnOZxz72sWMyFV8IFAKFQCFQCBQChcAEAuUT8v/BedrTnsbVIa85flz1qlf967/+67Oe9azM2H/4wx/4
    JLz2ta991KMe9dOf/pTYRz7ykYMPPvjVr351S7szewaYS29xi1ug1BFz/OMf/yY3uckVr3hFjh8MpSmWgZ/85CdMpwy0X/nKVxDZ
    u971rm95y1ue//zn815ImenA3/zN35z3vOdNmYte9KLCxz72sTUjIwXYntvLlWGtOvTQQ0PsF7/4xR3veEfG+5W5ti5wrnOd6+lP
    fzpaH7A/+MEP5s7x0Ic+dK2SH/jAB37hC1+ILPxt8OyTnvSkUApYfvvb3/ISef/7328tggzPE4/+9Kc//VpVlHAhUAgUAoVAIVAI
    FAL/Z181yK97X29605uOfOQjR4c4xjGOgcyNlcC/Fu3OrvNP//RPY5LiP/e5z6HmIcy79373u9/Pf/7zCflMYkN9xCMewQk48vL2
    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
    L9a+E1yOUmV/AAAAAElFTkSuQmCC
    """

}

@MainActor final class CaptureRecoveryTests: XCTestCase {
    func testUIKitCaptureTraitAndRestorationPreserveUserVisibilityChoice() async throws {
        let protection = ScreenProtectionState()
        let view = CaptureStatusView(frame: .zero)
        view.onChange = protection.update
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController(); controller.view = view; window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKey() }
        window.traitOverrides.sceneCaptureState = .active
        try await Task.sleep(for: .milliseconds(20)); view.refresh()
        XCTAssertTrue(protection.isCaptured); XCTAssertFalse(protection.allowsSecret(revealed: true))
        window.traitOverrides.sceneCaptureState = .inactive
        try await Task.sleep(for: .milliseconds(20)); view.refresh()
        XCTAssertFalse(protection.isCaptured); XCTAssertTrue(protection.allowsSecret(revealed: true))
        XCTAssertFalse(protection.allowsSecret(revealed: false))
    }
}

@MainActor final class GeneratorLifecycleTests: XCTestCase {
    func testSettingsAndRevealPreferenceSurviveNewModelButSecretsDoNot() async throws {
        let suite = "passgen.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let first = GeneratorModel(preferences: defaults)
        first.recipe.lengthText = "４８"; first.recipe.countText = "2"
        first.recipe.uppercaseCharacters = "AZ"; first.recipe.similarExclusions = "O0"
        first.recipe.noAdjacent = true; first.recipe.words = 9; first.recipe.separator = "_"
        first.revealed = true
        first.security.allowUniversalClipboard = true; first.security.clipboardSeconds = 90
        first.security.protectCapture = false; first.security.clearClipboardOnBackground = false
        first.generateOrStop()
        for _ in 0..<100 {
            if !first.busy { break }
            try await Task.sleep(for: .milliseconds(5))
        }
        let restored = GeneratorModel(preferences: defaults)
        XCTAssertEqual(restored.recipe.length, 48); XCTAssertEqual(restored.recipe.count, 2)
        XCTAssertEqual(restored.recipe.uppercaseCharacters, "AZ")
        XCTAssertEqual(restored.recipe.similarExclusions, "O0"); XCTAssertTrue(restored.recipe.noAdjacent)
        XCTAssertEqual(restored.recipe.words, 9); XCTAssertEqual(restored.recipe.separator, "_")
        XCTAssertTrue(restored.revealed); XCTAssertTrue(restored.results.isEmpty)
        let data = defaults.data(forKey: "generation.preferences.v1")!
        let object = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        XCTAssertEqual(Set(object.keys), ["version", "recipe", "revealed", "security"])
        XCTAssertTrue(restored.security.allowUniversalClipboard); XCTAssertEqual(restored.security.clipboardSeconds, 90)
        XCTAssertFalse(restored.security.protectCapture); XCTAssertFalse(restored.security.clearClipboardOnBackground)
        restored.chooseMode(.passphrase); restored.chooseMode(.password)
        XCTAssertEqual(restored.recipe.length, 48)
    }

    func testRevealPreferenceSurvivesRegenerationAndBackground() async throws {
        let model = GeneratorModel(); model.revealed = true
        for _ in 0..<2 {
            model.generateOrStop()
            for _ in 0..<100 {
                if !model.busy { break }
                try await Task.sleep(for: .milliseconds(5))
            }
            XCTAssertTrue(model.revealed)
        }
        model.clear(); XCTAssertTrue(model.revealed)
        model.background(); XCTAssertTrue(model.revealed); XCTAssertTrue(model.results.isEmpty)
    }

    func testCopiedMarksArePerRowAndExpireWithResults() async throws {
        let model = GeneratorModel(); model.recipe.countText = "2"; model.generateOrStop()
        for _ in 0..<100 { if !model.busy { break }; try await Task.sleep(for: .milliseconds(5)) }
        model.copiedIDs.insert(0); model.copiedIDs.insert(1)
        XCTAssertEqual(model.copiedIDs, [0, 1])
        model.generateOrStop(); XCTAssertTrue(model.copiedIDs.isEmpty)
        model.background(); XCTAssertTrue(model.copiedIDs.isEmpty)
    }

    func testClipboardExpiresAtConfiguredTime() async throws {
        let clipboard = SecureClipboard()
        var settings = SecuritySettings(); settings.clipboardSeconds = 5
        clipboard.copy("fake-test-only", settings: settings)
        XCTAssertTrue(UIPasteboard.general.hasStrings)
        try await Task.sleep(for: .seconds(6))
        XCTAssertFalse(UIPasteboard.general.hasStrings)
    }

    func testInvalidPreferencesFallBackWithoutCrashing() {
        let suite = "passgen.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set(Data("invalid".utf8), forKey: "generation.preferences.v1")
        XCTAssertEqual(GeneratorModel(preferences: defaults).recipe.length, 24)
        defaults.set(Data("{\"version\":999}".utf8), forKey: "generation.preferences.v1")
        XCTAssertFalse(GeneratorModel(preferences: defaults).revealed)
    }
    func testNoGenerationOnLaunchAndExplicitGenerateWorks() async throws {
        let model = GeneratorModel()
        XCTAssertTrue(model.results.isEmpty)
        model.generateOrStop()
        for _ in 0..<100 {
            if !model.busy { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertEqual(model.results.count, 1)
        XCTAssertEqual(model.results.first?.value.count, 24)
        XCTAssertFalse(model.revealed)
    }
    func testBackgroundCancelsAndLateResultsNeverReappear() async throws {
        let model = GeneratorModel()
        model.recipe.lengthText = "999999"; model.recipe.countText = "10"
        model.generateOrStop(); model.background()
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertTrue(model.results.isEmpty); XCTAssertFalse(model.busy); XCTAssertFalse(model.revealed)
    }
    func testClipboardDoesNotEraseReplacement() {
        let clipboard = SecureClipboard()
        clipboard.copy("test-only-fake-value")
        UIPasteboard.general.string = "replacement"
        clipboard.clearOwned()
        XCTAssertEqual(UIPasteboard.general.string, "replacement")
        UIPasteboard.general.items = []
    }

    func testIndividualSelectionReachesSharedGenerator() async throws {
        let model = GeneratorModel()
        model.recipe.uppercaseCharacters = "AZ"
        model.recipe.lowercase = false; model.recipe.digits = false; model.recipe.symbolsEnabled = false
        model.generateOrStop()
        for _ in 0..<100 {
            if !model.busy { break }
            try await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertEqual(model.results.count, 1)
        XCTAssertTrue(model.results.first?.value.allSatisfy { "AZ".contains($0) } == true)
    }

    func testStopPreservesCompletedResults() async throws {
        let model = GeneratorModel()
        model.recipe.lengthText = "999999"; model.recipe.countText = "10"
        model.generateOrStop()
        for _ in 0..<200 {
            if !model.results.isEmpty { break }
            try await Task.sleep(for: .milliseconds(2))
        }
        XCTAssertFalse(model.results.isEmpty)
        XCTAssertTrue(model.busy)
        model.generateOrStop()
        for _ in 0..<100 {
            if !model.busy { break }
            try await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertFalse(model.busy)
        XCTAssertFalse(model.results.isEmpty)
        XCTAssertLessThan(model.results.count, model.goal)
    }

    func testDigitsOnlyPresetUsesPasswordMode() async throws {
        let model = GeneratorModel(); model.useDigitsOnly()
        XCTAssertEqual(model.recipe.mode, .password)
        XCTAssertTrue(model.isDigitsOnly); XCTAssertFalse(model.recipe.excludeSimilar)
        XCTAssertEqual(model.recipe.digitCharacters, "0123456789")
        model.generateOrStop()
        for _ in 0..<100 {
            if !model.busy { break }
            try await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertEqual(model.results.count, 1)
        XCTAssertTrue(model.results.first?.value.allSatisfy { $0.isASCII && $0.isNumber } == true)
    }

    func testPassphraseSeparatorReachesCore() async throws {
        let model = GeneratorModel(); model.recipe.mode = .passphrase; model.recipe.separator = "_"
        model.generateOrStop()
        for _ in 0..<100 {
            if !model.busy { break }
            try await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertEqual(model.results.count, 1)
        XCTAssertEqual(model.results.first?.value.split(separator: "_").count, 7)
    }
}
