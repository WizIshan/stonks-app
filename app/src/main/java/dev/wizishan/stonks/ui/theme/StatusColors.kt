package dev.wizishan.stonks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Reserved status colours. See DESIGN.md §3c.
 *
 * Fixed, never themed, and never reused as a category colour — so a status colour can
 * never impersonate a series. `warning` and `serious` sit below 3:1 on the light surface
 * by design: **every status colour ships with an icon and a text label**, so state never
 * depends on hue alone.
 */
object StatusColors {
    /** Budget healthy; positive net cash flow accent. */
    val good = Color(0xFF0CA30C)

    /** Spend has crossed the budget's alert threshold (default 80%). */
    val warning = Color(0xFFFAB219)

    /** Spend has crossed 100% of the budget. */
    val serious = Color(0xFFEC835A)

    /** Materially over budget; failed import. */
    val critical = Color(0xFFD03B3B)
}

/**
 * The diverging pair for net cash flow. See DESIGN.md §3d.
 *
 * Blue ↔ red, not green/red — green/red is the least distinguishable pair for the most
 * common form of colour blindness, and it is exactly the pair finance apps reach for by
 * reflex. The `+` / `−` sign is always rendered alongside, so sign never depends on hue.
 */
object DivergingColors {
    private val positiveLight = Color(0xFF2A78D6)
    private val positiveDark = Color(0xFF3987E5)
    private val negativeLight = Color(0xFFE34948)
    private val negativeDark = Color(0xFFE66767)
    private val zeroLineLight = Color(0xFFC3C2B7)
    private val zeroLineDark = Color(0xFF383835)

    @Composable
    @ReadOnlyComposable
    fun forAmount(amount: Double): Color {
        val dark = LocalIsDarkTheme.current
        return if (amount >= 0) {
            if (dark) positiveDark else positiveLight
        } else {
            if (dark) negativeDark else negativeLight
        }
    }

    @Composable
    @ReadOnlyComposable
    fun zeroLine(): Color = if (LocalIsDarkTheme.current) zeroLineDark else zeroLineLight
}
