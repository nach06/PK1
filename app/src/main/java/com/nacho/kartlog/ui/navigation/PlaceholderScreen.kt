package com.nacho.kartlog.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nacho.kartlog.ui.components.KartTopBar

/** Pantalla provisional de un destino aún no implementado (se sustituye en B6/B7). */
@Composable
fun PlaceholderScreen(@StringRes titleRes: Int) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(titleRes)) },
    ) { }
}
