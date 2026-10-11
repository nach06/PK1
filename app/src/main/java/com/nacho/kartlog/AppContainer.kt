package com.nacho.kartlog

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.debug.DebugTools
import com.nacho.kartlog.debug.DebugToolsFactory
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import com.nacho.kartlog.domain.repository.LapRepository
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
    val sessionRepository: SessionRepository = SessionRepositoryImpl(database.sessionDao())
    val lapRepository: LapRepository = LapRepositoryImpl(database.lapDao())

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
        DebugToolsFactory.create(database, circuitRepository, sessionRepository, lapRepository)

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(debugTools, SessionsCsvExporter(sessionRepository, lapRepository)) }
    }

    val sessionListViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionListViewModel(analyticsSource, circuitRepository) }
    }

    val newSessionViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { NewSessionViewModel(circuitRepository, sessionRepository) }
    }

    fun sessionDetailViewModelFactory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionDetailViewModel(sessionId, sessionRepository, lapRepository) }
    }
}
