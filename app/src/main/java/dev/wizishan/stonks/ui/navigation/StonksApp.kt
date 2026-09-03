package dev.wizishan.stonks.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.wizishan.stonks.R
import dev.wizishan.stonks.ui.budget.BudgetRoute
import dev.wizishan.stonks.ui.categories.CategoriesRoute
import dev.wizishan.stonks.ui.dashboard.DashboardRoute
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.wizishan.stonks.data.repository.HistoryItem
import dev.wizishan.stonks.ui.entry.AddEntryRoute
import dev.wizishan.stonks.ui.entry.AddEntryViewModel
import dev.wizishan.stonks.ui.entry.EntryType
import dev.wizishan.stonks.ui.history.HistoryRoute
import dev.wizishan.stonks.ui.settings.SettingsRoute
import dev.wizishan.stonks.ui.trips.TripsRoute
import dev.wizishan.stonks.ui.recurring.RecurringRoute

object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val BUDGETS = "budgets"
    const val SETTINGS = "settings"
    /**
     * One route for adding and editing. With no arguments it is a blank form; with an id
     * and a type it opens that row for correction. The type is on the route because the
     * two tables both number from 1, so an id alone is ambiguous.
     */
    const val ENTRY = "entry?entryId={entryId}&entryType={entryType}"

    fun addEntry(): String = "entry"

    fun editEntry(item: HistoryItem): String {
        val type = if (item is HistoryItem.ExpenseItem) EntryType.EXPENSE else EntryType.INCOME
        return "entry?entryId=${item.id}&entryType=${type.name}"
    }
    const val RECURRING = "recurring"
    const val CATEGORIES = "categories"
    const val TRIPS = "trips"
}

/**
 * The destinations the bottom bar switches between.
 *
 * All four from DESIGN.md §7, now that every one of them exists.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DASHBOARD(Routes.DASHBOARD, R.string.dashboard_title, Icons.Default.Home),
    HISTORY(Routes.HISTORY, R.string.history_title, Icons.AutoMirrored.Filled.List),
    BUDGETS(Routes.BUDGETS, R.string.budgets_title, Icons.Default.Notifications),
    SETTINGS(Routes.SETTINGS, R.string.settings_title, Icons.Default.Settings),
}

@Composable
fun StonksApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Add is pushed on top of a tab rather than being a tab itself, so the bar hides there:
    // leaving it visible would suggest you can switch tabs mid-entry and lose what you typed.
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardRoute(
                    onAddClick = { navController.navigate(Routes.addEntry()) },
                    onRecurringClick = { navController.navigate(Routes.RECURRING) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(
                    onAddClick = { navController.navigate(Routes.addEntry()) },
                    onEditEntry = { navController.navigate(Routes.editEntry(it)) },
                )
            }
            composable(Routes.BUDGETS) {
                BudgetRoute()
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onCategoriesClick = { navController.navigate(Routes.CATEGORIES) },
                    onTripsClick = { navController.navigate(Routes.TRIPS) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.TRIPS) {
                TripsRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.ENTRY,
                arguments = listOf(
                    navArgument(AddEntryViewModel.ENTRY_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(AddEntryViewModel.ENTRY_TYPE_ARG) {
                        type = NavType.StringType
                        defaultValue = EntryType.EXPENSE.name
                    },
                ),
            ) {
                AddEntryRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECURRING) {
                RecurringRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Switch tabs without stacking them.
 *
 * Without `popUpTo` every tap would push another copy onto the back stack, so back would
 * walk through the history of taps instead of leaving the app. `saveState`/`restoreState`
 * keep each tab's scroll position and filters across a switch.
 */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
