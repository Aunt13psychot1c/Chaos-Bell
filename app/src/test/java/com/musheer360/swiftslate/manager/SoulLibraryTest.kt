package com.musheer360.swiftslate.manager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoulLibraryTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun memory() = BellMemory(app.getSharedPreferences(BellMemory.PREFS_NAME, 0))
    // Read the real asset files straight from the source tree (unit tests run in the module dir).
    private val souls by lazy {
        fun read(name: String) = java.io.File("src/main/assets/souls/$name").readText()
        SoulLibrary.parse(read("souls.json"), read("knobs.json"))
    }

    @Before
    fun clear() { app.getSharedPreferences(BellMemory.PREFS_NAME, 0).edit().clear().commit() }

    @Test
    fun the_four_ready_made_souls_ship_in_the_app() {
        assertEquals(listOf("bell", "mary", "jake", "stan"), souls.regular.map { it.id })
        assertEquals(listOf("alpaca", "goddess"), souls.vault.map { it.id })
        assertEquals(setOf("emoji", "serious", "chaos"), souls.knobs.keys)
    }

    @Test
    fun new_users_start_with_bell_and_old_soul_writers_keep_their_own() {
        val m = memory()
        assertEquals("bell", souls.activeId(m))
        m.soul = "Glitch goddess who worships potatoes"
        assertEquals(SoulLibrary.CUSTOM_ID, souls.activeId(m))
        assertEquals("Glitch goddess who worships potatoes", souls.soulText(m))
    }

    @Test
    fun the_dial_adds_its_line_to_the_soul() {
        val m = memory()
        m.activeSoul = "jake"
        m.soulDial = "low"
        assertTrue(souls.soulText(m).contains("skip the flirting entirely"))
        m.soulDial = "high"
        assertTrue(souls.soulText(m).contains("playful flirting is fine"))
        assertTrue(souls.soulText(m).startsWith("JAKE"))
    }

    @Test
    fun knobs_only_soul_gets_a_base_and_rules_with_examples() {
        val m = memory()
        m.activeSoul = SoulLibrary.CUSTOM_ID
        m.customSoulName = "Sparkle"
        assertEquals("", souls.soulText(m))
        m.setKnob("chaos", "high")
        val text = souls.soulText(m)
        assertTrue(text.startsWith("You're Sparkle"))
        assertTrue(text.contains("still answer the question"))
        assertTrue(text.contains("raccoon"))
        m.setKnob("chaos", null)
        assertEquals("", souls.soulText(m))
    }

    @Test
    fun typed_soul_command_is_understood() {
        val m = memory()
        assertEquals(SoulLibrary.Switch.Status, souls.resolve("  ", m))
        assertEquals(SoulLibrary.Switch.Change("jake", null), souls.resolve("Jake", m))
        assertEquals(SoulLibrary.Switch.Change("stan", "high"), souls.resolve("stan high", m))
        assertEquals(SoulLibrary.Switch.Change(null, "low"), souls.resolve("low", m))
        assertEquals(SoulLibrary.Switch.Change(SoulLibrary.CUSTOM_ID, null), souls.resolve("my soul", m))
        assertEquals(SoulLibrary.Switch.Change("mary", null), souls.resolve("switch to mary", m))
        assertTrue(souls.resolve("gandalf", m) is SoulLibrary.Switch.Unknown)
        m.customSoulName = "Glitch Goddess"
        assertEquals(SoulLibrary.Switch.Change(SoulLibrary.CUSTOM_ID, null), souls.resolve("glitch goddess", m))
        m.customSoulName = "Ghost Girl"; m.soul = "boo"; m.saveToShelf()
        m.customSoulName = "Frog"; m.soul = "ribbit"; m.saveToShelf()
        assertEquals(SoulLibrary.Switch.Change(SoulLibrary.SHELF_PREFIX + "Ghost Girl", null), souls.resolve("ghost girl", m))
        assertTrue(souls.apply(souls.resolve("ghost girl", m) as SoulLibrary.Switch.Change, m))
        assertEquals("Ghost Girl", m.customSoulName)
        assertEquals(SoulLibrary.Switch.Change(SoulLibrary.SHELF_PREFIX + "Frog", "high"), souls.resolve("frog high", m))
    }

    @Test
    fun vault_souls_answer_to_their_names_but_stay_out_of_the_hint() {
        val m = memory()
        assertEquals(SoulLibrary.Switch.Change("alpaca", "high"), souls.resolve("alpaca high", m))
        assertEquals(SoulLibrary.Switch.Change("goddess", null), souls.resolve("goddess", m))
        assertFalse(souls.choices().contains("alpaca"))
        m.activeSoul = "alpaca"
        assertTrue(souls.soulText(m).contains("Conversational Asshole"))
        assertTrue(souls.soulText(m).contains("drop the act"))
    }

    @Test
    fun history_is_labelled_with_whoever_is_talking() {
        val m = memory()
        m.remember("hey", "Well, hell, hey!")
        val block = m.contextBlock(soulText = "You're Jake.", speaker = "Jake")
        assertTrue(block.contains("Jake: Well, hell, hey!"))
        assertFalse(block.contains("Bell: "))
    }

    @Test
    fun soul_picks_ride_along_in_the_backup() {
        val m = memory()
        m.activeSoul = "stan"; m.soulDial = "high"; m.customSoulName = "Spud"; m.setKnob("emoji", "high")
        val backup = m.export()
        clear()
        assertEquals(null, m.activeSoul)
        m.import(backup)
        assertEquals("stan", m.activeSoul)
        assertEquals("high", m.soulDial)
        assertEquals("Spud", m.customSoulName)
        assertEquals("high", m.knob("emoji"))
        assertEquals(null, m.knob("chaos"))
    }

    @Test
    fun broken_files_never_crash() {
        val c = SoulLibrary.parse("not json", "{]")
        assertTrue(c.souls.isEmpty())
        val m = memory()
        assertEquals(SoulLibrary.CUSTOM_ID, c.activeId(m))
        assertEquals("", c.soulText(m))
    }
}
