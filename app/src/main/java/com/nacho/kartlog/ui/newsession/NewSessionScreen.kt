package com.nacho.kartlog.ui.newsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.ChipSelector
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.RacingTextField
import com.nacho.kartlog.ui.components.TempSlider
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.labelRes

@Composable
fun NewSessionScreen(
    viewModel: NewSessionViewModel,
    onBack: () -> Unit,
    onSaved: (sessionId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.new_session_title), onBack = onBack) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.circuitId == null) {
                    Text(
                        text = stringResource(R.string.circuit_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kartColors.warning,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::save,
                    enabled = state.canSave,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.label_circuit)) {
                ChipSelector(
                    options = state.circuits.map { it.id },
                    selected = state.circuitId,
                    onSelect = viewModel::selectCircuit,
                    label = { id -> state.circuits.first { it.id == id }.name },
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = stringResource(R.string.circuit_new), onClick = viewModel::openCircuitDialog)
            }

            Section(stringResource(R.string.label_session_type)) {
                ChipSelector(
                    options = SessionType.entries,
                    selected = state.type,
                    onSelect = viewModel::selectType,
                    label = { stringResource(it.labelRes()) },
                )
            }

            KartCard(Modifier.fillMaxWidth()) {
                BigStepper(
                    label = stringResource(R.string.label_kart_number),
                    valueText = state.kartNumber?.let { stringResource(R.string.format_kart_value, it) }
                        ?: stringResource(R.string.value_unset),
                    onDecrement = viewModel::decrementKart,
                    onIncrement = viewModel::incrementKart,
                )
                Spacer(Modifier.height(16.dp))
                BigStepper(
                    label = stringResource(R.string.label_ballast),
                    valueText = state.ballastKg?.let { stringResource(R.string.format_ballast_value, it) }
                        ?: stringResource(R.string.value_unset),
                    onDecrement = viewModel::decrementBallast,
                    onIncrement = viewModel::incrementBallast,
                )
            }

            KartCard(Modifier.fillMaxWidth()) {
                TempSlider(
                    label = stringResource(R.string.label_ambient_temp),
                    value = state.ambientTempC,
                    onValueChange = viewModel::setAmbientTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                )
                Spacer(Modifier.height(16.dp))
                TempSlider(
                    label = stringResource(R.string.label_track_temp),
                    value = state.trackTempC,
                    onValueChange = viewModel::setTrackTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                )
            }

            Section(stringResource(R.string.label_track_condition)) {
                ChipSelector(
                    options = TrackCondition.entries,
                    selected = state.condition,
                    onSelect = viewModel::selectCondition,
                    label = { stringResource(it.labelRes()) },
                )
            }

            Section(stringResource(R.string.label_notes)) {
                RacingTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    placeholder = stringResource(R.string.hint_notes),
                    minLines = 3,
                )
            }
        }
    }

    state.circuitDialog?.let { dialog ->
        CircuitDialog(
            state = dialog,
            onNameChange = viewModel::setCircuitName,
            onDecrementLength = viewModel::decrementCircuitLength,
            onIncrementLength = viewModel::incrementCircuitLength,
            onConfirm = viewModel::confirmCircuitDialog,
            onDismiss = viewModel::dismissCircuitDialog,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.kartColors.textMuted,
        )
        content()
    }
}

@Composable
private fun CircuitDialog(
    state: CircuitDialogState,
    onNameChange: (String) -> Unit,
    onDecrementLength: () -> Unit,
    onIncrementLength: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.kartColors.card,
        title = { Text(stringResource(R.string.circuit_dialog_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RacingTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.label_circuit_name),
                    singleLine = true,
                    isError = state.nameError,
                    supportingText = if (state.nameError) stringResource(R.string.circuit_name_required) else null,
                )
                BigStepper(
                    label = stringResource(R.string.label_circuit_length),
                    valueText = state.lengthMeters?.let { stringResource(R.string.format_length_m, it) }
                        ?: stringResource(R.string.value_unset),
                    onDecrement = onDecrementLength,
                    onIncrement = onIncrementLength,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.height(56.dp)) {
                Text(stringResource(R.string.action_add).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}
