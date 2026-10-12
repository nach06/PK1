package com.nacho.kartlog.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.importing.CellStatus
import com.nacho.kartlog.domain.importing.CheckState
import com.nacho.kartlog.domain.importing.DriverChecks
import com.nacho.kartlog.domain.importing.ImportReview
import com.nacho.kartlog.domain.importing.LapSuggestion
import com.nacho.kartlog.domain.importing.ReviewCell
import com.nacho.kartlog.domain.importing.ReviewDriver
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.MoreBelowHint
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.labelRes

/**
 * Revisión antes de guardar. Todo lo importante cabe sin desplazarse: datos de la tanda en una línea, formato, miniatura de la hoja
 * con la celda resaltada, columnas de pilotos, tabla de tiempos editable (la única parte que se desplaza), controles cruzados y el
 * botón GUARDAR con el resumen y, si está desactivado, la razón.
 */
@Composable
internal fun ReviewContent(state: ImportUiState, viewModel: ImportViewModel, onBack: () -> Unit, onEditMeta: () -> Unit, debugShortcuts: Boolean = false) {
    val review = state.review
    val viewed = state.viewedDriver
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.import_review_title), onBack = onBack) },
        bottomBar = { ReviewBottomBar(state, onSave = viewModel::save) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetaSummary(state, onEditMeta)
            FormatRow(state, onChange = viewModel::openFormatPicker)
            val image = state.reviewImage
            if (image != null && state.scan != null) {
                SheetPreview(
                    imageUri = image.uri, imageWidth = image.width, imageHeight = image.height, box = state.selectedBox,
                    modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable(onClick = viewModel::showImage),
                )
            }
            if (review != null) {
                DriverStrip(review, state.viewedDriverKey, state.suggestedDriverKey, onSelect = viewModel::viewDriver)
                if (state.me == null) {
                    Text(stringResource(if (state.suggestedDriverKey != null) R.string.import_who_suggested else R.string.import_who_pick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.warning, modifier = Modifier.testTag("who_banner"))
                }
                if (viewed != null) {
                    DriverActions(
                        isMe = review.meKey == viewed.key,
                        canMerge = review.drivers.size > 1,
                        hasDoubts = review.nextToReview(null, null) != null,
                        onMe = { viewModel.setMe(viewed.key) },
                        onMerge = viewModel::openMergePicker,
                        onSplit = viewModel::splitDriver,
                        onNext = viewModel::nextDoubtful,
                    )
                    state.suggestion?.let { SuggestionCard(it, onApply = viewModel::applySuggestion, onIgnore = viewModel::ignoreSuggestion) }
                    if (state.duplicateCount > 0) {
                        Text(
                            stringResource(R.string.import_duplicates_warning, state.duplicateCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kartColors.warning,
                        )
                    }
                    LapTable(
                        review = review,
                        driver = viewed,
                        selected = state.selectedLap,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onSelect = viewModel::selectLap,
                        onEdit = viewModel::startEdit,
                        onConfirm = viewModel::confirmLap,
                        onDelete = viewModel::deleteLap,
                        onAdd = viewModel::addLap,
                        debugShortcuts = debugShortcuts,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaSummary(state: ImportUiState, onEdit: () -> Unit) {
    val kart = MaterialTheme.kartColors
    val circuit = state.selectedCircuit?.name
    val line = buildList {
        add(circuit ?: stringResource(R.string.import_meta_no_circuit))
        add(formatDateTime(state.dateTime))
        add(stringResource(state.type.labelRes()))
        state.kartNumber?.let { add(stringResource(R.string.format_kart, it)) }
        state.durationMin?.let { add(stringResource(R.string.format_duration_value, it)) }
    }.joinToString(" · ")
    KartCard(Modifier.fillMaxWidth().testTag("meta_summary"), onClick = onEdit, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line,
                style = MaterialTheme.typography.titleSmall,
                color = if (circuit == null && state.destination == ImportDestination.NEW_SESSION) kart.warning else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("meta_line"),
            )
            Text(stringResource(R.string.import_meta_edit).uppercase().take(6) + "…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FormatRow(state: ImportUiState, onChange: () -> Unit) {
    val format = state.format ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.import_format_detected, formatLabel(format)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.kartColors.textMuted,
            modifier = Modifier.weight(1f).testTag("format_label"),
            maxLines = 1,
        )
        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 32.dp).testTag("format_change"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(R.string.import_format_change).uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Una ficha por piloto con vueltas: la tuya con ★, y ⚠ si sus controles cruzados no cuadran. */
@Composable
private fun DriverStrip(review: ImportReview, viewedKey: String?, suggestedKey: String?, onSelect: (String) -> Unit) {
    val kart = MaterialTheme.kartColors
    LazyRow(Modifier.fillMaxWidth().testTag("driver_strip"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(review.drivers.filter { d -> d.cells.isNotEmpty() || d.key == review.meKey }, key = { it.key }) { d ->
            val selected = d.key == viewedKey
            val warn = review.checks(d.key)?.allOk == false
            val label = (if (d.key == review.meKey) "★ " else if (d.key == suggestedKey) "☆ " else "") + d.displayName + (if (warn) " ⚠" else "")
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else kart.card)
                    .clickable { onSelect(d.key) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { stateDescription = if (selected) "selected" else "" }
                    .testTag("driver_chip_${d.name ?: d.kartNumber ?: d.key}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else if (warn) kart.warning else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DriverActions(
    isMe: Boolean,
    canMerge: Boolean,
    hasDoubts: Boolean,
    onMe: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onNext: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        item { ActionChip(stringResource(if (isMe) R.string.import_me_marked else R.string.import_me), "action_me", selected = isMe, onClick = onMe) }
        if (hasDoubts) item { ActionChip(stringResource(R.string.import_next_doubt), "action_next", onClick = onNext) }
        if (canMerge) item { ActionChip(stringResource(R.string.import_merge), "action_merge", onClick = onMerge) }
        item { ActionChip(stringResource(R.string.import_split), "action_split", onClick = onSplit) }
    }
}

@Composable
private fun ActionChip(text: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
    val kart = MaterialTheme.kartColors
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) kart.telemetry.copy(alpha = 0.25f) else kart.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) kart.telemetry else MaterialTheme.colorScheme.onSurface, maxLines = 1) }
}

@Composable
private fun SuggestionCard(suggestion: LapSuggestion, onApply: () -> Unit, onIgnore: () -> Unit) {
    KartCard(Modifier.fillMaxWidth().testTag("suggestion_card"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(
                R.string.import_suggestion,
                stringResource(R.string.lap_short, suggestion.lapNumber),
                LapTime.format(suggestion.currentMs),
                LapTime.format(suggestion.proposedMs),
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.kartColors.warning,
            modifier = Modifier.testTag("suggestion_text"),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_suggestion_apply), onApply, color = MaterialTheme.kartColors.warning, modifier = Modifier.testTag("suggestion_apply")) }
            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_suggestion_ignore), onIgnore, color = MaterialTheme.kartColors.textMuted, modifier = Modifier.testTag("suggestion_ignore")) }
        }
    }
}

// ---------------------------------------------------------------- Tabla de tiempos

@Composable
private fun LapTable(
    review: ImportReview,
    driver: ReviewDriver,
    selected: Int?,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    debugShortcuts: Boolean = false,
) {
    val rows = review.rows(driver.key)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(modifier) {
    LazyColumn(Modifier.fillMaxSize().testTag("lap_table"), state = listState) {
        items(rows, key = { "${driver.key}-${it.first.number}" }) { (cell, status) ->
            ReviewLapRow(
                cell = cell,
                status = status,
                selected = cell.number == selected,
                onSelect = { onSelect(cell.number) },
                onEdit = { onEdit(cell.number) },
                onConfirm = { onConfirm(cell.number) },
                onDelete = { onDelete(cell.number) },
            )
        }
        item(key = "add") {
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_lap")) {
                Text(stringResource(R.string.import_add_lap).uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    MoreBelowHint(
        visible = listState.canScrollForward,
        modifier = Modifier.align(Alignment.BottomCenter),
        onDebugJump = if (debugShortcuts) ({ scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } }) else null,
    )
    }
}

@Composable
private fun ReviewLapRow(
    cell: ReviewCell,
    status: CellStatus,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
) {
    val kart = MaterialTheme.kartColors
    val red = MaterialTheme.colorScheme.primary
    val background = when (status) {
        CellStatus.DOUBTFUL -> kart.warning.copy(alpha = 0.16f)
        CellStatus.INVALID -> red.copy(alpha = 0.18f)
        CellStatus.EMPTY -> Color.Transparent
        CellStatus.OK -> Color.Transparent
    }
    val timeColor = when (status) {
        CellStatus.DOUBTFUL -> kart.warning
        CellStatus.INVALID -> red
        CellStatus.EMPTY -> kart.textMuted
        CellStatus.OK -> MaterialTheme.colorScheme.onSurface
    }
    val timeText = when {
        status == CellStatus.EMPTY -> "—"
        status == CellStatus.INVALID -> cell.rawText ?: cell.timeMs?.let(LapTime::format) ?: "?"
        else -> LapTime.format(cell.timeMs ?: 0)
    }
    val notes = buildList {
        when (status) {
            CellStatus.DOUBTFUL -> {
                add(stringResource(R.string.import_cell_doubtful))
                if (cell.ocrFixed) add(stringResource(R.string.import_lap_ocr) + (cell.rawText?.let { " · " + stringResource(R.string.import_cell_ocr_raw, it) } ?: ""))
                else if (cell.lowConfidence) add(stringResource(R.string.import_cell_low_confidence))
                else add(stringResource(R.string.import_cell_no_match))
            }
            CellStatus.INVALID -> add(stringResource(R.string.import_cell_invalid))
            CellStatus.EMPTY -> add(stringResource(R.string.import_cell_empty))
            CellStatus.OK -> if (cell.edited) add(stringResource(R.string.import_lap_edited))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .background(background)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp)
            .semantics { stateDescription = status.name }
            .testTag("review_row_${cell.number}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.lap_short, cell.number), style = MaterialTheme.typography.titleSmall, color = kart.textMuted, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            AutoSizeText(
                text = timeText,
                style = KartTextStyles.lapTime.copy(fontSize = 20.sp, textDecoration = if (status == CellStatus.INVALID) TextDecoration.LineThrough else TextDecoration.None),
                color = timeColor,
                minSize = 14.sp,
                modifier = Modifier.fillMaxWidth().testTag("review_time_${cell.number}"),
            )
            Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = if (status == CellStatus.OK) kart.textMuted else timeColor, maxLines = 1)
        }
        if (status == CellStatus.DOUBTFUL) {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_${cell.number}")) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = kart.telemetry)
            }
        }
        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("edit_${cell.number}")) {
            Text("✎", style = MaterialTheme.typography.titleMedium)
        }
        if (status != CellStatus.EMPTY) {
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp).testTag("delete_${cell.number}")) {
                Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.kartColors.textMuted)
            }
        }
    }
}

// ---------------------------------------------------------------- Pie: controles, resumen y GUARDAR

@Composable
private fun ReviewBottomBar(state: ImportUiState, onSave: () -> Unit) {
    val summary = state.summary
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.checks?.let { CheckRow(it, state.viewedDriver?.reportedBestMs, state.viewedDriver?.reportedLapCount, state.viewedDriver?.reportedAvgMs, state.review?.savedTimes(state.viewedDriver!!)?.size ?: 0) }
        if (summary != null) {
            Text(
                stringResource(R.string.import_summary, summary.driverCount, summary.lapCount, summary.doubtful, summary.invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.needsConfirmation) MaterialTheme.kartColors.warning else MaterialTheme.kartColors.textMuted,
                modifier = Modifier.testTag("review_summary"),
            )
        }
        state.error?.let { ErrorText(it) }
        state.blockedReason?.let {
            Text(
                stringResource(
                    when (it) {
                        BlockedReason.NO_ME -> R.string.import_blocked_no_me
                        BlockedReason.NO_VALID_LAPS, BlockedReason.NO_DATA -> R.string.import_blocked_no_laps
                        BlockedReason.NO_CIRCUIT -> R.string.import_blocked_no_circuit
                        BlockedReason.NO_SESSION -> R.string.import_blocked_no_session
                        BlockedReason.BUSY -> R.string.import_blocked_busy
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kartColors.warning,
                modifier = Modifier.testTag("save_blocked"),
            )
        }
        PrimaryButton(text = stringResource(R.string.import_save), onClick = onSave, enabled = state.canSave, modifier = Modifier.testTag("review_save"))
    }
}

/** Los tres indicadores de la validación cruzada (✓ cuadra, ⚠ no cuadra, – sin dato en el documento). */
@Composable
private fun CheckRow(checks: DriverChecks, reportedBest: Long?, reportedCount: Int?, reportedAvg: Long?, currentCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("check_row")) {
        CheckChip(
            label = stringResource(R.string.import_check_best),
            value = checks.computedBestMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
            doc = reportedBest?.let(LapTime::format),
            state = checks.best,
            modifier = Modifier.weight(1f).testTag("check_best"),
        )
        CheckChip(
            label = stringResource(R.string.import_check_laps),
            value = currentCount.toString(),
            doc = reportedCount?.toString(),
            state = checks.lapCount,
            modifier = Modifier.weight(0.8f).testTag("check_laps"),
        )
        CheckChip(
            label = stringResource(R.string.import_check_average),
            value = checks.computedAvgMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
            doc = reportedAvg?.let(LapTime::format),
            state = checks.average,
            modifier = Modifier.weight(1f).testTag("check_avg"),
        )
    }
}

@Composable
private fun CheckChip(label: String, value: String, doc: String?, state: CheckState, modifier: Modifier = Modifier) {
    val kart = MaterialTheme.kartColors
    val (symbol, color) = when (state) {
        CheckState.OK -> "✓" to kart.telemetry
        CheckState.WARN -> "⚠" to kart.warning
        CheckState.NOT_AVAILABLE -> "–" to kart.textMuted
    }
    KartCard(modifier.semantics { stateDescription = state.name }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        // Una línea: ✓ + etiqueta + valor. Solo si no cuadra se añade el valor del documento debajo.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(symbol, style = MaterialTheme.typography.titleSmall, color = color)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = kart.textMuted, maxLines = 1)
            AutoSizeText(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                minSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (state == CheckState.WARN && doc != null) {
            Text(stringResource(R.string.import_check_doc_value, doc), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}
