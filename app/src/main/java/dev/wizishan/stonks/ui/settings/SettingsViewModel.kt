package dev.wizishan.stonks.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.backup.BackupFile
import dev.wizishan.stonks.data.backup.BackupFiles
import dev.wizishan.stonks.data.backup.BackupManager
import dev.wizishan.stonks.data.backup.ImportException
import dev.wizishan.stonks.data.backup.ImportFailure
import dev.wizishan.stonks.data.backup.ImportSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val busy: Boolean = false,
    /** The file the user picked, held while the replace-everything warning is shown. */
    val pendingImport: Uri? = null,
) {
    val formatVersion: Int get() = BackupFile.CURRENT_VERSION
}

sealed interface SettingsEvent {
    data class ShareExport(val uri: Uri) : SettingsEvent
    data class Exported(val bytes: Int) : SettingsEvent
    data class Imported(val summary: ImportSummary) : SettingsEvent
    data class ImportFailed(val failure: ImportFailure) : SettingsEvent
    data object ExportFailed : SettingsEvent
}

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val backupFiles: BackupFiles,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun export() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                val json = backupManager.export()
                backupFiles.writeExport(json) to json.length
            }.onSuccess { (uri, size) ->
                _uiState.update { it.copy(busy = false) }
                _events.send(SettingsEvent.Exported(size))
                _events.send(SettingsEvent.ShareExport(uri))
            }.onFailure {
                _uiState.update { it.copy(busy = false) }
                _events.send(SettingsEvent.ExportFailed)
            }
        }
    }

    /**
     * Picking a file only arms the restore. Restoring replaces everything, so the warning
     * comes between the picker and the write rather than after it.
     */
    fun onFilePicked(uri: Uri) = _uiState.update { it.copy(pendingImport = uri) }

    fun cancelImport() = _uiState.update { it.copy(pendingImport = null) }

    fun confirmImport() {
        val uri = _uiState.value.pendingImport ?: return
        _uiState.update { it.copy(pendingImport = null, busy = true) }

        viewModelScope.launch {
            val text = backupFiles.readText(uri)
            if (text == null) {
                _uiState.update { it.copy(busy = false) }
                _events.send(SettingsEvent.ImportFailed(ImportFailure.NotJson))
                return@launch
            }

            backupManager.import(text)
                .onSuccess { summary ->
                    _uiState.update { it.copy(busy = false) }
                    _events.send(SettingsEvent.Imported(summary))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false) }
                    _events.send(
                        SettingsEvent.ImportFailed(
                            (error as? ImportException)?.failure
                                ?: ImportFailure.Invalid(error.message.orEmpty())
                        )
                    )
                }
        }
    }
}
