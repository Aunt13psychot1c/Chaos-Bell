package com.musheer360.swiftslate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class KeyTroubleTest {
    @Test
    fun weak_signal_is_not_a_dead_key() {
        assertEquals(KeyTrouble.Kind.NO_CONNECTION, KeyTrouble.kind(UnknownHostException("api.openai.com")))
        assertEquals(KeyTrouble.Kind.NO_CONNECTION, KeyTrouble.kind(SocketTimeoutException("Read timed out")))
        assertTrue(KeyTrouble.explain(SocketTimeoutException("x")).contains("probably fine"))
    }

    @Test
    fun provider_saying_bad_key_or_no_money_is_a_dead_key() {
        assertEquals(KeyTrouble.Kind.DEAD_KEY, KeyTrouble.kind(Exception("Incorrect API key provided: sk-...")))
        assertEquals(KeyTrouble.Kind.DEAD_KEY, KeyTrouble.kind(Exception("You exceeded your current quota")))
    }

    @Test
    fun anything_else_shows_what_the_server_said() {
        val why = KeyTrouble.explain(Exception("The server had an error while processing your request"))
        assertEquals(KeyTrouble.Kind.OTHER, KeyTrouble.kind(Exception("The server had an error")))
        assertTrue(why.contains("The server had an error"))
    }
}
