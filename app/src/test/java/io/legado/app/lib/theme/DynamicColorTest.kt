package io.legado.app.lib.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicColorTest {

    private val fallback = DynamicColor.Palette(1, 2, 3, 4)

    @Test
    fun buildPalette_day_overridesAll() {
        val p = DynamicColor.buildPalette(false, fallback) { name ->
            when (name) {
                "system_accent1_50" -> 100
                "system_accent1_600" -> 200
                "system_neutral1_50" -> 300
                "system_neutral1_100" -> 400
                else -> null
            }
        }
        assertEquals(DynamicColor.Palette(100, 200, 300, 400), p)
    }

    @Test
    fun buildPalette_night_overridesAll() {
        val p = DynamicColor.buildPalette(true, fallback) { name ->
            when (name) {
                "system_neutral1_800" -> 100
                "system_accent1_300" -> 200
                "system_neutral1_900" -> 300
                else -> null
            }
        }
        assertEquals(DynamicColor.Palette(100, 200, 300, 100), p)
    }

    @Test
    fun buildPalette_allMissing_fallsBack() {
        val p = DynamicColor.buildPalette(false, fallback) { null }
        assertEquals(fallback, p)
    }

    @Test
    fun buildPalette_partialMissing_fallsBackPerColor() {
        val p = DynamicColor.buildPalette(false, fallback) { name ->
            if (name == "system_accent1_600") 200 else null
        }
        assertEquals(DynamicColor.Palette(1, 200, 3, 4), p)
    }
}