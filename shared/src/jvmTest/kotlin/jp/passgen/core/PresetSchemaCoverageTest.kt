package jp.passgen.core

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class PresetSchemaCoverageTest {
    @Test fun requestChangesRequireAnExplicitTransferCompatibilityReview() {
        // A new defaulted constructor parameter must not silently disappear from exported presets.
        // On change, review migration/fixtures or explicitly classify device-only policy.
        val transferred = setOf("mode", "length", "wordCount", "uppercase", "lowercase", "digits", "symbolsEnabled",
            "symbols", "excludeSimilar", "requireEach", "noAdjacent", "separator", "uppercaseCharacters",
            "lowercaseCharacters", "digitCharacters", "similarExclusions")
        // App-wide restrictions belong to the receiving device, never to a shared preset.
        val deviceOnly = setOf("blockedSymbols")
        val actual = GenerationRequest::class.java.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(transferred + deviceOnly, actual, "Review QR writer version, old readers and explicit migration defaults when the request schema changes.")
    }
}
