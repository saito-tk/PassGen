package jp.passgen.app

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import jp.passgen.core.StorageLessonExamples

class GeneratorUiTest {
    private fun emulatorShell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun changeFold(state: Int) {
        emulatorShell("cmd device_state state $state")
        emulatorShell("input keyevent KEYCODE_WAKEUP")
        emulatorShell("wm dismiss-keyguard")
    }
    private var lastFoldWake = 0L
    private fun keepFoldDisplayAwake() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFoldWake > 1000) {
            // Cover/inner display handover can power off after the first wake event.
            emulatorShell("input keyevent KEYCODE_WAKEUP"); emulatorShell("wm dismiss-keyguard")
            lastFoldWake = now
        }
    }
    @Test fun paneWidthsRespectBoundariesAndPhysicalHinge() {
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, generatorOrientation(599))
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, generatorOrientation(600))
        assertNull(generatorSplit(719f)); assertNull(generatorSplit(Float.NaN))
        val smallest = generatorSplit(720f)!!
        assertEquals(GeneratorSplit(360f, 0f), smallest)
        assertEquals(900f, generatorSplit(1800f)!!.settingsWidth)
        assertEquals(GeneratorSplit(402f, 0f), generatorSplit(804f))
        assertEquals(GeneratorSplit(390f, 20f), generatorSplit(800f, 390f, 410f))
        assertEquals(GeneratorSplit(350f, 0f), generatorSplit(700f, 350f, 350f))
        assertEquals(GeneratorSplit(400f, 24f), generatorSplit(900f, 400f, 424f))
        assertNull(generatorSplit(800f, 180f, 220f))
        assertEquals(GeneratorRegion(220f, 580f), generatorRegion(800f, 180f, 220f, 344f))
        assertEquals(GeneratorRegion(0f, 580f), generatorRegion(800f, 580f, 620f, 344f))
        assertEquals(GeneratorRegion(0f, 800f), generatorRegion(800f, 390f, 410f, 344f))
        assertEquals(GeneratorRegion(10f, 790f), generatorRegion(800f, -10f, 10f, 344f))
        assertEquals(GeneratorRegion(0f, 790f), generatorRegion(800f, 790f, 810f, 344f))
        assertEquals(GeneratorSplit(320f, 20f), generatorTabletop(700f, 320f, 340f))
        assertNull(generatorTabletop(400f, 100f, 120f))
    }
    @Test fun foldTransitionsKeepResultsSettingsCopyMarksAndRevealState() {
        org.junit.Assume.assumeTrue("専用emulatorのみで開閉を実行", android.os.Build.HARDWARE == "ranchu" &&
            InstrumentationRegistry.getArguments().getString("foldTransitions") == "true")
        changeFold(0)
        try {
            rule.waitUntil(15000) { keepFoldDisplayAwake(); rule.activity.resources.configuration.screenWidthDp < 600 &&
                rule.onAllNodesWithTag("results-pane").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("generate").fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle(); android.os.SystemClock.sleep(600)
            // Compare usable settings content, not the screen or outer padding.
            val coverSettingsWidthDp = rule.onNodeWithTag("generation-settings").fetchSemanticsNode().boundsInRoot.width /
                rule.activity.resources.displayMetrics.density
            rule.onNodeWithText("記号").performClick()
            rule.onNodeWithText("生成ルール").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
                model.lengthText = "64"; model.countText = "20"; model.autoClearClipboard = false
            }
            rule.onNodeWithTag("generate").performClick()
            rule.waitUntil(10000) { !model.busy && model.results.size == 20 }
            rule.onNodeWithTag("results-visibility").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("copy-0").performClick()
            lateinit var expected: List<String>
            lateinit var recipe: PresetRecipe
            rule.runOnIdle { expected = model.results.map { it.value }; recipe = PresetRecipe.capture(model) }
            repeat(2) {
                changeFold(2)
                rule.waitUntil(15000) { keepFoldDisplayAwake(); rule.activity.resources.configuration.screenWidthDp >= 720 && rule.activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                    rule.onAllNodesWithTag("results-pane").fetchSemanticsNodes().isNotEmpty() }
                rule.waitForIdle(); android.os.SystemClock.sleep(600)
                rule.onNodeWithTag("results-sheet").assertDoesNotExist()
                rule.onNodeWithText("記号を含める").assertExists()
                rule.onNodeWithText("各文字種を必ず含める").assertExists()
                rule.onNodeWithTag("generate").assertIsDisplayed().assertTextEquals("再生成")
                rule.onNodeWithTag("results-visibility").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "表示中"))
                rule.runOnIdle {
                    assertTrue(expected == model.results.map { it.value }); assertEquals(recipe, PresetRecipe.capture(model))
                    assertEquals(setOf(0), model.copiedIndices); assertTrue(model.revealed)
                }
                // The settings and results must be usable at the same time.
                val left = rule.onNodeWithTag("settings-pane").fetchSemanticsNode().boundsInRoot
                val right = rule.onNodeWithTag("results-pane").fetchSemanticsNode().boundsInRoot
                assertTrue(left.right <= right.left)
                val density = rule.activity.resources.displayMetrics.density
                // Scroll semantics cover the whole viewport, including padding.
                // Compare the settings card with the results Surface instead.
                val settingsCardWidth = rule.onNodeWithTag("generation-settings").fetchSemanticsNode().size.width.toFloat()
                val openSettingsWidthDp = settingsCardWidth / density
                println("Fold editor widths (dp): cover=$coverSettingsWidthDp, open=$openSettingsWidthDp, results=${right.width / density}")
                assertTrue("Unfolded settings $openSettingsWidthDp dp must fit the cover editor $coverSettingsWidthDp dp",
                    openSettingsWidthDp + 1f >= coverSettingsWidthDp)
                assertTrue("Fold panes should have equal content widths: settings=$settingsCardWidth, results=${right.width}",
                    kotlin.math.abs(settingsCardWidth - right.width) <= 1f)
                changeFold(0)
                rule.waitUntil(15000) { keepFoldDisplayAwake(); rule.activity.resources.configuration.screenWidthDp < 600 && rule.activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                    rule.onAllNodesWithTag("results-sheet").fetchSemanticsNodes().isNotEmpty() }
                rule.waitForIdle(); android.os.SystemClock.sleep(600)
                rule.onNodeWithTag("results-pane").assertDoesNotExist()
                rule.onNodeWithTag("regenerate").assertTextEquals("再生成")
                rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー済み。もう一度コピー")
                rule.runOnIdle { assertTrue(expected == model.results.map { it.value }); assertEquals(recipe, PresetRecipe.capture(model)) }
            }
        } finally { changeFold(0) }
    }
    @Test fun inlineResultsRemainWhenSettingsChangeAndClear() {
        org.junit.Assume.assumeTrue(rule.activity.resources.configuration.screenWidthDp >= 720)
        rule.onNodeWithTag("results-pane").assertIsDisplayed()
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.onNodeWithTag("results-sheet").assertDoesNotExist()
        rule.onNodeWithTag("copy-0").performClick()
        lateinit var beforeEdit: List<String>
        rule.runOnIdle { beforeEdit = model.results.map { it.value } }
        rule.onNodeWithText("数字").performClick()
        rule.onNodeWithContentDescription("数字 9").performClick()
        rule.runOnIdle {
            assertFalse('9' in model.digitCharacters)
            assertTrue(beforeEdit == model.results.map { it.value })
            model.lengthText = "64"
        }
        rule.onNodeWithTag("generate").assertTextEquals("生成")
        rule.onNodeWithTag("results-summary").assertTextEquals("24文字")
        rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー済み。もう一度コピー")
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.firstOrNull()?.value?.length == 64 }
        rule.runOnIdle { model.protectCapture = false }
        rule.waitForIdle(); android.os.SystemClock.sleep(600)
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.getExternalFilesDir(null), "task38-columns-open.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        rule.onNodeWithTag("clear-results").performClick()
        rule.onNodeWithTag("results-pane").assertIsDisplayed()
        rule.onNodeWithTag("generate").assertIsDisplayed().assertTextEquals("生成")
        rule.onNodeWithTag("results-sheet").assertDoesNotExist()
    }
    @Test fun copiedMarksStayCorrectAcrossPrefetchedResultRows() {
        rule.runOnIdle { model.lengthText = "64"; model.countText = "100" }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(10000) { !model.busy && model.results.size == 100 }
        rule.onNodeWithTag("copy-0").performClick()
        rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー済み。もう一度コピー")
        rule.onNodeWithTag("result-list").performScrollToIndex(80)
        rule.onNodeWithTag("copy-80").performClick()
        rule.onNodeWithTag("copy-80").assertContentDescriptionEquals("コピー済み。もう一度コピー")
        rule.runOnIdle { assertEquals(setOf(0, 80), model.copiedIndices) }
        rule.onNodeWithTag("result-list").performScrollToIndex(0)
        rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー済み。もう一度コピー")
    }
    @Test fun compactDisplayStaysPortraitWhenDeviceRotates() {
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE == "ranchu" && rule.activity.resources.configuration.smallestScreenWidthDp < 600)
        val auto = emulatorShell("settings get system accelerometer_rotation").trim().toIntOrNull()
        val rotation = emulatorShell("settings get system user_rotation").trim().toIntOrNull()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
            android.os.SystemClock.sleep(800); rule.waitForIdle()
            rule.runOnIdle {
                assertEquals(android.content.res.Configuration.ORIENTATION_PORTRAIT, rule.activity.resources.configuration.orientation)
                assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
            }
            rule.onNodeWithTag("generate").assertIsDisplayed()
        } finally {
            automation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            if (rotation != null && rotation in 0..3) emulatorShell("settings put system user_rotation $rotation")
            if (auto != null && auto in 0..1) emulatorShell("settings put system accelerometer_rotation $auto")
        }
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(rule.activity)[PassgenModel::class.java]

    @Before fun resetTestOwnedConfiguration() {
        rule.runOnIdle {
            model.background(); model.clear(); model.autoClearClipboard = true; model.clipboardSecondsText = "30"; model.protectCapture = true; model.revealed = false; model.mode = jp.passgen.core.GenerationMode.PASSWORD
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

    @Test fun presetQrImageRestoresExactSettingsWithoutChangingCurrentState() {
        rule.runOnIdle {
            val fields = PresetRecipe::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
            assertEquals("設定変更時はQR形式・旧reader・互換値・固定fixtureを確認する", setOf("mode", "length", "count", "words", "separator", "uppercase", "lowercase", "digits", "symbolsEnabled",
                "symbols", "excludeSimilar", "requireEach", "noAdjacent", "uppercaseCharacters", "lowercaseCharacters", "digitCharacters", "similarExclusions"), fields)
            val prefs = rule.activity.getSharedPreferences("test.qr.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            val first = PassgenModel(prefs)
            var restored: PassgenModel? = null
            try {
                first.mode = jp.passgen.core.GenerationMode.PASSPHRASE; first.words = 12; first.separator = "."
                first.lengthText = "64"; first.countText = "1000"; first.uppercaseCharacters = "AZ"
                first.lowercaseCharacters = "bm"; first.digitCharacters = "27"; first.symbols = "!{}\\"
                first.uppercase = false; first.symbolsEnabled = false; first.excludeSimilar = false
                first.requireEach = false; first.noAdjacent = true; first.similarExclusions = "0"
                first.savePreset("共有設定"); val original = first.presets.single()
                val text = first.presetQR(original)
                val image = PresetQrImage.encode(text)
                assertEquals(text, PresetQrImage.decode(image))
                val incoming = first.decodedPresetQR(text)
                assertEquals(original.recipe, incoming.recipe)
                first.mode = jp.passgen.core.GenerationMode.PASSWORD; first.lengthText = "24"
                first.revealed = true; first.protectCapture = false; first.copiedIndices = setOf(0)
                val before = PresetRecipe.capture(first)
                assertThrows(PresetException::class.java) { first.importPreset(incoming, original.name) }
                assertEquals(1, first.presets.size)
                first.importPreset(incoming, "受け取った設定")
                assertEquals(before, PresetRecipe.capture(first)); assertEquals(setOf(0), first.copiedIndices)
                assertTrue(first.revealed); assertFalse(first.protectCapture)
                assertEquals(original, first.presets.last()); assertNotEquals(original.id, first.presets.first().id)
                val next = PassgenModel(prefs); restored = next
                assertEquals(original.recipe, next.presets.first().recipe)
                val vector = "PG1:H4MACAP77777777777742DYAZAHUK6DBNVYGYZP3KHHRA"
                assertEquals(vector, first.presetQR(first.decodedPresetQR(vector)))
                // Permanent image from Apple's encoder: verify interoperability, not just our own writer.
                val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("ios-pg1.png").use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                val ios = first.decodedPresetQR(PresetQrImage.decode(fixture)).recipe
                assertEquals(PresetRecipe(jp.passgen.core.GenerationMode.PASSWORD, 64, 3, 7, " ",
                    false, true, true, false, "!{}\\", false, false, true, "AZ", "bm", "27", "0"), ios)
                val known = PresetQrImage.encode(vector)
                val exported = File(rule.activity.getExternalFilesDir(null), "task37-android-qr.png")
                exported.outputStream().use { known.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertEquals(vector, PresetQrImage.read(rule.activity, android.net.Uri.fromFile(exported)))
                val blank = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                assertThrows(Exception::class.java) { PresetQrImage.decode(blank) }
                assertThrows(Exception::class.java) { first.decodedPresetQR(vector.replace("PG1:", "PG2:")) }
            } finally { first.viewModelScope.cancel(); restored?.viewModelScope?.cancel(); prefs.edit().clear().apply() }
        }
    }

    @Test fun presetQrShareAndReviewRequireExplicitNewRegistration() {
        val name = "QR-${System.nanoTime()}"
        val owned = mutableListOf<String>()
        try {
            lateinit var original: GeneratorPreset
            rule.runOnIdle { model.protectCapture = false; model.savePreset(name); original = model.presets.first(); owned += original.id }
            rule.onNodeWithTag("presets").performClick()
            rule.onNodeWithTag("preset-qr-read").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithContentDescription("${name}の編集").performClick()
            rule.onNodeWithText("QRコードで共有").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("preset-qr-image").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("preset-qr-image").assertIsDisplayed()
            rule.onNodeWithTag("preset-qr-share").assertIsEnabled()
            rule.waitForIdle(); android.os.SystemClock.sleep(600) // Allow the rendered frame, not only its semantics, to reach the screenshot.
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(rule.activity.getExternalFilesDir(null), "task37-qr-share-ui.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            rule.onNodeWithTag("preset-qr-share").performClick()
            // Activity pause precedes the chooser's input focus. Wait for the actual system window.
            rule.waitUntil(10000) {
                val owner = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()
                owner == "com.android.intentresolver" || owner == "android"
            }
            emulatorShell("input keyevent KEYCODE_BACK")
            rule.waitUntil(10000) { rule.activity.lifecycle.currentState == Lifecycle.State.RESUMED }
            rule.onNodeWithTag("preset-qr-back").performClick()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            var saved = false
            rule.runOnIdle {
                rule.activity.setContent { PassgenTheme {
                    androidx.compose.material3.Surface {
                        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                            PresetQrReview(original, model, back = {}, saved = { saved = true })
                        }
                    }
                } }
            }
            rule.onNodeWithTag("preset-qr-register").assertIsNotEnabled()
            rule.onNodeWithTag("preset-qr-name").performTextReplacement("$name-import")
            rule.onNodeWithTag("preset-qr-name").performImeAction()
            rule.onNodeWithTag("preset-qr-register").assertIsEnabled().performClick()
            rule.runOnIdle {
                assertTrue(saved)
                val received = model.presets.first { it.name == "$name-import" }; owned += received.id
                assertEquals(original.recipe, received.recipe)
                assertEquals(original.recipe, PresetRecipe.capture(model))
                assertEquals(original, model.presets.first { it.id == original.id })
            }
        } finally { rule.runOnIdle { owned.forEach { id -> if (model.presets.any { it.id == id }) model.deletePreset(id) } } }
    }

    @Test fun namedPresetsRestoreGenerationSettingsWithoutSecretsOrSecurity() {
        rule.runOnIdle {
            val prefs = rule.activity.getSharedPreferences("test.presets.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            val first = PassgenModel(prefs)
            var second: PassgenModel? = null
            try {
                first.mode = jp.passgen.core.GenerationMode.PASSPHRASE; first.words = 10; first.separator = "_"
                first.lengthText = "64"; first.countText = "2"; first.uppercaseCharacters = "AZ"; first.lowercaseCharacters = "bm"
                first.digitCharacters = "27"; first.symbols = "!@"; first.uppercase = false; first.symbolsEnabled = false
                first.requireEach = false; first.excludeSimilar = false; first.noAdjacent = true; first.similarExclusions = "0"
                val expected = PresetRecipe.capture(first)
                first.savePreset("  Mail  "); val id = first.presets.first().id
                val raw = prefs.getString("generation.presets.v1", "")!!
                assertFalse(raw.contains("revealed")); assertFalse(raw.contains("protectCapture")); assertFalse(raw.contains("results"))
                first.lengthText = "24"; first.revealed = true; first.protectCapture = false; first.background()
                val loaded = PassgenModel(prefs); second = loaded
                assertEquals(expected, loaded.presets.first().recipe); assertEquals("Mail", loaded.presets.first().name)
                loaded.applyPreset(id); assertEquals(expected, PresetRecipe.capture(loaded))
                assertTrue(loaded.revealed); assertFalse(loaded.protectCapture); assertTrue(loaded.results.isEmpty())
                loaded.lengthText = "80"; loaded.replacePreset(id); loaded.renamePreset(id, "Work")
                val entry = org.json.JSONObject(prefs.getString("generation.presets.v1", "")!!).getJSONArray("presets").getJSONObject(0)
                assertEquals("Work", entry.getString("name")); assertEquals(80, entry.getJSONObject("recipe").getInt("length"))
                loaded.deletePreset(id); assertTrue(loaded.presets.isEmpty())
            } finally { first.viewModelScope.cancel(); second?.viewModelScope?.cancel(); prefs.edit().clear().apply() }
        }
    }
    @Test fun presetValidationProtectsDuplicatesCapacityAndUnreadableData() {
        rule.runOnIdle {
            val prefs = rule.activity.getSharedPreferences("test.presets.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            val model = PassgenModel(prefs)
            var blocked: PassgenModel? = null
            try {
                for (name in listOf("", " ", "bad\nname", "a".repeat(41))) assertThrows(PresetException::class.java) { model.savePreset(name) }
                model.savePreset("Mail"); assertThrows(PresetException::class.java) { model.savePreset(" mail ") }
                for (index in 1..49) model.savePreset("Preset $index")
                assertThrows(PresetException::class.java) { model.savePreset("Overflow") }; assertEquals(50, model.presets.size)
                val future = """{"version":99,"presets":[]}"""
                prefs.edit().putString("generation.presets.v1", future).apply()
                val unreadable = PassgenModel(prefs); blocked = unreadable
                assertNotNull(unreadable.presetStorageIssue)
                assertThrows(PresetException::class.java) { unreadable.savePreset("New") }
                assertEquals(future, prefs.getString("generation.presets.v1", null))
            } finally { model.viewModelScope.cancel(); blocked?.viewModelScope?.cancel(); prefs.edit().clear().apply() }
        }
    }
    @Test fun presetsCanBeSavedLoadedRenamedAndDeletedFromTheLeftToolbar() {
        val name = "UITest-${System.nanoTime()}"
        var owned: String? = null
        try {
            rule.onNodeWithText("数字のみ").assertDoesNotExist()
            rule.runOnIdle { model.lengthText = "64" }
            rule.onNodeWithTag("presets").performClick()
            rule.onNodeWithTag("preset-name").performTextInput(name)
            rule.onNodeWithTag("preset-name").performImeAction()
            rule.onNodeWithTag("preset-save").assertIsDisplayed().assertIsEnabled().performClick()
            rule.runOnIdle { owned = model.presets.first { it.name == name }.id }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.runOnIdle { model.lengthText = "24" }
            rule.onNodeWithTag("presets").performClick()
            rule.onNodeWithContentDescription("${name}を読み込む").performClick()
            rule.runOnIdle { assertEquals(64, model.length); assertTrue(model.results.isEmpty()) }
            rule.onNodeWithTag("presets").performClick()
            rule.onNodeWithContentDescription("${name}の編集").performClick(); rule.onNodeWithText("名前を変更").performClick()
            rule.onNodeWithTag("preset-rename-name").performClick().performTextReplacement("${name}-new")
            rule.onNodeWithTag("preset-rename-name").performImeAction()
            rule.onNodeWithTag("preset-confirm").performClick()
            rule.waitUntil(3000) { rule.onAllNodesWithContentDescription("${name}-newの編集").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("${name}-newの編集").performClick(); rule.onNodeWithText("削除").performClick()
            rule.onNodeWithTag("preset-confirm").performClick()
            rule.runOnIdle { assertTrue(model.presets.none { it.id == owned }) }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.onNodeWithTag("generate").assertIsDisplayed()
        } finally { rule.runOnIdle { owned?.let { id -> if (model.presets.any { it.id == id }) model.deletePreset(id) } } }
    }

    @Test fun onlySettingsAndRevealPreferenceSurviveNewModel() {
        rule.runOnIdle {
            val prefs = rule.activity.getSharedPreferences("test.preferences.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            val first = PassgenModel(prefs)
            first.editorSession.presetName.value = "未保存"
            first.editorSession.symbolText.value = "!@#"
            first.lengthText = "48"; first.uppercaseCharacters = "AZ"; first.revealed = true
            first.noAdjacent = true; first.words = 9; first.separator = "_"; first.background()
            first.clipboardSecondsText = "90"; first.protectCapture = false; first.background()
            val restored = PassgenModel(prefs)
            assertEquals("", restored.editorSession.presetName.value)
            assertEquals("", restored.editorSession.symbolText.value)
            assertEquals(48, restored.length); assertEquals("AZ", restored.uppercaseCharacters)
            assertTrue(restored.revealed); assertTrue(restored.noAdjacent); assertEquals(9, restored.words)
            assertEquals("_", restored.separator); assertTrue(restored.results.isEmpty())
            assertEquals(90, restored.clipboardSeconds); assertFalse(restored.protectCapture)
            restored.chooseMode(jp.passgen.core.GenerationMode.PASSPHRASE)
            restored.chooseMode(jp.passgen.core.GenerationMode.PASSWORD)
            assertEquals(48, restored.length)
            prefs.edit().clear().apply()
        }
    }

    @Test fun legacyBackgroundClipboardFlagDoesNotResetPreferences() {
        rule.runOnIdle {
            val prefs = rule.activity.getSharedPreferences("test.preferences.${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
            prefs.edit().putString("generation.preferences.v1", """{"version":1,"length":48,"revealed":true,"clipboardSeconds":90,"autoClearClipboard":false,"protectCapture":false,"clearClipboardOnBackground":true}""").apply()
            val restored = PassgenModel(prefs)
            assertEquals(48, restored.length); assertTrue(restored.revealed)
            assertEquals(90, restored.clipboardSeconds); assertFalse(restored.autoClearClipboard); assertFalse(restored.protectCapture)
            val stored = org.json.JSONObject(prefs.getString("generation.preferences.v1", null)!!)
            assertFalse(stored.has("clearClipboardOnBackground"))
            prefs.edit().clear().apply()
        }
    }

    @Test fun clipboardRemainsUntilDeadlineAcrossBackground() {
        rule.runOnIdle { SecureClipboard(rule.activity).copy("fake-test-only", 5) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnIdle {
            val manager = rule.activity.getSystemService(ClipboardManager::class.java)
            assertTrue(manager.primaryClip?.getItemAt(0)?.text?.toString() == "fake-test-only")
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        android.os.SystemClock.sleep(6000)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(3000) { rule.activity.hasWindowFocus() }
        rule.runOnIdle { assertFalse(rule.activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip()) }
    }

    @Test fun clipboardExpiryPreservesReplacementAfterBackground() {
        rule.runOnIdle {
            SecureClipboard(rule.activity).copy("fake-test-only", 5)
            rule.activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("another-app", "replacement"))
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        android.os.SystemClock.sleep(6000)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(3000) { rule.activity.hasWindowFocus() }
        rule.runOnIdle {
            val manager = rule.activity.getSystemService(ClipboardManager::class.java)
            assertTrue(manager.primaryClip?.getItemAt(0)?.text?.toString() == "replacement")
            manager.clearPrimaryClip()
        }
    }

    @Test fun regenerationAndBackgroundKeepVisibilityPreference() {
        rule.runOnIdle { model.revealed = true; model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.revealed); model.generateOrStop() }
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.revealed); model.background(); assertTrue(model.revealed); assertTrue(model.results.isNotEmpty()) }
    }

    @Test fun launchDoesNotGenerateAndWindowIsSecure() {
        rule.runOnIdle {
            assertTrue(model.results.isEmpty())
            assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        rule.onNodeWithTag("generate").assertIsEnabled()
    }

    @Test fun emptyEnabledGroupsGenerateUntilNoUsableCharactersRemain() {
        rule.runOnIdle {
            model.uppercaseCharacters = ""; model.lowercaseCharacters = ""; model.symbols = ""
            model.digitCharacters = "79"
            assertNotNull(model.estimate()); assertNull(model.configurationIssue); assertTrue(model.isDigitsOnly)
        }
        rule.onNodeWithTag("generate").assertIsEnabled().performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle {
            assertTrue(model.results.single().value.all { it in "79" })
            assertTrue(model.uppercase && model.lowercase && model.symbolsEnabled)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.runOnIdle { model.digitCharacters = ""; assertFalse(model.isDigitsOnly) }
        rule.onNodeWithTag("generate").assertIsNotEnabled()
        rule.onNodeWithText("生成に使える文字を1文字以上選択してください。").assertIsDisplayed()
        rule.runOnIdle { model.uppercase = false; model.lowercase = false; model.digits = false; model.symbolsEnabled = false }
        rule.onNodeWithTag("generate").assertIsNotEnabled()
        rule.onNodeWithText("生成に使える文字を1文字以上選択してください。").assertIsDisplayed()
    }

    @Test fun resultPreviewBreaksAtEveryCharacterAndPreservesCopyValue() {
        val original = "abcd/".repeat(12) + "wxyz"
        assertEquals(64, original.length)
        assertEquals(original, resultPreview(original, true).replace("\u200B", ""))
        assertEquals(100, resultPreview("a/".repeat(80), true).replace("\u200B", "").length)
        rule.runOnIdle { model.lengthText = "64"; model.revealed = true }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.onNodeWithTag("generate").assertDoesNotExist()
        rule.onNodeWithTag("regenerate").assertIsEnabled()
        rule.onNodeWithText("閉じる").assertDoesNotExist()
        rule.onNodeWithTag("copy-0").performClick()
        rule.runOnIdle {
            val copied = rule.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
            assertEquals(model.results.first().value, copied)
            assertEquals(64, copied?.length)
            assertFalse(copied?.contains('\u200B') ?: true)
        }
        rule.onNodeWithTag("regenerate").performClick()
        rule.waitUntil(5000) { !model.busy }
        rule.runOnIdle { assertTrue(model.revealed) }
    }

    @Test fun actionTitleAndBackgroundRetentionFollowCurrentGenerationSettings() {
        rule.onNodeWithTag("generate").assertTextEquals("生成").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.onNodeWithTag("regenerate").assertTextEquals("再生成")
        rule.onNodeWithTag("copy-0").performClick()
        val before = rule.runOnIdle { model.results.map { it.value } }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(before, model.results.map { it.value }); assertEquals(setOf(0), model.copiedIndices)
            model.symbols = "@"; assertEquals("生成", model.generationActionTitle)
            model.generateOrStop()
        }
        rule.waitUntil(5000) { !model.busy }
        rule.onNodeWithTag("regenerate").assertTextEquals("再生成")
        rule.onNodeWithTag("clear-results").performClick()
        rule.onNodeWithTag("generate").assertIsDisplayed().assertTextEquals("生成")
        rule.onNodeWithTag("regenerate").assertDoesNotExist()
        rule.runOnIdle { assertTrue(model.results.isEmpty()); assertTrue(model.copiedIndices.isEmpty()) }
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
        rule.onNodeWithTag("storage-plaintext-value").performScrollTo().assertTextEquals(StorageLessonExamples.PASSWORD)
        saveGuideScreenshot("storage-plaintext.png")
        rule.onNodeWithTag("storage-hash-value").performScrollTo().assertTextEquals(StorageLessonExamples.COMMON_SALT_HASH)
        rule.onNodeWithText("Argon2idで計算した保存形式の一例です。方式・設定・ソルトも含まれます。").performScrollTo()
        saveGuideScreenshot("storage-full-hash.png")
        rule.onNodeWithTag("hash-details").performScrollTo().performClick()
        rule.onNodeWithTag("hash-guessing").assertExists()
        rule.onNodeWithTag("salt-hash-a").assertTextEquals(StorageLessonExamples.COMMON_SALT_HASH)
        rule.onNodeWithTag("salt-hash-b").assertTextEquals(StorageLessonExamples.COMMON_SALT_HASH)
        rule.onNodeWithTag("salt-outcome").performScrollTo().assertTextEquals("保存値の一致が、同じパスワードの手掛かりに。")
        saveGuideScreenshot("salt-common.png")
        rule.onNodeWithTag("salt-unique").performScrollTo().performClick()
        rule.onNodeWithTag("salt-hash-a").assertTextEquals(StorageLessonExamples.INDIVIDUAL_SALT_HASH_A)
        rule.onNodeWithTag("salt-hash-b").assertTextEquals(StorageLessonExamples.INDIVIDUAL_SALT_HASH_B)
        rule.onNodeWithTag("salt-outcome").performScrollTo().assertTextEquals("同じパスワードでも、保存値は異なります。")
        saveGuideScreenshot("salt-unique.png")
        rule.onNodeWithTag("salt-common").performScrollTo().performClick()
        rule.onNodeWithTag("salt-hash-b").assertTextEquals(StorageLessonExamples.COMMON_SALT_HASH)
        rule.onNodeWithTag("storage-topic-1").performClick()
        rule.onNodeWithTag("reuse-outcome").assertTextEquals("同じ値が別のサービスでも試されます。")
        rule.onNodeWithTag("reuse-outcome").performScrollTo()
        for (tag in listOf("reuse-password-a", "reuse-password-b", "reuse-password-c")) {
            rule.onNodeWithTag(tag).assertTextEquals(StorageLessonExamples.PASSWORD)
        }
        saveGuideScreenshot("storage-reuse.png")
        rule.onNodeWithTag("reuse-unique").performScrollTo().performClick()
        rule.onNodeWithTag("reuse-outcome").assertTextEquals("Aで漏れた値は、BとCのパスワードには一致しません。")
        rule.onNodeWithTag("reuse-outcome").performScrollTo()
        rule.onNodeWithTag("reuse-password-b").assertTextEquals(StorageLessonExamples.PASSWORD_B)
        rule.onNodeWithTag("reuse-password-c").assertTextEquals(StorageLessonExamples.PASSWORD_C)
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

    @Test fun passwordExamplesShowPatternsAndInteractiveComparisons() {
        allowGuideScreenshots()
        rule.onNodeWithTag("safety-settings").performClick(); rule.onNodeWithTag("security-guide").performClick()
        rule.onNodeWithTag("password-examples-guide").performScrollTo().performClick()
        val choices = (0..2).map { rule.onNodeWithTag("password-topic-$it").getUnclippedBoundsInRoot() }
        assertEquals(choices[0].bottom - choices[0].top, choices[1].bottom - choices[1].top)
        assertEquals(choices[0].bottom - choices[0].top, choices[2].bottom - choices[2].top)
        for (sample in jp.passgen.core.SecurityGuideExamples.weakExamples) {
            rule.onNodeWithTag("weak-value-${sample.key}").performScrollTo().assertTextEquals(sample.value)
        }
        rule.onNodeWithTag("weak-mutation-2").performScrollTo().assertTextEquals("P@ssw0rd1!")
        saveGuideScreenshot("password-mutations.png")
        rule.onNodeWithTag("weak-value-word").performScrollTo()
        saveGuideScreenshot("password-weak-examples.png")
        rule.onNodeWithTag("password-topic-1").performClick()
        rule.onNodeWithTag("guide-length-count").assertTextEquals("1万通り")
        rule.onNodeWithTag("guide-length-1").performClick()
        rule.onNodeWithTag("guide-length-value").assertTextEquals("604839")
        rule.onNodeWithTag("guide-length-count").assertTextEquals("100万通り")
        rule.onNodeWithTag("guide-length-2").performClick()
        rule.onNodeWithTag("guide-length-count").assertTextEquals("1億通り")
        saveGuideScreenshot("password-length.png")
        rule.onNodeWithTag("password-topic-2").performClick()
        for ((index, separator) in listOf(" ", "-", "_").withIndex()) {
            rule.onNodeWithTag("guide-separator-$index").performScrollTo().performClick()
            rule.onNodeWithTag("guide-phrase-value").assertTextEquals(jp.passgen.core.SecurityGuideExamples.phrase(separator))
        }
        rule.onNodeWithTag("guide-phrase-value").performScrollTo()
        saveGuideScreenshot("password-phrase.png")
        rule.onNodeWithTag("password-examples-back").performClick()
        rule.onNodeWithTag("password-length-guide").performScrollTo().performClick()
        rule.onNodeWithTag("guide-length-value").assertTextEquals("4827")
        rule.runOnIdle { assertTrue(model.results.isEmpty()) }
    }

    @Test fun guideShowsConcreteAccountAuthenticationAndLookalikeExamples() {
        allowGuideScreenshots()
        rule.onNodeWithTag("safety-settings").performClick(); rule.onNodeWithTag("security-guide").performClick()
        rule.onNodeWithTag("guide-manager-password").performScrollTo().assertTextEquals(jp.passgen.core.SecurityGuideExamples.PASSWORD)
        saveGuideScreenshot("guide-manager.png")
        rule.onNodeWithText("482915（説明用）").performScrollTo().assertExists()
        saveGuideScreenshot("guide-authentication.png")
        rule.onNodeWithText("端末で本人確認").performScrollTo().assertExists()
        rule.onNodeWithTag("guide-lookalike-url").performScrollTo().assertTextEquals(jp.passgen.core.SecurityGuideExamples.LOOKALIKE_URL)
        saveGuideScreenshot("guide-lookalike.png")
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

    @Test fun backgroundCancelsAndRejectsLateWork() {
        rule.runOnIdle { model.lengthText = "999999"; model.countText = "10"; model.generateOrStop(); model.background() }
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

    @Test fun symbolPasteDoneClearsFocusWithoutApplying() {
        allowGuideScreenshots()
        rule.runOnIdle { model.symbols = "!@#" }
        rule.onAllNodesWithText("記号")[0].performClick()
        rule.onNodeWithText("記号一覧を貼り付け").performScrollTo().performClick()
        val field = rule.onNodeWithTag("symbol-paste-input")
        field.performClick().performTextInput("@#%")
        field.assertIsFocused().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ImeAction, androidx.compose.ui.text.input.ImeAction.Done))
        field.performImeAction()
        field.assertIsNotFocused().assertTextContains("@#%")
        rule.runOnIdle { assertEquals("!@#", model.symbols) }
        rule.waitUntil(3000) {
            var visible = false
            rule.runOnIdle {
                visible = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
            !visible
        }
        saveGuideScreenshot("outlined-symbol-input.png")
        rule.onNodeWithText("記号に反映").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(setOf('@', '#', '%'), model.symbols.toSet()) }
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
        rule.onNodeWithTag("character-Z").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("AZ", model.uppercaseCharacters) }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(5000) { !model.busy && model.results.isNotEmpty() }
        rule.runOnIdle { assertTrue(model.results.first().value.all { it in "AZ" }) }
    }

    @Test fun digitsOnlyCanBeConfiguredWithoutTheShortcut() {
        rule.onNodeWithText("数字のみ").assertDoesNotExist()
        rule.runOnIdle { model.uppercase = false; model.lowercase = false; model.symbolsEnabled = false; model.excludeSimilar = false }
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

    @Test fun officialMaterialIconGalleryInLightAndDark() {
        val names = "bookmark settings visibility visibility_off refresh stop delete add remove expand_more expand_less tune content_paste qr_code_scanner qr_code_2 share arrow_back more_vert save edit chevron_right open_in_new verified_user key menu_book select_all deselect close check_circle content_copy check".split(" ")
        rule.runOnIdle {
            assertTrue(model.results.isEmpty())
            rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            rule.activity.setContent {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().systemBarsPadding()) {
                    listOf(false, true).forEach { dark ->
                        val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
                        configuration.uiMode = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                            PassgenTheme {
                                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.weight(1f).fillMaxWidth()) {
                                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(8.dp)) {
                                        names.chunked(7).forEach { row ->
                                            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
                                                row.forEach { name ->
                                                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f).heightIn(min = 60.dp),
                                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                                        val icon = rule.activity.resources.getIdentifier("ic_$name", "drawable", rule.activity.packageName)
                                                        AppIcon(icon)
                                                        androidx.compose.material3.Text(name, fontSize = 8.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.getExternalFilesDir(null), "task39-icons-light-dark.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        rule.runOnIdle { rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test fun presetDraftSurvivesRecreationWithoutBeingSaved() {
        rule.onNodeWithTag("presets").performClick()
        rule.onNodeWithTag("preset-name").performTextInput("未保存の下書き")
        rule.onNodeWithTag("preset-name").performImeAction()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("preset-name").assertTextContains("未保存の下書き")
        rule.runOnIdle { assertTrue(model.presets.none { it.name == "未保存の下書き" }) }
    }

    @Test fun pastedSymbolDraftSurvivesRecreationUntilDismissed() {
        rule.onNodeWithText("記号").performClick()
        rule.onNodeWithText("記号一覧を貼り付け").performScrollTo().performClick()
        rule.onNodeWithTag("symbol-paste-input").performTextInput("!@#")
        rule.onNodeWithTag("symbol-paste-input").performImeAction()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("symbol-paste-input").assertTextContains("!@#")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("symbol-paste-input").fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { assertEquals("", model.editorSession.symbolText.value) }
    }

    @Test fun cameraFramesDecodeOnlyAppQrWithCropStrideAndRotation() {
        val vector = "PG1:H4MACAP77777777777742DYAZAHUK6DBNVYGYZP3KHHRA"
        fun decode(bitmap: Bitmap, inverted: Boolean = false): String? {
            val w = bitmap.width + 16; val h = bitmap.height + 12; val stride = w * 2 + 16
            val bytes = ByteArray(7 + stride * h) { (if (inverted) 0 else 255).toByte() }
            repeat(bitmap.height) { y -> repeat(bitmap.width) { x ->
                val light = android.graphics.Color.red(bitmap.getPixel(x, y))
                bytes[7 + (y + 6) * stride + (x + 8) * 2] = (if (inverted) 255 - light else light).toByte()
            } }
            val buffer = java.nio.ByteBuffer.wrap(bytes).apply { position(7) }
            return PresetQrFrames.decode(buffer, stride, 2, w, h, android.graphics.Rect(8, 6, 8 + bitmap.width, 6 + bitmap.height))
        }
        val original = PresetQrImage.encode(vector)
        try {
            for (angle in listOf(0f, 90f, 180f, 270f)) {
                val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height,
                    android.graphics.Matrix().apply { postRotate(angle) }, false)
                assertEquals(vector, decode(rotated)); if (rotated !== original) rotated.recycle()
            }
            assertEquals(vector, decode(original, inverted = true))
            val foreign = PresetQrImage.encode("https://example.com/")
            try {
                assertNull(decode(foreign))
                val mixed = Bitmap.createBitmap(1584, 800, Bitmap.Config.ARGB_8888)
                try {
                    android.graphics.Canvas(mixed).apply {
                        drawColor(android.graphics.Color.WHITE); drawBitmap(original, 8f, 16f, null); drawBitmap(foreign, 808f, 16f, null)
                    }
                    assertEquals(vector, decode(mixed))
                } finally { mixed.recycle() }
            } finally { foreign.recycle() }
            assertNull(PresetQrFrames.decode(java.nio.ByteBuffer.allocate(8), 32, 1, 32, 32, android.graphics.Rect(0, 0, 32, 32)))
            assertNull(PresetQrFrames.decode(java.nio.ByteBuffer.allocate(8), 1, 1, 32, 32, android.graphics.Rect(-1, 0, 1, 1)))
        } finally { original.recycle() }
    }

    @Test fun cameraMenuPermissionDenialAndCloseKeepExistingSettings() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val granted = rule.activity.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeFalse("Run before granting the camera permission", granted)
        val before = rule.runOnIdle { PresetRecipe.capture(model) to model.presets }
        rule.onNodeWithTag("presets").performClick(); rule.onNodeWithTag("preset-qr-read").performClick()
        rule.onNodeWithTag("preset-qr-image-read").assertIsDisplayed()
        rule.onNodeWithTag("preset-qr-camera-read").performClick()
        rule.waitUntil(5000) {
            val root = automation.rootInActiveWindow
            root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")?.firstOrNull()
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        rule.onNodeWithText("設定を開く").assertIsDisplayed()
        rule.onNodeWithContentDescription("カメラを閉じる").performClick()
        rule.onNodeWithTag("preset-name").assertIsDisplayed()
        rule.runOnIdle { assertEquals(before.first, PresetRecipe.capture(model)); assertEquals(before.second, model.presets) }
    }

    @Test fun emulatorCameraReadsValidPresetIntoReviewOnly() {
        org.junit.Assume.assumeTrue("Dedicated emulator with a QR image camera only", android.os.Build.HARDWARE == "ranchu" &&
            InstrumentationRegistry.getArguments().getString("qrCameraFixture") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(rule.activity.packageName, android.Manifest.permission.CAMERA)
        val before = rule.runOnIdle { model.protectCapture = false; PresetRecipe.capture(model) to model.presets }
        rule.onNodeWithTag("presets").performClick(); rule.onNodeWithTag("preset-qr-read").performClick()
        rule.onNodeWithTag("preset-qr-camera-read").performClick()
        try { rule.waitUntil(20000) { rule.onAllNodesWithTag("preset-qr-name").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            val image = automation.takeScreenshot()
            File(rule.activity.getExternalFilesDir(null), "task41-camera-failure.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle(); throw error
        }
        rule.onNodeWithTag("preset-qr-name").assertTextContains("Example")
        rule.onNodeWithTag("preset-qr-register").assertExists()
        rule.onNodeWithTag("preset-qr-camera").assertDoesNotExist()
        rule.runOnIdle { assertEquals(before.first, PresetRecipe.capture(model)); assertEquals(before.second, model.presets) }
    }

    @Test fun bottomSheetsRemainStableAfterUpwardOverscroll() {
        org.junit.Assume.assumeTrue(rule.activity.resources.configuration.screenWidthDp < 600)
        fun exercise(label: String, tag: String, scrollTag: String = tag) {
            repeat(10) {
                rule.onNodeWithTag(scrollTag).performTouchInput {
                    swipe(start = androidx.compose.ui.geometry.Offset(width * .4f, height * .8f),
                        end = androidx.compose.ui.geometry.Offset(width * .4f, height * .25f), durationMillis = 100)
                }
            }
            repeat(3) {
                rule.onNodeWithTag("sheet-handle", useUnmergedTree = true).performTouchInput { swipeUp(durationMillis = 100) }
            }
            rule.waitForIdle()
            val sheet = rule.onNodeWithTag(tag).fetchSemanticsNode()
            // Observe a descendant of Material's anchor modifier: the outer sheet tag itself
            // can stay in place while the rendered Surface is translated inside it.
            val handle = rule.onNodeWithTag("sheet-handle", useUnmergedTree = true).fetchSemanticsNode()
            val tops = mutableListOf<Float>()
            val heights = mutableListOf<Float>()
            repeat(90) {
                rule.mainClock.advanceTimeByFrame()
                rule.runOnUiThread {
                    if (it >= 30) { tops += handle.boundsInRoot.top; heights += sheet.boundsInRoot.height }
                }
                android.os.SystemClock.sleep(16) // Include native RenderThread / Android edge effects.
            }
            assertTrue("$label keeps moving: top span ${tops.max() - tops.min()}, height span ${heights.max() - heights.min()}",
                tops.max() - tops.min() <= 1f && heights.max() - heights.min() <= 1f)
        }
        fun dismissByDragging(tag: String) {
            val distance = rule.activity.resources.displayMetrics.density * 250f
            rule.onNodeWithTag("sheet-handle", useUnmergedTree = true).performTouchInput {
                swipe(center, center + androidx.compose.ui.geometry.Offset(0f, distance), durationMillis = 200)
            }
            rule.waitUntil(5000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
        }
        val owned = mutableListOf<String>()
        try {
            rule.runOnIdle { model.countText = "100"; model.lengthText = "64" }
            rule.onNodeWithTag("generate").performClick()
            rule.waitUntil(10000) { !model.busy && model.results.size == 100 }
            exercise("long results", "results-sheet", "result-list")
            rule.onNodeWithTag("copy-99").assertIsDisplayed()
            dismissByDragging("results-sheet")
            rule.runOnIdle { model.countText = "1"; model.lengthText = "128"; model.revealed = true }
            rule.onNodeWithTag("generate").performClick()
            rule.waitUntil(5000) { !model.busy && model.results.size == 1 }
            exercise("short revealed results", "results-sheet", "result-list")
            dismissByDragging("results-sheet")
            rule.runOnIdle {
                repeat(20) {
                    val name = "overscroll-${System.nanoTime()}-$it"
                    model.savePreset(name); owned += model.presets.single { it.name == name }.id
                }
            }
            rule.onNodeWithTag("presets").performClick()
            exercise("presets", "modal-sheet")
            dismissByDragging("modal-sheet")
            rule.onNodeWithTag("safety-settings").performClick()
            exercise("settings", "modal-sheet")
            dismissByDragging("modal-sheet")
        } finally { rule.runOnIdle { owned.forEach { id -> if (model.presets.any { it.id == id }) model.deletePreset(id) } } }
    }

    @Test fun materialControlsHaveAccessibleNamesAndTouchTargets() {
        rule.onNodeWithTag("presets").assertContentDescriptionEquals("プリセット").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("safety-settings").assertContentDescriptionEquals("設定と安全性").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("character-A").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsOn()
        rule.onNodeWithText("大文字を含める").performClick()
        rule.onNodeWithTag("character-A").assertIsOff().assertIsNotEnabled()
        rule.onNodeWithText("大文字を含める").performClick()
        rule.onNodeWithTag("character-A").assertIsOn()
        rule.onNodeWithText("数字").performClick()
        rule.onNodeWithContentDescription("数字の選択を変更").performClick()
        rule.onNodeWithText("選択を解除").performClick()
        rule.runOnIdle { assertTrue(model.digitCharacters.isEmpty()); assertNull(model.configurationIssue) }
        rule.onNodeWithContentDescription("数字の選択を変更").performClick()
        rule.onNodeWithText("全選択").performClick()
        rule.onNodeWithTag("character-9").assertIsOn()
        rule.onNodeWithTag("configuration-issue").assertDoesNotExist()
        rule.runOnIdle { model.uppercase = false; model.lowercase = false; model.digits = false; model.symbolsEnabled = false }
        rule.onNodeWithTag("configuration-issue").assertIsDisplayed()
        rule.onNodeWithTag("generate").assertIsNotEnabled()
    }

    private fun captureReview(name: String) {
        rule.runOnIdle { model.protectCapture = false; assertFalse(model.revealed) }
        rule.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val label = InstrumentationRegistry.getArguments().getString("reviewLabel") ?: "default"
        File(rule.activity.getExternalFilesDir(null), "task39-$label-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun androidScreensWorkWithCurrentSizeThemeAndFont() {
        android.os.SystemClock.sleep(4000) // Let the OS clipboard overlay from earlier tests expire.
        captureReview("main")
        rule.onNodeWithText("記号").performClick()
        captureReview("symbols")
        rule.onNodeWithTag("overview-SYMBOLS").performScrollTo()
        captureReview("overview")
        rule.onNodeWithText("生成ルール").performScrollTo().performClick()
        rule.onNodeWithText("同じ文字を連続させない").performScrollTo()
        captureReview("rules")
        rule.onNodeWithText("生成形式").performScrollTo()
        rule.onNodeWithText("パスフレーズ").performClick()
        rule.onNodeWithContentDescription("単語数を増やす").assertWidthIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(8, model.words) }
        rule.onNodeWithContentDescription("単語数を減らす").performClick()
        rule.runOnIdle { assertEquals(7, model.words) }
        captureReview("passphrase")
        rule.onNodeWithText("パスワード").performClick()
        rule.onNodeWithTag("presets").performClick()
        rule.onNodeWithTag("preset-name").assertExists()
        captureReview("presets")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("preset-name").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("safety-settings").performClick()
        captureReview("settings")
        rule.onNodeWithTag("security-guide").performScrollTo().performClick()
        captureReview("guide")
        rule.onNodeWithContentDescription("閉じる").performClick()
        rule.runOnIdle { model.lengthText = "64"; model.countText = "20" }
        rule.onNodeWithTag("generate").performClick()
        rule.waitUntil(10000) { !model.busy && model.results.size == 20 }
        rule.onNodeWithTag("results-visibility").assertContentDescriptionEquals("生成結果を表示")
        rule.onNodeWithTag("copy-0").assertContentDescriptionEquals("コピー")
        captureReview("results")
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
