package jp.passgen.app

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File

class GeneratorUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(rule.activity)[PassgenModel::class.java]

    @Before fun resetTestOwnedConfiguration() {
        rule.runOnIdle {
            model.background(); model.autoClearClipboard = true; model.clipboardSecondsText = "30"; model.clearClipboardOnBackground = true; model.protectCapture = true; model.revealed = false; model.mode = jp.passgen.core.GenerationMode.PASSWORD
            model.lengthText = "24"; model.countText = "1"; model.words = 7; model.separator = " "
            model.uppercase = true; model.lowercase = true; model.digits = true; model.symbolsEnabled = true
            model.symbols = "!@#\u0024%&*-_+=?"; model.excludeSimilar = true; model.requireEach = true; model.noAdjacent = false
            model.uppercaseCharacters = jp.passgen.core.CharacterPolicy.UPPERCASE
            model.lowercaseCharacters = jp.passgen.core.CharacterPolicy.LOWERCASE
            model.digitCharacters = jp.passgen.core.CharacterPolicy.DIGITS
            model.similarExclusions = jp.passgen.core.CharacterPolicy.SIMILAR
        }
    }

    @After fun restoreTestCaptureProtection() {
        rule.runOnIdle { model.protectCapture = true }
    }

    @Test fun onlySettingsAndRevealPreferenceSurviveNewModel() {
        rule.runOnIdle {
            val prefs = rule.activity.getSharedPreferences("test.preferences.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            val first = PassgenModel(prefs)
            first.lengthText = "48"; first.uppercaseCharacters = "AZ"; first.revealed = true
            first.noAdjacent = true; first.words = 9; first.separator = "_"; first.background()
            first.clipboardSecondsText = "90"; first.protectCapture = false; first.clearClipboardOnBackground = false; first.background()
            val restored = PassgenModel(prefs)
            assertEquals(48, restored.length); assertEquals("AZ", restored.uppercaseCharacters)
            assertTrue(restored.revealed); assertTrue(restored.noAdjacent); assertEquals(9, restored.words)
            assertEquals("_", restored.separator); assertTrue(restored.results.isEmpty())
            assertEquals(90, restored.clipboardSeconds); assertFalse(restored.protectCapture); assertFalse(restored.clearClipboardOnBackground)
            restored.chooseMode(jp.passgen.core.GenerationMode.PASSPHRASE)
            restored.chooseMode(jp.passgen.core.GenerationMode.PASSWORD)
            assertEquals(48, restored.length)
            prefs.edit().clear().apply()
        }
    }

    @Test fun regenerationAndBackgroundKeepVisibilityPreference() {
        rule.runOnIdle { model.revealed = true; model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.revealed); model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.revealed); model.background(); assertTrue(model.revealed); assertTrue(model.results.isEmpty()) }
    }

    @Test fun launchDoesNotGenerateAndWindowIsSecure() {
        rule.runOnIdle {
            assertTrue(model.results.isEmpty())
            assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        rule.onNodeWithTag("generate").assertIsEnabled()
    }

    @Test fun generationStartsMaskedAndCopiesFullValue() {
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle { assertFalse(model.revealed); assertEquals(24, model.results.first().value.length) }
        rule.onNodeWithTag("copy-0").performClick()
        rule.waitUntil(3000) { 0 in model.copiedIndices }
        rule.runOnIdle {
            val manager = rule.activity.getSystemService(ClipboardManager::class.java)
            assertTrue(manager.primaryClip?.getItemAt(0)?.text?.length == 24)
            assertTrue(manager.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
        }
    }

    @Test fun copyMarksRemainOnBothRowsUntilRegeneration() {
        rule.runOnIdle { model.countText = "2"; model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy && model.results.size == 2 }
        // Open the existing result sheet without generating another batch.
        rule.onNodeWithText("結果 2件").performClick()
        rule.onNodeWithTag("copy-0").performClick(); rule.onNodeWithTag("copy-1").performClick()
        rule.runOnIdle { assertEquals(setOf(0, 1), model.copiedIndices) }
        rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー済み。もう一度コピー")
        rule.onNodeWithTag("copy-1").assertContentDescriptionEquals("コピー済み。もう一度コピー")
        rule.runOnIdle { model.generateOrStop(); assertTrue(model.copiedIndices.isEmpty()) }
    }

    @Test fun captureProtectionFollowsPreferenceAndGuideIsReachable() {
        rule.runOnIdle { model.protectCapture = false }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
        rule.runOnIdle { model.protectCapture = true }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        rule.onNodeWithTag("safety-settings").performClick()
        rule.onNodeWithTag("security-guide").performClick()
        rule.onNodeWithText("アカウントを守る").assertExists()
    }

    @Test fun storageLessonExplainsGuessingAndShowsReuseConsequences() {
        allowGuideScreenshots()
        rule.onNodeWithTag("safety-settings").performClick(); rule.onNodeWithTag("security-guide").performClick()
        rule.onNodeWithTag("password-storage-guide").performClick()
        rule.onNodeWithText("平文（ひらぶん）").assertExists()
        rule.onNodeWithTag("hash-details").performScrollTo().performClick()
        rule.onNodeWithTag("hash-guessing").assertExists()
        rule.onNodeWithTag("storage-topic-1").performClick()
        rule.onNodeWithTag("reuse-outcome").assertTextEquals("同じ値が別のサービスでも試されます。")
        rule.onNodeWithTag("reuse-outcome").performScrollTo()
        saveGuideScreenshot("storage-reuse.png")
        rule.onNodeWithTag("reuse-unique").performScrollTo().performClick()
        rule.onNodeWithTag("reuse-outcome").assertTextEquals("Aで漏れた値は、BとCのパスワードには一致しません。")
        rule.onNodeWithTag("reuse-outcome").performScrollTo()
        saveGuideScreenshot("storage-unique.png")
        rule.onNodeWithTag("storage-back").performClick()
        rule.onNodeWithText("アカウントを守る").assertExists()
        rule.runOnIdle { model.protectCapture = true }
    }

    @Test fun authenticationLessonDistinguishesStepsFromFactors() {
        allowGuideScreenshots()
        rule.onNodeWithTag("safety-settings").performClick(); rule.onNodeWithTag("security-guide").performClick()
        rule.onNodeWithTag("authentication-guide").performScrollTo().performClick()
        rule.onNodeWithTag("auth-steps").assertTextEquals("2段階")
        rule.onNodeWithTag("auth-count").assertTextEquals("2種類")
        val choices = (0..2).map { rule.onNodeWithTag("auth-example-$it").getUnclippedBoundsInRoot() }
        assertTrue("Example buttons must align even at large font sizes", choices.all { kotlin.math.abs(((it.bottom - it.top) - (choices.first().bottom - choices.first().top)).value) < 1f })
        rule.onNodeWithTag("auth-outcome").assertTextEquals("2段階で、異なる2つの要素を確認します。")
        saveGuideScreenshot("auth-app.png")
        rule.onNodeWithTag("auth-example-0").performClick()
        rule.onNodeWithTag("auth-steps").assertTextEquals("2段階")
        rule.onNodeWithTag("auth-count").assertTextEquals("1種類")
        rule.onNodeWithTag("auth-outcome").assertTextEquals("2回確認しても、2要素にはなりません。")
        rule.onNodeWithTag("auth-example-2").performClick()
        rule.onNodeWithTag("auth-steps").assertTextEquals("一連の操作")
        rule.onNodeWithTag("auth-count").assertTextEquals("2種類")
        rule.onNodeWithTag("auth-outcome").assertTextEquals("一連の操作で、複数の要素を確認できます。")
        saveGuideScreenshot("auth-passkey.png")
        rule.onNodeWithTag("auth-factors").performScrollTo().performClick()
        rule.onNodeWithText("知っていること").assertExists()
        rule.onNodeWithTag("auth-methods").performScrollTo().performClick()
        rule.onNodeWithText("SMSの一時コード").assertExists()
        rule.onNodeWithTag("auth-back").performClick()
        rule.onNodeWithText("アカウントを守る").assertExists()
        rule.runOnIdle { model.protectCapture = true }
    }

    private fun allowGuideScreenshots() {
        // Dialog windows inherit FLAG_SECURE when opened. Disable it before opening
        // these test-only diagrams, which never generate or display credentials.
        rule.runOnIdle { assertTrue(model.results.isEmpty()); model.protectCapture = false }
        rule.waitForIdle()
    }

    private fun saveGuideScreenshot(name: String) {
        rule.runOnIdle { assertTrue(model.results.isEmpty()); assertFalse(model.protectCapture) }
        rule.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val colors = (1..20).flatMap { x -> (1..20).map { y -> bitmap.getPixel(bitmap.width * x / 21, bitmap.height * y / 21) } }.toSet()
        assertTrue("Guide screenshot must contain visible content", colors.size > 4)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun clipboardUsesConfiguredExpiry() {
        rule.runOnIdle { SecureClipboard(rule.activity).copy("fake-test-only", 5) }
        android.os.SystemClock.sleep(6000)
        rule.runOnIdle { assertFalse(rule.activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip()) }
    }

    @Test fun backgroundClearsResultsAndCancelsWork() {
        rule.runOnIdle { model.lengthText = "999999"; model.countText = "10"; model.generateOrStop() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(model.results.isEmpty()); assertFalse(model.busy); assertFalse(model.revealed) }
    }

    @Test fun stopKeepsCompletedRows() {
        rule.runOnIdle { model.lengthText = "10000"; model.countText = "1000"; model.generateOrStop() }
        rule.waitUntil(5000) { model.results.isNotEmpty() }
        rule.runOnUiThread { assertTrue(model.busy); model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.results.isNotEmpty()); assertTrue(model.results.size < model.goal) }
    }

    @Test fun clipboardOwnershipPreservesReplacement() {
        rule.runOnIdle {
            val clipboard = SecureClipboard(rule.activity)
            val manager = rule.activity.getSystemService(ClipboardManager::class.java)
            clipboard.copy("fake-test-only")
            manager.setPrimaryClip(ClipData.newPlainText("another-app", "replacement"))
            clipboard.clearOwned()
            assertTrue(manager.primaryClip?.getItemAt(0)?.text?.toString() == "replacement")
            manager.clearPrimaryClip()
        }
    }

    @Test fun clipboardOwnershipSurvivesControllerRecreation() {
        rule.runOnIdle {
            SecureClipboard(rule.activity).copy("fake-test-only")
            SecureClipboard(rule.activity).clearOwned()
            assertFalse(rule.activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip())
        }
    }

    @Test fun symbolPasteWithoutSupportedCharactersPreservesSelection() {
        rule.runOnIdle { model.symbols = "@" }
        rule.onAllNodesWithText("記号")[0].performClick()
        rule.onNodeWithText("記号一覧を貼り付け").performScrollTo().performClick()
        rule.onNodeWithText("使える記号の一覧を貼り付け").performTextReplacement("abc😀")
        rule.onNodeWithText("記号に反映").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("@", model.symbols) }
        rule.onNodeWithText("対応する記号がないため、選択は変更していません。").assertExists()
    }

    @Test fun characterPreviewReflectsSelectionExclusionsAndDisabledGroups() {
        rule.runOnIdle {
            model.uppercaseCharacters = "AIOZ"; model.lowercase = false; model.digitCharacters = "0129"
            model.similarExclusions = "I0"; model.symbols = "[]`\\"
        }
        rule.onNodeWithTag("overview-UPPER").assertContentDescriptionEquals("大文字、生成対象: A O Z").assertHasNoClickAction()
        rule.onNodeWithTag("overview-LOWER").assertContentDescriptionEquals("小文字、生成対象: なし")
        rule.onNodeWithTag("overview-DIGITS").assertContentDescriptionEquals("数字、生成対象: 1 2 9")
        rule.onNodeWithTag("overview-SYMBOLS").assertContentDescriptionEquals("記号、生成対象: [ ] ` \\")
        rule.runOnIdle { model.excludeSimilar = false }
        rule.onNodeWithTag("overview-UPPER").assertContentDescriptionEquals("大文字、生成対象: A I O Z")
        rule.onNodeWithTag("overview-DIGITS").assertContentDescriptionEquals("数字、生成対象: 0 1 2 9")
        rule.onNodeWithTag("overview-SYMBOLS").performScrollTo()
        rule.runOnIdle { assertTrue(model.results.isEmpty()); model.protectCapture = false }
        rule.waitForIdle(); android.os.SystemClock.sleep(2000)
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "character-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally { rule.runOnIdle { model.protectCapture = true } }
    }

    @Test fun individualCharacterChoiceReachesGenerator() {
        rule.runOnIdle {
            model.uppercaseCharacters = "A"; model.lowercase = false; model.digits = false; model.symbolsEnabled = false
            model.lengthText = "64"
        }
        rule.onNodeWithTag("character-Z").performClick()
        rule.runOnIdle { assertEquals("AZ", model.uppercaseCharacters) }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle { assertTrue(model.results.first().value.all { it in "AZ" }) }
    }

    @Test fun digitsOnlyIsACharacterPreset() {
        rule.onNodeWithText("数字のみ").performClick()
        rule.runOnIdle {
            assertTrue(model.isDigitsOnly)
            assertEquals("0123456789", model.digitCharacters)
            assertFalse(model.excludeSimilar)
        }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle { assertTrue(model.results.first().value.all { it in '0'..'9' }) }
    }

    @Test fun passphraseSeparatorReachesGenerator() {
        rule.onNodeWithText("パスフレーズ").performClick()
        rule.onNodeWithText("_").performClick()
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle { assertEquals(7, model.results.first().value.split('_').size) }
    }

    @Test fun screenshotForVisualReviewContainsNoGeneratedSecret() {
        rule.runOnIdle {
            assertTrue(model.results.isEmpty())
            // Test-only capture of the initial screen and masked results; no secrets are revealed.
            rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        rule.waitForIdle()
        // Wait for WindowManager's secure-surface update, which is outside Compose idling.
        android.os.SystemClock.sleep(4_000) // Also let Android's previous clipboard overlay expire.
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "initial-layout.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            rule.onNodeWithTag("generate").performClick()
            rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
            rule.runOnIdle { assertFalse(model.revealed) }
            android.os.SystemClock.sleep(500)
            val sheet = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "result-sheet.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            sheet.recycle()
        } finally { rule.runOnIdle { rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    }
}
