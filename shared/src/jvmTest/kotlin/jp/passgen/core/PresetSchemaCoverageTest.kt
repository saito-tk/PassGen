package jp.passgen.core

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class PresetSchemaCoverageTest {
    @Test fun requestChangesRequireAnExplicitTransferCompatibilityReview() {
        // A new defaulted constructor parameter must not silently disappear from exported presets.
        // On change, add a versioned migration and fixtures before updating this guard.
        val transferred = setOf("mode", "length", "wordCount", "uppercase", "lowercase", "digits", "symbolsEnabled",
            "symbols", "excludeSimilar", "requireEach", "noAdjacent", "separator", "uppercaseCharacters",
            "lowercaseCharacters", "digitCharacters", "similarExclusions")
        val actual = GenerationRequest::class.java.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(transferred, actual, "Review QR writer version, old readers and explicit migration defaults when the request schema changes.")
    }
}
