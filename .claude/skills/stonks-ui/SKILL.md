---
name: stonks-ui
description: Design and code conventions for the Stonks finance app's Jetpack Compose UI. Use whenever writing, editing, or reviewing any Composable, theme file, screen, chart, or Material 3 component in this project — including dashboards, budget meters, history lists, entry forms, empty states, and category colors. Also use when choosing a chart type or assigning colors to categories.
---

# Stonks UI conventions

`DESIGN.md` in the repo root is the source of truth. **Read it before writing your
first composable in a session.** This file is the short operational version.

## The one rule that governs everything

The app has **two color systems that never mix**:

1. **UI chrome** → `MaterialTheme.colorScheme` (dynamic color, follows the wallpaper).
2. **Data identity** → the fixed 8-slot `CategoryPalette` (never dynamic, never cycled,
   never reordered). A category's color is a property of the *entity*, read from
   `Category.colorHex` — never derived from its position in a sorted list.

If a chart repaints when the user changes a filter or the sort order, that is a bug.

## Before writing any screen

- Layout spacing comes from `Spacing` tokens. A raw `16.dp` in a screen file is a bug.
- Text style comes from `MaterialTheme.typography` via the role map in DESIGN.md §2.
  Constructing a `TextStyle` in a screen file is a bug.
- A `Color(0xFF…)` literal in a screen file is a bug — it comes from `colorScheme`,
  `CategoryPalette`, or `StatusColors`.
- Amounts in a vertical column use tabular figures (`fontFeatureSettings = "tnum"`).
- Every composable ships with a light **and** a dark `@Preview`.

## Before drawing any chart

Consult DESIGN.md §4 — the forms are already decided. In short:

- Spend by category → **ranked horizontal bar**, top 6 + gray "Other". **Never a pie or donut.**
- Trend → line, max 2 series. Net cash flow → diverging bar around zero, with an explicit `+`/`−`.
- Budget vs limit → **meter**, not a series bar (DESIGN.md §5).
- One number → hero figure, not a one-bar chart.
- **Never a dual-axis chart.** Never a value label on every point.
- Every colored mark carries a visible text label. Three of the eight hues sit below
  3:1 contrast on the light surface, and the label is what makes them legal.

Chart text wears `onSurface`/`onSurfaceVariant`, never the series color.

## Token files (create these once, then only ever add to them)

```kotlin
// ui/theme/Spacing.kt
object Spacing {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp
    val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp
}

// ui/theme/CategoryPalette.kt — fixed slot order; index = slot - 1
object CategoryPalette {
    val light = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
    )
    val dark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )
    val otherLight = Color(0xFF898781)   // reserved for the "Other" fold bucket
    val otherDark  = Color(0xFF898781)

    /** Resolve a stored hex to the step for the current mode. */
    @Composable fun forCategory(colorHex: String): Color { /* map hex -> slot -> mode step */ }
}

// ui/theme/StatusColors.kt — reserved; never used as a series color
object StatusColors {
    val good     = Color(0xFF0CA30C)
    val warning  = Color(0xFFFAB219)
    val serious  = Color(0xFFEC835A)
    val critical = Color(0xFFD03B3B)
}
```

Status colors always ship with an icon **and** text — never color alone.

## Assigning a color to a new category

Take the next unused slot in order (1 → 8). Past slot 8, let the user pick an existing
slot; duplicates are acceptable because categories are always name-labeled. Never
generate a new hue, never randomize, never assign the reserved gray.

## If you change any palette hex

Re-run the validator and paste the result into DESIGN.md §3b. It must pass in both
modes on the adjacent pairlist before the change lands:

```bash
py -3 tools/validate_palette.py "<light hexes, comma-separated>" --mode light --surface "#FEF7FF"
```

## Done checklist

DESIGN.md §8. Run through it before calling any UI work finished.
