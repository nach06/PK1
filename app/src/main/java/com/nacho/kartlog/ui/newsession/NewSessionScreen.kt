package com.nacho.kartlog.ui.newsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.ChipSelector
import com.nacho.kartlog.ui.components.CircuitEditSheet
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.RacingTextField
import com.nacho.kartlog.ui.components.TempSlider
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDate
import com.nacho.kartlog.ui.util.formatTime
import com.nacho.kartlog.ui.util.labelRes
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun NewSessionScreen(
    viewModel: NewSessionViewModel,
    onBack: () -> Unit,
    onSaved: (sessionId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.new_session_title), onBack = onBack) },
        // El botón GUARDAR sube con el teclado del sistema y el formulario se desplaza por encima.
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = viewModel::openCircuitPicker,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = state.selectedCircuit?.name ?: stringResource(R.string.circuit_pick_hint),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.selectedCircuit != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.kartColors.textMuted
                            },
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▾", style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(
                        onClick = viewModel::openCircuitDialog,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(R.string.circuit_new).uppercase(), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Section(stringResource(R.string.label_date) + " / " + stringResource(R.string.label_time)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(formatDate(state.dateTime), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .weight(0.7f)
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(formatTime(state.dateTime), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
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
                    onClear = viewModel::clearAmbientTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                    idleValue = 20,
                )
                Spacer(Modifier.height(8.dp))
                TempSlider(
                    label = stringResource(R.string.label_track_temp),
                    value = state.trackTempC,
                    onValueChange = viewModel::setTrackTemp,
                    onClear = viewModel::clearTrackTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                    idleValue = 30,
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

    if (state.showCircuitPicker) {
        CircuitPickerSheet(
            circuits = state.circuits,
            selectedId = state.circuitId,
            onSelect = { viewModel.selectCircuit(it.id) },
            onDismiss = viewModel::dismissCircuitPicker,
        )
    }

    state.circuitDialog?.let { dialog ->
        CircuitEditSheet(
            title = stringResource(R.string.circuit_dialog_title),
            name = dialog.name,
            lengthMeters = dialog.lengthMeters,
            errorText = if (dialog.nameError) stringResource(R.string.circuit_name_required) else null,
            onNameChange = viewModel::setCircuitName,
            onDecrementLength = viewModel::decrementCircuitLength,
            onIncrementLength = viewModel::incrementCircuitLength,
            onConfirm = viewModel::confirmCircuitDialog,
            onDismiss = viewModel::dismissCircuitDialog,
        )
    }

    if (showDatePicker) {
        SessionDatePickerDialog(
            dateTime = state.dateTime,
            onConfirm = {
                viewModel.setDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        SessionTimePickerDialog(
            dateTime = state.dateTime,
            onConfirm = { h, m ->
                viewModel.setTime(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDatePickerDialog(dateTime: Long, onConfirm: (utcMillis: Long) -> Unit, onDismiss: () -> Unit) {
    // El DatePicker trabaja en UTC: medianoche UTC del día local seleccionado.
    val initial = Instant.ofEpochMilli(dateTime).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_accept).uppercase(), style = MaterialTheme.typography.titleSmall) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.kartColors.card),
    ) {
        DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.kartColors.card))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTimePickerDialog(dateTime: Long, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val time = Instant.ofEpochMilli(dateTime).atZone(ZoneId.systemDefault()).toLocalTime()
    val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.kartColors.card,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_accept).uppercase(), style = MaterialTheme.typography.titleSmall) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}
