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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
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

    val circuitRepository: CircuitRepository = CircuitRepositoryImpl(database.circuitDao())
    /** Trazados GPS (`filesDir/tracks/{sessionId}.csv`); se borran junto con su tanda. */
    val trackStore: TrackStore = FileTrackStore(File(context.filesDir, "tracks"))
    val sessionRepository: SessionRepository = SessionRepositoryImpl(database.sessionDao(), trackStore)
    val lapRepository: LapRepository = LapRepositoryImpl(database.lapDao())
    val participantRepository: ParticipantRepository = ParticipantRepositoryImpl(database.participantDao())
    val stintRepository: StintRepository = StintRepositoryImpl(database.stintDao())
    val importRepository: ImportRepository = ImportRepositoryImpl(database.importDao())

    val userSettings: UserSettings = SharedPreferencesUserSettings(context)

    /** Buzón del contenido recibido con «Compartir → KartLog». */
    val importInbox = ImportInbox()

    /** OCR del dispositivo (ML Kit, lo conecta KartLogApp); mientras sea `null` «Escanear» no está disponible. */
    var imageTextRecognizer: ImageTextRecognizer? = null

    val importViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ImportViewModel(
                circuitRepository, sessionRepository, lapRepository, importRepository,
                userSettings, importInbox, imageTextRecognizer,
                participantRepository = participantRepository,
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
                now = { wallClockMs() },
            )
        }
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

    /** Tras restaurar una copia la app se reinicia para abrir la BD nueva; los tests lo sustituyen. */
    var restartApp: () -> Unit = { com.nacho.kartlog.ui.util.restartApp(context) }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(debugTools, SessionsCsvExporter(sessionRepository, lapRepository), userSettings, importInbox, importRepository, onSimulationReady = { locationSource = it }, backup = backupService, restartApp = { restartApp() }) }
    }

    val sessionListViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionListViewModel(analyticsSource, circuitRepository) }
    }

    val newSessionViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { NewSessionViewModel(circuitRepository, sessionRepository) }
    }

    fun sessionDetailViewModelFactory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionDetailViewModel(sessionId, sessionRepository, lapRepository, participantRepository, stintRepository) }
    }
}
