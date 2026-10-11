package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.kartColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Selector de fecha (Material 3). [onConfirm] recibe la medianoche UTC del día elegido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDatePickerDialog(dateTime: Long, onConfirm: (utcMillis: Long) -> Unit, onDismiss: () -> Unit) {
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
fun SessionTimePickerDialog(dateTime: Long, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
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
