package dev.wizishan.stonks.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wizishan.stonks.StonksApplication
import dev.wizishan.stonks.ui.budget.BudgetViewModel
import dev.wizishan.stonks.ui.categories.CategoriesViewModel
import dev.wizishan.stonks.ui.dashboard.DashboardViewModel
import dev.wizishan.stonks.ui.entry.AddEntryViewModel
import dev.wizishan.stonks.ui.history.HistoryViewModel
import dev.wizishan.stonks.ui.recurring.RecurringViewModel
import dev.wizishan.stonks.ui.settings.SettingsViewModel
import dev.wizishan.stonks.ui.trips.TripsViewModel

/** Builds ViewModels from the [dev.wizishan.stonks.AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            val container = stonksApplication().container
            AddEntryViewModel(
                container.repository,
                container.recurringGenerator,
                createSavedStateHandle(),
            )
        }
        initializer { HistoryViewModel(stonksApplication().container.repository) }
        initializer { DashboardViewModel(stonksApplication().container.repository) }
        initializer { RecurringViewModel(stonksApplication().container.repository) }
        initializer { CategoriesViewModel(stonksApplication().container.repository) }
        initializer { TripsViewModel(stonksApplication().container.repository) }
        initializer { BudgetViewModel(stonksApplication().container.repository) }
        initializer {
            val container = stonksApplication().container
            SettingsViewModel(container.backupManager, container.backupFiles)
        }
    }
}

private fun CreationExtras.stonksApplication(): StonksApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as StonksApplication
