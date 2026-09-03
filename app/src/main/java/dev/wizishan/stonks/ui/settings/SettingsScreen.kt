package dev.wizishan.stonks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.data.backup.BackupFile
import dev.wizishan.stonks.data.backup.ImportFailure
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.theme.Radius
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme

@Composable
fun SettingsRoute(
    onCategoriesClick: () -> Unit,
    onTripsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val shareTitle = stringResource(R.string.settings_share_title)
    val exported = stringResource(R.string.settings_exported)
    val exportFailed = stringResource(R.string.settings_export_failed)
    val importedTemplate = stringResource(R.string.settings_imported)
    val failureStrings = importFailureStrings()

    // Any MIME type: providers label a .json file inconsistently (application/json,
    // text/plain, sometimes octet-stream), and a narrow filter greys out the very file
    // the user is trying to pick.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let(viewModel::onFilePicked) },
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShareExport -> context.startActivity(shareIntent(event.uri, shareTitle))
                is SettingsEvent.Exported -> snackbarHostState.showSnackbar(exported)
                is SettingsEvent.ExportFailed -> snackbarHostState.showSnackbar(exportFailed)
                is SettingsEvent.Imported -> snackbarHostState.showSnackbar(
                    importedTemplate.format(event.summary.entries, event.summary.categories)
                )

                is SettingsEvent.ImportFailed ->
                    snackbarHostState.showSnackbar(failureStrings(event.failure))
            }
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onCategoriesClick = onCategoriesClick,
        onTripsClick = onTripsClick,
        onExport = viewModel::export,
        onPickFile = { picker.launch(arrayOf("*/*")) },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onCategoriesClick: () -> Unit,
    onTripsClick: () -> Unit,
    onExport: () -> Unit,
    onPickFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ActionCard(
                title = stringResource(R.string.categories_manage),
                description = stringResource(R.string.categories_manage_description),
            ) {
                OutlinedButton(onClick = onCategoriesClick) {
                    Text(stringResource(R.string.categories_manage))
                }
            }

            ActionCard(
                title = stringResource(R.string.trips_title),
                description = stringResource(R.string.trips_manage_description),
            ) {
                OutlinedButton(onClick = onTripsClick) {
                    Text(stringResource(R.string.trips_title))
                }
            }

            Text(
                text = stringResource(R.string.settings_backup_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            ActionCard(
                title = stringResource(R.string.settings_export),
                description = stringResource(R.string.settings_export_description),
            ) {
                Button(onClick = onExport, enabled = !state.busy) {
                    Text(stringResource(R.string.settings_export))
                }
            }

            ActionCard(
                title = stringResource(R.string.settings_import),
                description = stringResource(R.string.settings_import_description),
            ) {
                OutlinedButton(onClick = onPickFile, enabled = !state.busy) {
                    Text(stringResource(R.string.settings_import))
                }
            }

            Text(
                text = stringResource(R.string.settings_format_version, state.formatVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmImport) {
                    Text(
                        text = stringResource(R.string.settings_restore_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action()
        }
    }
}

/**
 * Each failure gets its own wording. "Import failed" tells someone holding what they
 * believe are their records nothing about whether to try a different file, update the app,
 * or give up on that one.
 */
@Composable
private fun importFailureStrings(): (ImportFailure) -> String {
    val notJson = stringResource(R.string.settings_import_failed_not_json) + " · " +
        stringResource(R.string.settings_import_failed_not_json_detail)
    val versionTitle = stringResource(R.string.settings_import_failed_version)
    val versionDetail = stringResource(R.string.settings_import_failed_version_detail)
    val invalidTitle = stringResource(R.string.settings_import_failed_invalid)
    val invalidDetail = stringResource(R.string.settings_import_failed_invalid_detail)

    return { failure ->
        when (failure) {
            is ImportFailure.NotJson -> notJson
            is ImportFailure.UnsupportedVersion ->
                "$versionTitle · " + versionDetail.format(failure.found, BackupFile.CURRENT_VERSION)

            is ImportFailure.Invalid -> "$invalidTitle · " + invalidDetail.format(failure.reason)
        }
    }
}

private fun shareIntent(uri: Uri, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title)
}

@Preview(name = "Settings · light", showBackground = true)
@Preview(name = "Settings · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    StonksTheme {
        SettingsScreen(
            state = SettingsUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onCategoriesClick = {}, onTripsClick = {}, onExport = {}, onPickFile = {}, onConfirmImport = {}, onCancelImport = {},
        )
    }
}
