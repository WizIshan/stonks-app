package dev.wizishan.stonks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.wizishan.stonks.ui.navigation.StonksNavHost
import dev.wizishan.stonks.ui.theme.StonksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StonksTheme {
                StonksNavHost()
            }
        }
    }
}
