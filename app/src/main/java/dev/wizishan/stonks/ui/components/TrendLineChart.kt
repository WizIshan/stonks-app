package dev.wizishan.stonks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.repository.MonthPoint
import dev.wizishan.stonks.ui.theme.SeriesColors
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import dev.wizishan.stonks.ui.theme.TabularFigures
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Spend and income over time, two lines on one axis.
 *
 * Deliberately **one** y-axis for both series — they are the same measure in the same
 * currency, so a second scale would only distort the comparison (DESIGN.md §4 forbids
 * dual axes outright).
 *
 * Android has no hover, so the interaction is tap-to-select: the detail appears in a row
 * beneath the plot rather than in a floating tooltip.
 */
@Composable
fun TrendLineChart(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val spendColor = SeriesColors.spend()
    val incomeColor = SeriesColors.income()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }
    val detailFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    var selectedIndex by remember(points.size) { mutableStateOf<Int?>(null) }

    val ceiling = points.maxOf { maxOf(it.spendMinor, it.incomeMinor) }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                LegendEntry(color = spendColor, label = stringResource(R.string.trend_spend))
                LegendEntry(color = incomeColor, label = stringResource(R.string.trend_income))
            }
            // The only y-axis label. A full set of ticks would be chrome competing with
            // two lines; the ceiling plus a zero baseline is enough to read the scale.
            Text(
                text = Money.format(ceiling),
                style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = size.width / (points.size - 1).toFloat()
                        val index = (offset.x / step).toInt().coerceIn(points.indices)
                        selectedIndex = if (selectedIndex == index) null else index
                    }
                }
        ) {
            val step = size.width / (points.size - 1)
            fun yFor(value: Long) = size.height - (value.toFloat() / ceiling) * size.height
            fun pointAt(index: Int, value: Long) = Offset(index * step, yFor(value))

            // Horizontal gridlines only — vertical ones add nothing a line chart needs.
            listOf(0f, 0.5f, 1f).forEach { at ->
                val y = size.height * at
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = HairlineWidth.toPx(),
                )
            }

            drawSeries(points.mapIndexed { i, p -> pointAt(i, p.spendMinor) }, spendColor)
            drawSeries(points.mapIndexed { i, p -> pointAt(i, p.incomeMinor) }, incomeColor)

            // Selective markers: the current value always, plus whatever is selected.
            val last = points.lastIndex
            drawMarker(pointAt(last, points[last].spendMinor), spendColor, surfaceColor)
            drawMarker(pointAt(last, points[last].incomeMinor), incomeColor, surfaceColor)
            selectedIndex?.let { index ->
                drawMarker(pointAt(index, points[index].spendMinor), spendColor, surfaceColor)
                drawMarker(pointAt(index, points[index].incomeMinor), incomeColor, surfaceColor)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisLabel(points.first().month.format(monthFormatter))
            AxisLabel(points.last().month.format(monthFormatter))
        }

        val selected = selectedIndex?.let(points::getOrNull)
        if (selected != null) {
            Text(
                text = "${selected.month.format(detailFormatter)} · " +
                    stringResource(
                        R.string.trend_detail,
                        Money.format(selected.spendMinor),
                        Money.format(selected.incomeMinor),
                        Money.formatSigned(selected.netMinor),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun DrawScope.drawSeries(points: List<Offset>, color: Color) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path = path, color = color, style = Stroke(width = LineWidth.toPx()))
}

/** A surface-coloured ring keeps overlapping markers readable where the lines cross. */
private fun DrawScope.drawMarker(at: Offset, color: Color, surface: Color) {
    drawCircle(color = surface, radius = MarkerRadius.toPx() + RingWidth.toPx(), center = at)
    drawCircle(color = color, radius = MarkerRadius.toPx(), center = at)
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SolidDot(color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The same size as the category swatch, so legends and lists read as one system. */
@Composable
private fun SolidDot(color: Color) {
    Canvas(modifier = Modifier.size(Spacing.md)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val ChartHeight = 160.dp
private val LineWidth = 2.dp
private val HairlineWidth = 1.dp
private val MarkerRadius = 4.dp
private val RingWidth = 2.dp

@Preview(name = "Trend · light", showBackground = true)
@Preview(name = "Trend · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendLineChartPreview() {
    val start = YearMonth.of(2026, 4)
    StonksTheme {
        TrendLineChart(
            points = listOf(
                MonthPoint(start, 120_000, 250_000),
                MonthPoint(start.plusMonths(1), 185_000, 250_000),
                MonthPoint(start.plusMonths(2), 94_000, 250_000),
                MonthPoint(start.plusMonths(3), 210_000, 290_000),
                MonthPoint(start.plusMonths(4), 160_000, 250_000),
                MonthPoint(start.plusMonths(5), 138_500, 250_000),
            ),
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
