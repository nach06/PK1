package com.nacho.kartlog

import android.content.Context
import com.nacho.kartlog.domain.gps.TrackStore
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nacho.kartlog.data.backup.BackupService
import com.nacho.kartlog.data.backup.ZipBackupService
import com.nacho.kartlog.data.gps.FileDraftStore
import com.nacho.kartlog.data.gps.FileTrackStore
import com.nacho.kartlog.data.gps.FusedLocationSource
import com.nacho.kartlog.data.tracking.ServiceTrackingController
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.DraftStore
import com.nacho.kartlog.domain.gps.GpsSessionSaver
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.TimingSession
import com.nacho.kartlog.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.nacho.kartlog.ui.calibration.CalibrationViewModel
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.ImportRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.ParticipantRepositoryImpl
import com.nacho.kartlog.data.repository.StintRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.debug.DebugTools
import com.nacho.kartlog.debug.DebugToolsFactory
import com.nacho.kartlog.data.settings.SharedPreferencesUserSettings
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.data.imports.FileImportPhotoStore
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.data.ocr.AndroidPhotoProcessor
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import com.nacho.kartlog.domain.importing.PhotoProcessor
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.settings.UserSettings
import com.nacho.kartlog.ui.importing.ImportViewModel
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import com.nacho.kartlog.domain.repository.ImportRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.ParticipantRepository
import com.nacho.kartlog.domain.repository.StintRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import com.nacho.kartlog.ui.analysis.AnalysisSelection
import com.nacho.kartlog.ui.analysis.AnalysisViewModel
import com.nacho.kartlog.ui.circuits.CircuitsViewModel
import com.nacho.kartlog.ui.detail.SessionDetailViewModel
import com.nacho.kartlog.ui.history.SessionListViewModel
import com.nacho.kartlog.ui.newsession.NewSessionViewModel
import com.nacho.kartlog.ui.settings.SettingsViewModel

/** Inyección manual de dependencias (sin Hilt/Dagger). Se crea una vez en [KartLogApp]. */
class AppContainer(context: Context) {
    internal val database: KartLogDatabase = KartLogDatabase.create(context)

    init {
        live += java.lang.ref.WeakReference(this)
    }

    /**
     * Para los tests: cancela el trabajo en segundo plano y cierra la base de verdad (la llamada vuelve cuando Room ya no tiene nada
     * en vuelo), así se puede borrar el fichero sin dormir «a ver si terminan las consultas».
     */
    suspend fun closeAndAwait(timeoutMs: Long = 5_000) {
        appScope.cancel()
        kotlinx.coroutines.withTimeout(timeoutMs) { kotlinx.coroutines.withContext(Dispatchers.IO) { database.close() } }
        check(!database.isOpen) { "la base debería estar cerrada" }
    }

    companion object {
        private val live = java.util.concurrent.ConcurrentLinkedQueue<java.lang.ref.WeakReference<AppContainer>>()

        /** Para los tests: cierra y espera todos los contenedores creados en el proceso de test. */
        suspend fun closeAllAndAwait() {
            while (true) {
                val container = live.poll()?.get() ?: if (live.isEmpty()) return else continue
                runCatching { container.closeAndAwait() }
            }
        }
    }

    val circuitRepository: CircuitRepository = CircuitRepositoryImpl(database.circuitDao())
    /** Trazados GPS (`filesDir/tracks/{sessionId}.csv`); se borran junto con su tanda. */
    val trackStore: TrackStore = FileTrackStore(File(context.filesDir, "tracks"))
    /** Fotos originales de las hojas importadas (`filesDir/imports/{sessionId}.jpg`); se borran con su tanda. */
    val importPhotoStore: ImportPhotoStore = FileImportPhotoStore(context, File(context.filesDir, "imports"))
    val sessionRepository: SessionRepository = SessionRepositoryImpl(database.sessionDao(), trackStore, importPhotoStore)
    val lapRepository: LapRepository = LapRepositoryImpl(database.lapDao())
    val participantRepository: ParticipantRepository = ParticipantRepositoryImpl(database.participantDao())
    val stintRepository: StintRepository = StintRepositoryImpl(database.stintDao())
    val importRepository: ImportRepository = ImportRepositoryImpl(database.importDao(), importPhotoStore)

    val userSettings: UserSettings = SharedPreferencesUserSettings(context)

    /** Buzón del contenido recibido con «Compartir → KartLog». */
    val importInbox = ImportInbox()

    /** OCR del dispositivo (ML Kit, lo conecta KartLogApp); mientras sea `null` «Escanear» no está disponible. */
    var imageTextRecognizer: ImageTextRecognizer? = null

    /** Recorte, enderezado y mejora de las fotos de las hojas; los tests lo sustituyen. */
    var photoProcessor: PhotoProcessor = AndroidPhotoProcessor(context)

    val importViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ImportViewModel(
                circuitRepository, sessionRepository, lapRepository, importRepository,
                userSettings, importInbox, imageTextRecognizer,
                participantRepository = participantRepository,
                photoProcessor = photoProcessor,
                photoStore = importPhotoStore,
                debugTools = debugTools,
            )
        }
    }

    val analyticsSource = AnalyticsSource(sessionRepository, lapRepository)
    val analysisSelection = AnalysisSelection()

    val circuitsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { CircuitsViewModel(circuitRepository, analyticsSource, analysisSelection) }
    }

    val analysisViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AnalysisViewModel(analyticsSource, analysisSelection) }
    }

    val rivalsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { com.nacho.kartlog.ui.rivals.RivalsViewModel(analyticsSource, participantRepository, userSettings) }
    }

    /** Herramientas de depuración: `null` en release. */
    val debugTools: DebugTools? =
        DebugToolsFactory.create(context, database, circuitRepository, sessionRepository, lapRepository, trackStore)

    /** Origen de posiciones. GPS interno por defecto; los tests o el simulador lo sustituyen. */
    var locationSource: LocationSource = FusedLocationSource(context)

    /** Alcance de la app: el cronometraje sigue aunque se cierre la pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Borrador de la tanda en curso (para recuperarla si el sistema mata la app). */
    val draftStore: DraftStore = FileDraftStore(File(context.filesDir, "tracks"))
    val timingSession = TimingSession(appScope, clockNs = { nanoClock() }, wallClockMs = { wallClockMs() }, draftStore = draftStore)
    val gpsSessionSaver = GpsSessionSaver(sessionRepository, lapRepository, trackStore, stintRepository)

    /** Reloj monótono (ns) del cronometraje y del reloj en vivo del Dashboard; los tests lo sustituyen por uno manual. */
    var nanoClock: () -> Long = System::nanoTime

    /** Reloj de pared (epoch ms); los tests lo sustituyen para fijar la fecha de las tandas GPS. */
    var wallClockMs: () -> Long = System::currentTimeMillis

    /** Servicio en primer plano; los tests lo sustituyen por uno que no hace nada. */
    var trackingController: TrackingController = ServiceTrackingController(context.applicationContext)

    val dashboardViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                circuitRepository, timingSession, gpsSessionSaver,
                startTracking = { trackingController.start() },
                stopTracking = { trackingController.stop() },
                locationSource = { locationSource },
                userSettings = userSettings,
                analytics = analyticsSource,
                referenceProvider = com.nacho.kartlog.data.gps.CircuitReferenceLoader(sessionRepository, lapRepository, trackStore),
                now = { wallClockMs() },
            )
        }
    }

    /**
     * Ensayo en casa (Sprint 8, B6): reproduce una tanda guardada como si fuera GPS en vivo ([speed]× más rápido; los tiempos son los
     * reales del trazado). Devuelve `false` si la tanda no tiene trazado, meta o ya hay una tanda en marcha.
     */
    var replaySpeed: Double = 4.0

    suspend fun startReplay(sessionId: Long, speed: Double = replaySpeed): Boolean {
        val detail = sessionRepository.observeDetail(sessionId).first() ?: return false
        val finish = detail.circuit.finishLine ?: return false
        val fixes = trackStore.load(sessionId)
        if (fixes.size < 3) return false
        val setup = com.nacho.kartlog.domain.gps.GpsSessionSetup(
            circuitId = detail.circuit.id, finish = finish, kartNumber = detail.session.kartNumber, type = detail.session.type,
            startedAtMs = wallClockMs(), rehearsal = true,
        )
        val source = com.nacho.kartlog.domain.gps.ReplayLocationSource(fixes, speed)
        locationSource = source
        if (!timingSession.start(setup, source)) return false
        trackingController.start()
        return true
    }

    /** Estado del móvil para «Antes de salir»; los tests lo sustituyen. */
    var deviceSnapshot: () -> com.nacho.kartlog.domain.DeviceSnapshot = com.nacho.kartlog.data.DeviceSnapshotReader(context.applicationContext)::read

    val fieldCalibrationViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { com.nacho.kartlog.ui.fieldcal.FieldCalibrationViewModel(circuitRepository, locationSource, userSettings) }
    }

    fun calibrationViewModelFactory(circuitId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { CalibrationViewModel(circuitId, circuitRepository, locationSource) }
    }

    /** Copia de seguridad completa (BD + trazados) en un zip. Incluye datos de rivales. */
    val backupService: BackupService = ZipBackupService(
        databaseFile = context.getDatabasePath(KartLogDatabase.NAME),
        tracksDir = File(context.filesDir, "tracks"),
        cacheDir = context.cacheDir,
        schemaVersion = KartLogDatabase.VERSION,
        appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
        checkpoint = { withContext(Dispatchers.IO) { database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } } },
        closeDatabase = { database.close() },
    )

    /** Copia automática semanal en `filesDir/backups/` (se lanza al abrir la app). */
    val autoBackup = com.nacho.kartlog.data.backup.AutoBackup(
        backup = backupService,
        dir = File(context.filesDir, "backups"),
        settings = userSettings,
        hasData = { sessionRepository.observeSummaries().first().isNotEmpty() },
        now = { wallClockMs() },
    )

    /** Tras restaurar una copia la app se reinicia para abrir la BD nueva; los tests lo sustituyen. */
    var restartApp: () -> Unit = { com.nacho.kartlog.ui.util.restartApp(context) }

    /** Solo debug: arranca una resistencia simulada y pide las paradas del guion cuando toca (sin tocar nada en pantalla). */
    internal fun startEnduranceSimulation(sim: com.nacho.kartlog.debug.EnduranceSimulation) {
        locationSource = sim.source
        val setup = com.nacho.kartlog.domain.gps.GpsSessionSetup(
            circuitId = sim.circuitId, finish = sim.finish, kartNumber = 28, type = com.nacho.kartlog.domain.model.SessionType.ENDURANCE, startedAtMs = wallClockMs(),
        )
        if (!timingSession.start(setup, sim.source)) return
        trackingController.start()
        val script = com.nacho.kartlog.domain.gps.PitScript(sim.pitAfterLaps, sim.drivers)
        appScope.launch {
            timingSession.state.takeWhile { it.phase == com.nacho.kartlog.domain.gps.TimingPhase.RUNNING }.collect { s ->
                script.due(s.laps.size, s.started, s.pitPending)?.let { timingSession.requestPit(it) }
            }
        }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(debugTools, SessionsCsvExporter(sessionRepository, lapRepository), userSettings, importInbox, importRepository, onSimulationReady = { locationSource = it }, onEnduranceReady = ::startEnduranceSimulation, backup = backupService, restartApp = { restartApp() }) }
    }

    val sessionListViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionListViewModel(analyticsSource, circuitRepository, userSettings) }
    }

    val newSessionViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { NewSessionViewModel(circuitRepository, sessionRepository) }
    }

    fun sessionDetailViewModelFactory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionDetailViewModel(sessionId, sessionRepository, lapRepository, participantRepository, stintRepository, trackStore) }
    }
}
