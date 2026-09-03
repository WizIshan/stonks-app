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

## The tokens — these already exist; use them, don't re-declare them

| What | Where | How you use it |
|---|---|---|
| Spacing, radii, touch target | `ui/theme/Spacing.kt` | `Spacing.lg`, `Radius.card`, `MinTouchTarget` |
| Category colours | `ui/theme/CategoryPalette.kt` | `CategoryPalette.resolve(category.colorHex)` |
| Status + diverging | `ui/theme/StatusColors.kt` | `StatusColors.warning`, `DivergingColors.forAmount(net)` |
| Which surface we're on | `ui/theme/Theme.kt` | `LocalIsDarkTheme.current` — not `isSystemInDarkTheme()` |
| Money formatting | `core/Money.kt` | `Money.format(minor)`, `Money.formatSigned(net)` |

The palette itself lives in `core/CategorySlots.kt` as plain Kotlin, so the data layer
can seed and assign slots without depending on Compose. `CategoryPalette` is only the
Compose adapter over it. **Never hardcode a slot hex in a composable** — resolve the
one stored on the row.

Amounts are `Long` minor units everywhere (8250 == €82.50). Never format one by hand,
and never convert to `Double` except for chart geometry.

Status colours always ship with an icon **and** text — never colour alone.

## Assigning a color to a new category

`CategorySlots.nextFree(categoryDao.usedColorHexes())`. Past slot 8 it returns null and
the user picks an existing slot; duplicates are acceptable because categories are always
name-labeled. Never generate a new hue, never randomize, never assign the reserved gray.

## If you change any palette hex

Re-run the validator and paste the result into DESIGN.md §3b. It must pass in both
modes on the adjacent pairlist before the change lands:

```bash
py -3 tools/validate_palette.py "<light hexes, comma-separated>" --mode light --surface "#FEF7FF"
```

## Done checklist

DESIGN.md §8. Run through it before calling any UI work finished.
