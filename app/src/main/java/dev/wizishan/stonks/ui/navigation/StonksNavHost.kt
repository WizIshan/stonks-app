package dev.wizishan.stonks.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.wizishan.stonks.ui.entry.AddEntryRoute
import dev.wizishan.stonks.ui.history.HistoryRoute

object Routes {
    const val HISTORY = "history"
    const val ADD = "add"
}

/**
 * Two destinations for now: History is the home screen, and the FAB pushes Add on top.
 *
 * The bottom bar from DESIGN.md §7 arrives with the Dashboard — a navigation bar needs
 * somewhere to navigate, and stubbing Dashboard, Budgets and Settings as blank screens
 * just to fill it would be worse than not having it yet.
 */
@Composable
fun StonksNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HISTORY,
        modifier = modifier,
    ) {
        composable(Routes.HISTORY) {
            HistoryRoute(onAddClick = { navController.navigate(Routes.ADD) })
        }
        composable(Routes.ADD) {
            AddEntryRoute(onBack = { navController.popBackStack() })
        }
    }
}
