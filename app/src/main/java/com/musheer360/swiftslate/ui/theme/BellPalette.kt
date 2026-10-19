package com.musheer360.swiftslate.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Bell's color choices. Each one is an accent color laid over the normal light or dark
 * background: it paints buttons, links, sliders and the selected tab. Every accent comes in
 * two shades, a deeper one that reads on the light background and a softer one that reads on
 * the dark background. All of them are matte on purpose, no neon.
 *
 * The picker shows only the color itself. [displayName] is never drawn; it is what a screen
 * reader says for each swatch.
 */
enum class BellPalette(
    val id: String,
    val displayName: String,
    /** Accent on the light background, or null to keep the app's own black and white. */
    val lightAccent: Color?,
    /** Accent on the dark background, or null to keep the app's own black and white. */
    val darkAccent: Color?,
    /** Swatch shown in the picker. */
    val swatch: Color
) {
    CLASSIC("classic", "Classic", null, null, Color(0xFF141414)),
    BRASS("brass", "Gold", Color(0xFF8C6D2C), Color(0xFFC9A55A), Color(0xFFB8954A)),
    COPPER("copper", "Orange", Color(0xFF9A4F27), Color(0xFFD0784A), Color(0xFFB5653A)),
    OXBLOOD("oxblood", "Red", Color(0xFF7A1F2B), Color(0xFFC0505E), Color(0xFF7A1F2B)),
    OLIVE("olive", "Green", Color(0xFF5C5C2E), Color(0xFFA3A35E), Color(0xFF6B6B3A)),
    MAUVE("mauve", "Pink", Color(0xFF8A5577), Color(0xFFC49AB5), Color(0xFFB07A9A));

    companion object {
        fun fromId(id: String?): BellPalette = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Holds the chosen palette as Compose state, so picking a color in Settings repaints the whole
 * app at once, and saves it to the same "settings" preferences the rest of the app uses.
 */
object PaletteStore {
    const val PREF_PALETTE = "color_palette"

    /** Set once the user has picked colors, so the first-launch picker never shows again. */
    const val PREF_CHOSEN = "color_palette_chosen"

    var current by mutableStateOf(BellPalette.CLASSIC)
        private set

    /** False until the first-launch picker has been answered. */
    var chosen by mutableStateOf(true)
        private set

    fun load(prefs: SharedPreferences) {
        // A corrupted store must never crash: this runs in the process the accessibility
        // service shares.
        current = try { BellPalette.fromId(prefs.getString(PREF_PALETTE, null)) } catch (_: Exception) { BellPalette.CLASSIC }
        chosen = try { prefs.getBoolean(PREF_CHOSEN, false) } catch (_: Exception) { true }
    }

    fun choose(prefs: SharedPreferences, palette: BellPalette) {
        current = palette
        chosen = true
        prefs.edit().putString(PREF_PALETTE, palette.id).putBoolean(PREF_CHOSEN, true).apply()
    }
}
