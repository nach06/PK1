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
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
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
import com.nacho.kartlog.domain.settings.DashboardOrientation
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
        SettingsMessage.GPS_SEEDED -> stringResource(R.string.settings_gps_seeded)
        SettingsMessage.WIPED -> stringResource(R.string.settings_wiped)
        SettingsMessage.FAILED -> stringResource(R.string.settings_failed)
        SettingsMessage.NOTHING_TO_EXPORT -> stringResource(R.string.settings_nothing_to_export)
        SettingsMessage.BACKUP_FAILED -> stringResource(R.string.backup_failed)
        SettingsMessage.RESTORE_INVALID -> stringResource(R.string.restore_invalid)
        SettingsMessage.RESTORE_NEWER -> stringResource(R.string.restore_newer)
        SettingsMessage.RESTORE_IO -> stringResource(R.string.restore_io)
        SettingsMessage.RIVALS_CLEARED -> stringResource(R.string.settings_rivals_cleared)
        SettingsMessage.SAMPLES_MISSING -> stringResource(R.string.settings_samples_missing)
        SettingsMessage.NO_DEBUG_BACKUP -> stringResource(R.string.settings_debug_backup_none)
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
    val scroll = androidx.compose.foundation.rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pickSampleImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importPickedImage(it.toString()) }
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
                .verticalScroll(scroll)
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
                        // Rejilla de 2 columnas: todo cabe a la vista sin desplazarse (el emulador de pruebas solo admite clic).
                        val buttons = debugButtons(viewModel, state, pickImage = { pickSampleImage.launch(arrayOf("image/*")) }, onWipe = { wipeStep = 1 }, onEnd = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } })
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            buttons.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { b ->
                                        Box(Modifier.weight(1f)) {
                                            SecondaryButton(
                                                text = b.label,
                                                onClick = b.onClick,
                                                enabled = b.enabled,
                                                color = if (b.warn) MaterialTheme.kartColors.warning else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.testTag(b.tag),
                                            )
                                        }
                                    }
                                    if (pair.size == 1) Box(Modifier.weight(1f))
                                }
                            }
                            state.debugBackupPath?.let {
                                Text(
                                    stringResource(R.string.settings_debug_backup_saved, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.kartColors.textMuted,
                                    modifier = Modifier.testTag("debug_note"),
                                )
                            }
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
                    val savePhoto by viewModel.saveImportPhoto.collectAsStateWithLifecycle()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_save_photo),
                        hint = stringResource(R.string.settings_save_photo_hint),
                        checked = savePhoto,
                        onChange = viewModel::setSaveImportPhoto,
                        tag = "switch_save_photo",
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
                    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()
                    val lastAuto by viewModel.lastAutoBackupMs.collectAsStateWithLifecycle()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_backup),
                        hint = if (lastAuto == 0L) stringResource(R.string.settings_auto_backup_never) else stringResource(R.string.settings_auto_backup_last, formatDateTime(lastAuto)),
                        checked = autoBackup,
                        onChange = viewModel::setAutoBackup,
                        tag = "switch_auto_backup",
                    )
                }
            }

            KartCard(Modifier.fillMaxWidth().testTag("dashboard_settings")) {
                Text(
                    text = stringResource(R.string.settings_dashboard_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                )
                val orientation by viewModel.dashboardOrientation.collectAsStateWithLifecycle()
                Text(
                    stringResource(R.string.settings_orientation),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                androidx.compose.foundation.layout.Box(Modifier.testTag("orientation_selector")) {
                    com.nacho.kartlog.ui.components.ChipSelector(
                        options = DashboardOrientation.entries,
                        selected = orientation,
                        onSelect = viewModel::setDashboardOrientation,
                        label = {
                            stringResource(
                                when (it) {
                                    DashboardOrientation.LANDSCAPE -> R.string.orientation_landscape
                                    DashboardOrientation.AUTO -> R.string.orientation_auto
                                    DashboardOrientation.PORTRAIT -> R.string.orientation_portrait
                                },
                            )
                        },
                    )
                }
                val vibrationOnly by viewModel.vibrationOnly.collectAsStateWithLifecycle()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_vibration_only),
                    hint = stringResource(R.string.settings_vibration_only_hint),
                    checked = vibrationOnly,
                    onChange = viewModel::setVibrationOnly,
                    tag = "switch_vibration_only",
                )
                val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_max_brightness),
                    hint = stringResource(R.string.settings_max_brightness_hint),
                    checked = maxBrightness,
                    onChange = viewModel::setMaxBrightness,
                    tag = "switch_max_brightness",
                )
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

    state.sampleDiagnosis?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSampleDiagnosis,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.settings_samples_diag_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("samples_diag")) {
                    Text(stringResource(R.string.settings_samples_diag_count, d.entries.size, d.imageCount), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (d.entries.isEmpty()) stringResource(R.string.settings_samples_diag_empty) else d.entries.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.nacho.kartlog.ui.theme.MonoFontFamily),
                        color = MaterialTheme.kartColors.textMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(stringResource(R.string.settings_samples_diag_expected, d.expectedDir), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_samples_diag_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.warning, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSampleDiagnosis, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_accept).uppercase(), style = MaterialTheme.typography.titleSmall)
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


@Composable
private fun SettingSwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

private data class DebugButton(val label: String, val tag: String, val onClick: () -> Unit, val enabled: Boolean = true, val warn: Boolean = false)

/** Botones de la sección Depuración (solo builds debug), en el orden en que se enseñan. */
@Composable
private fun debugButtons(viewModel: SettingsViewModel, state: SettingsUiState, pickImage: () -> Unit, onWipe: () -> Unit, onEnd: () -> Unit): List<DebugButton> {
    val idle = !state.isBusy
    return listOf(
        DebugButton(stringResource(R.string.settings_simulate_track), "debug_simulate", viewModel::simulateTrackSession, idle),
        DebugButton(stringResource(R.string.settings_simulate_endurance), "debug_endurance", viewModel::simulateEndurance, idle),
        DebugButton(stringResource(R.string.settings_test_ocr), "debug_ocr_sample", viewModel::testOcrWithSample, idle),
        DebugButton(stringResource(R.string.settings_pick_image), "debug_pick_image", pickImage, idle),
        DebugButton(stringResource(R.string.settings_import_sample_a), "debug_text_a", { viewModel.importTextSample(TextSample.A) }, idle),
        DebugButton(stringResource(R.string.settings_import_sample_a_me), "debug_text_a_me", { viewModel.importTextSample(TextSample.A, asMe = "Nacho", circuit = "GP3") }, idle),
        DebugButton(stringResource(R.string.settings_import_sample_b), "debug_text_b", { viewModel.importTextSample(TextSample.B) }, idle),
        DebugButton(stringResource(R.string.settings_debug_backup_save), "debug_backup_save", viewModel::exportBackupToDownloads, idle),
        DebugButton(stringResource(R.string.settings_debug_backup_restore), "debug_backup_restore", viewModel::restoreLatestDebugBackup, idle),
        DebugButton(stringResource(R.string.settings_seed_gps), "debug_seed_gps", viewModel::seedGpsSession, idle),
        DebugButton(stringResource(R.string.settings_seed), "debug_seed", viewModel::seedDebugData, idle),
        DebugButton(stringResource(R.string.settings_to_end), "debug_to_end", onEnd),
        DebugButton(stringResource(R.string.settings_wipe), "debug_wipe", onWipe, idle, warn = true),
    )
}
