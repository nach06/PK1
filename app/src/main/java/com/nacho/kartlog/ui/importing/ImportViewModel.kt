package com.nacho.kartlog.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.importing.DriverChecks
import com.nacho.kartlog.domain.importing.DriverMatcher
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.ImportFormat
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import com.nacho.kartlog.domain.importing.ImportReview
import com.nacho.kartlog.domain.importing.LapSuggestion
import com.nacho.kartlog.domain.importing.LapTextParser
import com.nacho.kartlog.domain.importing.LoadedPhoto
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.OcrReliability
import com.nacho.kartlog.domain.importing.OcrScan
import com.nacho.kartlog.domain.importing.ParsedDocument
import com.nacho.kartlog.domain.importing.PhotoProcessor
import com.nacho.kartlog.domain.importing.PreparedPhoto
import com.nacho.kartlog.domain.importing.Quad
import com.nacho.kartlog.domain.importing.ReviewDriver
import com.nacho.kartlog.domain.importing.ReviewSummary
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
import com.nacho.kartlog.domain.importing.OcrDump
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
import java.time.ZoneId

enum class ImportDestination { NEW_SESSION, EXISTING_SESSION }

/** Pasos del flujo de importación. */
enum class ImportStage {
    /** Botón único «Importar» (foto, galería o texto). */
    ENTRY,

    /** Texto pegado, con un campo acotado y los botones siempre a la vista. */
    PASTE,

    /** Recorte y enderezado de la foto antes del OCR. */
    CROP,

    /** Revisión editable de todo lo leído, antes de guardar. */
    REVIEW,
}

/** Foto en la pantalla de recorte. */
data class PhotoState(
    val photo: LoadedPhoto,
    val quad: Quad,
    /** Igualar la iluminación antes del OCR. */
    val enhance: Boolean = true,
    /** Avisos de calidad ya vistos (no se repiten). */
    val qualityAcknowledged: Boolean = false,
)

data class EditingLap(val number: Int, val digits: String) {
    val timeText: String get() = LapTime.formatDigits(digits)
    val timeMs: Long? get() = LapTime.digitsToMs(digits)
}

data class ImportUiState(
    val stage: ImportStage = ImportStage.ENTRY,
    val rawText: String = "",
    val isBusy: Boolean = false,
    /** Mensaje de error (texto vacío, OCR fallido, lectura poco fiable…). */
    val error: ImportError? = null,
    // Foto
    val photo: PhotoState? = null,
    /** Imagen que se enseña en la revisión (la hoja ya recortada) y sus cajas. */
    val reviewImage: PreparedPhoto? = null,
    val scan: OcrScan? = null,
    // Revisión
    val parsed: ParsedDocument? = null,
    val format: ImportFormat? = null,
    val forcedFormat: ImportFormat? = null,
    val review: ImportReview? = null,
    val viewedDriverKey: String? = null,
    /** Columna sugerida por haber sido la elegida la última vez (sin nombre guardado); no cuenta como «tuya» hasta pulsar «Soy yo». */
    val suggestedDriverKey: String? = null,
    val selectedLap: Int? = null,
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
    val duplicateCount: Int = 0,
    // Diálogos
    val editing: EditingLap? = null,
    val showCircuitPicker: Boolean = false,
    val showSessionPicker: Boolean = false,
    val showNewCircuit: Boolean = false,
    val confirmReplace: Boolean = false,
    val confirmDoubts: Boolean = false,
    val showFormatPicker: Boolean = false,
    val showImageDialog: Boolean = false,
    val showMergePicker: Boolean = false,
    val showQualityPrompt: Boolean = false,
) {
    val viewedDriver: ReviewDriver? get() = review?.driver(viewedDriverKey)
    val me: ReviewDriver? get() = review?.me
    val selectedCircuit: Circuit? get() = circuits.firstOrNull { it.id == circuitId }
    val selectedSession: SessionSummary? get() = sessions.firstOrNull { it.session.id == existingSessionId }
    val summary: ReviewSummary? get() = review?.summary()
    val checks: DriverChecks? get() = viewedDriverKey?.let { review?.checks(it) }
    val suggestion: LapSuggestion? get() = viewedDriverKey?.let { review?.suggestion(it) }

    /** Caja de la celda seleccionada en la imagen de la hoja (para resaltarla junto a la tabla). */
    val selectedBox: OcrBox?
        get() {
            val driver = viewedDriver ?: return null
            val number = selectedLap ?: return null
            val cell = driver.cells.firstOrNull { it.number == number } ?: return null
            return scan?.grid?.boxAt(cell.line, cell.col)
        }

    /** Falta lo mínimo para poder guardar (la razón se muestra bajo el botón). */
    val missingDestination: Boolean
        get() = when (destination) {
            ImportDestination.NEW_SESSION -> circuitId == null
            ImportDestination.EXISTING_SESSION -> existingSessionId == null
        }

    val blockedReason: BlockedReason?
        get() {
            val r = review ?: return BlockedReason.NO_DATA
            return when {
                isBusy -> BlockedReason.BUSY
                r.blockedReason() == ImportReview.BlockedReason.NO_ME -> BlockedReason.NO_ME
                r.blockedReason() == ImportReview.BlockedReason.NO_VALID_LAPS -> BlockedReason.NO_VALID_LAPS
                missingDestination -> if (destination == ImportDestination.NEW_SESSION) BlockedReason.NO_CIRCUIT else BlockedReason.NO_SESSION
                else -> null
            }
        }

    /** GUARDAR está habilitado siempre que haya al menos una vuelta propia válida; si no, [blockedReason] dice por qué. */
    val canSave: Boolean get() = blockedReason == null
}

/** Por qué GUARDAR está deshabilitado (siempre se muestra el texto, nunca un botón gris sin explicación). */
enum class BlockedReason { NO_DATA, BUSY, NO_ME, NO_VALID_LAPS, NO_CIRCUIT, NO_SESSION }

enum class ImportError { EMPTY_TEXT, NOTHING_FOUND, OCR_FAILED, PHOTO_FAILED, UNRELIABLE, SAVE_FAILED }

private data class Form(
    val stage: ImportStage = ImportStage.ENTRY,
    val rawText: String = "",
    val isBusy: Boolean = false,
    val error: ImportError? = null,
    val photo: PhotoState? = null,
    val reviewImage: PreparedPhoto? = null,
    val scan: OcrScan? = null,
    val parsed: ParsedDocument? = null,
    val forcedFormat: ImportFormat? = null,
    val review: ImportReview? = null,
    val viewedDriverKey: String? = null,
    val suggestedDriverKey: String? = null,
    val selectedLap: Int? = null,
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
    val confirmDoubts: Boolean = false,
    val showFormatPicker: Boolean = false,
    val showImageDialog: Boolean = false,
    val showMergePicker: Boolean = false,
    val showQualityPrompt: Boolean = false,
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
    private val photoProcessor: PhotoProcessor? = null,
    private val photoStore: ImportPhotoStore? = null,
    /** Solo builds debug: guardar el volcado de OCR de la lectura actual. */
    private val debugTools: com.nacho.kartlog.debug.DebugTools? = null,
) : ViewModel() {

    private val form = MutableStateFlow(Form(dateTime = now()))

    private val existingLapTimes: Flow<Set<Long>> = form.map { it.existingSessionId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else lapRepository.observeBySession(id).map { l -> l.map { it.timeMs }.toSet() } }

    val uiState: StateFlow<ImportUiState> = combine(
        form, circuitRepository.observeAll(), sessionRepository.observeSummaries(), existingLapTimes,
    ) { f, circuitList, summaries, existingTimes ->
        val duplicateCount = if (f.destination == ImportDestination.EXISTING_SESSION) {
            f.review?.me?.let { me -> f.review.savedTimes(me).count { it in existingTimes } } ?: 0
        } else {
            0
        }
        ImportUiState(
            stage = f.stage, rawText = f.rawText, isBusy = f.isBusy, error = f.error, photo = f.photo,
            reviewImage = f.reviewImage, scan = f.scan, parsed = f.parsed, format = f.parsed?.format, forcedFormat = f.forcedFormat,
            review = f.review, viewedDriverKey = f.viewedDriverKey, suggestedDriverKey = f.suggestedDriverKey, selectedLap = f.selectedLap, othersExpanded = f.othersExpanded,
            destination = f.destination, circuits = circuitList, circuitId = f.circuitId, dateTime = f.dateTime, type = f.type,
            kartNumber = f.kartNumber, label = f.label, durationMin = f.durationMin, sessions = summaries,
            existingSessionId = f.existingSessionId, duplicateCount = duplicateCount, editing = f.editing,
            showCircuitPicker = f.showCircuitPicker, showSessionPicker = f.showSessionPicker, showNewCircuit = f.showNewCircuit,
            confirmReplace = f.confirmReplace, confirmDoubts = f.confirmDoubts, showFormatPicker = f.showFormatPicker,
            showImageDialog = f.showImageDialog, showMergePicker = f.showMergePicker, showQualityPrompt = f.showQualityPrompt,
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

    fun openPaste() = form.update { it.copy(stage = ImportStage.PASTE, error = null) }

    /** Vuelve a la pantalla de entrada (y descarta lo leído). */
    fun backToEntry() = form.update {
        Form(dateTime = now(), destination = it.destination, rawText = if (it.stage == ImportStage.PASTE) it.rawText else "")
            .copy(stage = ImportStage.ENTRY)
    }

    fun setRawText(text: String) = form.update { it.copy(rawText = text, error = null) }

    /** Pega texto (p. ej. del portapapeles) y lo analiza. */
    fun pasteAndAnalyze(text: String) {
        form.update { it.copy(rawText = text, error = null) }
        analyze()
    }

    /** Analiza el texto actual. Lee los circuitos de la base de datos en el momento, para reconocer el del documento. */
    fun analyze(forced: ImportFormat? = null) {
        val text = form.value.rawText
        if (text.isBlank()) {
            form.update { it.copy(error = ImportError.EMPTY_TEXT, parsed = null) }
            return
        }
        viewModelScope.launch { parseAndOpenReview(text, forced) }
    }

    private suspend fun parseAndOpenReview(text: String, forced: ImportFormat?, scan: OcrScan? = null) {
        val known = circuitRepository.observeAll().first()
        val doc = LapTextParser(knownCircuits = known.map { it.name }).parse(text, forced)
        if (doc.isEmpty) {
            form.update {
                it.copy(parsed = null, review = null, viewedDriverKey = null, error = ImportError.NOTHING_FOUND, isBusy = false, forcedFormat = forced)
            }
            return
        }
        val grid = scan?.grid
        val review = ImportReview.from(doc) { line, col -> grid?.boxAt(line, col)?.confidence }
        val forcedMe = pendingMeName?.let { DriverMatcher.findMe(doc.drivers, it) }
        pendingMeName = null
        val meDriver = forcedMe ?: DriverMatcher.findMe(doc.drivers, settings.driverName.value) ?: doc.drivers.singleOrNull { it.laps.isNotEmpty() }
        // Sin nombre guardado ni coincidencia: se sugiere (sin marcarla) la columna elegida la última vez.
        val suggested = if (meDriver == null) DriverMatcher.findSuggested(doc.drivers, settings.lastImportColumn.value) else null
        val circuit = doc.circuitName?.let { name -> known.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val withMe = review.setMe(meDriver?.key)
        form.update {
            it.copy(
                stage = ImportStage.REVIEW, parsed = doc, forcedFormat = forced, error = null, isBusy = false, rawText = text,
                review = withMe, viewedDriverKey = meDriver?.key ?: suggested?.key ?: doc.drivers.firstOrNull { d -> d.laps.isNotEmpty() }?.key,
                suggestedDriverKey = suggested?.key,
                selectedLap = null, editing = null, scan = scan ?: it.scan,
                circuitId = circuit?.id ?: it.circuitId,
                dateTime = doc.dateTime?.atZone(zone)?.toInstant()?.toEpochMilli() ?: now(),
                type = doc.type ?: SessionType.FREE,
                label = doc.label.orEmpty(),
                durationMin = doc.durationMin,
                kartNumber = meDriver?.kartNumber,
                existingSessionId = null,
            )
        }
    }

    /** Nombre de la columna «tuya» para la próxima lectura (atajo de depuración); se consume al abrir la revisión. */
    private var pendingMeName: String? = null

    private fun handleShared(content: SharedContent) {
        when (content) {
            is SharedContent.Text -> {
                pendingMeName = content.meName
                pasteAndAnalyze(content.text)
            }
            is SharedContent.Image -> loadPhoto(content.uri)
        }
    }

    // ---------------- Foto ----------------

    /** Foto o captura (cámara, galería o compartida): se carga y se enseña para recortarla antes del OCR. */
    fun loadPhoto(uri: String) {
        val processor = photoProcessor
        if (processor == null || recognizer == null) {
            form.update { it.copy(error = ImportError.OCR_FAILED) }
            return
        }
        form.update { it.copy(isBusy = true, error = null, stage = ImportStage.CROP) }
        viewModelScope.launch {
            processor.load(uri)
                .onSuccess { photo ->
                    form.update {
                        it.copy(
                            isBusy = false,
                            photo = PhotoState(photo, Quad.full(photo.width, photo.height), enhance = true),
                            review = null, parsed = null, scan = null, reviewImage = null,
                        )
                    }
                }
                .onFailure { form.update { f -> f.copy(isBusy = false, stage = ImportStage.ENTRY, error = ImportError.PHOTO_FAILED) } }
        }
    }

    fun setQuad(quad: Quad) = form.update { it.copy(photo = it.photo?.copy(quad = quad), error = null) }

    /** Gira la foto 90° en sentido horario; las esquinas vuelven a la foto entera. */
    fun rotatePhoto() {
        val processor = photoProcessor ?: return
        val state = form.value.photo ?: return
        form.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            processor.rotate90(state.photo)
                .onSuccess { rotated ->
                    form.update { it.copy(isBusy = false, photo = state.copy(photo = rotated, quad = Quad.full(rotated.width, rotated.height))) }
                }
                .onFailure { form.update { f -> f.copy(isBusy = false, error = ImportError.PHOTO_FAILED) } }
        }
    }

    fun setEnhance(enabled: Boolean) = form.update { f -> f.copy(photo = f.photo?.copy(enhance = enabled)) }

    /** Sigue adelante con una foto que ha dado avisos de calidad (movida, oscura, reflejos). */
    fun analyzeAnyway() {
        form.update { f -> f.copy(showQualityPrompt = false, photo = f.photo?.copy(qualityAcknowledged = true)) }
        analyzePhoto()
    }

    fun dismissQualityPrompt() = form.update { it.copy(showQualityPrompt = false) }

    /**
     * Endereza la hoja, la pasa por el OCR y, si la lectura es fiable, abre la revisión. Antes avisa si la foto está movida,
     * oscura o con reflejos. Nunca guarda nada.
     */
    fun analyzePhoto() {
        val state = form.value.photo ?: return
        if (!state.photo.quality.ok && !state.qualityAcknowledged) {
            form.update { it.copy(showQualityPrompt = true) }
            return
        }
        val processor = photoProcessor ?: return
        val engine = recognizer ?: return
        form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val prepared = processor.prepare(state.photo, state.quad, state.enhance).getOrElse {
                form.update { f -> f.copy(isBusy = false, error = ImportError.PHOTO_FAILED) }
                return@launch
            }
            val scan = engine.scan(prepared.uri).getOrElse {
                form.update { f -> f.copy(isBusy = false, error = ImportError.OCR_FAILED) }
                return@launch
            }
            val known = circuitRepository.observeAll().first()
            val doc = LapTextParser(knownCircuits = known.map { it.name }).parse(scan.text)
            val assessment = OcrReliability.assess(scan.boxes, doc)
            if (!assessment.reliable || doc.isEmpty) {
                form.update { f -> f.copy(isBusy = false, error = ImportError.UNRELIABLE, scan = null) }
                return@launch
            }
            form.update { it.copy(reviewImage = prepared) }
            parseAndOpenReview(scan.text, forced = null, scan = scan)
        }
    }

    /** Cambia el formato con el que se interpreta la hoja (si la detección automática falló). */
    fun openFormatPicker() = form.update { it.copy(showFormatPicker = true) }

    fun dismissFormatPicker() = form.update { it.copy(showFormatPicker = false) }

    fun changeFormat(format: ImportFormat?) {
        form.update { it.copy(showFormatPicker = false) }
        val text = form.value.rawText
        viewModelScope.launch {
            val known = circuitRepository.observeAll().first()
            val doc = LapTextParser(knownCircuits = known.map { it.name }).parse(text, format)
            if (doc.isEmpty) {
                form.update { it.copy(error = ImportError.NOTHING_FOUND, forcedFormat = format) }
                return@launch
            }
            val grid = form.value.scan?.grid
            val meKey = form.value.review?.meKey
            val review = ImportReview.from(doc) { line, col -> grid?.boxAt(line, col)?.confidence }
            val me = DriverMatcher.findMe(doc.drivers, settings.driverName.value)?.key ?: meKey?.takeIf { k -> doc.drivers.any { it.key == k } }
                ?: doc.drivers.singleOrNull { it.laps.isNotEmpty() }?.key
            form.update {
                it.copy(
                    parsed = doc, forcedFormat = format, error = null, review = review.setMe(me),
                    viewedDriverKey = me ?: doc.drivers.firstOrNull { d -> d.laps.isNotEmpty() }?.key, selectedLap = null, editing = null,
                )
            }
        }
    }

    private val dumpChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    /** Volcados de OCR (JSON) que la pantalla debe compartir (solo debug). */
    val ocrDumps = dumpChannel.receiveAsFlow()

    /** Ruta donde quedó el último volcado guardado (solo debug); se enseña en la revisión. */
    private val dumpPath = MutableStateFlow<String?>(null)
    val lastDumpPath: StateFlow<String?> = dumpPath

    /** Solo debug: guarda el volcado de OCR de la lectura en curso (cajas + geometría + confianza) y lo entrega para compartir. */
    fun shareOcrDump() {
        val scan = form.value.scan ?: return
        val json = OcrDump.toJson(scan)
        viewModelScope.launch {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date(now()))
            dumpPath.value = debugTools?.saveDump("ocr-dump-$stamp.json", json)
            dumpChannel.send(json)
        }
    }

    fun showImage() = form.update { it.copy(showImageDialog = true) }
    fun dismissImage() = form.update { it.copy(showImageDialog = false) }

    // ---------------- Revisión ----------------

    fun viewDriver(key: String) = form.update { it.copy(viewedDriverKey = key, selectedLap = null, editing = null) }

    /** Marca la columna que se está viendo como la tuya. */
    fun setMe(key: String) {
        val driver = form.value.review?.driver(key) ?: return
        settings.setLastImportColumn(DriverMatcher.encodeColumn(driver.name, driver.kartNumber))
        form.update { it.copy(review = it.review?.setMe(key), viewedDriverKey = key, suggestedDriverKey = null, kartNumber = driver.kartNumber ?: it.kartNumber) }
    }

    fun selectLap(number: Int?) = form.update { it.copy(selectedLap = if (it.selectedLap == number) null else number) }

    fun toggleOthers() = form.update { it.copy(othersExpanded = !it.othersExpanded) }

    private fun changeReview(change: (ImportReview) -> ImportReview) = form.update { f -> f.copy(review = f.review?.let(change)) }

    /** Quita la vuelta (no se guardará). */
    fun deleteLap(number: Int) {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.deleteCell(key, number) }
    }

    /** La celda dudosa está bien. */
    fun confirmLap(number: Int) {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.confirmCell(key, number) }
    }

    /** Abre el teclado para una vuelta nueva al final de la columna. */
    fun addLap() {
        val key = form.value.viewedDriverKey ?: return
        val number = form.value.review?.nextLapNumber(key) ?: return
        form.update { it.copy(editing = EditingLap(number, ""), selectedLap = number) }
    }

    fun applySuggestion() {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.applySuggestion(key) }
    }

    fun ignoreSuggestion() {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.ignoreSuggestion(key) }
    }

    /** Salta a la siguiente celda dudosa o inválida (y cambia de columna si hace falta). */
    fun nextDoubtful() {
        val f = form.value
        val next = f.review?.nextToReview(f.viewedDriverKey, f.selectedLap) ?: return
        form.update { it.copy(viewedDriverKey = next.first, selectedLap = next.second) }
    }

    fun openMergePicker() = form.update { it.copy(showMergePicker = true) }
    fun dismissMergePicker() = form.update { it.copy(showMergePicker = false) }

    /** Une [sourceKey] a la columna que se está viendo (el OCR partió un nombre en dos). */
    fun mergeInto(sourceKey: String) {
        val target = form.value.viewedDriverKey ?: return
        form.update { it.copy(showMergePicker = false) }
        changeReview { it.merge(sourceKey, target) }
    }

    fun splitDriver() {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.split(key) }
    }

    fun renameDriver(name: String) {
        val key = form.value.viewedDriverKey ?: return
        changeReview { it.rename(key, name) }
    }

    // Edición con el teclado propio
    fun startEdit(number: Int) = form.update { f ->
        val cell = f.viewedDriver()?.cells?.firstOrNull { it.number == number }
        val ms = cell?.timeMs?.takeIf { cell.timeMs in com.nacho.kartlog.domain.importing.TimeTokens.MIN_MS..com.nacho.kartlog.domain.importing.TimeTokens.MAX_MS }
        f.copy(editing = EditingLap(number, ms?.let(LapTime::msToDigits).orEmpty()), selectedLap = number)
    }

    private fun Form.viewedDriver() = review?.driver(viewedDriverKey)

    fun editDigits(input: String) = form.update { f ->
        f.copy(editing = f.editing?.let { it.copy(digits = LapTime.appendDigits(it.digits, input)) })
    }

    fun editBackspace() = form.update { f -> f.copy(editing = f.editing?.let { it.copy(digits = it.digits.dropLast(1)) }) }
    fun editClear() = form.update { f -> f.copy(editing = f.editing?.copy(digits = "")) }
    fun cancelEdit() = form.update { it.copy(editing = null) }

    fun confirmEdit() {
        val f = form.value
        val editing = f.editing ?: return
        val ms = editing.timeMs ?: return
        val key = f.viewedDriverKey ?: return
        form.update { it.copy(review = it.review?.editCell(key, editing.number, ms), editing = null) }
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
    fun incrementDuration() = form.update { it.copy(durationMin = ((it.durationMin ?: 0) + 5).coerceAtMost(600)) }
    fun decrementDuration() = form.update { f -> f.copy(durationMin = f.durationMin?.let { if (it <= 5) null else it - 5 }) }
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

    /** GUARDAR: con celdas dudosas o inválidas sin revisar pide confirmación explícita antes de seguir. */
    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        if (state.summary?.needsConfirmation == true) {
            form.update { it.copy(confirmDoubts = true) }
            return
        }
        continueSave()
    }

    fun dismissDoubts() = form.update { it.copy(confirmDoubts = false) }

    fun confirmDoubts() {
        form.update { it.copy(confirmDoubts = false) }
        continueSave()
    }

    private fun continueSave() {
        val state = uiState.value
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
        val review = state.review ?: return
        val me = review.me ?: return
        val myTimes = review.savedTimes(me)
        form.update { it.copy(isBusy = true, error = null) }

        val meImport = ParticipantImport(
            position = me.position, name = me.name, kartNumber = state.kartNumber ?: me.kartNumber,
            bestMs = me.reportedBestMs ?: myTimes.min(),
            avgMs = me.reportedAvgMs ?: (myTimes.sum() / myTimes.size),
            lapCount = me.reportedLapCount ?: myTimes.size,
        )
        val others = review.drivers.filter { it.key != me.key }.map { d ->
            val times = review.savedTimes(d)
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
        val photoUri = state.photo?.photo?.uri
        viewModelScope.launch {
            runCatching { importRepository.importSession(request) }
                .onSuccess { id ->
                    // La foto original se conserva solo si el ajuste lo permite; si falla, la importación ya está hecha.
                    if (photoUri != null && settings.saveImportPhoto.value) runCatching { photoStore?.save(id, photoUri) }
                    form.update { it.copy(isBusy = false) }
                    savedChannel.send(id)
                }
                .onFailure { form.update { f -> f.copy(isBusy = false, error = ImportError.SAVE_FAILED) } }
        }
    }
}
