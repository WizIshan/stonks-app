package dev.wizishan.stonks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import dev.wizishan.stonks.core.CategorySlot
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.core.ColorMath

/**
 * Turns a stored category colour into the [Color] to paint on the current surface.
 * See DESIGN.md §3b.
 *
 * Two paths, in this order:
 *
 * 1. **One of the eight built-in slots** — its hand-picked step for this surface is used
 *    verbatim. Those pairs were chosen and validated together, so nothing recomputes them.
 * 2. **Any other colour** — the hue and chroma are kept exactly as chosen and only the
 *    lightness is nudged into the band that reads against this surface. A colour picked in
 *    light mode is still recognisably itself in dark mode instead of disappearing into it.
 *
 * Deliberately NOT part of `MaterialTheme.colorScheme`: dynamic color follows the user's
 * wallpaper, and a category's identity must not.
 */
object CategoryPalette {

    /**
     * Resolve a stored hex for the current surface. An unparseable value falls back to the
     * reserved grey, so a hand-edited backup can never crash a chart.
     */
    @Composable
    @ReadOnlyComposable
    fun resolve(hex: String?): Color {
        val dark = LocalIsDarkTheme.current
        CategorySlots.forHex(hex)?.let { return colorFor(it, dark) }

        val custom = hex?.takeIf(ColorMath::isValidHex)
            ?: return colorFor(CategorySlots.other, dark)
        return Color(ColorMath.adaptForSurface(custom, dark).toColorInt())
    }

    /** The reserved grey for the "Other" bucket charts fold small categories into. */
    @Composable
    @ReadOnlyComposable
    fun other(): Color = colorFor(CategorySlots.other, LocalIsDarkTheme.current)

    private fun colorFor(slot: CategorySlot, dark: Boolean): Color =
        Color((if (dark) slot.darkHex else slot.lightHex).toColorInt())
}
