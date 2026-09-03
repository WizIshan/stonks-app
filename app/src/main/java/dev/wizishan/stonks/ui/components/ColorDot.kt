package dev.wizishan.stonks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.wizishan.stonks.ui.theme.CategoryPalette
import dev.wizishan.stonks.ui.theme.Spacing

/**
 * The small colour swatch that sits beside a category name.
 *
 * Always paired with the name — never used alone to identify a category. Three of the
 * eight hues are below 3:1 contrast on the light surface, so the text is what carries the
 * identity and the dot only reinforces it (DESIGN.md §3b).
 */
@Composable
fun ColorDot(
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.md,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(CategoryPalette.resolve(colorHex), CircleShape)
    )
}
