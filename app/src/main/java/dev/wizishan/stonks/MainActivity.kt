package dev.wizishan.stonks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.wizishan.stonks.ui.entry.AddEntryScreen
import dev.wizishan.stonks.ui.theme.StonksTheme

/**
 * For now this opens straight onto the Add screen — it is the only screen that exists.
 * Navigation and the bottom bar (Dashboard · History · Budgets · Settings, DESIGN.md §7)
 * arrive with the History screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StonksTheme {
                AddEntryScreen()
            }
        }
    }
}
