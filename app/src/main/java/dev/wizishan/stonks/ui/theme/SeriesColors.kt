package dev.wizishan.stonks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.wizishan.stonks.core.CategorySlots

/**
 * The two series on the trend line (DESIGN.md §4): spend in slot 2, income in slot 1.
 *
 * They come from the categorical palette rather than the M3 scheme for the same reason
 * category colours do — the line for spend must not change colour with the wallpaper. Both
 * series are named in the legend, so the hue reinforces identity rather than carrying it.
 */
object SeriesColors {

    @Composable
    @ReadOnlyComposable
    fun spend(): Color = CategoryPalette.resolve(CategorySlots.all[1].lightHex)

    @Composable
    @ReadOnlyComposable
    fun income(): Color = CategoryPalette.resolve(CategorySlots.all[0].lightHex)
}
