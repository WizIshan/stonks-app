package dev.wizishan.stonks.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wizishan.stonks.StonksApplication
import dev.wizishan.stonks.ui.entry.AddEntryViewModel

/** Builds ViewModels from the [dev.wizishan.stonks.AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            AddEntryViewModel(stonksApplication().container.repository)
        }
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.stonksApplication(): StonksApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as StonksApplication
