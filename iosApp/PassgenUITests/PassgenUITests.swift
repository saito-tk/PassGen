import XCTest

final class PassgenUITests: XCTestCase {
    @MainActor private func launchApp() -> XCUIApplication {
        let app = XCUIApplication(); app.launchArguments = ["--uitest-ephemeral-settings"]; app.launch(); return app
    }
    @MainActor func testGenerateAndBackgroundClear() {
        let app = launchApp()
        XCTAssertFalse(app.staticTexts["secret"].exists)
        app.buttons["generate"].tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertFalse(app.staticTexts["secret"].exists)
    }
    @MainActor func testNativeRecipeEditorAndSymbols() {
        let app = launchApp()
        XCTAssertTrue(app.textFields["length"].waitForExistence(timeout: 3))
        app.segmentedControls.buttons["記号"].tap()
        app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        XCTAssertTrue(app.navigationBars["使える記号"].waitForExistence(timeout: 3))
    }
    @MainActor func testInitialLayoutForVisualReview() {
        let app = launchApp()
        let field = app.textFields["length"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertLessThanOrEqual(field.frame.maxX, app.frame.maxX)
        XCTAssertTrue(app.buttons["character-A"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Initial layout (no generated password)"; attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor func testPassphraseSettings() {
        let app = launchApp()
        app.segmentedControls.buttons["パスフレーズ"].tap()
        app.segmentedControls.buttons["_"].tap()
        app.buttons["generate"].tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
    }
    @MainActor func testRulesAndCategoryOverviewFitSmallScreen() {
        let app = launchApp(); app.swipeUp()
        for id in ["category-upper", "category-lower", "category-digits", "category-symbols"] { XCTAssertTrue(app.buttons[id].exists) }
        app.buttons["generation-rules"].tap(); app.swipeUp()
        let layout = XCTAttachment(screenshot: app.screenshot()); layout.name = "Expanded rules"; layout.lifetime = .keepAlways; add(layout)
        for id in ["rule-requireEach", "rule-excludeSimilar", "rule-noAdjacent"] {
            let control = app.switches[id]
            XCTAssertTrue(control.exists)
            XCTAssertLessThanOrEqual(control.frame.maxX, app.frame.maxX - 8)
            XCTAssertGreaterThanOrEqual(control.frame.width, 45)
        }
    }

    @MainActor func testCopyMarksRemainOnBothRowsAndGuideOpens() {
        let app = launchApp()
        let count = app.textFields["count"]; count.tap(); count.typeText(XCUIKeyboardKey.delete.rawValue + "2")
        app.buttons["generate"].tap()
        let first = app.buttons["copy-0"]; XCTAssertTrue(first.waitForExistence(timeout: 5)); first.tap()
        let second = app.buttons["copy-1"]; second.tap()
        XCTAssertEqual(first.label, "コピー済み。もう一度コピー"); XCTAssertEqual(second.label, "コピー済み。もう一度コピー")
        let result = XCTAttachment(screenshot: app.screenshot()); result.name = "Copied result marks"; result.lifetime = .keepAlways; add(result)
        app.buttons["閉じる"].tap()
        app.buttons["安全性とプライバシー"].tap()
        XCTAssertTrue(app.switches["allow-universal-clipboard"].exists)
        app.buttons["security-guide"].tap()
        XCTAssertTrue(app.navigationBars["アカウントを守る"].waitForExistence(timeout: 3))
    }

}
