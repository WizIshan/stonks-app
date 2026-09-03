# DESIGN.md — Stonks App Design System

Canonical visual/UX conventions for this app. Every screen follows this document.
Claude Code reads it via the project skill in `.claude/skills/stonks-ui/`.

Base language: **Material Design 3 (Material You)** via `androidx.compose.material3`.
Work *with* M3, not around it — reach for a custom component only when M3 has no equivalent.

---

## 1. Spacing — 4dp grid

All spacing comes from tokens. **Never write a raw `dp` value in a screen file.**

| Token | Value | Use |
|---|---|---|
| `Spacing.xs` | 4.dp | Icon-to-label, chip inner gap, chart bar gap |
| `Spacing.sm` | 8.dp | Between related items in a group; list row vertical gap |
| `Spacing.md` | 12.dp | Inside compact components |
| `Spacing.lg` | 16.dp | **Screen horizontal padding; card inner padding** |
| `Spacing.xl` | 24.dp | Between sections on a screen |
| `Spacing.xxl` | 32.dp | Above/below a hero figure; empty-state breathing room |

```kotlin
// ui/theme/Spacing.kt
object Spacing {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp
    val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp
}
```

Corner radius: **16.dp** cards, **12.dp** dialogs/sheets, **8.dp** chips/inputs, **4.dp** chart bar ends.
Minimum touch target: **48.dp** — always, even when the drawn mark is smaller.

---

## 2. Type — M3 type scale, fixed role map

Use `MaterialTheme.typography` only. **Never construct an ad-hoc `TextStyle` in a screen file.**

| Role in this app | M3 style | Color |
|---|---|---|
| Top app bar title | `headlineSmall` | `onSurface` |
| Dashboard hero figure (month total) | `displaySmall` | `onSurface` |
| Amount entry field (the value being typed) | `headlineSmall` | `onSurface` |
| Section header ("This month", "By category") | `titleMedium` | `onSurface` |
| List row primary (category / source) | `bodyLarge` | `onSurface` |
| List row secondary (date, note, trip) | `bodyMedium` | `onSurfaceVariant` |
| Amounts in any list/table column | `bodyLarge` + **tabular figures** | `onSurface` |
| Chip / button / legend label | `labelLarge` | contextual |
| Axis ticks, chart labels | `labelSmall` | `onSurfaceVariant` |
| Empty-state body, helper text | `bodyMedium` | `onSurfaceVariant` |

**Figures:** the hero figure uses default proportional figures. Any amount in a
*vertical column* (history rows, budget lists, axis ticks) uses tabular figures so
decimal points align:

```kotlin
val TabularAmount = TextStyle(fontFeatureSettings = "tnum")
Text(amount.formatted(), style = MaterialTheme.typography.bodyLarge.merge(TabularAmount))
```

---

## 3. Color — two independent systems

This is the most important section. The app has **two** color systems that must never be mixed.

### 3a. UI chrome → M3 `colorScheme` (dynamic)

Surfaces, buttons, text, nav, inputs. Dynamic color **on** for Android 12+ so the app
adopts the user's wallpaper; static fallback below that.

```kotlin
val colors = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    dark -> FallbackDarkScheme
    else -> FallbackLightScheme
}
```

Fallback scheme seed: **`#2A78D6`** — generate the full scheme with
[Material Theme Builder](https://m3.material.io/theme-builder) and paste it into
`ui/theme/Color.kt`. Do not hand-pick individual scheme roles.

Card surface: `surfaceContainerLow`. Prefer **tonal** elevation over shadows
(`Card(colors = CardDefaults.cardColors(containerColor = surfaceContainerLow))`, elevation 0).

### 3b. Data identity → fixed 8-slot category palette (NEVER dynamic)

**Color follows the entity, never its rank, and never the wallpaper.** If "Food" is blue,
it is blue in the chart, the history chip, and the budget meter — this month and next,
whatever the user's wallpaper is. Dynamic color must not touch these.

Assigned in **fixed slot order** at category creation and stored in `Category.colorHex`.

| Slot | Hue | Light | Dark | Default category |
|---|---|---|---|---|
| 1 | blue | `#2A78D6` | `#3987E5` | Food & Drink |
| 2 | orange | `#EB6834` | `#D95926` | Transport |
| 3 | aqua | `#1BAF7A` | `#199E70` | Lodging |
| 4 | yellow | `#EDA100` | `#C98500` | Shopping |
| 5 | magenta | `#E87BA4` | `#D55181` | Entertainment |
| 6 | green | `#008300` | `#008300` | Groceries |
| 7 | violet | `#4A3AA7` | `#9085E9` | Health |
| 8 | red | `#E34948` | `#E66767` | Bills & Utilities |
| — | gray | `#898781` | `#898781` | **"Other"** (reserved fold bucket — not a real category) |

These eight are the **defaults and the presets**, not a limit. A new category takes the
next unused one, so leaving the picker alone still produces a set that reads well together
— but any colour can be chosen.

**What the eight still guarantee, and a custom colour does not:** no two of them are
confusable under simulated colour blindness, and each was checked against both surfaces.
An arbitrary colour carries no such promise. What makes that acceptable here is the rule
below it — every mark in this app is name-labeled, so colour reinforces identity rather
than carrying it. Someone who picks two similar blues gets two similar blues, and their
labels still tell them apart.

**Rules:**
- A **custom colour keeps its hue and chroma exactly**, but its lightness is clamped into
  the readable band for whichever surface it is being drawn on
  (`ColorMath.adaptForSurface`). This is the one guarantee kept by force: a near-black
  picked in light mode would otherwise vanish in dark mode. The eight built-in colours
  bypass this entirely — their light/dark pairs were chosen by hand and are used verbatim.
- Colours are **never generated, cycled or randomised** by the app. Every colour is either
  a built-in or one a person chose.
- A category's colour is editable and lives on its row, so changing it updates the chart,
  the history chip and the budget meter together.
- Duplicates are allowed. A category is always name-labeled, so colour is never the sole
  identifier.
- Gray is reserved for the "Other" bucket. Never assign it to a real category.
- The dark column is a **selected** set of steps for the dark surface, not an
  automatic lightening of the light column.

**Validation (re-run if you change any hex):**

```bash
py -3 tools/validate_palette.py "#2a78d6,#eb6834,#1baf7a,#eda100,#e87ba4,#008300,#4a3aa7,#e34948" --mode light --surface "#FEF7FF"
```

```bash
py -3 tools/validate_palette.py "#3987e5,#d95926,#199e70,#c98500,#d55181,#008300,#9085e9,#e66767" --mode dark --surface "#141218"
```

Current result: **all checks pass** in both modes on the adjacent pairlist.
Worst adjacent colorblind separation ΔE 9.1 light / 8.4 dark (target ≥8);
worst normal-vision separation ΔE 19.6 light / 19.3 dark (floor ≥15).

**One WARN carries an obligation:** on the light surface, aqua (2.68:1), yellow (2.06:1)
and magenta (2.56:1) fall below 3:1 against `#FEF7FF`. Relief rule → **every one of these
marks must carry a visible text label**. This is why bars are direct-labeled and why the
app has no unlabelled color-only chart.

### 3c. Status colors — reserved, fixed, never themed

Never reused as a series color. **Always shipped with an icon + text label**, never
color alone (`warning` and `serious` are deliberately sub-3:1 on light — the label is the mitigation).

| Role | Hex | Use |
|---|---|---|
| good | `#0CA30C` | Budget healthy; positive net cash flow accent |
| warning | `#FAB219` | Budget ≥ alert threshold (default 80%) |
| serious | `#EC835A` | Budget ≥ 100% |
| critical | `#D03B3B` | Budget materially over; failed import |

### 3d. Diverging (net cash flow)

Positive ↔ negative uses the **blue ↔ red** pair with a neutral gray zero line —
not green/red, which is the worst possible pair for colorblind readers.
Positive `#2A78D6` light / `#3987E5` dark · negative `#E34948` light / `#E66767` dark ·
zero line `#C3C2B7` light / `#383835` dark.
**The `+` / `−` sign is always rendered**, so sign never depends on hue.

---

## 4. Charts

Chart choices are decisions, not preferences. These are settled:

| Dashboard element | Form | Why |
|---|---|---|
| Month total spend | **Hero figure** (`displaySmall`), not a chart | One number is the chart |
| Income / net cash flow | **KPI row** of stat tiles beside the hero | Handful of headline numbers |
| Spend by category | **Ranked horizontal bar**, cap 6 + "Other" | Magnitude comparison; long names fit; every bar gets a text label |
| Spend by trip | Same ranked horizontal bar | Consistency |
| Trend over time | **Line**, max 2 series (expense, orange; income, blue) | Change over time |
| Net cash flow by month | **Diverging bar** around a zero baseline | Polarity |
| Budget vs limit | **Meter** (see §5), one per category | Single ratio against a limit |

**Explicitly rejected:**
- **No donut/pie for categories.** With 8 hues, arbitrary slice adjacency fails the
  colorblind *and* normal-vision separation floors (worst pair ΔE 3.2 light / 1.6 dark
  simulated). A ranked bar is more readable anyway. Verified: `--pairs all` FAILs,
  `--pairs adjacent` PASSes.
- **No dual-axis charts, ever.** Two measures of different scale → two charts, or index to a common base.
- **No numbers on every data point.** Label selectively — the extremes and the current value.

**Mark specs:**
- Bars: **4.dp** corner radius on the *value* end only, square at the baseline.
  **2.dp** surface-colored gap between adjacent bars.
- Lines: **2.dp** stroke. Point markers ≥ **8.dp** drawn, ≥ **48.dp** touch target.
- Gridlines: horizontal only, 1.dp hairline in `outlineVariant`. No vertical gridlines, no chart border.
- Axes: `labelSmall` / `onSurfaceVariant`. Y axis starts at zero for bars — always.
- Text in and around a chart wears **text colors** (`onSurface` / `onSurfaceVariant`),
  never the series color. The colored mark next to a label carries the identity.

**Legend & labels:** ranked bars are direct-labeled (name left, amount right) so they
need no legend box. The 2-series trend line gets a legend row above the plot.
A single-series chart never gets a legend — the section header names it.

**Interaction (Android has no hover):** tap a bar or point to select it → the detail
appears in a row directly beneath the chart (not a floating tooltip). Selected mark
gets a 2.dp surface-colored ring. Filters sit in **one horizontal `FilterChip` row above**
the chart, never inside it.

**Library: none — charts are drawn with Compose `Canvas`.** Vico was the original pick and
was dropped once the specs above were settled. Only the trend line was ever a candidate for
it: ranked bars are a labelled list with a rule behind each row, and budget meters and the
diverging bar are custom regardless. Against that one chart, a library brings defaults this
document spends its time overriding — its own gridlines, its own markers, its own label
placement. Drawing directly is less code than configuring it away, and it is the only way
these mark specs are exactly met rather than approximately.

---

## 5. Budget meters

A budget bar is a **meter**, not a series bar:
- Track: the category's own hue at **12% alpha**. Fill: the same hue at full strength.
- Fill switches to the **status** color once a threshold is crossed
  (≥ threshold → `warning`, ≥ 100% → `serious`, well over → `critical`).
- Always accompanied by an **icon + text** (e.g. `8,200 of 10,000 · 82%`), so the state
  never depends on color alone.
- Over-budget overflow is shown by the label, not by a bar that runs past its track.

---

## 6. Empty & error states

No screen ever shows a blank body. Every empty state is:
**icon (48.dp, `onSurfaceVariant`) → `titleMedium` headline → `bodyMedium` one-liner → primary action button**, centred, `Spacing.xxl` from the top.

| Screen | Headline | Line | Action |
|---|---|---|---|
| History (no data) | Nothing logged yet | Add your first expense to see it here. | Add expense |
| History (filtered to nothing) | No matches | Nothing matches these filters. | Clear filters |
| Dashboard (no data) | No spend this month | Your summary appears once you log something. | Add expense |
| Trips (none) | No trips yet | Group travel spend by creating a trip. | New trip |
| Budgets (none) | No budgets set | Set a monthly limit to track spend against it. | Set a budget |
| Import failed | Couldn't read that file | It isn't a Stonks backup, or it's from a newer version. | Choose another file |

Import failure uses the `critical` status color **with** the error icon and this text —
nothing is destroyed, since import runs inside a single Room transaction.

---

## 7. Screen structure

- **Bottom navigation**: Dashboard · History · Budgets · Settings.
- **FAB** (extended, "Add") on Dashboard and History → opens Add Expense; the entry
  sheet has an Expense / Income toggle at the top.
- Top app bar: `LargeTopAppBar` on Dashboard, `TopAppBar` elsewhere.
- Amount fields: `KeyboardType.Decimal`, currency symbol as a leading prefix,
  autofocus on open, amount is the first field in every entry form.
- Destructive actions (delete an expense, restore a backup) always confirm in a dialog.

---

## 8. Non-negotiables checklist

Before any UI change is done:

1. No raw `dp` literal in a screen file — use `Spacing`.
2. No ad-hoc `TextStyle` — use `MaterialTheme.typography`.
3. No `Color(0xFF…)` in a screen file — use `MaterialTheme.colorScheme` or `CategoryPalette`.
4. Category color read from the entity, never from its position in a sorted list.
5. Every colored mark has a text label beside it.
6. Every icon-only control has a `contentDescription`.
7. Every touch target ≥ 48.dp.
8. Light **and** dark `@Preview` on every new composable.
9. No dual-axis chart, no pie of categories, no value label on every point.
