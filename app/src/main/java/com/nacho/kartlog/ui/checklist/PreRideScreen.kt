package com.nacho.kartlog.ui.checklist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.DeviceSnapshot
import com.nacho.kartlog.domain.PreRideChecks
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.kartColors

/** «Antes de salir»: estado del móvil para el día de pista, en verde/ámbar, con atajo a los ajustes del sistema. */
@Composable
fun PreRideScreen(snapshot: () -> DeviceSnapshot, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var mount by rememberSaveable { mutableStateOf(false) }
    val checks = remember(refresh, mount) { PreRideChecks.evaluate(snapshot(), mount) }
    val kart = MaterialTheme.kartColors
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.preride_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (PreRideChecks.allOk(checks)) R.string.preride_all_ok else R.string.preride_pending),
                style = MaterialTheme.typography.titleMedium,
                color = if (PreRideChecks.allOk(checks)) kart.telemetry else kart.warning,
                modifier = Modifier.testTag("preride_summary"),
            )
            checks.forEach { c ->
                val ok = c.state == PreRideChecks.State.OK
                KartCard(Modifier.fillMaxWidth().testTag("preride_${c.id.name.lowercase()}").semantics { stateDescription = c.state.name }, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (ok) "✓" else "⚠", style = MaterialTheme.typography.headlineSmall, color = if (ok) kart.telemetry else kart.warning)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(titleOf(c.id)), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(hintOf(c.id, ok)), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted)
                        }
                    }
                    if (c.id == PreRideChecks.Id.MOUNT) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = mount, onCheckedChange = { mount = it }, modifier = Modifier.testTag("preride_mount_check"))
                            Text(stringResource(R.string.preride_mount_confirm), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else if (!ok) {
                        settingsIntent(context, c.id)?.let { intent ->
                            SecondaryButton(
                                stringResource(R.string.preride_open_settings).uppercase(),
                                { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                                modifier = Modifier.padding(top = 8.dp).testTag("preride_open_${c.id.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun titleOf(id: PreRideChecks.Id) = when (id) {
    PreRideChecks.Id.BATTERY -> R.string.preride_battery
    PreRideChecks.Id.LOCATION -> R.string.preride_location
    PreRideChecks.Id.PERMISSION -> R.string.preride_permission
    PreRideChecks.Id.BRIGHTNESS -> R.string.preride_brightness
    PreRideChecks.Id.POWER_SAVE -> R.string.preride_power_save
    PreRideChecks.Id.MOUNT -> R.string.preride_mount
}

private fun hintOf(id: PreRideChecks.Id, ok: Boolean) = when (id) {
    PreRideChecks.Id.BATTERY -> if (ok) R.string.preride_battery_ok else R.string.preride_battery_warn
    PreRideChecks.Id.LOCATION -> if (ok) R.string.preride_location_ok else R.string.preride_location_warn
    PreRideChecks.Id.PERMISSION -> if (ok) R.string.preride_permission_ok else R.string.preride_permission_warn
    PreRideChecks.Id.BRIGHTNESS -> if (ok) R.string.preride_brightness_ok else R.string.preride_brightness_warn
    PreRideChecks.Id.POWER_SAVE -> if (ok) R.string.preride_power_save_ok else R.string.preride_power_save_warn
    PreRideChecks.Id.MOUNT -> if (ok) R.string.preride_mount_ok else R.string.preride_mount_warn
}

private fun settingsIntent(context: android.content.Context, id: PreRideChecks.Id): Intent? = when (id) {
    PreRideChecks.Id.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    PreRideChecks.Id.PERMISSION -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    PreRideChecks.Id.BRIGHTNESS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
    PreRideChecks.Id.POWER_SAVE, PreRideChecks.Id.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    PreRideChecks.Id.MOUNT -> null
}
