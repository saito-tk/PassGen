package jp.passgen.app

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.passgen.core.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

enum class WorkState { IDLE, RUNNING, CANCELLING }

class SecretRow(val value: String, val bits: Double, val index: Int) {
    override fun toString() = "SecretRow([REDACTED])"
}

class PassgenModel(private val preferences: SharedPreferences? = null) : ViewModel() {
    var mode by mutableStateOf(GenerationMode.PASSWORD)
    var lengthText by mutableStateOf("24")
    var countText by mutableStateOf("1")
    var words by mutableIntStateOf(7)
    var separator by mutableStateOf(" ")
    var uppercase by mutableStateOf(true)
    var lowercase by mutableStateOf(true)
    var digits by mutableStateOf(true)
    var symbolsEnabled by mutableStateOf(true)
    var symbols by mutableStateOf("!@#\u0024%&*-_+=?")
    var excludeSimilar by mutableStateOf(true)
    var requireEach by mutableStateOf(true)
    var noAdjacent by mutableStateOf(false)
    var uppercaseCharacters by mutableStateOf(CharacterPolicy.UPPERCASE)
    var lowercaseCharacters by mutableStateOf(CharacterPolicy.LOWERCASE)
    var digitCharacters by mutableStateOf(CharacterPolicy.DIGITS)
    var similarExclusions by mutableStateOf(CharacterPolicy.SIMILAR)
    var work by mutableStateOf(WorkState.IDLE); private set
    var results by mutableStateOf<List<SecretRow>>(emptyList()); private set
    var goal by mutableIntStateOf(1); private set
    var revealed by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var copiedIndices by mutableStateOf<Set<Int>>(emptySet())
    var autoClearClipboard by mutableStateOf(true)
    var clipboardSecondsText by mutableStateOf("30")
    var protectCapture by mutableStateOf(true)
    val clipboardSeconds get() = clipboardSecondsText.toIntOrNull()?.coerceIn(5, 600) ?: 30
    val clipboardSummary get() = if (autoClearClipboard) "コピーの消去は${clipboardSeconds}秒後が目安" else "コピーの時間による自動消去はオフ"
    private var generatedConfiguration by mutableStateOf<PresetRecipe?>(null)
    private fun generationConfiguration() = PresetRecipe.capture(this)
    val generationActionTitle: String get() = when (work) {
        WorkState.CANCELLING -> "停止中…"
        WorkState.RUNNING -> "停止"
        WorkState.IDLE -> if (results.isNotEmpty() && generatedConfiguration == generationConfiguration()) "再生成" else "生成"
    }
    var presets by mutableStateOf<List<GeneratorPreset>>(emptyList()); private set
    var presetStorageIssue by mutableStateOf<String?>(null); private set
    private var job: Job? = null
    private var cancel = AtomicBoolean(false)
    private var epoch = 0L
    init {
        preferences?.getString("generation.preferences.v1", null)?.takeIf { it.length <= 16_384 }?.let { json ->
            try { restorePreferences(JSONObject(json)) } catch (_: Exception) { /* Invalid non-secret settings use defaults. */ }
        }
        loadPresets()
        if (preferences != null) viewModelScope.launch {
            snapshotFlow { preferencesJSON() }.collect { value -> preferences.edit { putString("generation.preferences.v1", value) } }
        }
    }
    private fun preferencesJSON(): String = PresetRecipe.capture(this).json().apply {
        put("revealed", revealed); put("autoClearClipboard", autoClearClipboard)
        put("clipboardSeconds", clipboardSeconds); put("protectCapture", protectCapture)
    }.toString()
    private fun restorePreferences(json: JSONObject) {
        if (json.optInt("version") != 1) return
        PresetRecipe.decode(json).applyTo(this)
        fun flag(key: String, fallback: Boolean) = json.opt(key) as? Boolean ?: fallback
        revealed = flag("revealed", false); autoClearClipboard = flag("autoClearClipboard", true)
        clipboardSecondsText = json.optInt("clipboardSeconds", 30).coerceIn(5, 600).toString()
        protectCapture = flag("protectCapture", true)
    }
    fun presetNameError(value: String, excluding: String? = null): String? = try {
        val name = normalizedPresetName(value)
        if (presets.any { it.id != excluding && presetNameKey(it.name) == presetNameKey(name) })
            "同じ名前のプリセットがあります。別の名前を入力してください。" else null
    } catch (e: PresetException) { e.message }
    private fun loadPresets() {
        try {
            val raw = preferences?.getString("generation.presets.v1", null) ?: return
            if (raw.length > 131072) throw PresetException("size")
            val root = JSONObject(raw)
            if (root.getInt("version") != 1) throw PresetException("version")
            val rows = root.getJSONArray("presets")
            if (rows.length() > 50) throw PresetException("count")
            val loaded = (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                val recipe = item.getJSONObject("recipe")
                if (recipe.getInt("version") != 1 || !recipe.keys().asSequence().toSet().containsAll(PresetRecipe.capture(this).json().keys().asSequence().toSet())) throw PresetException("version")
                GenerationMode.valueOf(recipe.getString("mode"))
                GeneratorPreset(UUID.fromString(item.getString("id")).toString(), normalizedPresetName(item.getString("name")), PresetRecipe.decode(recipe))
            }
            if (loaded.map { it.id }.toSet().size != loaded.size || loaded.map { presetNameKey(it.name) }.toSet().size != loaded.size)
                throw PresetException("duplicate")
            presets = loaded
        } catch (_: Exception) { presetStorageIssue = "保存済みプリセットを読み込めませんでした。元のデータは変更していません。" }
    }
    private fun commitPresets(next: List<GeneratorPreset>) {
        if (busy) throw PresetException("生成が終わってから操作してください。")
        presetStorageIssue?.let { throw PresetException(it) }
        if (next.size > 50) throw PresetException("保存できるプリセットは50件までです。")
        val rows = JSONArray()
        next.forEach { rows.put(JSONObject().put("id", it.id).put("name", it.name).put("recipe", it.recipe.json())) }
        val data = JSONObject().put("version", 1).put("presets", rows).toString()
        if (data.toByteArray(Charsets.UTF_8).size > 131072) throw PresetException("保存できる容量を超えています。")
        preferences?.edit { putString("generation.presets.v1", data) }
        presets = next
    }
    fun savePreset(value: String) {
        presetNameError(value)?.let { throw PresetException(it) }
        commitPresets(listOf(GeneratorPreset(UUID.randomUUID().toString(), normalizedPresetName(value), PresetRecipe.capture(this))) + presets)
    }
    fun applyPreset(id: String) {
        if (busy) throw PresetException("生成が終わってから操作してください。")
        val preset = presets.firstOrNull { it.id == id } ?: throw PresetException("プリセットが見つかりません。")
        preset.recipe.applyTo(this)
    }
    fun presetQR(preset: GeneratorPreset): String = PresetQrCodec.encode(SharedPreset(preset.name, preset.recipe.request(), preset.recipe.count))
    fun decodedPresetQR(text: String): GeneratorPreset {
        val decoded = PresetQrCodec.decode(text)
        return GeneratorPreset(UUID.randomUUID().toString(), decoded.name, PresetRecipe.fromTransfer(decoded))
    }
    fun importPreset(preset: GeneratorPreset, value: String) {
        presetNameError(value)?.let { throw PresetException(it) }
        commitPresets(listOf(GeneratorPreset(UUID.randomUUID().toString(), normalizedPresetName(value), preset.recipe)) + presets)
    }
    fun renamePreset(id: String, value: String) {
        presetNameError(value, id)?.let { throw PresetException(it) }
        if (presets.none { it.id == id }) throw PresetException("プリセットが見つかりません。")
        commitPresets(presets.map { if (it.id == id) it.copy(name = normalizedPresetName(value)) else it })
    }
    fun replacePreset(id: String) {
        if (presets.none { it.id == id }) throw PresetException("プリセットが見つかりません。")
        commitPresets(presets.map { if (it.id == id) it.copy(recipe = PresetRecipe.capture(this)) else it })
    }
    fun deletePreset(id: String) {
        if (presets.none { it.id == id }) throw PresetException("プリセットが見つかりません。")
        commitPresets(presets.filter { it.id != id })
    }
    val busy get() = work != WorkState.IDLE
    val isDigitsOnly get() = CharacterPolicy.isDigitsOnly(request())
    val length get() = InputPolicy.normalizedInteger(lengthText, 24, 4, InputPolicy.MAX_LENGTH)
    val maxCount get() = if (mode == GenerationMode.PASSPHRASE) 1000 else InputPolicy.maximumCount(length)
    val count get() = InputPolicy.normalizedInteger(countText, 1, 1, maxCount)

    fun normalize() { lengthText = length.toString(); countText = count.toString() }
    fun chooseMode(value: GenerationMode) {
        if (busy) return
        mode = value
        normalize()
    }
    fun request() = GenerationRequest(mode, length, words, uppercase, lowercase, digits,
        symbolsEnabled, symbols, excludeSimilar, requireEach, noAdjacent, separator,
        uppercaseCharacters, lowercaseCharacters, digitCharacters, similarExclusions)
    fun estimate(): Double? = try { PasswordEngine().estimatedEntropy(request()) } catch (_: GenerationException) { null }
    val generationActionEnabled get() = work != WorkState.CANCELLING && (busy || estimate() != null)
    val configurationIssue: String? get() = when {
        estimate() != null -> null
        mode == GenerationMode.PASSWORD && CharacterPolicy.effectivePasswordAlphabet(request()).isEmpty() ->
            "生成に使える文字を1文字以上選択してください。"
        else -> "生成条件を満たせません。文字の選択と生成ルールを確認してください。"
    }

    fun generateOrStop() {
        if (work == WorkState.CANCELLING) return
        if (work == WorkState.RUNNING) {
            work = WorkState.CANCELLING
            cancel.set(true)
            job?.cancel()
            return
        }
        normalize()
        val request = request()
        generatedConfiguration = generationConfiguration()
        val target = count
        val token = AtomicBoolean(false)
        cancel = token
        val run = ++epoch
        goal = target
        results = emptyList(); message = null; copiedIndices = emptySet()
        work = WorkState.RUNNING
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val engine = PasswordEngine()
                    val probe = object : CancellationProbe { override fun isCancelled() = token.get() }
                    repeat(target) { index ->
                        ensureActive()
                        val secret = engine.generate(request, probe)
                        withContext(Dispatchers.Main) {
                            if (run == epoch && !token.get()) results = results + SecretRow(secret.value, secret.entropyBits, index)
                        }
                        yield()
                    }
                }
            } catch (_: CancellationException) {
                // Manual stop and background both preserve completed rows; late work is invalidated.
            } catch (e: GenerationException) {
                if (run == epoch && e.code != "CANCELLED") message = when (e.code) {
                    "INVALID_CONFIGURATION" -> "生成に使える文字と生成ルールを確認してください。"
                    "CONDITIONS_TOO_STRICT" -> "条件を満たせませんでした。文字数を増やすか条件を見直してください。"
                    else -> "安全な乱数を取得できませんでした。生成を中止しました。"
                }
            } finally { if (run == epoch) work = WorkState.IDLE }
        }
    }

    fun clear() { if (!busy) { results = emptyList(); copiedIndices = emptySet(); generatedConfiguration = null } }
    fun background() {
        preferences?.edit { putString("generation.preferences.v1", preferencesJSON()) }
        ++epoch; cancel.set(true); job?.cancel(); job = null
        work = WorkState.IDLE; message = null
    }
    override fun onCleared() { cancel.set(true) }
}
