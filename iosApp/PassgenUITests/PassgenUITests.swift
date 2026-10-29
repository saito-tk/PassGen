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
        for id in ["overview-upper", "overview-lower", "overview-digits", "overview-symbols"] {
            let row = app.descendants(matching: .any).matching(identifier: id).firstMatch
            XCTAssertTrue(row.exists); XCTAssertLessThanOrEqual(row.frame.maxX, app.frame.maxX - 8)
        }
        app.buttons["generation-rules"].tap(); app.swipeUp()
        let layout = XCTAttachment(screenshot: app.screenshot()); layout.name = "Expanded rules"; layout.lifetime = .keepAlways; add(layout)
        for id in ["rule-requireEach", "rule-excludeSimilar", "rule-noAdjacent"] {
            let control = app.switches[id]
            XCTAssertTrue(control.exists)
            XCTAssertLessThanOrEqual(control.frame.maxX, app.frame.maxX - 8)
            XCTAssertGreaterThanOrEqual(control.frame.width, 45)
        }
    }

    @MainActor func testCharacterPreviewReflectsSelectionExclusionsAndDisabledGroups() {
        let app = launchApp()
        app.buttons["character-A"].tap(); app.swipeUp()
        func preview(_ group: String) -> XCUIElement {
            app.descendants(matching: .any).matching(identifier: "overview-\(group)").firstMatch
        }
        XCTAssertEqual(preview("upper").value as? String, "生成対象: B C D E F G H J K L M N P Q R S T U V W X Y Z")
        XCTAssertEqual(preview("digits").value as? String, "生成対象: 2 3 4 5 6 7 8 9")
        let initial = XCTAttachment(screenshot: app.screenshot()); initial.name = "All characters preview"; initial.lifetime = .keepAlways; add(initial)
        app.swipeDown(); app.swipeDown(); app.buttons["数字のみ"].tap(); app.swipeUp()
        XCTAssertEqual(preview("upper").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("lower").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("symbols").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("digits").value as? String, "生成対象: 0 1 2 3 4 5 6 7 8 9")
        let digits = XCTAttachment(screenshot: app.screenshot()); digits.name = "Disabled groups preview"; digits.lifetime = .keepAlways; add(digits)
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
