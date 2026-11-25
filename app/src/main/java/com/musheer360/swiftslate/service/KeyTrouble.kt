package com.musheer360.swiftslate.service

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Says what really went wrong when a provider's model list won't load, instead of blaming
 * the key every time. Only says "dead key" when the provider itself says the key is bad or
 * out of money; a weak signal or a server having a bad minute gets called what it is.
 * Pure Kotlin, so it can be unit tested on the JVM.
 */
object KeyTrouble {

    enum class Kind { NO_CONNECTION, DEAD_KEY, OTHER }

    fun kind(e: Throwable): Kind {
        if (e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ||
            e is SSLException) return Kind.NO_CONNECTION
        val m = (e.message ?: "").lowercase(Locale.ROOT)
        return when {
            KEY_WORDS.any { m.contains(it) } -> Kind.DEAD_KEY
            NET_WORDS.any { m.contains(it) } || (e is IOException && m.isBlank()) -> Kind.NO_CONNECTION
            else -> Kind.OTHER
        }
    }

    /** One plain sentence for the Settings screen. */
    fun explain(e: Throwable): String {
        val said = (e.message ?: "").trim()
        return when (kind(e)) {
            Kind.NO_CONNECTION ->
                "📵 Couldn't reach the server (no signal, or it timed out). Your key is probably fine. Try again in a bit."
            Kind.DEAD_KEY ->
                "💀 The provider says your key is bad or out of credits" +
                    (if (said.isNotEmpty()) ": \"${said.take(MAX_SAID)}\"" else ".") +
                    " Check it on the provider's site, or make a new one and add it on the Keys screen."
            Kind.OTHER ->
                "⚠️ Couldn't load the models" +
                    (if (said.isNotEmpty() && !said.startsWith("{")) ". The server said: \"${said.take(MAX_SAID)}\"" else ".") +
                    " Your key is probably fine. Try again in a bit."
        }
    }

    private const val MAX_SAID = 160

    private val KEY_WORDS = listOf(
        "invalid api key", "invalid_api_key", "incorrect api key", "api key not valid", "api_key_invalid",
        "insufficient_quota", "exceeded your current quota", "billing", "deactivated", "revoked",
        "account_deactivated", "no such api key", "authentication"
    )

    private val NET_WORDS = listOf(
        "timeout", "timed out", "unable to resolve host", "no address associated", "network is unreachable",
        "connection reset", "connection refused", "connect failed", "software caused connection abort",
        "broken pipe", "failed to connect"
    )
}
