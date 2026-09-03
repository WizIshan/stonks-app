package dev.wizishan.stonks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * Material 3's default type scale, unmodified.
 *
 * DESIGN.md §2 maps each role in this app onto one of these styles rather than defining
 * new ones — a screen file should be picking a role, never inventing a size.
 */
val Typography = Typography()

/**
 * Lining, fixed-width digits.
 *
 * Merge this into any amount that sits in a **vertical column** — history rows, budget
 * lists, axis ticks — so decimal points line up down the column instead of drifting with
 * the width of each digit. Standalone figures (the dashboard hero) keep the default
 * proportional digits, which look better on their own.
 */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")
