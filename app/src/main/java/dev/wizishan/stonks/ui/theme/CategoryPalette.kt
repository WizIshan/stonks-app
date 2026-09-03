package dev.wizishan.stonks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import dev.wizishan.stonks.core.CategorySlot
import dev.wizishan.stonks.core.CategorySlots

/**
 * Compose adapter over [CategorySlots]. See DESIGN.md §3b.
 *
 * The slots themselves live in `core` as plain Kotlin so the data layer can seed and
 * assign them; this file only turns a stored hex into the [Color] for the surface being
 * painted.
 *
 * Deliberately NOT part of `MaterialTheme.colorScheme`: dynamic color follows the user's
 * wallpaper, and a category's identity must not. If Food is blue it is blue in the chart,
 * the history chip and the budget meter, this month and next, on every device.
 */
object CategoryPalette {

    /**
     * Resolve a stored hex to the colour for the current surface. An unknown hex falls
     * back to the reserved grey, so a hand-edited backup can never crash a chart.
     */
    @Composable
    @ReadOnlyComposable
    fun resolve(hex: String?): Color = colorFor(
        slot = CategorySlots.forHex(hex) ?: CategorySlots.other,
        dark = LocalIsDarkTheme.current,
    )

    /** The reserved grey for the "Other" bucket charts fold small categories into. */
    @Composable
    @ReadOnlyComposable
    fun other(): Color = colorFor(CategorySlots.other, LocalIsDarkTheme.current)

    private fun colorFor(slot: CategorySlot, dark: Boolean): Color =
        Color((if (dark) slot.darkHex else slot.lightHex).toColorInt())
}
