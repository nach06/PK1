package com.nacho.kartlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.TimingState
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.theme.MonoFontFamily

/** Aviso persistente «Tanda en pista» sobre la barra inferior mientras hay una tanda GPS en marcha (Sprint 6, A3). */
@Composable
fun RunningBanner(timing: TimingState, onReturn: () -> Unit, modifier: Modifier = Modifier) {
    val text = if (timing.started) {
        stringResource(R.string.track_banner_lap, timing.currentLapNumber, timing.bestLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset))
    } else {
        stringResource(R.string.track_banner_waiting)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onReturn)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
            .testTag("running_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f).testTag("running_banner_text"),
        )
        Text(
            text = stringResource(R.string.track_banner_back).uppercase() + " ›",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("running_banner_return"),
        )
    }
}
