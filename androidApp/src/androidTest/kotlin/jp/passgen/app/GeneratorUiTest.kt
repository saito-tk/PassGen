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
import java.io.File

class GeneratorUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(rule.activity)[PassgenModel::class.java]

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
        rule.onNodeWithText("コピー", useUnmergedTree = true).performClick()
        rule.waitUntil(3000) { model.copiedIndex == 0 }
        rule.runOnIdle {
            val manager = rule.activity.getSystemService(ClipboardManager::class.java)
            assertTrue(manager.primaryClip?.getItemAt(0)?.text?.length == 24)
            assertTrue(manager.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
        }
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
        rule.onNodeWithText("記号").performClick()
        rule.onNodeWithText("記号一覧を貼り付け").performScrollTo().performClick()
        rule.onNodeWithText("使える記号の一覧を貼り付け").performTextReplacement("abc😀")
        rule.onNodeWithText("記号に反映").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("@", model.symbols) }
        rule.onNodeWithText("対応する記号がないため、選択は変更していません。").assertExists()
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
            // Test-only window flag change: production never permits screen capture.
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
