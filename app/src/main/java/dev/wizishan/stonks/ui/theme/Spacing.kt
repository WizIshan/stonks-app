package dev.wizishan.stonks.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing grid. See DESIGN.md §1.
 *
 * Screen files must not contain raw `dp` literals — every gap, padding and inset
 * comes from here, so the app stays on one rhythm.
 */
object Spacing {
    /** 4dp — icon-to-label, chip inner gap, chart bar gap. */
    val xs = 4.dp

    /** 8dp — between related items in a group; list row vertical gap. */
    val sm = 8.dp

    /** 12dp — inside compact components. */
    val md = 12.dp

    /** 16dp — screen horizontal padding; card inner padding. The default. */
    val lg = 16.dp

    /** 24dp — between sections on a screen. */
    val xl = 24.dp

    /** 32dp — above/below a hero figure; empty-state breathing room. */
    val xxl = 32.dp
}

/** Corner radii. See DESIGN.md §1. */
object Radius {
    val card = 16.dp
    val sheet = 12.dp
    val chip = 8.dp
    val chartBarEnd = 4.dp
}

/** Minimum touch target — applies even when the drawn mark is smaller. */
val MinTouchTarget = 48.dp
