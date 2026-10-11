package com.nacho.kartlog.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.nacho.kartlog.ui.circuits.CircuitsScreen
import com.nacho.kartlog.ui.detail.SessionDetailScreen
import com.nacho.kartlog.ui.history.SessionListScreen
import com.nacho.kartlog.ui.newsession.NewSessionScreen
import com.nacho.kartlog.ui.settings.SettingsScreen
import com.nacho.kartlog.ui.theme.kartColors

object Routes {
    const val LIST = "sessions"
    const val ANALYSIS = "analysis"
    const val CIRCUITS = "circuits"
    const val SETTINGS = "settings"
    const val NEW = "sessions/new"
    const val SESSION_ID = "sessionId"
    const val DETAIL = "sessions/{$SESSION_ID}"

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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                KartNavigationBar(
                    isSelected = { dest -> currentDestination?.hierarchy?.any { it.route == dest.route } == true },
                    onSelect = { navController.navigateTopLevel(it.route) },
                )
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
                    onOpenSession = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.ANALYSIS) {
                AnalysisScreen(viewModel = viewModel(factory = container.analysisViewModelFactory))
            }
            composable(Routes.CIRCUITS) {
                CircuitsScreen(
                    viewModel = viewModel(factory = container.circuitsViewModelFactory),
                    onOpenAnalysis = { navController.navigateTopLevel(Routes.ANALYSIS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel(factory = container.settingsViewModelFactory))
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
            // `NEW` ("sessions/new") es literal y se declara antes que DETAIL, así que tiene prioridad.
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
