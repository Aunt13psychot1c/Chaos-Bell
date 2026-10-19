package com.musheer360.swiftslate.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BellPaletteTest {

    @Test
    fun unknown_or_missing_ids_fall_back_to_classic() {
        assertEquals(BellPalette.CLASSIC, BellPalette.fromId(null))
        assertEquals(BellPalette.CLASSIC, BellPalette.fromId("neon"))
        assertEquals(BellPalette.BRASS, BellPalette.fromId("brass"))
    }

    @Test
    fun ids_are_unique() {
        assertEquals(BellPalette.entries.size, BellPalette.entries.map { it.id }.toSet().size)
    }

    @Test
    fun classic_leaves_the_scheme_alone() {
        val scheme = darkColorScheme()
        assertSame(scheme, scheme.withAccent(BellPalette.CLASSIC, darkTheme = true))
    }

    @Test
    fun accent_uses_the_shade_for_the_background() {
        val dark = darkColorScheme().withAccent(BellPalette.OXBLOOD, darkTheme = true)
        val light = lightColorScheme().withAccent(BellPalette.OXBLOOD, darkTheme = false)
        assertEquals(BellPalette.OXBLOOD.darkAccent, dark.primary)
        assertEquals(BellPalette.OXBLOOD.lightAccent, light.primary)
    }
}
