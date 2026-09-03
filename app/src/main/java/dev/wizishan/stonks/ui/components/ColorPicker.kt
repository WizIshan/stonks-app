package dev.wizishan.stonks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.core.ColorMath
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme

/**
 * Pick any colour, with the eight built-in ones offered first.
 *
 * The presets are still there because they are the validated set — no two are confusable
 * for a colourblind reader, and they are still what a new category defaults to. Below
 * them, hue/strength/brightness sliders give a free choice.
 *
 * The two preview chips are the point of the bottom half: they show the colour **as it
 * will actually render** in each theme, after the lightness adjustment that keeps it
 * legible. Picking a near-black in light mode and being surprised by it in dark mode is
 * exactly what they prevent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(Unit) { ColorMath.hexToHsv(selectedHex) ?: Triple(210f, 0.8f, 0.84f) }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var brightness by remember { mutableFloatStateOf(initialHsv.third) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CategorySlots.all.forEach { slot ->
                PresetSwatch(
                    hex = slot.lightHex,
                    selected = slot.lightHex.equals(selectedHex, ignoreCase = true),
                    onClick = {
                        ColorMath.hexToHsv(slot.lightHex)?.let { (h, s, v) ->
                            hue = h; saturation = s; brightness = v
                        }
                        onSelect(slot.lightHex)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.categories_colour_custom),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        fun emit() = onSelect(ColorMath.hsvToHex(hue, saturation, brightness))

        LabelledSlider(
            label = stringResource(R.string.categories_colour_hue),
            value = hue,
            range = 0f..360f,
            track = Brush.horizontalGradient(HueStops),
            onChange = { hue = it; emit() },
        )
        LabelledSlider(
            label = stringResource(R.string.categories_colour_saturation),
            value = saturation,
            range = 0f..1f,
            track = Brush.horizontalGradient(
                listOf(
                    Color(ColorMath.hsvToHex(hue, 0f, brightness).toColorInt()),
                    Color(ColorMath.hsvToHex(hue, 1f, brightness).toColorInt()),
                )
            ),
            onChange = { saturation = it; emit() },
        )
        LabelledSlider(
            label = stringResource(R.string.categories_colour_brightness),
            value = brightness,
            range = 0f..1f,
            track = Brush.horizontalGradient(
                listOf(Color.Black, Color(ColorMath.hsvToHex(hue, saturation, 1f).toColorInt()))
            ),
            onChange = { brightness = it; emit() },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ThemePreview(
                label = stringResource(R.string.categories_colour_preview_light),
                hex = ColorMath.adaptForSurface(selectedHex, dark = false),
                surface = Color(0xFFFEF7FF),
            )
            ThemePreview(
                label = stringResource(R.string.categories_colour_preview_dark),
                hex = ColorMath.adaptForSurface(selectedHex, dark = true),
                surface = Color(0xFF141218),
            )
        }

        Text(
            text = stringResource(R.string.categories_colour_adapted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The gradient is drawn behind the slider so the control shows what it controls;
        // Material's own track is a flat colour that says nothing about the value.
        Canvas(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
            drawRoundRect(brush = track, cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PresetSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(hex.toColorInt()),
        modifier = Modifier.size(SwatchSize),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(Spacing.lg),
            )
        }
    }
}

@Composable
private fun ThemePreview(label: String, hex: String, surface: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(Spacing.sm),
            color = surface,
            modifier = Modifier.size(width = PreviewWidth, height = SwatchSize),
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Canvas(modifier = Modifier.size(Spacing.xl)) {
                    drawCircle(color = Color(hex.toColorInt()))
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SwatchSize = 48.dp
private val PreviewWidth = 72.dp

private val HueStops = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

@Preview(name = "Colour picker · light", showBackground = true)
@Preview(name = "Colour picker · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ColorPickerPreview() {
    StonksTheme {
        ColorPicker(
            selectedHex = "#2A78D6",
            onSelect = {},
            modifier = Modifier.size(width = 360.dp, height = 560.dp),
        )
    }
}
