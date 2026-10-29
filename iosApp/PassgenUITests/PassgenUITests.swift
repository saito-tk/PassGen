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
    @MainActor func testCopiedPasswordCanBePastedAfterBackground() {
        let app = launchApp()
        app.buttons["数字のみ"].tap()
        let length = app.textFields["length"]; length.tap()
        length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "4")
        app.buttons["generate"].tap()
        let copy = app.buttons["copy-0"]; XCTAssertTrue(copy.waitForExistence(timeout: 5)); copy.tap()
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertFalse(app.staticTexts["secret"].exists)
        app.swipeDown()
        app.segmentedControls.buttons["記号"].tap()
        let symbols = app.switches["記号を含める"]
        XCTAssertTrue(symbols.waitForExistence(timeout: 3))
        if symbols.value as? String == "0" { symbols.tap() }
        app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        let field = app.descendants(matching: .any).matching(identifier: "symbol-paste-input").firstMatch
        let scroll = app.scrollViews["symbol-editor-scroll"]
        for _ in 0..<6 { if field.isHittable { break }; scroll.swipeUp() }
        field.tap(); field.press(forDuration: 1.2)
        let options = [app.menuItems["Paste"], app.menuItems["ペースト"], app.buttons["Paste"], app.buttons["ペースト"]]
        guard let paste = options.first(where: { $0.waitForExistence(timeout: 1) && $0.isHittable }) else {
            XCTFail("Copied test value must remain available for paste after background"); return
        }
        paste.tap()
        let value = field.value as? String ?? ""
        XCTAssertTrue(value.count == 4 && value.allSatisfy { "0123456789".contains($0) })
        app.buttons["完了"].tap()
    }
    @MainActor private func nativeSwitch(_ element: XCUIElement) -> XCUIElement {
        let control = element.switches.firstMatch
        return control.exists ? control : element
    }
    @MainActor func testBackgroundCoverPreferenceIsIndependentAndSurvivesBackground() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap()
        let cover = app.switches["protect-app-switcher"]
        for _ in 0..<6 { if nativeSwitch(cover).isHittable { break }; app.swipeUp() }
        XCTAssertEqual(cover.value as? String, "1")
        nativeSwitch(cover).tap(); XCTAssertEqual(cover.value as? String, "0")
        let capture = app.switches["protect-capture"]
        XCTAssertEqual(capture.value as? String, "1")
        let disabled = XCTAttachment(screenshot: app.screenshot()); disabled.name = "Background cover disabled"; disabled.lifetime = .keepAlways; add(disabled)
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertEqual(cover.value as? String, "0"); XCTAssertEqual(capture.value as? String, "1")
        app.buttons["閉じる"].tap()
        let settings = app.buttons["安全性とプライバシー"]
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: settings)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 5), .completed)
        settings.tap()
        for _ in 0..<6 { if nativeSwitch(cover).isHittable { break }; app.swipeUp() }
        XCTAssertEqual(cover.value as? String, "0")
        nativeSwitch(capture).tap(); nativeSwitch(cover).tap()
        XCTAssertEqual(cover.value as? String, "1"); XCTAssertEqual(capture.value as? String, "0")
        nativeSwitch(capture).tap(); app.buttons["閉じる"].tap()
    }
    @MainActor func testNativeRecipeEditorAndSymbols() {
        let app = launchApp()
        XCTAssertTrue(app.textFields["length"].waitForExistence(timeout: 3))
        app.segmentedControls.buttons["記号"].tap()
        app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        XCTAssertTrue(app.navigationBars["使える記号"].waitForExistence(timeout: 3))
    }
    @MainActor func testImportedSymbolsAndManualReviewSurviveBackground() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-ephemeral-settings", "--uitest-screenshot-import"]
        app.launch()
        app.segmentedControls.buttons["記号"].tap(); app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        XCTAssertTrue(app.navigationBars["使える記号"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["screenshot-import-status"].waitForExistence(timeout: 15))
        guard app.images["screenshot-import-preview"].exists else {
            XCTFail("Fixture did not produce an editable preview: " + app.staticTexts["screenshot-import-status"].label)
            return
        }
        let scroll = app.scrollViews["symbol-editor-scroll"]
        let ready = XCTAttachment(screenshot: app.screenshot()); ready.name = "Import ready before review"; ready.lifetime = .keepAlways; add(ready)
        let bang = app.buttons["candidate-!"]
        for _ in 0..<8 { if bang.isHittable { break }; scroll.swipeUp() }
        XCTAssertTrue(bang.waitForExistence(timeout: 5)); XCTAssertTrue(bang.isSelected)
        let brace = app.buttons["candidate-{"]
        for _ in 0..<3 { if brace.isHittable { break }; scroll.swipeUp() }
        // Vision revisions can miss a glyph: exercise a manual addition as well
        // as a removal, then verify both edits survive the real scene transition.
        if !brace.isSelected { brace.tap() }
        XCTAssertTrue(brace.isSelected)
        let hash = app.buttons["candidate-#"]
        for _ in 0..<3 { if hash.isHittable { break }; scroll.swipeDown() }
        XCTAssertTrue(hash.isSelected); hash.tap(); XCTAssertFalse(hash.isSelected)
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.navigationBars["使える記号"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.images["screenshot-import-preview"].exists)
        XCTAssertFalse(hash.isSelected); XCTAssertTrue(bang.isSelected)
        XCTAssertTrue(brace.isSelected)
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = "Imported symbols after background"; image.lifetime = .keepAlways; add(image)
        let apply = app.buttons["apply-image-symbols"]
        for _ in 0..<4 { if apply.isHittable { break }; scroll.swipeUp() }
        apply.tap()
        XCTAssertFalse(app.images["screenshot-import-preview"].exists)
        app.buttons["完了"].tap()
    }

    @MainActor func testUnlabelledSymbolListOffersReviewWithoutPermissionPhrase() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-ephemeral-settings", "--uitest-screenshot-import", "--uitest-symbol-list"]
        app.launch()
        app.segmentedControls.buttons["記号"].tap(); app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        let status = app.staticTexts["screenshot-import-status"]
        XCTAssertTrue(status.waitForExistence(timeout: 15))
        guard status.label.hasPrefix("記号の並びから拾った候補") else { XCTFail(status.label); return }
        XCTAssertTrue(status.label.contains("使える記号か確認"), status.label)
        let preview = app.images["screenshot-import-preview"]
        XCTAssertTrue(preview.exists)
        let scroll = app.scrollViews["symbol-editor-scroll"]
        for _ in 0..<6 { if preview.isHittable { break }; scroll.swipeUp() }
        let picture = XCTAttachment(screenshot: app.screenshot()); picture.name = "Unlabelled punctuation review"; picture.lifetime = .keepAlways; add(picture)
        XCTAssertTrue(app.buttons["apply-image-symbols"].isEnabled)
        app.buttons["完了"].tap()
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
        app.segmentedControls.buttons["2段階"].tap()
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
