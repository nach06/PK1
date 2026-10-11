package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors

/** Botones −/+ grandes con el valor en el centro (kart nº, lastre…). */
@Composable
fun BigStepper(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.kartColors.textMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton(text = "−", description = stringResource(R.string.stepper_decrease), onClick = onDecrement)
            Text(
                text = valueText,
                style = KartTextStyles.value,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label $valueText" },
            )
            StepperButton(text = "+", description = stringResource(R.string.stepper_increase), onClick = onIncrement)
        }
    }
}

@Composable
private fun StepperButton(text: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .size(width = 72.dp, height = 64.dp)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(text = text, style = KartTextStyles.value, color = MaterialTheme.colorScheme.onSurface)
    }
}
