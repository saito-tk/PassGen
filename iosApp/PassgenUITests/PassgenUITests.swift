import XCTest

final class PassgenUITests: XCTestCase {
    @MainActor func testGenerateAndBackgroundClear() {
        let app = XCUIApplication(); app.launch()
        XCTAssertFalse(app.staticTexts["secret"].exists)
        app.buttons["generate"].tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertFalse(app.staticTexts["secret"].exists)
    }
    @MainActor func testNativeRecipeEditorAndSymbols() {
        let app = XCUIApplication(); app.launch()
        app.buttons["カスタマイズ"].tap()
        XCTAssertTrue(app.textFields["length"].waitForExistence(timeout: 3))
    }
    @MainActor func testInitialLayoutForVisualReview() {
        let app = XCUIApplication(); app.launch()
        let card = app.otherElements["secret-card"]
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        XCTAssertLessThanOrEqual(card.frame.maxX, app.frame.maxX)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Initial layout (no generated password)"; attachment.lifetime = .keepAlways
        add(attachment)
    }
}
