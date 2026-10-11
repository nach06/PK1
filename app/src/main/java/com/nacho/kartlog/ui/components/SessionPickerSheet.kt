package com.nacho.kartlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime

/** Hoja con las tandas existentes (la más reciente primero) para elegir a cuál añadir las vueltas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerSheet(
    sessions: List<SessionSummary>,
    selectedId: Long?,
    onSelect: (SessionSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.import_pick_session_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.import_no_sessions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(sessions, key = { it.session.id }) { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onSelect(summary) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = summary.circuit.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (summary.session.id == selectedId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        val kart = summary.session.kartNumber?.let { stringResource(R.string.format_kart, it) }
                        Text(
                            text = listOfNotNull(
                                formatDateTime(summary.session.dateTime),
                                kart,
                                pluralStringResource(R.plurals.format_lap_count, summary.lapCount, summary.lapCount),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kartColors.textMuted,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.kartColors.cardBorder)
                }
            }
        }
    }
}
