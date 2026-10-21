package com.musheer360.swiftslate.manager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BellMemoryTest {

    private var now = 1_000_000L
    private fun memory() = BellMemory(
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(BellMemory.PREFS_NAME, 0)
    ) { now }

    @Test
    fun empty_memory_adds_nothing() {
        assertEquals("", memory().contextBlock())
    }

    @Test
    fun about_me_and_recent_chat_are_handed_to_the_ai() {
        val m = memory()
        m.aboutMe = "Queen of Chaos, talks fast."
        m.remember("weather today?", "Sunny and 70.")
        val block = m.contextBlock()
        assertTrue(block.contains("Queen of Chaos"))
        assertTrue(block.contains("User: weather today?"))
        assertTrue(block.contains("Bell: Sunny and 70."))
    }

    @Test
    fun conversation_fades_after_an_hour_but_about_me_stays() {
        val m = memory()
        m.aboutMe = "Queen"
        m.remember("q", "a")
        now += BellMemory.MEMORY_TTL_MS + 1
        val block = m.contextBlock()
        assertFalse(block.contains("User: q"))
        assertTrue(block.contains("Queen"))
    }

    @Test
    fun only_the_newest_few_exchanges_are_kept() {
        val m = memory()
        repeat(10) { m.remember("question $it", "answer $it") }
        val block = m.contextBlock()
        assertFalse(block.contains("question 3"))
        assertTrue(block.contains("question 9"))
    }

    @Test
    fun forget_wipes_the_chat() {
        val m = memory()
        m.remember("q", "a"); m.forgetConversation()
        assertEquals("", m.contextBlock())
    }

    @Test
    fun backup_carries_commands_and_memory_and_still_reads_old_backups() {
        val m = memory()
        m.aboutMe = "Queen"
        val file = BellBackup.build("""[{"trigger":"?x","prompt":"p"}]""", m.export())
        val (commands, mem) = BellBackup.parse(file)
        assertTrue(commands.contains("?x"))
        assertEquals("Queen", mem!!.getString("about_me"))

        val (oldCommands, oldMem) = BellBackup.parse("""[{"trigger":"?y","prompt":"p"}]""")
        assertTrue(oldCommands.contains("?y"))
        assertNull(oldMem)

        m.aboutMe = ""
        m.import(JSONObject().put("about_me", "restored"))
        assertEquals("restored", m.aboutMe)
    }
}
