import XCTest

final class PassgenUITests: XCTestCase {
    @MainActor private func launchApp() -> XCUIApplication {
        let app = XCUIApplication(); app.launchArguments = ["--uitest-ephemeral-settings"]; app.launch(); return app
    }
    @MainActor private func dismissPresets(_ app: XCUIApplication) {
        let bar = app.navigationBars["プリセット"]
        bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: bar)
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 5), .completed)
    }
    @MainActor private func chooseOnlyDigits(_ app: XCUIApplication) {
        for group in ["大文字", "小文字", "記号"] {
            app.segmentedControls.buttons[group].tap()
            let toggle = nativeSwitch(app.switches[group + "を含める"])
            if toggle.value as? String == "1" { toggle.tap() }
        }
    }
    @MainActor func testNamedPresetsSaveLoadRenameAndDelete() {
        let app = launchApp(); XCTAssertFalse(app.buttons["数字のみ"].exists)
        let length = app.textFields["length"]
        length.tap(); length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "64")
        app.buttons["完了"].tap(); app.buttons["presets"].tap()
        // iOS 18 exposes sheet elements before the detent transition finishes.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        let name = app.textFields["preset-name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5)); name.tap(); name.typeText("Mail\n")
        app.buttons["preset-save"].tap()
        XCTAssertTrue(app.buttons["Mailを読み込む"].waitForExistence(timeout: 3))
        let preview = XCTAttachment(screenshot: app.screenshot()); preview.name = "Named generation presets"; preview.lifetime = .keepAlways; add(preview)
        dismissPresets(app)
        if app.windows.firstMatch.frame.width < 700 { XCTAssertTrue(app.navigationBars["生成結果 0件"].waitForExistence(timeout: 5)) }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: app.buttons["generate"])
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 5), .completed)
        length.tap(); length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "32")
        app.buttons["完了"].tap(); app.buttons["presets"].tap()
        // iOS 18 exposes sheet elements before the detent transition finishes.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        app.buttons["Mailを読み込む"].tap()
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "64"), object: length)
        XCTAssertEqual(XCTWaiter.wait(for: [loaded], timeout: 5), .completed)
        let returned = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: app.buttons["generate"])
        XCTAssertEqual(XCTWaiter.wait(for: [returned], timeout: 5), .completed)
        XCTAssertFalse(app.staticTexts["secret"].exists)
        app.buttons["presets"].tap()
        // iOS 18 exposes sheet elements before the detent transition finishes.
        RunLoop.current.run(until: Date().addingTimeInterval(1)); app.buttons["Mailの編集"].tap(); app.buttons["名前を変更"].tap()
        let rename = app.textFields["preset-rename-name"]
        XCTAssertTrue(rename.waitForExistence(timeout: 3)); rename.tap()
        rename.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 8) + "Backup\n")
        XCTAssertEqual(rename.value as? String, "Backup")
        app.buttons["preset-rename-save"].tap()
        XCTAssertTrue(app.buttons["Backupを読み込む"].waitForExistence(timeout: 3))
        app.buttons["Backupの編集"].tap(); app.buttons["削除"].tap()
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        app.buttons["「Backup」を削除"].tap()
        let removed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.buttons["Backupを読み込む"])
        XCTAssertEqual(XCTWaiter.wait(for: [removed], timeout: 5), .completed)
        dismissPresets(app)
        XCTAssertTrue(app.buttons["generate"].waitForExistence(timeout: 5))
    }
    @MainActor func testPeekSheetAllowsSettingsAndMovesGenerationToHeader() {
        let app = launchApp()
        let generate = app.buttons["generate"]
        XCTAssertTrue(generate.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["results-visibility"].exists)
        let length = app.textFields["length"]; XCTAssertTrue(length.isHittable)
        length.tap(); length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "64")
        app.buttons["完了"].tap()
        generate.tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        let visibility = app.buttons["results-visibility"]
        XCTAssertEqual(visibility.value as? String, "非表示")
        visibility.tap(); XCTAssertEqual(visibility.value as? String, "表示中")
        let expanded = XCTAttachment(screenshot: app.screenshot()); expanded.name = "Full 64-character result"; expanded.lifetime = .keepAlways; add(expanded)
        XCTAssertEqual(generate.label, "再生成")
        XCTAssertFalse(app.buttons["閉じる"].exists)
        XCTAssertEqual(app.buttons.matching(identifier: "generate").count, 1)
        generate.tap()
        XCTAssertEqual(visibility.value as? String, "表示中")
        let bar = app.navigationBars.matching(identifier: "生成結果 1件").firstMatch
        bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.93)))
        let collapsed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: visibility)
        XCTAssertEqual(XCTWaiter.wait(for: [collapsed], timeout: 5), .completed)
        XCTAssertTrue(length.isHittable)
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = "Interactive minimal result sheet"; image.lifetime = .keepAlways; add(image)
    }
    @MainActor func testWideLayoutUsesCompactGenerateAndModalResults() throws {
        let app = launchApp()
        guard app.windows.firstMatch.frame.width > 700 else { throw XCTSkip("Wide-layout simulator required") }
        let generate = app.buttons["generate"]
        XCTAssertTrue(generate.waitForExistence(timeout: 5))
        XCTAssertLessThan(generate.frame.width, 150)
        generate.tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons.matching(identifier: "generate").count, 1)
        XCTAssertEqual(generate.label, "再生成")
        XCTAssertFalse(app.buttons["閉じる"].exists)
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = "Modal results on wide layout"; image.lifetime = .keepAlways; add(image)
    }
    @MainActor func testHundredSixtyFourCharacterResultsKeepVisibilityOnRegeneration() {
        let app = launchApp()
        let length = app.textFields["length"]
        length.tap(); length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "64")
        app.buttons["完了"].tap()
        let count = app.textFields["count"]
        count.tap(); count.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "100")
        app.buttons["完了"].tap(); app.buttons["generate"].tap()
        let title = app.navigationBars["生成結果 100件"]
        XCTAssertTrue(title.waitForExistence(timeout: 8))
        let visibility = app.buttons["results-visibility"]
        visibility.tap(); XCTAssertEqual(visibility.value as? String, "表示中")
        XCTAssertGreaterThan(app.staticTexts.matching(identifier: "secret").firstMatch.frame.height, 35)
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = "100 results of 64 characters"; image.lifetime = .keepAlways; add(image)
        app.buttons["generate"].tap()
        XCTAssertTrue(title.waitForExistence(timeout: 8))
        XCTAssertEqual(visibility.value as? String, "表示中")
    }
    @MainActor func testClearCollapsesResultsAndChangedConditionsResetGenerationTitle() {
        let app = launchApp(); let generate = app.buttons["generate"]
        generate.tap(); XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCTAssertEqual(generate.label, "再生成")
        let bar = app.navigationBars["生成結果 1件"]
        bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.93)))
        let length = app.textFields["length"]; XCTAssertTrue(length.isHittable)
        length.tap(); length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "64")
        app.buttons["完了"].tap(); XCTAssertEqual(generate.label, "生成")
        XCTAssertFalse(app.navigationBars["Passgen"].buttons["結果 1件"].exists)
        generate.tap(); XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCTAssertEqual(generate.label, "再生成")
        app.buttons["clear-results"].tap()
        let hidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.buttons["results-visibility"])
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 5), .completed)
        XCTAssertFalse(app.staticTexts["secret"].exists); XCTAssertTrue(length.isHittable)
        XCTAssertEqual(generate.label, "生成")
    }
    @MainActor func testWideRetainedResultsCanBeOpenedWithoutRegenerating() throws {
        let app = launchApp()
        guard app.windows.firstMatch.frame.width > 700 else { throw XCTSkip("Wide-layout simulator required") }
        app.buttons["generate"].tap()
        let copy = app.buttons["copy-0"]; XCTAssertTrue(copy.waitForExistence(timeout: 5)); copy.tap()
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertEqual(copy.label, "コピー済み。もう一度コピー")
        let show = app.buttons["show-retained-results"]
        for _ in 0..<2 {
            if show.exists { break }
            app.navigationBars["生成結果 1件"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))
        }
        XCTAssertTrue(show.waitForExistence(timeout: 5)); show.tap()
        XCTAssertTrue(copy.waitForExistence(timeout: 5))
        XCTAssertEqual(copy.label, "コピー済み。もう一度コピー")
        XCTAssertEqual(app.buttons["generate"].label, "再生成")
    }
    @MainActor func testGenerateAndBackgroundRetainsResults() {
        let app = launchApp()
        XCTAssertFalse(app.staticTexts["secret"].exists)
        app.buttons["generate"].tap()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["generate"].label, "再生成")
    }
    @MainActor func testCopiedPasswordCanBePastedAfterBackground() {
        let app = launchApp()
        chooseOnlyDigits(app)
        let length = app.textFields["length"]; length.tap()
        length.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "4")
        app.buttons["完了"].tap()
        app.buttons["generate"].tap()
        let copy = app.buttons["copy-0"]; XCTAssertTrue(copy.waitForExistence(timeout: 5)); copy.tap()
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.staticTexts["secret"].waitForExistence(timeout: 5))
        let bar = app.navigationBars["生成結果 1件"]
        bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.93)))
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
        app.navigationBars["使える記号"].buttons["完了"].tap()
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
    @MainActor func testSymbolPasteDoneDismissesKeyboardWithoutApplying() {
        let app = launchApp()
        app.segmentedControls.buttons["記号"].tap(); app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        let field = app.textFields["symbol-paste-input"]
        let scroll = app.scrollViews["symbol-editor-scroll"]
        for _ in 0..<6 { if field.isHittable { break }; scroll.swipeUp() }
        field.tap(); field.typeText("@#%")
        XCTAssertTrue(app.keyboards.firstMatch.exists)
        XCTAssertTrue(app.keyboards.buttons["Done"].exists || app.keyboards.buttons["完了"].exists)
        // Send Return through the focused field. This also covers a hardware
        // Enter key without depending on cached keyboard-plane coordinates.
        field.typeText("\n")
        let hidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 3), .completed)
        XCTAssertEqual(field.value as? String, "@#%")
        XCTAssertTrue(scroll.buttons["記号 !"].isSelected, "Done must only dismiss the keyboard")
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = "Outlined single-line symbol input"; image.lifetime = .keepAlways; add(image)
        let apply = app.buttons["apply-pasted-symbols"]
        for _ in 0..<4 { if apply.isHittable { break }; scroll.swipeUp() }
        apply.tap()
        XCTAssertFalse(scroll.buttons["記号 !"].isSelected)
        for symbol in ["@", "#", "%"] { XCTAssertTrue(scroll.buttons["記号 " + symbol].isSelected) }
        app.buttons["完了"].tap()
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

    @MainActor func testInlineWrappedPermissionKeepsBothRowsAndExplicitApply() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-ephemeral-settings", "--uitest-screenshot-import", "--uitest-inline-permission"]
        app.launch()
        app.segmentedControls.buttons["記号"].tap(); app.swipeUp()
        app.buttons["記号一覧を貼り付け・画像から読み取る"].tap()
        let status = app.staticTexts["screenshot-import-status"]
        XCTAssertTrue(status.waitForExistence(timeout: 15))
        let preview = app.images["screenshot-import-preview"]
        XCTAssertTrue(preview.exists, status.label)
        let scroll = app.scrollViews["symbol-editor-scroll"]
        for _ in 0..<6 { if preview.isHittable { break }; scroll.swipeUp() }
        let picture = XCTAttachment(screenshot: app.screenshot()); picture.name = "Inline and wrapped punctuation review"; picture.lifetime = .keepAlways; add(picture)
        for symbol in ["#", "%", "~"] {
            let candidate = app.buttons["candidate-" + symbol]
            for _ in 0..<6 { if candidate.isHittable { break }; scroll.swipeUp() }
            XCTAssertTrue(candidate.isSelected, "Both rows must contribute: " + symbol)
        }
        // This UI fixture deliberately uses OCR only. A font-dependent missed
        // glyph remains editable; the real supplied-image unit test covers @.
        let at = app.buttons["candidate-@"]
        if !at.isSelected { at.tap() }
        XCTAssertTrue(at.isSelected)
        let apply = app.buttons["apply-image-symbols"]
        for _ in 0..<6 { if apply.isHittable { break }; scroll.swipeUp() }
        XCTAssertTrue(apply.isEnabled); apply.tap()
        XCTAssertFalse(preview.exists)
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
        app.swipeDown(); app.swipeDown(); chooseOnlyDigits(app); app.swipeUp()
        XCTAssertEqual(preview("upper").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("lower").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("symbols").value as? String, "生成対象: なし")
        XCTAssertEqual(preview("digits").value as? String, "生成対象: 2 3 4 5 6 7 8 9")
        let digits = XCTAttachment(screenshot: app.screenshot()); digits.name = "Disabled groups preview"; digits.lifetime = .keepAlways; add(digits)
    }

    @MainActor func testStorageLessonExplainsGuessingAndShowsReuseConsequences() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap(); app.buttons["security-guide"].tap()
        app.buttons["password-storage-guide"].tap()
        XCTAssertTrue(app.navigationBars["保存方法と使い回し"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["平文（ひらぶん）"].exists)
        XCTAssertTrue(app.staticTexts["ハッシュ化"].exists)
        let plaintext = app.descendants(matching: .any).matching(identifier: "storage-plaintext-value").firstMatch
        XCTAssertTrue(plaintext.label.contains("Demo!7_mikan-Sora"))
        let hash = app.descendants(matching: .any).matching(identifier: "storage-hash-value").firstMatch
        let surface = app.scrollViews["storage-lesson-scroll"]
        let top = max(surface.frame.minY, app.segmentedControls.element(boundBy: 0).frame.maxY) + 8
        let bottom = min(surface.frame.maxY, app.frame.maxY) - 12
        for _ in 0..<12 {
            if hash.frame.minY >= top && hash.frame.maxY <= bottom { break }
            let delta = max(-180, min(180, hash.frame.midY - (top + bottom) / 2))
            let start = surface.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65))
            start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -delta)))
        }
        XCTAssertGreaterThanOrEqual(hash.frame.minY, top)
        XCTAssertLessThanOrEqual(hash.frame.maxY, bottom)

        XCTAssertNotNil(hash.label.range(of: #"\$argon2id\$v=19\$m=19456,t=2,p=1\$[A-Za-z0-9+/]{22}\$[A-Za-z0-9+/]{43}"#, options: .regularExpression))
        XCTAssertFalse(hash.label.contains("…")); XCTAssertFalse(hash.label.contains("..."))
        XCTAssertGreaterThanOrEqual(hash.frame.minX, surface.frame.minX)
        XCTAssertLessThanOrEqual(hash.frame.maxX, surface.frame.maxX)
        let fullHash = XCTAttachment(screenshot: app.screenshot()); fullHash.name = "Full database hash example"; fullHash.lifetime = .keepAlways; add(fullHash)
        let details = app.buttons["ハッシュ化なら、漏れても安全？"]
        for _ in 0..<14 { if details.isHittable { break }; app.swipeUp() }
        details.tap(); XCTAssertTrue(app.staticTexts["hash-guessing"].exists)
        XCTAssertEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        let saltChoice = app.segmentedControls.buttons["個別ソルト"]
        for _ in 0..<10 { if saltChoice.isHittable { break }; app.swipeUp() }
        let common = XCTAttachment(screenshot: app.screenshot()); common.name = "Same hash with shared salt"; common.lifetime = .keepAlways; add(common)
        saltChoice.tap()
        XCTAssertNotEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        XCTAssertEqual(app.staticTexts["salt-outcome"].label, "同じパスワードでも、保存値は異なります。")
        let outcome = app.staticTexts["salt-outcome"]
        for _ in 0..<3 { if outcome.isHittable { break }; app.swipeUp() }
        let salted = XCTAttachment(screenshot: app.screenshot()); salted.name = "Different hashes with unique salts"; salted.lifetime = .keepAlways; add(salted)
        let commonSalt = app.segmentedControls.buttons["共通ソルト"]
        for _ in 0..<3 { if commonSalt.isHittable { break }; app.swipeDown() }
        commonSalt.tap()
        XCTAssertEqual(app.staticTexts["salt-hash-a"].label, app.staticTexts["salt-hash-b"].label)
        let stored = XCTAttachment(screenshot: app.screenshot()); stored.name = "Hash storage explanation"; stored.lifetime = .keepAlways; add(stored)
        app.segmentedControls.buttons["使い回し"].tap()
        XCTAssertEqual(app.staticTexts["reuse-outcome"].label, "同じ値が別のサービスでも試されます。")
        for key in ["reuse-password-a", "reuse-password-b", "reuse-password-c"] {
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: key).firstMatch.label.contains("Demo!7_mikan-Sora"))
        }
        let same = XCTAttachment(screenshot: app.screenshot()); same.name = "Reused password scenario"; same.lifetime = .keepAlways; add(same)
        app.segmentedControls.buttons["別々にする"].tap()
        XCTAssertEqual(app.staticTexts["reuse-outcome"].label, "Aで漏れた値は、BとCのパスワードには一致しません。")
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "reuse-password-b").firstMatch.label.contains("Example#4_kiwi-Nami"))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "reuse-password-c").firstMatch.label.contains("Sample+9_momo-Tsuki"))
        let unique = XCTAttachment(screenshot: app.screenshot()); unique.name = "Unique passwords scenario"; unique.lifetime = .keepAlways; add(unique)
    }

    @MainActor private func choosePasswordTopic(_ title: String, app: XCUIApplication) {
        let segment = app.segmentedControls["password-example-topic"].buttons[title]
        if segment.exists { segment.tap() }
        else {
            app.buttons["password-example-topic"].tap()
            let options = app.buttons.matching(identifier: title).allElementsBoundByIndex
            guard let option = options.first(where: { $0.isHittable }) else { XCTFail("Theme option must be reachable"); return }
            option.tap()
        }
    }
    @MainActor private func bringGuideElementIntoView(_ element: XCUIElement, surface: XCUIElement) {
        for _ in 0..<45 {
            var direction: CGFloat = -1
            if element.exists {
                let frame = element.frame
                if element.isHittable && frame.minY >= surface.frame.minY && frame.maxY <= surface.frame.maxY { return }
                if frame.midY < surface.frame.midY { direction = 1 }
            }
            let start = surface.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65))
            start.press(forDuration: 0.15, thenDragTo: start.withOffset(CGVector(dx: 0, dy: direction * min(180, surface.frame.height * 0.3))))
        }
        XCTFail("Guide example must be visible: \(element.identifier)")
    }
    @MainActor private func saveGuideExampleScreenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    @MainActor func testPasswordExamplesShowPatternsAndInteractiveComparisons() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap(); app.buttons["security-guide"].tap()
        let guide = app.descendants(matching: .any).matching(identifier: "security-guide-list").firstMatch
        let link = app.buttons["password-examples-guide"]
        bringGuideElementIntoView(link, surface: guide); link.tap()
        XCTAssertTrue(app.navigationBars["パスワードを例で見る"].waitForExistence(timeout: 3))
        let scroll = app.scrollViews["password-examples-scroll"]
        XCTAssertEqual(app.staticTexts["weak-value-word"].label, "password")
        XCTAssertEqual(app.staticTexts["weak-value-sequence"].label, "12345678")
        saveGuideExampleScreenshot(app, "Concrete weak passwords")
        let mutation = app.staticTexts["weak-mutation-2"]
        bringGuideElementIntoView(mutation, surface: scroll)
        XCTAssertEqual(mutation.label, "P@ssw0rd1!")
        saveGuideExampleScreenshot(app, "Predictable mutations")
        choosePasswordTopic("長さ", app: app)
        XCTAssertEqual(app.staticTexts["guide-length-count"].label, "1万通り")
        app.segmentedControls.buttons["6桁"].tap()
        XCTAssertEqual(app.staticTexts["guide-length-value"].label, "604839")
        XCTAssertEqual(app.staticTexts["guide-length-count"].label, "100万通り")
        app.segmentedControls.buttons["8桁"].tap()
        XCTAssertEqual(app.staticTexts["guide-length-count"].label, "1億通り")
        saveGuideExampleScreenshot(app, "Numeric length comparison")
        choosePasswordTopic("パスフレーズ", app: app)
        let phrase = app.staticTexts["guide-phrase-value"]
        let words = phrase.label.split(separator: " ").map(String.init)
        XCTAssertEqual(words.count, 7)
        for separator in ["-", "_"] {
            app.segmentedControls.buttons[separator].tap()
            XCTAssertEqual(phrase.label, words.joined(separator: separator))
        }
        XCTAssertGreaterThanOrEqual(phrase.frame.minX, scroll.frame.minX)
        XCTAssertLessThanOrEqual(phrase.frame.maxX, scroll.frame.maxX)
        saveGuideExampleScreenshot(app, "Seven words with selectable separators")
        app.navigationBars["パスワードを例で見る"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(guide.waitForExistence(timeout: 3))
        let lengthLink = app.buttons["password-length-guide"]
        bringGuideElementIntoView(lengthLink, surface: guide); lengthLink.tap()
        XCTAssertEqual(app.staticTexts["guide-length-value"].label, "4827")
    }
    @MainActor func testGuideShowsConcreteAccountAuthenticationAndLookalikeExamples() {
        let app = launchApp()
        app.buttons["安全性とプライバシー"].tap(); app.buttons["security-guide"].tap()
        let guide = app.descendants(matching: .any).matching(identifier: "security-guide-list").firstMatch
        let password = app.staticTexts["guide-manager-password"]
        bringGuideElementIntoView(password, surface: guide)
        XCTAssertEqual(password.label.count, 24)
        saveGuideExampleScreenshot(app, "Password manager record example")
        let code = app.staticTexts["482915（説明用）"]
        bringGuideElementIntoView(code, surface: guide)
        saveGuideExampleScreenshot(app, "Authenticator code example")
        let passkey = app.descendants(matching: .any).matching(identifier: "guide-passkey-unlock").firstMatch
        bringGuideElementIntoView(passkey, surface: guide)
        let lookalike = app.staticTexts["guide-lookalike-url"]
        bringGuideElementIntoView(lookalike, surface: guide)
        XCTAssertEqual(lookalike.label, "https://account.example.com.login-check.example.net")
        XCTAssertGreaterThanOrEqual(lookalike.frame.minX, guide.frame.minX)
        XCTAssertLessThanOrEqual(lookalike.frame.maxX, guide.frame.maxX)
        saveGuideExampleScreenshot(app, "Lookalike URL example")
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
        app.buttons["完了"].tap()
        app.buttons["generate"].tap()
        let first = app.buttons["copy-0"]; XCTAssertTrue(first.waitForExistence(timeout: 5)); first.tap()
        let second = app.buttons["copy-1"]; second.tap()
        XCTAssertEqual(first.label, "コピー済み。もう一度コピー"); XCTAssertEqual(second.label, "コピー済み。もう一度コピー")
        let result = XCTAttachment(screenshot: app.screenshot()); result.name = "Copied result marks"; result.lifetime = .keepAlways; add(result)
        let bar = app.navigationBars["生成結果 2件"]
        bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).press(forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.93)))
        app.buttons["安全性とプライバシー"].tap()
        XCTAssertTrue(app.switches["allow-universal-clipboard"].exists)
        app.buttons["security-guide"].tap()
        XCTAssertTrue(app.navigationBars["アカウントを守る"].waitForExistence(timeout: 3))
    }

}
