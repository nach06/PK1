package com.nacho.kartlog.ui.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.importing.ImportFormat
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.ChipSelector
import com.nacho.kartlog.ui.components.CircuitEditSheet
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.RacingTextField
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.components.SessionDatePickerDialog
import com.nacho.kartlog.ui.components.SessionPickerSheet
import com.nacho.kartlog.ui.components.SessionTimePickerDialog
import com.nacho.kartlog.ui.components.TimeKeypad
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDate
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.formatTime
import com.nacho.kartlog.ui.util.labelRes
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Importar tiempos, por pasos: **entrada** (un botón con tres vías) → **recorte** de la foto → **revisión** editable → guardar.
 * Pegar texto salta directamente a la revisión. Nada se guarda hasta confirmar en la revisión.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showMeta by rememberSaveable { mutableStateOf(false) }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it) } }

    // Cámara del sistema (foto temporal en cache/photos) o galería. El OCR es del dispositivo.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhotoPath
        if (ok && path != null) {
            viewModel.loadPhoto(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path)).toString())
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.loadPhoto(it.toString()) }
    }
    val launchCamera = {
        val file = File(File(context.cacheDir, "photos").apply { mkdirs() }, "escaneo.jpg")
        pendingPhotoPath = file.absolutePath
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    BackHandler(enabled = state.stage != ImportStage.ENTRY) { viewModel.backToEntry() }

    when (state.stage) {
        ImportStage.ENTRY -> EntryContent(state, ocrAvailable, onBack, onImport = { showSources = true })
        ImportStage.PASTE -> PasteContent(
            state = state,
            onBack = viewModel::backToEntry,
            onTextChange = viewModel::setRawText,
            onPaste = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::setRawText) },
            onAnalyze = { viewModel.analyze() },
        )
        ImportStage.CROP -> CropContent(
            state = state,
            onBack = viewModel::backToEntry,
            onQuad = viewModel::setQuad,
            onRotate = viewModel::rotatePhoto,
            onEnhance = viewModel::setEnhance,
            onAnalyze = viewModel::analyzePhoto,
            onRetake = {
                viewModel.backToEntry()
                showSources = true
            },
            onPasteInstead = viewModel::openPaste,
        )
        ImportStage.REVIEW -> ReviewContent(
            state = state,
            viewModel = viewModel,
            onBack = viewModel::backToEntry,
            onEditMeta = { showMeta = true },
        )
    }

    if (showSources) {
        ModalBottomSheet(
            onDismissRequest = { showSources = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    stringResource(R.string.import_sheet_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SourceRow(stringResource(R.string.import_option_camera), "import_source_camera", enabled = ocrAvailable) { showSources = false; launchCamera() }
                SourceRow(stringResource(R.string.import_option_gallery), "import_source_gallery", enabled = ocrAvailable) {
                    showSources = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceRow(stringResource(R.string.import_option_paste), "import_source_paste") { showSources = false; viewModel.openPaste() }
            }
        }
    }

    if (showMeta) {
        MetaSheet(
            state = state,
            viewModel = viewModel,
            onDate = { showDatePicker = true },
            onTime = { showTimePicker = true },
            onDismiss = { showMeta = false },
        )
    }

    if (state.confirmReplace) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReplace,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.import_replace_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.import_replace_message), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReplace, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.import_replace_confirm).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplace, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }
    if (state.confirmDoubts) {
        val summary = state.summary
        AlertDialog(
            onDismissRequest = viewModel::dismissDoubts,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.import_doubts_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("doubts_title")) },
            text = {
                Text(
                    stringResource(R.string.import_doubts_message, summary?.doubtful ?: 0, summary?.invalid ?: 0),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDoubts, modifier = Modifier.heightIn(min = 56.dp).testTag("doubts_save")) {
                    Text(stringResource(R.string.import_doubts_save).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDoubts, modifier = Modifier.heightIn(min = 56.dp).testTag("doubts_review")) {
                    Text(stringResource(R.string.import_doubts_review).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }
    if (state.showQualityPrompt) QualityPrompt(state, onRetake = {
        viewModel.dismissQualityPrompt()
        viewModel.backToEntry()
        showSources = true
    }, onAnyway = viewModel::analyzeAnyway)
    if (state.showFormatPicker) {
        FormatPicker(state.format, state.forcedFormat, onPick = viewModel::changeFormat, onDismiss = viewModel::dismissFormatPicker)
    }
    if (state.showMergePicker) {
        val viewing = state.viewedDriver
        val others = state.review?.drivers.orEmpty().filter { it.key != viewing?.key }
        AlertDialog(
            onDismissRequest = viewModel::dismissMergePicker,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.import_merge_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(stringResource(R.string.import_merge_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
                    others.forEach { d ->
                        TextButton(onClick = { viewModel.mergeInto(d.key) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("merge_${d.name}")) {
                            Text(d.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMergePicker, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }
    if (state.showImageDialog) ImageDialog(state, onDismiss = viewModel::dismissImage)

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

// ---------------------------------------------------------------- Entrada

@Composable
private fun SourceRow(text: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.kartColors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .testTag(tag),
    )
}

@Composable
private fun EntryContent(state: ImportUiState, ocrAvailable: Boolean, onBack: () -> Unit, onImport: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.import_title), onBack = onBack) },
    ) { padding ->
        Column(
            // Texto y botón juntos, arriba (no centrados ni pegados al borde inferior): el botón queda justo bajo la explicación.
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.import_entry_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.kartColors.textMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp).testTag("import_entry_hint"),
            )
            state.error?.let { ErrorText(it, Modifier.padding(bottom = 16.dp)) }
            if (!ocrAvailable) {
                Text(
                    stringResource(R.string.import_scan_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            PrimaryButton(text = stringResource(R.string.import_main), onClick = onImport, modifier = Modifier.testTag("import_main"))
        }
    }
}

/**
 * Pegar texto sin que el campo se coma la pantalla: el campo ocupa el espacio que queda y se desplaza por dentro, y los botones
 * PEGAR / ANALIZAR están siempre abajo, a la vista.
 */
@Composable
private fun PasteContent(
    state: ImportUiState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.import_paste_title), onBack = onBack) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { ErrorText(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_paste), onPaste, modifier = Modifier.testTag("paste_clipboard")) }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(
                            text = stringResource(R.string.import_analyze),
                            onClick = onAnalyze,
                            enabled = state.rawText.isNotBlank() && !state.isBusy,
                            modifier = Modifier.testTag("paste_analyze"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RacingTextField(
                value = state.rawText,
                onValueChange = onTextChange,
                label = stringResource(R.string.import_input_label),
                placeholder = stringResource(R.string.import_input_hint),
                modifier = Modifier.weight(1f).testTag("paste_field"),
            )
            if (state.rawText.isNotBlank()) {
                Text(
                    stringResource(R.string.import_paste_lines, state.rawText.lines().size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.kartColors.textMuted,
                )
            } else if (state.rawText.isEmpty()) {
                Text(
                    stringResource(R.string.import_analyze_disabled_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.testTag("paste_hint"),
                )
            }
        }
    }
}

@Composable
internal fun ErrorText(error: ImportError, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (error) {
                ImportError.EMPTY_TEXT -> R.string.import_error_empty
                ImportError.NOTHING_FOUND -> R.string.import_error_nothing
                ImportError.OCR_FAILED -> R.string.import_error_ocr
                ImportError.PHOTO_FAILED -> R.string.import_error_photo
                ImportError.UNRELIABLE -> R.string.import_error_unreliable
                ImportError.SAVE_FAILED -> R.string.import_error_save
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.kartColors.warning,
        modifier = modifier.testTag("import_error"),
    )
}

@Composable
private fun FormatPicker(detected: ImportFormat?, forced: ImportFormat?, onPick: (ImportFormat?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.kartColors.card,
        title = { Text(stringResource(R.string.import_format_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                val options: List<ImportFormat?> = listOf(null) + ImportFormat.entries
                options.forEach { option ->
                    val selected = option == forced
                    TextButton(onClick = { onPick(option) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("format_${option?.name ?: "AUTO"}")) {
                        Text(
                            (if (selected) "● " else "○ ") + (option?.let { formatLabel(it) } ?: stringResource(R.string.import_format_auto)),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

@Composable
internal fun formatLabel(format: ImportFormat): String = stringResource(
    when (format) {
        ImportFormat.MATRIX -> R.string.import_format_matrix
        ImportFormat.ROW_PER_DRIVER -> R.string.import_format_row_per_driver
        ImportFormat.SINGLE_COLUMN -> R.string.import_format_single_column
        ImportFormat.SUMMARY -> R.string.import_format_summary
    },
)

// ---------------------------------------------------------------- Datos de la tanda

/** Hoja con todos los datos de la tanda editables: destino, circuito, fecha, hora, tipo, kart, duración y título. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaSheet(state: ImportUiState, viewModel: ImportViewModel, onDate: () -> Unit, onTime: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.import_meta_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kartColors.textMuted)
            DestinationSection(
                state = state,
                onDestination = viewModel::setDestination,
                onCircuit = viewModel::openCircuitPicker,
                onNewCircuit = viewModel::openNewCircuit,
                onDate = onDate,
                onTime = onTime,
                onType = viewModel::setType,
                onKartMinus = viewModel::decrementKart,
                onKartPlus = viewModel::incrementKart,
                onDurationMinus = viewModel::decrementDuration,
                onDurationPlus = viewModel::incrementDuration,
                onLabel = viewModel::setLabel,
                onSession = viewModel::openSessionPicker,
            )
            PrimaryButton(stringResource(R.string.import_meta_done), onDismiss, modifier = Modifier.testTag("meta_done"))
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
    onDurationMinus: () -> Unit,
    onDurationPlus: () -> Unit,
    onLabel: (String) -> Unit,
    onSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("meta_circuit"),
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
                    OutlinedButton(onClick = onNewCircuit, modifier = Modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.circuit_new).uppercase(), style = MaterialTheme.typography.titleSmall)
                    }
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
                BigStepper(
                    label = stringResource(R.string.label_duration),
                    valueText = state.durationMin?.let { stringResource(R.string.format_duration_value, it) } ?: stringResource(R.string.value_unset),
                    onDecrement = onDurationMinus,
                    onIncrement = onDurationPlus,
                )
                RacingTextField(
                    value = state.label,
                    onValueChange = onLabel,
                    placeholder = stringResource(R.string.import_label_hint),
                    singleLine = true,
                )
            }
        }
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
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kartColors.textMuted)
            Text(
                text = timeText,
                style = KartTextStyles.timeEntry,
                color = if (hasDigits && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (hasDigits) 1f else 0.5f).testTag("edit_time"),
            )
            TimeKeypad(onDigits = onDigits, onBackspace = onBackspace, onClear = onClear, keyHeight = 56.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.action_cancel), onCancel, color = MaterialTheme.kartColors.textMuted) }
                Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.action_accept), onConfirm, enabled = valid, modifier = Modifier.testTag("edit_accept")) }
            }
        }
    }
}
