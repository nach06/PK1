package com.nacho.kartlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 0 = sin diálogo, 1 = primera confirmación, 2 = segunda confirmación.
    var wipeStep by rememberSaveable { mutableIntStateOf(0) }

    val messageText = when (state.message) {
        SettingsMessage.SEEDED -> stringResource(R.string.settings_seeded)
        SettingsMessage.WIPED -> stringResource(R.string.settings_wiped)
        SettingsMessage.FAILED -> stringResource(R.string.settings_failed)
        SettingsMessage.NOTHING_TO_EXPORT -> stringResource(R.string.settings_nothing_to_export)
        null -> null
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareFiles.collect { shareCsv(context, it.fileName, it.content) }
    }
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
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
                }
            }

            if (state.debugToolsAvailable) {
                KartCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_debug_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.kartColors.warning,
                    )
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
