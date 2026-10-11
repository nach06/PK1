package com.nacho.kartlog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nacho.kartlog.AppContainer
import com.nacho.kartlog.ui.detail.SessionDetailScreen
import com.nacho.kartlog.ui.history.SessionListScreen
import com.nacho.kartlog.ui.newsession.NewSessionScreen

object Routes {
    const val LIST = "sessions"
    const val NEW = "sessions/new"
    const val SESSION_ID = "sessionId"
    const val DETAIL = "sessions/{$SESSION_ID}"

    fun detail(sessionId: Long) = "sessions/$sessionId"
}

@Composable
fun KartNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST, modifier = modifier) {
        composable(Routes.LIST) {
            SessionListScreen(
                viewModel = viewModel(factory = container.sessionListViewModelFactory),
                onNewSession = { navController.navigate(Routes.NEW) },
                onOpenSession = { navController.navigate(Routes.detail(it)) },
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
