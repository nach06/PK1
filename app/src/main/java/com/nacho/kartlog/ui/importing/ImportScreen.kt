package com.nacho.kartlog.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.importing.CheckState
import com.nacho.kartlog.domain.importing.DriverChecks
import com.nacho.kartlog.domain.importing.LapSuggestion
import com.nacho.kartlog.domain.importing.ParsedDocument
import com.nacho.kartlog.domain.importing.ParsedDriver
import com.nacho.kartlog.domain.importing.ParsedLap
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.ChipSelector
import com.nacho.kartlog.ui.components.CircuitEditSheet
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.RacingTextField
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.components.SessionDatePickerDialog
import com.nacho.kartlog.ui.components.SessionPickerSheet
import com.nacho.kartlog.ui.components.SessionTimePickerDialog
import com.nacho.kartlog.ui.components.TimeKeypad
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDate
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.formatTime
import com.nacho.kartlog.ui.util.labelRes
import java.io.File
import java.time.Instant
import java.time.ZoneId

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    ocrAvailable: Boolean,
    onBack: () -> Unit,
    onSaved: (sessionId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showScanOptions by rememberSaveable { mutableStateOf(false) }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it) } }

    // Escanear: cámara (foto temporal en cache/photos, no se guarda) o galería. El OCR es del dispositivo.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhotoPath
        if (ok && path != null) {
            viewModel.recognizeImage(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path)).toString())
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.recognizeImage(it.toString()) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.import_title), onBack = onBack) },
        bottomBar = {
            if (state.parsed != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.error?.let { ErrorText(it) }
                    PrimaryButton(text = stringResource(R.string.import_save), onClick = viewModel::save, enabled = state.canSave)
                }
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
            InputSection(
                state = state,
                ocrAvailable = ocrAvailable,
                showScanOptions = showScanOptions,
                onTextChange = viewModel::setRawText,
                onPaste = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::pasteAndAnalyze) },
                onToggleScan = { showScanOptions = !showScanOptions },
                onCamera = {
                    val file = File(File(context.cacheDir, "photos").apply { mkdirs() }, "escaneo.jpg")
                    pendingPhotoPath = file.absolutePath
                    takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                },
                onGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onAnalyze = viewModel::analyze,
            )
            val parsed = state.parsed
            if (parsed != null) {
                if (parsed.warnings.isNotEmpty() || parsed.ignoredTimes.isNotEmpty()) WarningsCard(parsed)
                DriverChooser(parsed.drivers, state.selectedDriverKey, viewModel::selectDriver)
                if (state.me != null) {
                    PreviewSection(
                        state = state,
                        onEdit = viewModel::startEdit,
                        onToggle = viewModel::toggleLap,
                        onApplySuggestion = viewModel::applySuggestion,
                    )
                    OthersSection(state.others, state.othersExpanded, viewModel::toggleOthers)
                    DestinationSection(
                        state = state,
                        onDestination = viewModel::setDestination,
                        onCircuit = viewModel::openCircuitPicker,
                        onNewCircuit = viewModel::openNewCircuit,
                        onDate = { showDatePicker = true },
                        onTime = { showTimePicker = true },
                        onType = viewModel::setType,
                        onKartMinus = viewModel::decrementKart,
                        onKartPlus = viewModel::incrementKart,
                        onLabel = viewModel::setLabel,
                        onSession = viewModel::openSessionPicker,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    state.editing?.let { editing ->
        EditLapSheet(
            title = stringResource(R.string.import_edit_title, editing.number),
            timeText = editing.timeText,
            hasDigits = editing.digits.isNotEmpty(),
            valid = editing.timeMs != null,
            onDigits = viewModel::editDigits,
            onBackspace = viewModel::editBackspace,
            onClear = viewModel::editClear,
            onConfirm = viewModel::confirmEdit,
            onCancel = viewModel::cancelEdit,
        )
    }
    if (state.showCircuitPicker) {
        CircuitPickerSheet(
            circuits = state.circuits,
            selectedId = state.circuitId,
            onSelect = { viewModel.selectCircuit(it.id) },
            onDismiss = viewModel::dismissCircuitPicker,
        )
    }
    if (state.showNewCircuit) {
        var name by rememberSaveable { mutableStateOf("") }
        var length by rememberSaveable { mutableStateOf<Int?>(null) }
        CircuitEditSheet(
            title = stringResource(R.string.circuit_dialog_title),
            name = name, lengthMeters = length, errorText = null,
            onNameChange = { name = it },
            onDecrementLength = { length = length?.let { m -> if (m <= 50) null else m - 50 } },
            onIncrementLength = { length = ((length ?: 0) + 50).coerceAtMost(10_000) },
            onConfirm = { viewModel.createCircuit(name, length) },
            onDismiss = viewModel::dismissNewCircuit,
        )
    }
    if (state.showSessionPicker) {
        SessionPickerSheet(
            sessions = state.sessions,
            selectedId = state.existingSessionId,
            onSelect = { viewModel.selectExistingSession(it.session.id) },
            onDismiss = viewModel::dismissSessionPicker,
        )
    }
    if (showDatePicker) {
        SessionDatePickerDialog(
            dateTime = state.dateTime,
            onConfirm = { utcMillis ->
                val zone = ZoneId.systemDefault()
                val date = Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                val time = Instant.ofEpochMilli(state.dateTime).atZone(zone).toLocalTime()
                viewModel.setDateTime(date.atTime(time).atZone(zone).toInstant().toEpochMilli())
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        SessionTimePickerDialog(
            dateTime = state.dateTime,
            onConfirm = { h, m ->
                val zone = ZoneId.systemDefault()
                val date = Instant.ofEpochMilli(state.dateTime).atZone(zone).toLocalDate()
                viewModel.setDateTime(date.atTime(h, m).atZone(zone).toInstant().toEpochMilli())
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun ErrorText(error: ImportError) {
    Text(
        text = stringResource(
            when (error) {
                ImportError.EMPTY_TEXT -> R.string.import_error_empty
                ImportError.NOTHING_FOUND -> R.string.import_error_nothing
                ImportError.OCR_FAILED -> R.string.import_error_ocr
                ImportError.SAVE_FAILED -> R.string.import_error_save
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.kartColors.warning,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kartColors.textMuted)
}

@Composable
private fun InputSection(
    state: ImportUiState,
    ocrAvailable: Boolean,
    showScanOptions: Boolean,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onToggleScan: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RacingTextField(
            value = state.rawText,
            onValueChange = onTextChange,
            label = stringResource(R.string.import_input_label),
            placeholder = stringResource(R.string.import_input_hint),
            minLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_paste), onPaste, enabled = !state.isBusy) }
            Box(Modifier.weight(1f)) {
                SecondaryButton(stringResource(R.string.import_scan), onToggleScan, enabled = !state.isBusy && ocrAvailable)
            }
        }
        if (!ocrAvailable) {
            Text(
                stringResource(R.string.import_scan_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kartColors.textMuted,
            )
        }
        if (showScanOptions && ocrAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_scan_camera), onCamera) }
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_scan_gallery), onGallery) }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.import_analyze),
            onClick = onAnalyze,
            enabled = state.rawText.isNotBlank() && !state.isBusy,
        )
        if (state.parsed == null) state.error?.let { ErrorText(it) }
    }
}

@Composable
private fun WarningsCard(doc: ParsedDocument) {
    KartCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.import_warnings))
        doc.warnings.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.warning)
        }
        if (doc.ignoredTimes.isNotEmpty()) {
            Text(
                stringResource(R.string.import_ignored_times, doc.ignoredTimes.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kartColors.textMuted,
            )
        }
    }
}

@Composable
private fun DriverChooser(drivers: List<ParsedDriver>, selectedKey: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.import_who_title))
        ChipSelector(
            options = drivers.filter { it.laps.isNotEmpty() },
            selected = drivers.firstOrNull { it.key == selectedKey },
            onSelect = { onSelect(it.key) },
            label = { it.displayName },
        )
        if (selectedKey == null) {
            Text(stringResource(R.string.import_who_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
        }
    }
}

@Composable
private fun PreviewSection(
    state: ImportUiState,
    onEdit: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onApplySuggestion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.import_preview_title))
        state.checks?.let { CheckRow(it, state.me?.reportedBestMs, state.me?.reportedLapCount, state.me?.reportedAvgMs, state.includedLaps.size) }
        state.checks?.suggestion?.let { SuggestionCard(it, onApplySuggestion) }
        KartCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
            state.laps.forEachIndexed { index, lap ->
                PreviewLapRow(lap, onEdit = { onEdit(lap.number) }, onToggle = { onToggle(lap.number) })
                if (index < state.laps.lastIndex) HorizontalDivider(color = MaterialTheme.kartColors.cardBorder)
            }
        }
        if (state.duplicateCount > 0) {
            Text(
                stringResource(R.string.import_duplicates_warning, state.duplicateCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kartColors.warning,
            )
        }
    }
}

/** Los tres indicadores de la validación cruzada (✓ cuadra, ⚠ no cuadra, – sin dato en el documento). */
@Composable
private fun CheckRow(checks: DriverChecks, reportedBest: Long?, reportedCount: Int?, reportedAvg: Long?, currentCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CheckChip(
            label = stringResource(R.string.import_check_best),
            value = checks.computedBestMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
            doc = reportedBest?.let(LapTime::format),
            state = checks.best,
            modifier = Modifier.weight(1f),
        )
        CheckChip(
            label = stringResource(R.string.import_check_laps),
            value = currentCount.toString(),
            doc = reportedCount?.toString(),
            state = checks.lapCount,
            modifier = Modifier.weight(0.8f),
        )
        CheckChip(
            label = stringResource(R.string.import_check_average),
            value = checks.computedAvgMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
            doc = reportedAvg?.let(LapTime::format),
            state = checks.average,
            modifier = Modifier.weight(1f),
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
    KartCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, color = color)
            AutoSizeText(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface,
                minSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (state == CheckState.WARN && doc != null) {
            Text(
                stringResource(R.string.import_check_doc_value, doc),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: LapSuggestion, onApply: () -> Unit) {
    KartCard(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.import_suggestion,
                stringResource(R.string.lap_short, suggestion.lapNumber),
                LapTime.format(suggestion.currentMs),
                LapTime.format(suggestion.proposedMs),
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.kartColors.warning,
        )
        Text(
            stringResource(R.string.import_suspect_cells),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.kartColors.textMuted,
        )
        SecondaryButton(
            text = stringResource(R.string.import_suggestion_apply),
            onClick = onApply,
            color = MaterialTheme.kartColors.warning,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PreviewLapRow(lap: PreviewLap, onEdit: () -> Unit, onToggle: () -> Unit) {
    val kart = MaterialTheme.kartColors
    val amber = lap.suspect || lap.possibleDuplicate
    val timeColor = when {
        !lap.included -> kart.textMuted
        amber -> kart.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = lap.included, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.lap_short, lap.number),
            style = MaterialTheme.typography.titleSmall,
            color = kart.textMuted,
            modifier = Modifier.width52(),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (lap.suspect) "⚠ " else "") + LapTime.format(lap.timeMs),
                style = KartTextStyles.lapTime.copy(
                    textDecoration = if (lap.included) TextDecoration.None else TextDecoration.LineThrough,
                ),
                color = timeColor,
                maxLines = 1,
                softWrap = false,
            )
            val notes = buildList {
                if (lap.edited) add(stringResource(R.string.import_lap_edited))
                if (lap.ocrFixed) add(stringResource(R.string.import_lap_ocr))
                if (lap.possibleDuplicate) add(stringResource(R.string.import_lap_duplicate))
            }
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = kart.warning)
            }
        }
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(
                text = stringResource(if (lap.included) R.string.import_lap_discard else R.string.import_lap_restore).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (lap.included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun Modifier.width52(): Modifier = this.then(Modifier.size(width = 52.dp, height = 28.dp))

@Composable
private fun OthersSection(others: List<ParsedDriver>, expanded: Boolean, onToggle: () -> Unit) {
    if (others.isEmpty()) return
    val kart = MaterialTheme.kartColors
    KartCard(Modifier.fillMaxWidth(), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.import_others, others.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = kart.textMuted)
        }
        if (expanded) {
            others.forEach { d ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d.position?.toString() ?: "–", style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.size(width = 32.dp, height = 24.dp))
                    Text(d.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        text = (d.reportedBestMs ?: d.laps.minOfOrNull { it.timeMs })?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationSection(
    state: ImportUiState,
    onDestination: (ImportDestination) -> Unit,
    onCircuit: () -> Unit,
    onNewCircuit: () -> Unit,
    onDate: () -> Unit,
    onTime: () -> Unit,
    onType: (SessionType) -> Unit,
    onKartMinus: () -> Unit,
    onKartPlus: () -> Unit,
    onLabel: (String) -> Unit,
    onSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.import_destination_title))
        ChipSelector(
            options = ImportDestination.entries,
            selected = state.destination,
            onSelect = onDestination,
            label = {
                stringResource(
                    if (it == ImportDestination.NEW_SESSION) R.string.import_destination_new else R.string.import_destination_existing,
                )
            },
        )
        when (state.destination) {
            ImportDestination.EXISTING_SESSION -> {
                OutlinedButton(
                    onClick = onSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    val picked = state.selectedSession
                    Text(
                        text = picked?.let { it.circuit.name + " · " + formatDateTime(it.session.dateTime) } ?: stringResource(R.string.import_pick_session),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
            }
            ImportDestination.NEW_SESSION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onCircuit,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = state.selectedCircuit?.name ?: stringResource(R.string.circuit_pick_hint),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▾")
                    }
                    OutlinedButton(
                        onClick = onNewCircuit,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(stringResource(R.string.circuit_new).uppercase(), style = MaterialTheme.typography.titleSmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                        Text(formatDate(state.dateTime), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    OutlinedButton(onClick = onTime, modifier = Modifier.weight(0.7f).heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                        Text(formatTime(state.dateTime), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
                ChipSelector(
                    options = SessionType.entries,
                    selected = state.type,
                    onSelect = onType,
                    label = { stringResource(it.labelRes()) },
                )
                BigStepper(
                    label = stringResource(R.string.label_kart_number),
                    valueText = state.kartNumber?.let { stringResource(R.string.format_kart_value, it) } ?: stringResource(R.string.value_unset),
                    onDecrement = onKartMinus,
                    onIncrement = onKartPlus,
                )
                RacingTextField(
                    value = state.label,
                    onValueChange = onLabel,
                    placeholder = stringResource(R.string.import_label_hint),
                    singleLine = true,
                )
                state.durationMin?.let {
                    Text(stringResource(R.string.import_duration, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
                }
            }
        }
        Text(stringResource(R.string.import_me_saved_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
    }
}

/** Editor de un tiempo con el teclado numérico propio (mismo comportamiento que al teclear una vuelta). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLapSheet(
    title: String,
    timeText: String,
    hasDigits: Boolean,
    valid: Boolean,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kartColors.textMuted)
            Text(
                text = timeText,
                style = KartTextStyles.timeEntry,
                color = if (hasDigits && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (hasDigits) 1f else 0.5f),
            )
            TimeKeypad(onDigits = onDigits, onBackspace = onBackspace, onClear = onClear, keyHeight = 56.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.action_cancel), onCancel, color = MaterialTheme.kartColors.textMuted) }
                Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.action_accept), onConfirm, enabled = valid) }
            }
        }
    }
}

// ---------------------------------------------------------------- Previews

private fun previewDriver() = ParsedDriver(
    name = "Nacho", kartNumber = 28, position = 5,
    laps = listOf(83_488L, 80_902L, 82_209L, 81_767L, 79_074L, 79_505L).mapIndexed { i, ms -> ParsedLap(i + 1, ms) },
    reportedBestMs = 79_074, reportedLapCount = 6, reportedAvgMs = 81_157,
)

/** Vista previa con una fila ámbar (celda sospechosa) y propuesta de corrección. */
@Preview(widthDp = 393, heightDp = 900, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun ImportPreviewWithSuspectRowPreview() {
    KartLogTheme {
        val laps = previewDriver().laps.map { PreviewLap(it.number, if (it.number == 1) 83_438 else it.timeMs, it.timeMs) }
            .map { if (it.number == 1) it.copy(suspect = true) else it }
        val state = ImportUiState(
            parsed = ParsedDocument(label = "Sesión 46 GP3 DIEGO", drivers = listOf(previewDriver())),
            selectedDriverKey = previewDriver().key, laps = laps, kartNumber = 28,
            checks = com.nacho.kartlog.domain.importing.ImportValidation.check(
                laps.map { it.number to it.timeMs }, 79_074, 6, 81_157,
            ),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DriverChooser(state.parsed!!.drivers, state.selectedDriverKey) {}
            PreviewSection(state, onEdit = {}, onToggle = {}, onApplySuggestion = {})
        }
    }
}

/** Pantalla vacía de entrada (sin analizar). */
@Preview(widthDp = 393, heightDp = 500, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun ImportEmptyPreview() {
    KartLogTheme {
        Column(Modifier.padding(16.dp)) {
            InputSection(
                state = ImportUiState(), ocrAvailable = true, showScanOptions = false,
                onTextChange = {}, onPaste = {}, onToggleScan = {}, onCamera = {}, onGallery = {}, onAnalyze = {},
            )
        }
    }
}
