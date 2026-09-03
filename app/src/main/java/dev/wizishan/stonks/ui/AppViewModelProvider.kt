package dev.wizishan.stonks.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wizishan.stonks.StonksApplication
import dev.wizishan.stonks.ui.dashboard.DashboardViewModel
import dev.wizishan.stonks.ui.entry.AddEntryViewModel
import dev.wizishan.stonks.ui.history.HistoryViewModel

/** Builds ViewModels from the [dev.wizishan.stonks.AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { AddEntryViewModel(stonksApplication().container.repository) }
        initializer { HistoryViewModel(stonksApplication().container.repository) }
        initializer { DashboardViewModel(stonksApplication().container.repository) }
    }
}

private fun CreationExtras.stonksApplication(): StonksApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as StonksApplication
