package com.nacho.kartlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import com.nacho.kartlog.ui.theme.kartColors
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
    val accent = MaterialTheme.colorScheme.primary
    // Sobrio: superficie oscura de la app con el rojo solo en el borde superior y en el punto de «en pista».
    Column(modifier.fillMaxWidth().background(MaterialTheme.kartColors.card).testTag("running_banner")) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReturn)
                .heightIn(min = 54.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("running_banner_text"),
            )
            Text(
                text = stringResource(R.string.track_banner_back).uppercase() + " ›",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier.testTag("running_banner_return"),
            )
        }
    }
}
