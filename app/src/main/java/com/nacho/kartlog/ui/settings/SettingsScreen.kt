package com.nacho.kartlog.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.shareBackup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.BuildConfig
import com.nacho.kartlog.debug.TextSample
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.shareCsv

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onOpenDashboard: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 0 = sin diálogo, 1 = primera confirmación, 2 = segunda confirmación.
    var wipeStep by rememberSaveable { mutableIntStateOf(0) }
    var confirmRivals by rememberSaveable { mutableStateOf(false) }

    val messageText = when (state.message) {
        SettingsMessage.SEEDED -> stringResource(R.string.settings_seeded)
        SettingsMessage.WIPED -> stringResource(R.string.settings_wiped)
        SettingsMessage.FAILED -> stringResource(R.string.settings_failed)
        SettingsMessage.NOTHING_TO_EXPORT -> stringResource(R.string.settings_nothing_to_export)
        SettingsMessage.BACKUP_FAILED -> stringResource(R.string.backup_failed)
        SettingsMessage.RESTORE_INVALID -> stringResource(R.string.restore_invalid)
        SettingsMessage.RESTORE_NEWER -> stringResource(R.string.restore_newer)
        SettingsMessage.RESTORE_IO -> stringResource(R.string.restore_io)
        SettingsMessage.RIVALS_CLEARED -> stringResource(R.string.settings_rivals_cleared)
        SettingsMessage.SAMPLES_MISSING -> stringResource(R.string.settings_samples_missing)
        null -> null
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareFiles.collect { shareCsv(context, it.fileName, it.content) }
    }
    LaunchedEffect(viewModel) { viewModel.backupFiles.collect { shareBackup(context, it) } }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.prepareRestore { context.contentResolver.openInputStream(it) } }
    }
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    LaunchedEffect(state.openDashboard) {
        if (state.openDashboard) {
            viewModel.dashboardOpened()
            onOpenDashboard()
        }
    }

    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.nav_settings), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.debugToolsAvailable) {
                // Sección de depuración al principio y plegable: las herramientas de prueba quedan a un toque.
                var debugExpanded by rememberSaveable { mutableStateOf(false) }
                KartCard(Modifier.fillMaxWidth().testTag("debug_section")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { debugExpanded = !debugExpanded }
                            .testTag("debug_header"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_debug_title).uppercase() + if (debugExpanded) " ▾" else " ▸",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.kartColors.warning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (debugExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SecondaryButton(
                                text = stringResource(R.string.settings_simulate_track),
                                onClick = viewModel::simulateTrackSession,
                                enabled = !state.isBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.settings_test_ocr),
                                onClick = viewModel::testOcrWithSample,
                                enabled = !state.isBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.settings_import_sample_a),
                                onClick = { viewModel.importTextSample(TextSample.A) },
                                enabled = !state.isBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.settings_import_sample_b),
                                onClick = { viewModel.importTextSample(TextSample.B) },
                                enabled = !state.isBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.settings_seed),
                                onClick = viewModel::seedDebugData,
                                enabled = !state.isBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.settings_wipe),
                                onClick = { wipeStep = 1 },
                                enabled = !state.isBusy,
                                color = MaterialTheme.kartColors.warning,
                            )
                        }
                    }
                }
            }

            KartCard(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_driver_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                )
                val savedName by viewModel.driverName.collectAsStateWithLifecycle()
                var nameDraft by rememberSaveable(savedName) { mutableStateOf(savedName) }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it
                        viewModel.setDriverName(it)
                    },
                    label = { Text(stringResource(R.string.settings_driver_name)) },
                    supportingText = { Text(stringResource(R.string.settings_driver_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val feedbackOn by viewModel.lapFeedback.collectAsStateWithLifecycle()
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_lap_feedback), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_lap_feedback_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kartColors.textMuted,
                        )
                    }
                    androidx.compose.material3.Switch(checked = feedbackOn, onCheckedChange = viewModel::setLapFeedback)
                }
            }

            KartCard(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_data_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                )
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.settings_export_all),
                        onClick = viewModel::exportAll,
                        enabled = !state.isBusy,
                    )
                    Text(
                        text = stringResource(R.string.settings_export_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kartColors.textMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.settings_clear_rivals),
                        onClick = { confirmRivals = true },
                        enabled = !state.isBusy,
                        color = MaterialTheme.kartColors.warning,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_clear_rivals_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kartColors.textMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            KartCard(Modifier.fillMaxWidth().testTag("backup_card")) {
                Text(
                    text = stringResource(R.string.backup_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                )
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.backup_export),
                        onClick = viewModel::exportBackup,
                        enabled = !state.isBusy,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.backup_restore),
                        onClick = { pickBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        enabled = !state.isBusy,
                        color = MaterialTheme.kartColors.warning,
                    )
                    Text(
                        text = stringResource(R.string.backup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kartColors.textMuted,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.settings_version,
                    stringResource(R.string.app_name),
                    versionName,
                    BuildConfig.GIT_HASH,
                    BuildConfig.BUILD_TIME,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kartColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.pendingRestore?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(R.string.restore_message, formatDateTime(info.createdAtMs), info.trackCount, info.schemaVersion),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("restore_message"),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore, modifier = Modifier.heightIn(min = 56.dp).testTag("restore_confirm")) {
                    Text(stringResource(R.string.restore_confirm).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }

    if (confirmRivals) {
        AlertDialog(
            onDismissRequest = { confirmRivals = false },
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.settings_clear_rivals_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.settings_clear_rivals_message), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRivals = false
                        viewModel.clearRivalData()
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_rivals_confirm).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRivals = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }

    if (state.sampleChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSampleChoices,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.settings_test_ocr), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    state.sampleChoices.forEach { name ->
                        TextButton(onClick = { viewModel.runSample(name) }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissSampleChoices, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }

    if (wipeStep > 0) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            containerColor = MaterialTheme.kartColors.card,
            title = {
                Text(
                    stringResource(if (wipeStep == 1) R.string.settings_wipe_title_1 else R.string.settings_wipe_title_2),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(if (wipeStep == 1) R.string.settings_wipe_message_1 else R.string.settings_wipe_message_2),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (wipeStep == 1) {
                            wipeStep = 2
                        } else {
                            wipeStep = 0
                            viewModel.wipeAllData()
                        }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(
                        stringResource(if (wipeStep == 1) R.string.action_continue else R.string.settings_wipe_confirm).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeStep = 0 }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }
}
