package com.nacho.kartlog.ui.dashboard

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.GpsHealth
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TimingState
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.settings.DashboardOrientation
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val FINISH_HOLD_MS = 1_500
private const val SIGNAL_LOST_MS = 3_000L
private val ControlHeight = 64.dp

// ---------------------------------------------------------------- Ventana, orientación, batería

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Mantiene la pantalla encendida, fija la orientación y (si se pide) sube el brillo mientras se cronometra. */
@Composable
internal fun DashboardWindowEffects(prefs: DashboardPrefs) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(activity, prefs.orientation) {
        activity?.requestedOrientation = when (prefs.orientation) {
            // Admite las dos direcciones apaisadas: el soporte puede girar el móvil 180°.
            DashboardOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            DashboardOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            DashboardOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(activity, prefs.maxBrightness) {
        val window = activity?.window
        if (prefs.maxBrightness && window != null) {
            window.attributes = window.attributes.also { it.screenBrightness = 1f }
        }
        onDispose {
            if (window != null) window.attributes = window.attributes.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

private fun batteryOf(intent: Intent?): BatteryStatus? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus(levelPercent = level * 100 / scale, charging = charging)
}

/** Estado de la batería, actualizado con el aviso del sistema (sticky). */
@Composable
internal fun rememberBatteryStatus(): BatteryStatus? {
    val context = LocalContext.current
    var status by remember { mutableStateOf(batteryOf(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                batteryOf(intent)?.let { status = it }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return status
}

// ---------------------------------------------------------------- En marcha

/**
 * Cronometraje pensado para el móvil apaisado fijado al volante (referencia 640×360 dp): tiempo de la vuelta en curso
 * gigante, delta con barra a la izquierda, última/mejor a la derecha y, al pie, estado del GPS y los dos únicos
 * controles táctiles (pulsación larga para terminar y, en Resistencia, PIT). El reloj en vivo se anima entre posiciones del GPS.
 */
@Composable
fun RunningDashboard(
    timing: TimingState,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onPit: (String?) -> Unit = {},
    prefs: DashboardPrefs = DashboardPrefs(),
    alert: LapAlert? = null,
    battery: BatteryStatus? = null,
) {
    val palette = DashPalette.of(prefs.dayMode)
    val live = LocalLiveClock.current
    val clock = LocalNanoClock.current
    var nowNs by remember { mutableLongStateOf(timing.lastFixArrivalNs ?: clock()) }
    if (live) {
        LaunchedEffect(Unit) {
            while (true) {
                nowNs = clock()
                delay(50)
            }
        }
    } else {
        nowNs = timing.lastFixArrivalNs ?: nowNs
    }
    val currentMs = timing.currentLapMs(nowNs)
    val signalLost = timing.lastFixArrivalNs?.let { (nowNs - it) / 1_000_000L > SIGNAL_LOST_MS } ?: false
    val delta = timing.deltaMs
    val deltaColor = when {
        delta == null -> palette.muted
        delta <= 0 -> palette.good
        else -> palette.bad
    }
    val endurance = timing.setup?.type == SessionType.ENDURANCE

    BoxWithConstraints(modifier.background(palette.background).testTag("dash_root")) {
        val landscape = maxWidth > maxHeight
        val timeMax = if (landscape) (maxHeight.value * 0.38f).coerceIn(96f, 150f) else 92f
        val timeStyle = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = timeMax.sp, lineHeight = 1.05.em)
        val timeText: @Composable (Modifier) -> Unit = { m ->
            AutoSizeText(
                text = currentMs?.let(LapTime::format) ?: "–:––.–––",
                style = timeStyle,
                color = palette.foreground,
                minSize = if (landscape) 96.sp else 40.sp,
                modifier = m.testTag("dash_current"),
            )
        }
        val lapLabel: @Composable () -> Unit = {
            Text(
                text = if (timing.started) stringResource(R.string.track_lap_n, timing.currentLapNumber).uppercase() else stringResource(R.string.track_waiting_start).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.muted,
                maxLines = 1,
                modifier = Modifier.testTag("dash_lap_number"),
            )
        }
        val gap: @Composable () -> Unit = {
            if (timing.pitPending) PitBanner(palette) else DeltaBar(delta, palette, Modifier.fillMaxWidth().height(24.dp))
        }
        val deltaBlock: @Composable (Modifier) -> Unit = { m ->
            DashValue(stringResource(R.string.track_delta_short), delta?.let(LapTime::formatDelta) ?: stringResource(R.string.value_unset), deltaColor, palette, m.testTag("dash_delta"), size = if (landscape) 48.sp else 44.sp)
        }
        val lastBest: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InlineValue(stringResource(R.string.track_last), timing.lastLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), palette.foreground, palette, Modifier.testTag("dash_last"))
                InlineValue(stringResource(R.string.badge_best), timing.bestLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), palette.good, palette, Modifier.testTag("dash_best"))
            }
        }
        val stintBlock: @Composable () -> Unit = {
            if (endurance) {
                val driver = timing.stints.last().driverName
                val stintMs = stintElapsedMs(timing.laps, timing.lapStint, timing.stints.lastIndex, currentMs)
                Text(
                    text = stringResource(R.string.stint_current, timing.currentStintNumber) + (driver?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground,
                    maxLines = 1,
                    modifier = Modifier.testTag("stint_label"),
                )
                Text(
                    text = LapTime.format(stintMs),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = palette.muted,
                    maxLines = 1,
                    modifier = Modifier.testTag("stint_time"),
                )
            }
            if (timing.hasUnreliableLap) {
                Text(stringResource(R.string.track_unreliable_short), style = MaterialTheme.typography.bodyMedium, color = palette.warning, maxLines = 1)
            }
        }
        val footer: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(ControlHeight), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    GpsIndicator(timing, signalLost, palette)
                    if (battery?.low == true) {
                        Text(
                            text = stringResource(R.string.track_battery_low, battery.levelPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.warning,
                            maxLines = 1,
                            modifier = Modifier.testTag("battery_low"),
                        )
                    }
                }
                if (endurance) PitButton(timing, onPit, palette, Modifier.width(if (landscape) 190.dp else 150.dp))
                FinishHoldButton(onFinish, palette, Modifier.width(if (landscape) 230.dp else 160.dp))
            }
        }

        if (landscape) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    timeText(Modifier.fillMaxWidth())
                }
                gap()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    deltaBlock(Modifier.weight(0.9f))
                    Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                        lapLabel()
                        stintBlock()
                    }
                    lastBest(Modifier.weight(1.3f))
                }
                Spacer(Modifier.height(8.dp))
                footer()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    lapLabel()
                    timeText(Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    gap()
                    deltaBlock(Modifier.fillMaxWidth())
                    lastBest(Modifier.fillMaxWidth())
                    Column { stintBlock() }
                }
                Spacer(Modifier.height(12.dp))
                footer()
            }
        }
        if (alert != null) LapAlertOverlay(alert, palette)
    }
}

/** Barra de delta: cero en el centro; a la derecha (rojo) pierdes, a la izquierda (verde) ganas. Fondo de escala ±2 s. */
@Composable
internal fun DeltaBar(deltaMs: Long?, palette: DashPalette, modifier: Modifier = Modifier) {
    val fraction = DeltaBarModel.fraction(deltaMs)
    val color = if (fraction <= 0f) palette.good else palette.bad
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(palette.surface).testTag("delta_bar")) {
        val half = size.width / 2f
        val extent = half * kotlin.math.abs(fraction)
        if (extent > 0f) {
            val left = if (fraction < 0f) half - extent else half
            drawRect(color, topLeft = Offset(left, 0f), size = Size(extent, size.height))
        }
        drawRect(palette.foreground, topLeft = Offset(half - 1.5f, 0f), size = Size(3f, size.height))
    }
}

@Composable
private fun PitBanner(palette: DashPalette) {
    Box(
        Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(8.dp)).background(palette.warning).testTag("pit_pending"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.stint_in_pit_short).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.onAccent,
            maxLines = 1,
        )
    }
}

/** Aviso a pantalla completa al cerrar una vuelta (2 s): delta grande con color; morado y «MEJOR» si es récord del circuito. */
@Composable
internal fun LapAlertOverlay(alert: LapAlert, palette: DashPalette) {
    val background = when (alert.kind) {
        LapAlertKind.RECORD -> palette.record
        LapAlertKind.BEST -> palette.good
        LapAlertKind.SLOWER -> palette.bad
    }
    Column(
        Modifier.fillMaxSize().background(background).padding(16.dp).testTag("lap_alert"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (alert.kind == LapAlertKind.RECORD) stringResource(R.string.alert_best).uppercase() else stringResource(R.string.track_lap_n, alert.lapNumber).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.onAccent,
            modifier = Modifier.testTag("lap_alert_label"),
        )
        val main = alert.deltaMs?.let(LapTime::formatDelta) ?: LapTime.format(alert.lapMs)
        AutoSizeText(
            text = main,
            style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 150.sp, lineHeight = 1.05.em, textAlign = TextAlign.Center),
            color = palette.onAccent,
            minSize = 60.sp,
            modifier = Modifier.fillMaxWidth().testTag("lap_alert_value"),
        )
        if (alert.deltaMs != null) {
            Text(
                text = LapTime.format(alert.lapMs),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                color = palette.onAccent,
                modifier = Modifier.testTag("lap_alert_time"),
            )
        }
        if (alert.kind == LapAlertKind.RECORD) {
            Text(stringResource(R.string.alert_record_hint), style = MaterialTheme.typography.bodyMedium, color = palette.onAccent)
        }
    }
}

/** PIT / cambio de piloto (Resistencia): zona grande, abre un diálogo de dos toques. */
@Composable
private fun PitButton(timing: TimingState, onPit: (String?) -> Unit, palette: DashPalette, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val enabled = timing.started && !timing.pitPending
    Box(
        modifier
            .height(ControlHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) palette.warning else palette.surface)
            .clickable(enabled = enabled) { showDialog = true }
            .testTag("pit_button"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.stint_pit).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) palette.onAccent else palette.muted,
            maxLines = 1,
        )
    }
    if (showDialog) {
        var name by remember { mutableStateOf("") }
        var typing by remember { mutableStateOf(false) }
        val suggestions = timing.stints.mapNotNull { it.driverName }.distinct()
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.stint_pit_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.stint_pit_message), style = MaterialTheme.typography.bodyMedium)
                    // El campo de texto solo aparece si se pide: el diálogo es de dos toques en pista (con guantes) y,
                    // con un piloto ya conocido, basta tocar su nombre.
                    if (typing) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.stint_next_driver)) },
                            singleLine = true,
                        )
                    } else {
                        if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.titleMedium, color = palette.good)
                        suggestions.forEach { suggestion ->
                            TextButton(onClick = { name = suggestion }, modifier = Modifier.heightIn(min = 48.dp).testTag("pit_suggestion_$suggestion")) { Text(suggestion) }
                        }
                        TextButton(onClick = { typing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("pit_new_driver")) {
                            Text(stringResource(R.string.stint_new_driver))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onPit(name.ifBlank { null })
                    },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("pit_confirm"),
                ) { Text(stringResource(R.string.stint_pit_confirm).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            },
        )
    }
}

@Composable
private fun DashValue(label: String, value: String, color: Color, palette: DashPalette, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 44.sp) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.muted, maxLines = 1)
        AutoSizeText(
            text = value,
            style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = 1.1.em),
            color = color,
            minSize = 24.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** «ÚLTIMA 1:17.263» en una línea: etiqueta pequeña y cifra mediana. */
@Composable
private fun InlineValue(label: String, value: String, color: Color, palette: DashPalette, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.muted, maxLines = 1, modifier = Modifier.width(64.dp))
        AutoSizeText(
            text = value,
            style = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 1.1.em),
            color = color,
            minSize = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GpsIndicator(timing: TimingState, signalLost: Boolean, palette: DashPalette) {
    val health = GpsHealth.of(timing.accuracyM, signalLost)
    val color = when (health) {
        GpsHealth.GOOD -> palette.good
        GpsHealth.FAIR -> palette.warning
        GpsHealth.POOR -> palette.bad
        null -> palette.muted
    }
    val label = when (health) {
        GpsHealth.GOOD -> stringResource(R.string.gps_health_good)
        GpsHealth.FAIR -> stringResource(R.string.gps_health_fair)
        GpsHealth.POOR -> stringResource(R.string.gps_health_poor)
        null -> stringResource(R.string.gps_waiting)
    }
    val detail = if (signalLost) {
        stringResource(R.string.track_signal_lost)
    } else {
        timing.accuracyM?.let {
            stringResource(R.string.gps_status, String.format(Locale.ROOT, "%.0f", it), timing.rateHz?.let { hz -> String.format(Locale.ROOT, "%.1f", hz) } ?: "—")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("dash_gps")) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color).testTag("dash_gps_dot"))
        Text(
            text = "  $label" + (detail?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            modifier = Modifier.testTag("dash_gps_text"),
        )
    }
}

/**
 * Para terminar hay que mantener pulsado 1,5 s (con barra de progreso): sin menús ni botones que se puedan tocar
 * sin querer con los guantes puestos. Soltar antes deja la barra a cero y no hace nada.
 */
@Composable
internal fun FinishHoldButton(onConfirmed: () -> Unit, palette: DashPalette, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .height(ControlHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .testTag("finish_hold")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val hold = scope.launch {
                            progress.animateTo(1f, tween(FINISH_HOLD_MS, easing = LinearEasing))
                            onConfirmed()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(palette.bad.copy(alpha = 0.6f)),
        )
        Text(
            stringResource(R.string.track_hold_to_finish).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = palette.foreground,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ---------------------------------------------------------------- Previews

internal fun sampleTiming() = TimingState(
    phase = TimingPhase.RUNNING,
    setup = null,
    laps = listOf(73_412, 72_986, 72_540, 73_105),
    currentLapStartNs = 100_000_000_000L,
    lastFixElapsedNs = 143_700_000_000L,
    lastFixArrivalNs = 5_000_000_000L,
    accuracyM = 3f,
    rateHz = 1.0,
    speedMps = 16f,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 640, heightDp = 360)
@Composable
private fun RunningDashboardLandscape640Preview() {
    KartLogTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming(), onFinish = {}, modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 800, heightDp = 360)
@Composable
private fun RunningDashboardLandscape800Preview() {
    KartLogTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming().copy(accuracyM = 12f), onFinish = {}, battery = BatteryStatus(15, false), modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 640, heightDp = 360)
@Composable
private fun RunningDashboardDayPreview() {
    KartLogTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming(), onFinish = {}, prefs = DashboardPrefs(dayMode = true), modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 640, heightDp = 360)
@Composable
private fun RunningDashboardAlertPreview() {
    KartLogTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(
                timing = sampleTiming(), onFinish = {}, modifier = Modifier.fillMaxSize(),
                alert = LapAlert.of(5, 72_100, 72_540, isBest = true, circuitRecordMs = 72_300),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 780)
@Composable
private fun RunningDashboardPortraitPreview() {
    KartLogTheme {
        androidx.compose.runtime.CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming(), onFinish = {}, modifier = Modifier.fillMaxSize())
        }
    }
}
