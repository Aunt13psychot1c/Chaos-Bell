package com.musheer360.swiftslate.manager

import android.content.Context
import org.json.JSONObject

/**
 * Bell's souls: who's talking. Four ready-made souls (Bell, Mary, Jake, Stan) ship in
 * assets/souls/souls.json, and "My Soul" is the user's own, written in Soul & Ping (it is the
 * old single Soul box, so nothing anyone already wrote is lost).
 *
 *  - Ready-made souls get a **dial** (low / middle / high) that tacks one extra line onto the
 *    end of their prompt, like a volume knob on their attitude.
 *  - My Soul gets three **knobs** (emoji, serious, chaos). What each level means lives in
 *    assets/souls/knobs.json, rule plus a short example, so the meaning can be tweaked without
 *    touching code. A knob nobody has touched adds nothing.
 *
 * Which soul is on, the dial and the knobs are stored in [BellMemory] so they travel in the
 * backup file. Swap from Soul & Ping, or type a name before ?soul anywhere (jake ?soul).
 */
object SoulLibrary {
    const val CUSTOM_ID = "custom"
    const val DEFAULT_CUSTOM_NAME = "My Soul"
    const val CUSTOM_EMOJI = "✨"
    val LEVELS = listOf("low", "middle", "high")
    val KNOBS = listOf("emoji", "serious", "chaos")

    /** Extra words that mean the user's own soul. */
    private val CUSTOM_WORDS = setOf("custom", "mine", "my", "me", "own", "mysoul")
    /** Words people naturally type around a name that mean nothing here ("switch to jake"). */
    private val FILLER_WORDS = setOf("soul", "switch", "to", "use", "please", "pls", "the", "on", "dial", "turn", "it", "with")

    data class Soul(
        val id: String,
        val name: String,
        val emoji: String,
        val tagline: String,
        val intro: String,
        val prompt: String,
        /** Always added, whatever the dial says (her guardrail line). */
        val always: String,
        val dial: Map<String, String>
    )

    data class KnobLevel(val rule: String, val example: String)

    data class Knob(
        val id: String,
        val label: String,
        val lowLabel: String,
        val highLabel: String,
        val levels: Map<String, KnobLevel>
    )

    /** What a typed ?soul asked for. */
    sealed class Switch {
        /** Just "?soul": say who's on. */
        object Status : Switch()
        /** A soul and/or a dial level to switch to (either may be null, not both). */
        data class Change(val soulId: String?, val level: String?) : Switch()
        /** A word that isn't a soul or a level. */
        data class Unknown(val word: String) : Switch()
    }

    /** Turns "mid", "up", "LOW" and friends into low / middle / high, or null. */
    fun normalizeLevel(word: String): String? = when (word.lowercase().trim()) {
        "low", "lo", "down", "soft", "chill", "calm" -> "low"
        "middle", "mid", "medium", "med", "normal", "default" -> "middle"
        "high", "hi", "up", "loud", "spicy", "max" -> "high"
        else -> null
    }

    class Catalog(val souls: List<Soul>, val knobBase: String, val knobs: Map<String, Knob>) {

        fun find(id: String?): Soul? = souls.firstOrNull { it.id == id }

        /**
         * Who's on. Never picked yet: anyone who already wrote a Soul keeps it (My Soul),
         * everyone else starts with Bell.
         */
        fun activeId(memory: BellMemory): String {
            val chosen = memory.activeSoul
            if (chosen == CUSTOM_ID) return CUSTOM_ID
            if (chosen != null && find(chosen) != null) return chosen
            if (memory.soul.isNotBlank()) return CUSTOM_ID
            return souls.firstOrNull()?.id ?: CUSTOM_ID
        }

        fun customName(memory: BellMemory): String = memory.customSoulName.trim().ifEmpty { DEFAULT_CUSTOM_NAME }

        fun displayName(memory: BellMemory): String =
            find(activeId(memory))?.name ?: customName(memory)

        fun emoji(memory: BellMemory): String =
            find(activeId(memory))?.emoji ?: CUSTOM_EMOJI

        /** The full soul text the AI reads, or "" when there is nothing to say. */
        fun soulText(memory: BellMemory): String {
            val preset = find(activeId(memory))
            if (preset != null) {
                val sb = StringBuilder(preset.prompt.trim())
                if (preset.always.isNotBlank()) sb.append("\n\n").append(preset.always.trim())
                val dialLine = preset.dial[memory.soulDial].orEmpty().trim()
                if (dialLine.isNotEmpty()) sb.append("\n\n").append(dialLine)
                return sb.toString()
            }
            return customText(memory)
        }

        private fun customText(memory: BellMemory): String {
            val written = memory.soul.trim()
            val rules = KNOBS.mapNotNull { id ->
                val level = memory.knob(id) ?: return@mapNotNull null
                val l = knobs[id]?.levels?.get(level) ?: return@mapNotNull null
                if (l.rule.isBlank()) null
                else if (l.example.isBlank()) "- ${l.rule}"
                else "- ${l.rule} Example of this style: \"${l.example}\""
            }
            if (written.isEmpty() && rules.isEmpty()) return ""
            val base = written.ifEmpty { knobBase.replace("{name}", customName(memory)) }
            if (rules.isEmpty()) return base
            return base + "\n\nStyle settings:\n" + rules.joinToString("\n")
        }

        /** Reads what came before ?soul, like "jake", "stan high" or just "low". */
        fun resolve(words: String, memory: BellMemory): Switch {
            val clean = words.trim()
            if (clean.isEmpty()) return Switch.Status
            val customName = customName(memory).lowercase()
            // A multi-word custom name typed whole ("glitch goddess ?soul").
            if (clean.lowercase() == customName) return Switch.Change(CUSTOM_ID, null)
            var soulId: String? = null
            var level: String? = null
            for (raw in clean.split(Regex("[\\s,]+"))) {
                val word = raw.trim { !it.isLetterOrDigit() }.lowercase()
                if (word.isEmpty() || word in FILLER_WORDS) continue
                val lvl = normalizeLevel(word)
                val soul = souls.firstOrNull { it.id == word || it.name.lowercase() == word }?.id
                    ?: if (word in CUSTOM_WORDS || word == customName.replace(" ", "")) CUSTOM_ID else null
                when {
                    soul != null && soulId == null -> soulId = soul
                    lvl != null && level == null -> level = lvl
                    else -> return Switch.Unknown(raw)
                }
            }
            return if (soulId == null && level == null) Switch.Status else Switch.Change(soulId, level)
        }

        /** Saves a [Switch.Change]. */
        fun apply(change: Switch.Change, memory: BellMemory) {
            change.soulId?.let { memory.activeSoul = it }
            change.level?.let { memory.soulDial = it }
        }

        /** "⚡ Jake (middle)" or "✨ My Soul". */
        fun nowLine(memory: BellMemory): String {
            val id = activeId(memory)
            return if (id == CUSTOM_ID) "$CUSTOM_EMOJI ${customName(memory)}"
            else "${emoji(memory)} ${displayName(memory)} (${memory.soulDial})"
        }

        fun choices(): String = (souls.map { it.id } + "mine").joinToString(", ")
    }

    /** Parses the two asset files. Bad or missing parts are skipped, never thrown. */
    fun parse(soulsJson: String, knobsJson: String): Catalog {
        val souls = try {
            val arr = JSONObject(soulsJson).optJSONArray("souls")
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").trim().lowercase()
                val prompt = o.optString("prompt").trim()
                if (id.isEmpty() || id == CUSTOM_ID || prompt.isEmpty()) return@mapNotNull null
                val dialObj = o.optJSONObject("dial")
                Soul(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    emoji = o.optString("emoji"),
                    tagline = o.optString("tagline"),
                    intro = o.optString("intro"),
                    prompt = prompt,
                    always = o.optString("always"),
                    dial = LEVELS.associateWith { dialObj?.optString(it).orEmpty() }
                )
            }.distinctBy { it.id }
        } catch (_: Exception) { emptyList() }

        var base = "You're {name}, a friendly, helpful assistant."
        val knobs = try {
            val root = JSONObject(knobsJson)
            root.optString("base").takeIf { it.isNotBlank() }?.let { base = it }
            val obj = root.optJSONObject("knobs")
            KNOBS.mapNotNull { id ->
                val k = obj?.optJSONObject(id) ?: return@mapNotNull null
                id to Knob(
                    id = id,
                    label = k.optString("label").ifBlank { id },
                    lowLabel = k.optString("low_label").ifBlank { "Low" },
                    highLabel = k.optString("high_label").ifBlank { "High" },
                    levels = LEVELS.associateWith { lvl ->
                        val l = k.optJSONObject(lvl)
                        KnobLevel(l?.optString("rule").orEmpty(), l?.optString("example").orEmpty())
                    }
                )
            }.toMap()
        } catch (_: Exception) { emptyMap() }

        return Catalog(souls, base, knobs)
    }

    @Volatile private var cached: Catalog? = null

    /** The catalog from the app's assets, read once and kept. */
    fun load(context: Context): Catalog {
        cached?.let { return it }
        fun read(name: String) = try {
            context.assets.open("souls/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) { "" }
        return parse(read("souls.json"), read("knobs.json")).also { cached = it }
    }
}
