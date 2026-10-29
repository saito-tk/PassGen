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

    @MainActor func testStorageLessonExplainsGuessingAndShowsReuseConsequences() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap(); app.buttons["security-guide"].tap()
        app.buttons["password-storage-guide"].tap()
        XCTAssertTrue(app.navigationBars["保存方法と使い回し"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["平文（ひらぶん）"].exists)
        XCTAssertTrue(app.staticTexts["ハッシュ化"].exists)
        let details = app.buttons["ハッシュ化なら、漏れても安全？"]
        for _ in 0..<5 { if details.isHittable { break }; app.swipeUp() }
        details.tap(); XCTAssertTrue(app.staticTexts["hash-guessing"].exists)
        XCTAssertEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        let saltChoice = app.segmentedControls.buttons["個別ソルト"]
        for _ in 0..<5 { if saltChoice.isHittable { break }; app.swipeUp() }
        let unsalted = XCTAttachment(screenshot: app.screenshot()); unsalted.name = "Same hash without salt"; unsalted.lifetime = .keepAlways; add(unsalted)
        saltChoice.tap()
        XCTAssertNotEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        XCTAssertEqual(app.staticTexts["salt-outcome"].label, "同じパスワードでも、保存値は異なります。")
        let outcome = app.staticTexts["salt-outcome"]
        for _ in 0..<3 { if outcome.isHittable { break }; app.swipeUp() }
        let salted = XCTAttachment(screenshot: app.screenshot()); salted.name = "Different hashes with unique salts"; salted.lifetime = .keepAlways; add(salted)
        let noSalt = app.segmentedControls.buttons["ソルトなし"]
        for _ in 0..<3 { if noSalt.isHittable { break }; app.swipeDown() }
        noSalt.tap()
        XCTAssertEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        let stored = XCTAttachment(screenshot: app.screenshot()); stored.name = "Hash storage explanation"; stored.lifetime = .keepAlways; add(stored)
        app.segmentedControls.buttons["使い回し"].tap()
        XCTAssertEqual(app.staticTexts["reuse-outcome"].label, "同じ値が別のサービスでも試されます。")
        let same = XCTAttachment(screenshot: app.screenshot()); same.name = "Reused password scenario"; same.lifetime = .keepAlways; add(same)
        app.segmentedControls.buttons["別々にする"].tap()
        XCTAssertEqual(app.staticTexts["reuse-outcome"].label, "Aで漏れた値は、BとCのパスワードには一致しません。")
        let unique = XCTAttachment(screenshot: app.screenshot()); unique.name = "Unique passwords scenario"; unique.lifetime = .keepAlways; add(unique)
    }

    @MainActor func testAuthenticationLessonDistinguishesStepsFromFactors() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap(); app.buttons["security-guide"].tap()
        let link = app.buttons["authentication-guide"]
        for _ in 0..<6 { if link.isHittable { break }; app.swipeUp() }
        link.tap()
        XCTAssertTrue(app.navigationBars["2要素と2段階"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["auth-steps"].label, "2段階")
        XCTAssertEqual(app.staticTexts["auth-count"].label, "2種類")
        XCTAssertEqual(app.staticTexts["auth-outcome"].label, "2段階で、異なる2つの要素を確認します。")
        let initial = XCTAttachment(screenshot: app.screenshot()); initial.name = "Authenticator factors"; initial.lifetime = .keepAlways; add(initial)
        app.segmentedControls.buttons["質問"].tap()
        XCTAssertEqual(app.staticTexts["auth-steps"].label, "2段階")
        XCTAssertEqual(app.staticTexts["auth-count"].label, "1種類")
        XCTAssertEqual(app.staticTexts["auth-outcome"].label, "2回確認しても、2要素にはなりません。")
        app.segmentedControls.buttons["パスキー"].tap()
        XCTAssertEqual(app.staticTexts["auth-steps"].label, "一連の操作")
        XCTAssertEqual(app.staticTexts["auth-count"].label, "2種類")
        XCTAssertEqual(app.staticTexts["auth-outcome"].label, "一連の操作で、複数の要素を確認できます。")
        let passkey = XCTAttachment(screenshot: app.screenshot()); passkey.name = "Passkey factors"; passkey.lifetime = .keepAlways; add(passkey)
        let factors = app.buttons["3つの要素とは？"]
        for _ in 0..<5 { if factors.isHittable { break }; app.swipeUp() }
        factors.tap(); XCTAssertTrue(app.staticTexts["知っていること"].exists)
        let methods = app.buttons["どれを設定したらいい？"]
        for _ in 0..<5 { if methods.isHittable { break }; app.swipeUp() }
        methods.tap(); XCTAssertTrue(app.staticTexts["SMSの一時コード"].exists)
        app.navigationBars["2要素と2段階"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["アカウントを守る"].exists)
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
