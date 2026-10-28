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
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "使用可能 !", fullText: "使用可能 !\n#は禁止", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "Example !", fullText: "Example !", explicit: true).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "!", evidence: "Allowed !", fullText: "Allowed !", explicit: false).isEmpty)
        XCTAssertTrue(SymbolEvidenceValidator.candidate(symbols: "#", evidence: "Allowed !", fullText: "Allowed !", explicit: true).isEmpty)
    }
    func testInvalidImageFailsWithoutCallingModel() async {
        do { _ = try await ScreenshotImporter.analyze(Data([0, 1, 2])); XCTFail("Invalid image accepted") }
        catch { XCTAssertTrue(error is ScreenshotImportError) }
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
        let result = try await ScreenshotImporter.analyze(image.pngData()!)
        XCTAssertTrue(result.recognizedText.lowercased().contains("allowed"))
    }
}

@MainActor final class GeneratorLifecycleTests: XCTestCase {
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
}
