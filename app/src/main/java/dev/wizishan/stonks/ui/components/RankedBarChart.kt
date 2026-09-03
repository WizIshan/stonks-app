package dev.wizishan.stonks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.repository.RankedSlice
import dev.wizishan.stonks.ui.theme.CategoryPalette
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import dev.wizishan.stonks.ui.theme.TabularFigures

/**
 * A ranked horizontal bar chart: name on the left, amount on the right, bar underneath.
 *
 * This is the app's answer to "spend by category" instead of a donut (DESIGN.md §4). Every
 * bar is directly labelled, which is what makes the three low-contrast hues in the palette
 * legal — the text carries the identity and the colour reinforces it.
 *
 * Bars are scaled against the largest value, not against the total, because the question
 * these answer is "which is biggest and by how much", not "what share of the whole".
 */
@Composable
fun RankedBarChart(
    slices: List<RankedSlice>,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return
    val largest = slices.maxOf { it.amountMinor }.coerceAtLeast(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        slices.forEach { slice ->
            RankedBar(slice = slice, fraction = slice.amountMinor.toFloat() / largest)
        }
    }
}

@Composable
private fun RankedBar(slice: RankedSlice, fraction: Float) {
    val color = CategoryPalette.resolve(slice.colorHex)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = slice.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = Spacing.sm),
            )
            Text(
                text = Money.format(slice.amountMinor),
                style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Bar(color = color, fraction = fraction)
    }
}

/**
 * Square against the baseline, rounded at the value end.
 *
 * Rounding both ends would make a short bar read as a pill floating free of the axis; the
 * square left edge is what anchors it. Drawn as a fully rounded rect with the left radius
 * squared off again, which is cheaper than building a path per frame.
 */
@Composable
private fun Bar(color: Color, fraction: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
    ) {
        val radius = BarEndRadius.toPx()
        val width = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(radius)

        drawRoundRect(
            color = color,
            size = Size(width, size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRect(
            color = color,
            size = Size(minOf(width, radius), size.height),
        )
    }
}

private val BarHeight = 8.dp
private val BarEndRadius = 4.dp

@Preview(name = "Ranked bars · light", showBackground = true)
@Preview(name = "Ranked bars · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RankedBarChartPreview() {
    StonksTheme {
        RankedBarChart(
            slices = listOf(
                RankedSlice("Bills & Utilities", "#E34948", 82_000),
                RankedSlice("Food & Drink", "#2A78D6", 45_250),
                RankedSlice("Transport", "#EB6834", 21_000),
                RankedSlice("Groceries", "#008300", 8_400),
                RankedSlice("Other", "#898781", 3_100),
            ),
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
