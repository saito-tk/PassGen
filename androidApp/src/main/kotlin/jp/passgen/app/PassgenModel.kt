package jp.passgen.app

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

class PassgenModel : ViewModel() {
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
    var copiedIndex by mutableStateOf<Int?>(null)
    private var job: Job? = null
    private var cancel = AtomicBoolean(false)
    private var epoch = 0L
    val busy get() = work != WorkState.IDLE
    val isDigitsOnly get() = mode == GenerationMode.PASSWORD && digits && !uppercase && !lowercase && !symbolsEnabled
    val length get() = InputPolicy.normalizedInteger(lengthText, 24, 4, InputPolicy.MAX_LENGTH)
    val maxCount get() = if (mode == GenerationMode.PASSPHRASE) 1000 else InputPolicy.maximumCount(length)
    val count get() = InputPolicy.normalizedInteger(countText, 1, 1, maxCount)

    fun normalize() { lengthText = length.toString(); countText = count.toString() }
    fun useDigitsOnly() {
        if (busy) return
        mode = GenerationMode.PASSWORD
        uppercase = false; lowercase = false; digits = true; symbolsEnabled = false
        digitCharacters = CharacterPolicy.DIGITS; excludeSimilar = false
        normalize()
    }
    fun chooseMode(value: GenerationMode) {
        if (busy) return
        mode = value
        if (value == GenerationMode.PASSWORD) lengthText = "24"
        normalize()
    }
    fun request() = GenerationRequest(mode, length, words, uppercase, lowercase, digits,
        symbolsEnabled, symbols, excludeSimilar, requireEach, noAdjacent, separator,
        uppercaseCharacters, lowercaseCharacters, digitCharacters, similarExclusions)
    fun estimate(): Double? = try { PasswordEngine().estimatedEntropy(request()) } catch (_: GenerationException) { null }

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
        val target = count
        val token = AtomicBoolean(false)
        cancel = token
        val run = ++epoch
        goal = target
        results = emptyList(); revealed = false; message = null; copiedIndex = null
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
                // Manual stop preserves completed rows; background invalidates the run and clears them.
            } catch (e: GenerationException) {
                if (run == epoch && e.code != "CANCELLED") message = when (e.code) {
                    "INVALID_CONFIGURATION" -> "文字種と記号を確認してください。少なくとも1種類必要です。"
                    "CONDITIONS_TOO_STRICT" -> "条件を満たせませんでした。文字数を増やすか条件を見直してください。"
                    else -> "安全な乱数を取得できませんでした。生成を中止しました。"
                }
            } finally { if (run == epoch) work = WorkState.IDLE }
        }
    }

    fun clear() { if (!busy) { results = emptyList(); revealed = false; copiedIndex = null } }
    fun background() {
        ++epoch; cancel.set(true); job?.cancel(); job = null
        results = emptyList(); revealed = false; copiedIndex = null; work = WorkState.IDLE; message = null
    }
    override fun onCleared() { cancel.set(true) }
}
