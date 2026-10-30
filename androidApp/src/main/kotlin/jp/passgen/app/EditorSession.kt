package jp.passgen.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** In-memory drafts only. Never add these fields to preferences or SavedStateHandle. */
internal class EditorSession {
    val presetName = mutableStateOf("")
    val editing = mutableStateOf<GeneratorPreset?>(null)
    val action = mutableStateOf("")
    val newName = mutableStateOf("")
    val sharing = mutableStateOf<GeneratorPreset?>(null)
    val incoming = mutableStateOf<GeneratorPreset?>(null)
    val symbolText = mutableStateOf("")
    val symbolInfo = mutableStateOf<String?>(null)
    private var reviewId: String? = null
    private var reviewedName = mutableStateOf("")

    fun reviewName(preset: GeneratorPreset): MutableState<String> {
        if (reviewId != preset.id) {
            reviewId = preset.id
            reviewedName = mutableStateOf(preset.name)
        }
        return reviewedName
    }

    fun clearPresets() {
        presetName.value = ""; editing.value = null; action.value = ""; newName.value = ""
        sharing.value = null; incoming.value = null; reviewedName.value = ""; reviewId = null
    }

    fun clearSymbols() { symbolText.value = ""; symbolInfo.value = null }
    fun clear() { clearPresets(); clearSymbols() }
}
