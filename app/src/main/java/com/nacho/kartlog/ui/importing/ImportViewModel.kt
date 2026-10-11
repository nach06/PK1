package com.nacho.kartlog.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.importing.DriverChecks
import com.nacho.kartlog.domain.importing.DriverMatcher
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.importing.ImportValidation
import com.nacho.kartlog.domain.importing.LapTextParser
import com.nacho.kartlog.domain.importing.ParsedDocument
import com.nacho.kartlog.domain.importing.ParsedDriver
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.ImportRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.ParticipantRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import com.nacho.kartlog.domain.settings.UserSettings
import com.nacho.kartlog.domain.util.LapTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

enum class ImportDestination { NEW_SESSION, EXISTING_SESSION }

/** Vuelta en la vista previa: editable, descartable y marcada si es sospechosa. */
data class PreviewLap(
    val number: Int,
    val timeMs: Long,
    val originalMs: Long,
    val included: Boolean = true,
    val ocrFixed: Boolean = false,
    val edited: Boolean = false,
    /** Celda sospechosa según la validación cruzada (⚠ en ámbar). */
    val suspect: Boolean = false,
    /** Mismo tiempo exacto que una vuelta que ya tiene la tanda de destino. */
    val possibleDuplicate: Boolean = false,
)

data class EditingLap(val number: Int, val digits: String) {
    val timeText: String get() = LapTime.formatDigits(digits)
    val timeMs: Long? get() = LapTime.digitsToMs(digits)
}

data class ImportUiState(
    val rawText: String = "",
    val isBusy: Boolean = false,
    /** Mensaje de error (texto vacío, OCR fallido…) para mostrar bajo la entrada. */
    val error: ImportError? = null,
    val parsed: ParsedDocument? = null,
    val selectedDriverKey: String? = null,
    val laps: List<PreviewLap> = emptyList(),
    val checks: DriverChecks? = null,
    val othersExpanded: Boolean = false,
    // Destino
    val destination: ImportDestination = ImportDestination.NEW_SESSION,
    val circuits: List<Circuit> = emptyList(),
    val circuitId: Long? = null,
    val dateTime: Long = 0L,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val label: String = "",
    val durationMin: Int? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val existingSessionId: Long? = null,
    // Diálogos
    val editing: EditingLap? = null,
    val showCircuitPicker: Boolean = false,
    val showSessionPicker: Boolean = false,
    val showNewCircuit: Boolean = false,
    val confirmReplace: Boolean = false,
) {
    val me: ParsedDriver? get() = parsed?.drivers?.firstOrNull { it.key == selectedDriverKey }
    val others: List<ParsedDriver> get() = parsed?.drivers.orEmpty().filter { it.key != selectedDriverKey }
    val includedLaps: List<PreviewLap> get() = laps.filter { it.included }
    val selectedCircuit: Circuit? get() = circuits.firstOrNull { it.id == circuitId }
    val selectedSession: SessionSummary? get() = sessions.firstOrNull { it.session.id == existingSessionId }
    val duplicateCount: Int get() = laps.count { it.included && it.possibleDuplicate }

    /** Solo se guarda lo que ha pasado por la vista previa: hay que haber elegido «yo» y haber al menos una vuelta. */
    val canSave: Boolean
        get() = !isBusy && me != null && includedLaps.isNotEmpty() && when (destination) {
            ImportDestination.NEW_SESSION -> circuitId != null
            ImportDestination.EXISTING_SESSION -> existingSessionId != null
        }
}

enum class ImportError { EMPTY_TEXT, NOTHING_FOUND, OCR_FAILED, SAVE_FAILED }

private data class Form(
    val rawText: String = "",
    val isBusy: Boolean = false,
    val error: ImportError? = null,
    val parsed: ParsedDocument? = null,
    val selectedDriverKey: String? = null,
    val laps: List<PreviewLap> = emptyList(),
    val othersExpanded: Boolean = false,
    val destination: ImportDestination = ImportDestination.NEW_SESSION,
    val circuitId: Long? = null,
    val dateTime: Long = 0L,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val label: String = "",
    val durationMin: Int? = null,
    val existingSessionId: Long? = null,
    val editing: EditingLap? = null,
    val showCircuitPicker: Boolean = false,
    val showSessionPicker: Boolean = false,
    val showNewCircuit: Boolean = false,
    val confirmReplace: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModel(
    private val circuitRepository: CircuitRepository,
    sessionRepository: SessionRepository,
    private val lapRepository: LapRepository,
    private val importRepository: ImportRepository,
    private val settings: UserSettings,
    private val inbox: ImportInbox,
    private val recognizer: ImageTextRecognizer?,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val participantRepository: ParticipantRepository? = null,
) : ViewModel() {

    private val form = MutableStateFlow(Form(dateTime = now()))

    private val existingLapTimes: Flow<Set<Long>> = form.map { it.existingSessionId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else lapRepository.observeBySession(id).map { l -> l.map { it.timeMs }.toSet() } }

    val uiState: StateFlow<ImportUiState> = combine(
        form, circuitRepository.observeAll(), sessionRepository.observeSummaries(), existingLapTimes,
    ) { f, circuitList, summaries, existingTimes ->
        val duplicatesOn = f.destination == ImportDestination.EXISTING_SESSION
        val laps = f.laps.map { it.copy(possibleDuplicate = duplicatesOn && it.timeMs in existingTimes) }
        ImportUiState(
            rawText = f.rawText, isBusy = f.isBusy, error = f.error, parsed = f.parsed,
            selectedDriverKey = f.selectedDriverKey, laps = laps, checks = checksFor(f),
            othersExpanded = f.othersExpanded, destination = f.destination, circuits = circuitList,
            circuitId = f.circuitId, dateTime = f.dateTime, type = f.type, kartNumber = f.kartNumber,
            label = f.label, durationMin = f.durationMin, sessions = summaries,
            existingSessionId = f.existingSessionId, editing = f.editing,
            showCircuitPicker = f.showCircuitPicker, showSessionPicker = f.showSessionPicker, showNewCircuit = f.showNewCircuit,
            confirmReplace = f.confirmReplace,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState(dateTime = form.value.dateTime))

    private val savedChannel = Channel<Long>(Channel.BUFFERED)

    /** Emite el id de la sesión guardada para ir a su detalle. */
    val saved = savedChannel.receiveAsFlow()

    init {
        // Contenido compartido desde otra app (texto o imagen).
        viewModelScope.launch {
            inbox.content.collect { content ->
                if (content != null) {
                    inbox.take()
                    handleShared(content)
                }
            }
        }
    }

    // ---------------- Entrada ----------------

    fun setRawText(text: String) = form.update { it.copy(rawText = text, error = null) }

    /** Pega texto (p. ej. del portapapeles) y lo analiza. */
    fun pasteAndAnalyze(text: String) {
        form.update { it.copy(rawText = text, error = null) }
        analyze()
    }

    /** Analiza el texto actual. Lee los circuitos de la base de datos en el momento, para reconocer el del documento. */
    fun analyze() {
        val text = form.value.rawText
        if (text.isBlank()) {
            form.update { it.copy(error = ImportError.EMPTY_TEXT, parsed = null) }
            return
        }
        viewModelScope.launch {
            val known = circuitRepository.observeAll().first()
            val doc = LapTextParser(knownCircuits = known.map { it.name }).parse(text)
            if (doc.isEmpty) {
                form.update { it.copy(parsed = null, laps = emptyList(), selectedDriverKey = null, error = ImportError.NOTHING_FOUND) }
                return@launch
            }
            val me = DriverMatcher.findMe(doc.drivers, settings.driverName.value)
                ?: doc.drivers.singleOrNull { it.laps.isNotEmpty() }
            val circuit = doc.circuitName?.let { name -> known.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            form.update {
                it.copy(
                    parsed = doc, error = null, selectedDriverKey = null, laps = emptyList(),
                    circuitId = circuit?.id ?: it.circuitId,
                    dateTime = doc.dateTime?.atZone(zone)?.toInstant()?.toEpochMilli() ?: now(),
                    type = doc.type ?: SessionType.FREE,
                    label = doc.label.orEmpty(),
                    durationMin = doc.durationMin,
                    existingSessionId = null,
                )
            }
            me?.let { selectDriver(it.key) }
        }
    }

    private fun handleShared(content: SharedContent) {
        when (content) {
            is SharedContent.Text -> pasteAndAnalyze(content.text)
            is SharedContent.Image -> recognizeImage(content.uri)
        }
    }

    /** Foto o captura: OCR en el dispositivo y después el mismo análisis que con texto. */
    fun recognizeImage(uri: String) {
        val engine = recognizer
        if (engine == null) {
            form.update { it.copy(error = ImportError.OCR_FAILED) }
            return
        }
        form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = engine.recognize(uri)
            result.onSuccess { text ->
                form.update { it.copy(isBusy = false, rawText = text) }
                analyze()
            }.onFailure {
                form.update { f -> f.copy(isBusy = false, error = ImportError.OCR_FAILED) }
            }
        }
    }

    // ---------------- «Yo» y vista previa ----------------

    fun selectDriver(key: String) {
        val doc = form.value.parsed ?: return
        val driver = doc.drivers.firstOrNull { it.key == key } ?: return
        form.update {
            it.copy(
                selectedDriverKey = key,
                laps = driver.laps.map { lap -> PreviewLap(lap.number, lap.timeMs, lap.timeMs, ocrFixed = lap.ocrFixed) },
                kartNumber = driver.kartNumber,
                editing = null,
            )
        }
        refreshSuspects()
    }

    fun toggleOthers() = form.update { it.copy(othersExpanded = !it.othersExpanded) }

    fun toggleLap(number: Int) {
        form.update { f -> f.copy(laps = f.laps.map { if (it.number == number) it.copy(included = !it.included) else it }) }
        refreshSuspects()
    }

    /** Aplica la corrección propuesta (nunca se aplica sola). */
    fun applySuggestion() {
        val s = checksFor(form.value)?.suggestion ?: return
        form.update { f ->
            f.copy(laps = f.laps.map { if (it.number == s.lapNumber) it.copy(timeMs = s.proposedMs, edited = true) else it })
        }
        refreshSuspects()
    }

    // Edición con el teclado propio
    fun startEdit(number: Int) = form.update { f ->
        val lap = f.laps.firstOrNull { it.number == number } ?: return@update f
        f.copy(editing = EditingLap(number, LapTime.msToDigits(lap.timeMs)))
    }

    fun editDigits(input: String) = form.update { f ->
        f.copy(editing = f.editing?.let { it.copy(digits = LapTime.appendDigits(it.digits, input)) })
    }

    fun editBackspace() = form.update { f -> f.copy(editing = f.editing?.let { it.copy(digits = it.digits.dropLast(1)) }) }
    fun editClear() = form.update { f -> f.copy(editing = f.editing?.copy(digits = "")) }
    fun cancelEdit() = form.update { it.copy(editing = null) }

    fun confirmEdit() {
        val editing = form.value.editing ?: return
        val ms = editing.timeMs ?: return
        form.update { f ->
            f.copy(
                laps = f.laps.map { if (it.number == editing.number) it.copy(timeMs = ms, edited = it.originalMs != ms || it.edited) else it },
                editing = null,
            )
        }
        refreshSuspects()
    }

    private fun checksFor(f: Form): DriverChecks? {
        val me = f.parsed?.drivers?.firstOrNull { it.key == f.selectedDriverKey } ?: return null
        val included = f.laps.filter { it.included }
        return ImportValidation.check(included.map { it.number to it.timeMs }, me.reportedBestMs, me.reportedLapCount, me.reportedAvgMs)
    }

    /** Recalcula qué celdas son sospechosas tras cada cambio (las editadas por el usuario dejan de serlo). */
    private fun refreshSuspects() {
        form.update { f ->
            val suspects = checksFor(f)?.suspectLapNumbers.orEmpty()
            f.copy(laps = f.laps.map { it.copy(suspect = it.number in suspects && !it.edited) })
        }
    }

    // ---------------- Destino ----------------

    fun setDestination(destination: ImportDestination) = form.update { it.copy(destination = destination) }
    fun openCircuitPicker() = form.update { it.copy(showCircuitPicker = true) }
    fun dismissCircuitPicker() = form.update { it.copy(showCircuitPicker = false) }
    fun selectCircuit(id: Long) = form.update { it.copy(circuitId = id, showCircuitPicker = false) }
    fun openSessionPicker() = form.update { it.copy(showSessionPicker = true) }
    fun dismissSessionPicker() = form.update { it.copy(showSessionPicker = false) }
    fun selectExistingSession(id: Long) = form.update { it.copy(existingSessionId = id, showSessionPicker = false) }
    fun setType(type: SessionType) = form.update { it.copy(type = type) }
    fun setLabel(label: String) = form.update { it.copy(label = label) }
    fun incrementKart() = form.update { it.copy(kartNumber = ((it.kartNumber ?: 0) + 1).coerceAtMost(99)) }
    fun decrementKart() = form.update { f -> f.copy(kartNumber = f.kartNumber?.let { if (it <= 1) null else it - 1 }) }
    fun setDateTime(epochMs: Long) = form.update { it.copy(dateTime = epochMs) }
    fun openNewCircuit() = form.update { it.copy(showNewCircuit = true, showCircuitPicker = false) }
    fun dismissNewCircuit() = form.update { it.copy(showNewCircuit = false) }

    fun createCircuit(name: String, lengthMeters: Int?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = circuitRepository.add(name, lengthMeters)
            form.update { it.copy(circuitId = id, showNewCircuit = false) }
        }
    }

    // ---------------- Guardar ----------------

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        val existingId = state.existingSessionId
        if (state.destination == ImportDestination.EXISTING_SESSION && existingId != null && participantRepository != null) {
            // Importar sobre una tanda que ya tiene clasificación la sustituye: se pide confirmación.
            viewModelScope.launch {
                val hasClassification = participantRepository.observeBySession(existingId).first().isNotEmpty()
                if (hasClassification) form.update { it.copy(confirmReplace = true) } else doSave()
            }
            return
        }
        doSave()
    }

    fun confirmReplace() {
        form.update { it.copy(confirmReplace = false) }
        doSave()
    }

    fun dismissReplace() = form.update { it.copy(confirmReplace = false) }

    private fun doSave() {
        val state = uiState.value
        if (!state.canSave) return
        val me = state.me ?: return
        val doc = state.parsed ?: return
        val myLaps = state.includedLaps.sortedBy { it.number }
        val myTimes = myLaps.map { it.timeMs }
        form.update { it.copy(isBusy = true, error = null) }

        val meImport = ParticipantImport(
            position = me.position, name = me.name, kartNumber = state.kartNumber ?: me.kartNumber,
            bestMs = me.reportedBestMs ?: myTimes.min(),
            avgMs = me.reportedAvgMs ?: (myTimes.sum() / myTimes.size),
            lapCount = me.reportedLapCount ?: myTimes.size,
        )
        val others = doc.drivers.filter { it.key != me.key }.map { d ->
            val times = d.laps.sortedBy { it.number }.map { it.timeMs }
            ParticipantImport(
                position = d.position, name = d.name, kartNumber = d.kartNumber,
                bestMs = d.reportedBestMs ?: times.minOrNull(),
                avgMs = d.reportedAvgMs ?: times.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
                lapCount = d.reportedLapCount ?: times.size.takeIf { it > 0 },
                lapTimesMs = times,
            )
        }
        val request = when (state.destination) {
            ImportDestination.NEW_SESSION -> ImportRequest(
                newSession = NewSession(
                    circuitId = requireNotNull(state.circuitId), dateTime = state.dateTime, type = state.type,
                    kartNumber = state.kartNumber, ballastKg = null, ambientTempC = null, trackTempC = null,
                    trackCondition = TrackCondition.DRY, notes = "",
                    label = state.label.trim().ifEmpty { null }, durationMin = state.durationMin, importedRaw = state.rawText,
                ),
                existingSessionId = null, myLapTimesMs = myTimes, me = meImport, others = others,
            )
            ImportDestination.EXISTING_SESSION -> ImportRequest(
                newSession = null, existingSessionId = requireNotNull(state.existingSessionId), myLapTimesMs = myTimes,
                me = meImport, others = others, importedRaw = state.rawText,
            )
        }
        viewModelScope.launch {
            runCatching { importRepository.importSession(request) }
                .onSuccess { id ->
                    form.update { it.copy(isBusy = false) }
                    savedChannel.send(id)
                }
                .onFailure { form.update { f -> f.copy(isBusy = false, error = ImportError.SAVE_FAILED) } }
        }
    }
}
