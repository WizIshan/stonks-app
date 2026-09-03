package dev.wizishan.stonks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.budget.BudgetProgress
import dev.wizishan.stonks.data.budget.BudgetStatus
import dev.wizishan.stonks.ui.theme.CategoryPalette
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StatusColors
import dev.wizishan.stonks.ui.theme.StonksTheme
import dev.wizishan.stonks.ui.theme.TabularFigures

/**
 * A budget as a meter, not a series bar (DESIGN.md §5).
 *
 * The track is the category's own hue at low alpha and the fill is the same hue at full
 * strength, so a healthy budget reads as one colour at two weights. Once a threshold is
 * crossed the fill switches to a status colour — and every status ships with an icon and
 * the numbers spelled out, so the state never depends on colour alone.
 *
 * An overspend is reported by the label, not by a bar running past the end of its track.
 */
@Composable
fun BudgetMeter(
    progress: BudgetProgress,
    modifier: Modifier = Modifier,
) {
    val fillColor = progress.fillColor()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    imageVector = progress.status.icon,
                    contentDescription = null,
                    tint = fillColor,
                    modifier = Modifier.size(Spacing.lg),
                )
                Text(
                    text = progress.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (progress.remainingMinor >= 0) {
                    stringResource(R.string.budget_remaining, Money.format(progress.remainingMinor))
                } else {
                    stringResource(R.string.budget_over_by, Money.format(-progress.remainingMinor))
                },
                style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Track(fillColor = fillColor, fraction = progress.fillFraction)

        Text(
            text = stringResource(
                R.string.budget_progress,
                Money.format(progress.spentMinor),
                Money.format(progress.limitMinor),
                progress.percent,
            ),
            style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Track(fillColor: Color, fraction: Float) {
    val trackColor = fillColor.copy(alpha = TrackAlpha)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
    ) {
        val radius = MeterEndRadius.toPx()
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        if (fraction > 0f) {
            val width = (size.width * fraction).coerceAtLeast(radius)
            drawRoundRect(
                color = fillColor,
                size = Size(width, size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRect(color = fillColor, size = Size(minOf(width, radius), size.height))
        }
    }
}

/** Healthy stays in the category's own hue; anything else is a reserved status colour. */
@Composable
private fun BudgetProgress.fillColor(): Color = when (status) {
    BudgetStatus.HEALTHY ->
        if (colorHex != null) CategoryPalette.resolve(colorHex) else MaterialTheme.colorScheme.primary

    BudgetStatus.WARNING -> StatusColors.warning
    BudgetStatus.OVER -> StatusColors.serious
    BudgetStatus.CRITICAL -> StatusColors.critical
}

private val BudgetStatus.icon: ImageVector
    get() = when (this) {
        BudgetStatus.HEALTHY -> Icons.Default.CheckCircle
        BudgetStatus.WARNING -> Icons.Default.Info
        BudgetStatus.OVER, BudgetStatus.CRITICAL -> Icons.Default.Warning
    }

private const val TrackAlpha = 0.12f
private val MeterHeight = 8.dp
private val MeterEndRadius = 4.dp

@Preview(name = "Budget meters · light", showBackground = true)
@Preview(name = "Budget meters · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetMeterPreview() {
    StonksTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            BudgetMeter(BudgetProgress(1, null, "Overall", null, 120_000, 300_000, 80))
            BudgetMeter(BudgetProgress(2, 1, "Food & Drink", "#2A78D6", 21_000, 50_000, 80))
            BudgetMeter(BudgetProgress(3, 2, "Transport", "#EB6834", 43_000, 50_000, 80))
            BudgetMeter(BudgetProgress(4, 3, "Shopping", "#EDA100", 52_000, 50_000, 80))
            BudgetMeter(BudgetProgress(5, 4, "Entertainment", "#E87BA4", 75_000, 50_000, 80))
        }
    }
}
