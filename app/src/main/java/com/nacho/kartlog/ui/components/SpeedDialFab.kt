package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R

/** Opción de un [SpeedDialFab]; la primera es la principal (relleno de color), las demás son secundarias. */
data class FabAction(val label: String, val onClick: () -> Unit, val tag: String? = null)

/**
 * Botón de acción desplegable: cerrado muestra la acción principal con una flecha; al tocarlo se abren todas las
 * acciones apiladas y el botón pasa a «Cerrar». Elegir una acción cierra el menú.
 */
@Composable
fun SpeedDialFab(
    actions: List<FabAction>,
    closedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(actions.isNotEmpty())
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            // Se apilan de abajo arriba: la principal queda justo encima del botón.
            actions.reversed().forEachIndexed { index, action ->
                val primary = index == actions.lastIndex
                ExtendedFloatingActionButton(
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                    containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.testTag(action.tag ?: "fab_action_$index"),
                ) {
                    Text(
                        text = (if (primary) "+  " else "") + action.label.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.testTag("fab_main"),
        ) {
            Text(
                text = if (expanded) stringResource(R.string.action_close).uppercase() + "  ▾" else "+  " + closedLabel.uppercase() + "  ▴",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
