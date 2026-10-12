package com.nacho.kartlog.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import com.nacho.kartlog.domain.gps.TimingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nacho.kartlog.domain.gps.SessionDraft
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.ui.util.formatDateTime
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nacho.kartlog.AppContainer
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.analysis.AnalysisScreen
import com.nacho.kartlog.ui.calibration.CalibrationScreen
import com.nacho.kartlog.ui.components.RunningBanner
import com.nacho.kartlog.ui.circuits.CircuitsScreen
import com.nacho.kartlog.ui.dashboard.DashboardScreen
import com.nacho.kartlog.ui.detail.SessionDetailScreen
import com.nacho.kartlog.ui.history.SessionListScreen
import com.nacho.kartlog.ui.importing.ImportScreen
import com.nacho.kartlog.ui.newsession.NewSessionScreen
import com.nacho.kartlog.ui.settings.SettingsScreen
import com.nacho.kartlog.ui.theme.kartColors

object Routes {
    const val LIST = "sessions"
    const val ANALYSIS = "analysis"
    const val CIRCUITS = "circuits"
    const val SETTINGS = "settings"
    const val NEW = "sessions/new"
    const val IMPORT = "sessions/import"
    const val TRACK = "sessions/track"
    const val CIRCUIT_ID = "circuitId"
    const val FINISH = "circuits/{$CIRCUIT_ID}/finish"
    const val FIELD_CAL = "settings/fieldcal"
    const val PRERIDE = "settings/preride"
    const val RIVALS = "analysis/rivals"
    const val RIVAL_KEY = "rivalKey"
    const val RIVAL = "analysis/rivals/{$RIVAL_KEY}"

    fun rival(key: String) = "analysis/rivals/$key"

    fun finish(circuitId: Long) = "circuits/$circuitId/finish"
    const val SESSION_ID = "sessionId"
    const val DETAIL = "sessions/{$SESSION_ID}"
    const val EDIT = "sessions/{$SESSION_ID}/edit"

    fun edit(sessionId: Long) = "sessions/$sessionId/edit"

    fun detail(sessionId: Long) = "sessions/$sessionId"
}

/** Destinos de la barra inferior, en orden. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SESSIONS(Routes.LIST, R.string.nav_sessions, Icons.Default.Timer),
    ANALYSIS(Routes.ANALYSIS, R.string.nav_analysis, Icons.Default.ShowChart),
    CIRCUITS(Routes.CIRCUITS, R.string.nav_circuits, Icons.Default.Flag),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
}

/** Cambia de pestaña conservando y restaurando el estado de cada una. */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun KartNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // La barra solo aparece en los cuatro destinos principales (no en Nueva tanda ni Detalle).
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val timing by container.timingSession.state.collectAsState()
    val replayScope = androidx.compose.runtime.rememberCoroutineScope()

    // Contenido recibido con «Compartir → KartLog»: abre la pantalla de importación (que lo recoge del buzón).
    val shared by container.importInbox.content.collectAsState()
    LaunchedEffect(shared != null) {
        if (shared != null && navController.currentDestination?.route != Routes.IMPORT) {
            navController.navigate(Routes.IMPORT) { launchSingleTop = true }
        }
    }

    // Tanda GPS interrumpida (la app murió a mitad de una tanda): se ofrece recuperarla o descartarla.
    var pendingDraft by remember { mutableStateOf<SessionDraft?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val draftEmptyNotice = stringResource(R.string.draft_empty_discarded)
    LaunchedEffect(Unit) {
        if (container.timingSession.state.value.phase == TimingPhase.IDLE) {
            val draft = container.draftStore.load()
            // Sin circuito (borrado) no se podría guardar: se tira.
            if (draft != null && container.circuitRepository.getById(draft.setup.circuitId) == null) {
                container.draftStore.clear()
            } else if (draft != null && countRecoverableLaps(draft) == 0) {
                // Sin ninguna vuelta cerrada no hay nada que guardar: se descarta y se avisa (no queda una tanda «terminada» vacía).
                container.draftStore.clear()
                snackbarHost.showSnackbar(draftEmptyNotice, duration = androidx.compose.material3.SnackbarDuration.Long)
            } else {
                pendingDraft = draft
            }
        }
    }
    pendingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.draft_title), style = MaterialTheme.typography.titleLarge) },
            // Desplazable: en apaisado (360 dp de alto) el mensaje y los botones no caben sin scroll.
            text = {
                Text(
                    stringResource(R.string.draft_message, formatDateTime(draft.setup.startedAtMs), draft.fixes.size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.verticalScroll(rememberScrollState()).testTag("draft_message"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.timingSession.restore(draft)
                        pendingDraft = null
                        navController.navigate(Routes.TRACK) { launchSingleTop = true }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.draft_recover).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDraft = null
                        container.appScope.launch { container.draftStore.clear() }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.draft_discard).uppercase(), style = MaterialTheme.typography.titleSmall) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBottomBar) {
                androidx.compose.foundation.layout.Column {
                    if (timing.phase == TimingPhase.RUNNING) {
                        RunningBanner(timing = timing, onReturn = { navController.navigate(Routes.TRACK) { launchSingleTop = true } })
                    }
                    KartNavigationBar(
                        isSelected = { dest -> currentDestination?.hierarchy?.any { it.route == dest.route } == true },
                        onSelect = { navController.navigateTopLevel(it.route) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIST,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.LIST) {
                SessionListScreen(
                    viewModel = viewModel(factory = container.sessionListViewModelFactory),
                    onNewSession = { navController.navigate(Routes.NEW) },
                    onImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onTrack = { navController.navigate(Routes.TRACK) { launchSingleTop = true } },
                    onOpenSession = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.ANALYSIS) {
                AnalysisScreen(
                    viewModel = viewModel(factory = container.analysisViewModelFactory),
                    onOpenRivals = { navController.navigate(Routes.RIVALS) { launchSingleTop = true } },
                )
            }
            composable(Routes.RIVALS) {
                com.nacho.kartlog.ui.rivals.RivalsScreen(
                    viewModel = viewModel(factory = container.rivalsViewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenRival = { navController.navigate(Routes.rival(it)) },
                )
            }
            composable(Routes.RIVAL, arguments = listOf(androidx.navigation.navArgument(Routes.RIVAL_KEY) { type = androidx.navigation.NavType.StringType })) { entry ->
                com.nacho.kartlog.ui.rivals.RivalDetailScreen(
                    viewModel = viewModel(factory = container.rivalsViewModelFactory),
                    rivalKey = entry.arguments?.getString(Routes.RIVAL_KEY).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CIRCUITS) {
                CircuitsScreen(
                    viewModel = viewModel(factory = container.circuitsViewModelFactory),
                    onOpenAnalysis = { navController.navigateTopLevel(Routes.ANALYSIS) },
                    onMarkFinish = { navController.navigate(Routes.finish(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel(factory = container.settingsViewModelFactory),
                    onOpenDashboard = { navController.navigate(Routes.TRACK) { launchSingleTop = true } },
                    onOpenFieldCalibration = { navController.navigate(Routes.FIELD_CAL) { launchSingleTop = true } },
                    onOpenPreRide = { navController.navigate(Routes.PRERIDE) { launchSingleTop = true } },
                )
            }
            composable(Routes.PRERIDE) {
                com.nacho.kartlog.ui.checklist.PreRideScreen(snapshot = { container.deviceSnapshot() }, onBack = { navController.popBackStack() })
            }
            composable(Routes.FIELD_CAL) {
                com.nacho.kartlog.ui.fieldcal.FieldCalibrationScreen(
                    viewModel = viewModel(factory = container.fieldCalibrationViewModelFactory),
                    onBack = { navController.popBackStack() },
                    debugShortcuts = container.debugTools != null,
                )
            }
            composable(Routes.NEW) {
                NewSessionScreen(
                    viewModel = viewModel(factory = container.newSessionViewModelFactory),
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.NEW) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.FINISH,
                arguments = listOf(navArgument(Routes.CIRCUIT_ID) { type = NavType.LongType }),
            ) { entry ->
                val circuitId = requireNotNull(entry.arguments?.getLong(Routes.CIRCUIT_ID))
                CalibrationScreen(
                    viewModel = viewModel(key = "finish-$circuitId", factory = container.calibrationViewModelFactory(circuitId)),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    debugShortcuts = container.debugTools != null,
                )
            }
            composable(Routes.TRACK) {
                val feedback by container.userSettings.lapFeedback.collectAsState()
                androidx.compose.runtime.CompositionLocalProvider(com.nacho.kartlog.ui.dashboard.LocalNanoClock provides container.nanoClock) {
                DashboardScreen(
                    viewModel = viewModel(factory = container.dashboardViewModelFactory),
                    timingEvents = container.timingSession.events,
                    feedbackEnabled = feedback,
                    debugShortcuts = container.debugTools != null,
                    onBack = { navController.popBackStack() },
                    onMarkFinish = { navController.navigate(Routes.finish(it)) },
                    onSaved = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.TRACK) { inclusive = true }
                        }
                    },
                )
                }
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    viewModel = viewModel(factory = container.importViewModelFactory),
                    ocrAvailable = container.imageTextRecognizer != null,
                    debugShortcuts = container.debugTools != null,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.IMPORT) { inclusive = true }
                        }
                    },
                )
            }
            // `NEW` ("sessions/new") es literal y se declara antes que DETAIL, así que tiene prioridad.
            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument(Routes.SESSION_ID) { type = NavType.LongType }),
            ) { entry ->
                val sessionId = requireNotNull(entry.arguments?.getLong(Routes.SESSION_ID))
                NewSessionScreen(
                    viewModel = viewModel(key = "edit-$sessionId", factory = container.editSessionViewModelFactory(sessionId)),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument(Routes.SESSION_ID) { type = NavType.LongType }),
            ) { entry ->
                val sessionId = requireNotNull(entry.arguments?.getLong(Routes.SESSION_ID))
                SessionDetailScreen(
                    viewModel = viewModel(
                        key = "detail-$sessionId",
                        factory = container.sessionDetailViewModelFactory(sessionId),
                    ),
                    onBack = { navController.popBackStack() },
                    debugShortcuts = container.debugTools != null,
                    onEdit = { navController.navigate(Routes.edit(sessionId)) },
                    onReplay = {
                        replayScope.launch {
                            if (container.startReplay(sessionId)) navController.navigate(Routes.TRACK) { launchSingleTop = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KartNavigationBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val kart = MaterialTheme.kartColors
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = isSelected(dest)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes), style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = kart.textMuted,
                    unselectedTextColor = kart.textMuted,
                ),
            )
        }
    }
}

/** Vueltas que saldrían de recuperar [draft] (se recalcula en una sesión desechable, sin tocar la real ni el disco). */
internal fun countRecoverableLaps(draft: SessionDraft): Int {
    val probe = TimingSession(CoroutineScope(Dispatchers.Unconfined))
    probe.restore(draft)
    return probe.state.value.laps.size
}
