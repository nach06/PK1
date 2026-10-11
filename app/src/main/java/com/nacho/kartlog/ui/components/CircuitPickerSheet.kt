package com.nacho.kartlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.ui.theme.kartColors
import java.text.Normalizer

private fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Hoja inferior con buscador para elegir un circuito. Respeta el orden recibido (recientes primero).
 * La búsqueda ignora mayúsculas y acentos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitPickerSheet(
    circuits: List<Circuit>,
    selectedId: Long?,
    onSelect: (Circuit) -> Unit,
    onDismiss: () -> Unit,
    /** Si no es nulo, añade una primera fila «todos» que llama a [onSelectAll]. */
    allLabel: String? = null,
    onSelectAll: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(circuits, query) {
        val q = query.normalized().trim()
        if (q.isEmpty()) circuits else circuits.filter { it.name.normalized().contains(q) }
    }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.circuit_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RacingTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.circuit_search),
                singleLine = true,
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.circuit_none_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (allLabel != null && query.isBlank()) {
                    item(key = "all") {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { onSelectAll() },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = allLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selectedId == null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.kartColors.cardBorder)
                        }
                    }
                }
                items(filtered, key = { it.id }) { circuit ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onSelect(circuit) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = circuit.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (circuit.id == selectedId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            circuit.lengthMeters?.let {
                                Text(
                                    text = stringResource(R.string.format_length_m, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.kartColors.textMuted,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.kartColors.cardBorder)
                    }
                }
            }
        }
    }
}
