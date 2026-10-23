package com.musheer360.swiftslate.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellPauseTest {

    private val main = "com.musheer360.swiftslate"
    private val preview = "com.musheer360.swiftslate.preview"

    @Test
    fun findsBellAmongOtherServices() {
        val setting = "com.other.app/.Helper:$main/.service.AssistantService"
        assertTrue(BellPause.isSwitchedOn(setting, main))
    }

    @Test
    fun previewIsItsOwnSwitch() {
        // The preview's service is listed with its full class name, not the short form.
        val previewOn = "$preview/com.musheer360.swiftslate.service.AssistantService"
        assertTrue(BellPause.isSwitchedOn(previewOn, preview))
        assertFalse(BellPause.isSwitchedOn(previewOn, main))

        val mainOn = "$main/.service.AssistantService"
        assertFalse(BellPause.isSwitchedOn(mainOn, preview))
    }

    @Test
    fun offWhenNothingIsOn() {
        assertFalse(BellPause.isSwitchedOn(null, main))
        assertFalse(BellPause.isSwitchedOn("", main))
    }
}
