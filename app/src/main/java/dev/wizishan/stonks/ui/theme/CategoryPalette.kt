package dev.wizishan.stonks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The fixed 8-slot category identity palette. See DESIGN.md §3b.
 *
 * This is deliberately NOT part of [androidx.compose.material3.MaterialTheme.colorScheme]:
 * dynamic color follows the user's wallpaper, and a category's colour must not. If Food
 * is blue it is blue in the chart, the history chip and the budget meter, this month and
 * next, on every device.
 *
 * Slots are assigned **in order** and never cycled, randomised, or generated. Past slot 8
 * the user picks an existing slot — duplicates are acceptable because every category is
 * always name-labelled, so colour is never the sole identifier.
 *
 * The palette was validated with `tools/validate_palette.py` against the Material 3
 * baseline surfaces (light `#FEF7FF`, dark `#141218`) and passes every check in both
 * modes on the adjacent pairlist. Do not edit a hex without re-running it.
 */
object CategoryPalette {

    /** One categorical slot: the same hue, stepped for each surface. */
    data class Slot(
        val name: String,
        val light: Color,
        val dark: Color,
        /** Canonical identifier persisted in `Category.colorHex`. */
        val hex: String,
    )

    val slots: List<Slot> = listOf(
        Slot("blue", Color(0xFF2A78D6), Color(0xFF3987E5), "#2A78D6"),
        Slot("orange", Color(0xFFEB6834), Color(0xFFD95926), "#EB6834"),
        Slot("aqua", Color(0xFF1BAF7A), Color(0xFF199E70), "#1BAF7A"),
        Slot("yellow", Color(0xFFEDA100), Color(0xFFC98500), "#EDA100"),
        Slot("magenta", Color(0xFFE87BA4), Color(0xFFD55181), "#E87BA4"),
        Slot("green", Color(0xFF008300), Color(0xFF008300), "#008300"),
        Slot("violet", Color(0xFF4A3AA7), Color(0xFF9085E9), "#4A3AA7"),
        Slot("red", Color(0xFFE34948), Color(0xFFE66767), "#E34948"),
    )

    /**
     * Reserved grey for the "Other" bucket that charts fold small categories into.
     * Never assign this to a real category.
     */
    val other = Slot("other", Color(0xFF898781), Color(0xFF898781), "#898781")

    /** Default category names, in slot order. Seeded into the DB on first run. */
    val defaultCategoryNames: List<String> = listOf(
        "Food & Drink",
        "Transport",
        "Lodging",
        "Shopping",
        "Entertainment",
        "Groceries",
        "Health",
        "Bills & Utilities",
    )

    private val byHex: Map<String, Slot> =
        (slots + other).associateBy { it.hex.uppercase() }

    /** The slot a stored hex belongs to, or null if the hex is not from this palette. */
    fun slotForHex(hex: String?): Slot? =
        hex?.let { byHex[it.trim().uppercase()] }

    /**
     * The next slot to hand a newly created category, given the ones already in use.
     * Returns null once all eight are taken — the caller must then let the user pick.
     */
    fun nextFreeSlot(usedHexes: Collection<String>): Slot? {
        val used = usedHexes.map { it.trim().uppercase() }.toSet()
        return slots.firstOrNull { it.hex.uppercase() !in used }
    }

    /**
     * Resolve a stored hex to the colour for the current surface. Falls back to the
     * reserved grey for an unknown hex, so a hand-edited backup can never crash a chart.
     */
    @Composable
    @ReadOnlyComposable
    fun resolve(hex: String?): Color {
        val slot = slotForHex(hex) ?: other
        return if (LocalIsDarkTheme.current) slot.dark else slot.light
    }
}
