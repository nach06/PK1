package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R

/**
 * Hoja inferior para dar de alta o editar un circuito (nombre + longitud). Los botones
 * Cancelar/Guardar quedan siempre por encima del teclado (`imePadding`). El nombre se escribe con
 * mayúscula por palabra y «Hecho» del teclado guarda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitEditSheet(
    title: String,
    name: String,
    lengthMeters: Int?,
    errorText: String?,
    onNameChange: (String) -> Unit,
    onDecrementLength: () -> Unit,
    onIncrementLength: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RacingTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.focusRequester(focusRequester),
                label = stringResource(R.string.label_circuit_name),
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            )
            BigStepper(
                label = stringResource(R.string.label_circuit_length),
                valueText = lengthMeters?.let { stringResource(R.string.format_length_m, it) }
                    ?: stringResource(R.string.value_unset),
                onDecrement = onDecrementLength,
                onIncrement = onIncrementLength,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall) }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.action_save).uppercase(), style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}
