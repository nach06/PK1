package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.MatchedLap
import com.nacho.kartlog.domain.gps.TrackAnalysis
import com.nacho.kartlog.domain.gps.TrackGeometry
import com.nacho.kartlog.domain.gps.TrackLapPath
import com.nacho.kartlog.domain.gps.TrackPt
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import java.util.Locale

/** Qué vuelta(s) se dibujan. Se guarda como texto: `ALL`, `BEST`, `LAST` o `LAP:n`. */
internal sealed interface TrackSelection {
    data object All : TrackSelection
    data object Best : TrackSelection
    data object Last : TrackSelection
    data class Lap(val number: Int) : TrackSelection

    fun encode(): String = when (this) {
        All -> "ALL"
        Best -> "BEST"
        Last -> "LAST"
        is Lap -> "LAP:$number"
    }

    companion object {
        fun decode(text: String): TrackSelection = when {
            text == "BEST" -> Best
            text == "LAST" -> Last
            text.startsWith("LAP:") -> text.removePrefix("LAP:").toIntOrNull()?.let(::Lap) ?: All
            else -> All
        }
    }
}

internal enum class TrackColorMode { LAP, SPEED, PACE }

/** Lo que se dibuja para una selección: la vuelta resaltada, la de referencia (comparación) y las demás, tenues. */
internal data class TrackScene(
    val highlighted: List<MatchedLap>,
    val reference: MatchedLap?,
    val faint: List<MatchedLap>,
    val fullTrace: Boolean,
)

internal fun sceneOf(matched: List<MatchedLap>?, selection: TrackSelection, compare: Boolean): TrackScene {
    if (matched == null) return TrackScene(emptyList(), null, emptyList(), fullTrace = true)
    val best = matched.minByOrNull { it.path.timeMs }
    val picked: MatchedLap? = when (selection) {
        TrackSelection.All -> null
        TrackSelection.Best -> best
        TrackSelection.Last -> matched.lastOrNull()
        is TrackSelection.Lap -> matched.firstOrNull { it.storedNumber == selection.number }
    }
    if (picked == null) return TrackScene(matched, null, emptyList(), fullTrace = false)
    val reference = if (compare && best != null && best.storedNumber != picked.storedNumber) best else null
    val faint = if (reference != null) emptyList() else matched.filter { it.storedNumber != picked.storedNumber }
    return TrackScene(listOf(picked), reference, faint, fullTrace = false)
}

/**
 * Tarjeta «Trazado» del Detalle de una tanda GPS: la traza proyectada (norte arriba) con la meta, selector de vuelta,
 * modo de color (vuelta / velocidad / ritmo), comparación con la mejor y lectura al tocar un punto.
 */
@Composable
internal fun TrackCard(track: TrackUiState.Ready, modifier: Modifier = Modifier) {
    val kart = MaterialTheme.kartColors
    var expanded by rememberSaveable { mutableStateOf(true) }
    var selectionText by rememberSaveable { mutableStateOf(TrackSelection.All.encode()) }
    var modeName by rememberSaveable { mutableStateOf(TrackColorMode.LAP.name) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Pair<Int, TrackPt>?>(null) }

    val geometry = track.geometry
    val matched = track.matched
    val selection = TrackSelection.decode(selectionText)
    val scene = remember(matched, selectionText, compare) { sceneOf(matched, selection, compare) }
    val single = scene.highlighted.singleOrNull()
    val best = matched?.minByOrNull { it.path.timeMs }
    val canCompare = single != null && best != null && best.storedNumber != single.storedNumber
    val canPace = canCompare && !compare
    val mode = TrackColorMode.valueOf(modeName).let { if (it == TrackColorMode.PACE && !canPace) TrackColorMode.LAP else it }

    KartCard(modifier.fillMaxWidth().testTag("track_card"), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }.testTag("track_header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.track_card_title).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = kart.textMuted)
        }
        if (!expanded) return@KartCard

        track.note?.let { note ->
            Text(
                stringResource(if (note == TrackNote.NO_FINISH) R.string.track_note_no_finish else R.string.track_note_mismatch),
                style = MaterialTheme.typography.bodyMedium,
                color = kart.warning,
                modifier = Modifier.padding(bottom = 8.dp).testTag("track_note"),
            )
        }

        TrackCanvas(
            geometry = geometry,
            scene = scene,
            mode = mode,
            selectionText = selectionText,
            bestPath = best?.path,
            picked = picked,
            onPick = { picked = it },
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )

        // Lectura: punto tocado o descripción de lo que se ve.
        Text(
            text = infoText(geometry, scene, picked, selection),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("track_info"),
        )
        Legend(mode = mode, geometry = geometry, comparing = scene.reference != null, selected = single, best = best)

        if (matched != null) {
            LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") { SmallChip(stringResource(R.string.track_sel_all), selection == TrackSelection.All, "track_sel_all") { selectionText = TrackSelection.All.encode(); picked = null } }
                item(key = "best") { SmallChip(stringResource(R.string.track_sel_best), selection == TrackSelection.Best, "track_sel_best") { selectionText = TrackSelection.Best.encode(); picked = null } }
                item(key = "last") { SmallChip(stringResource(R.string.track_sel_last), selection == TrackSelection.Last, "track_sel_last") { selectionText = TrackSelection.Last.encode(); picked = null } }
                items(matched, key = { "lap-${it.storedNumber}" }) { lap ->
                    SmallChip(
                        stringResource(R.string.lap_short, lap.storedNumber),
                        selection == TrackSelection.Lap(lap.storedNumber),
                        "track_sel_${lap.storedNumber}",
                    ) { selectionText = TrackSelection.Lap(lap.storedNumber).encode(); picked = null }
                }
            }
        }
        LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "m-lap") { SmallChip(stringResource(R.string.track_mode_lap), mode == TrackColorMode.LAP, "track_mode_lap") { modeName = TrackColorMode.LAP.name } }
            item(key = "m-speed") { SmallChip(stringResource(R.string.track_mode_speed), mode == TrackColorMode.SPEED, "track_mode_speed") { modeName = TrackColorMode.SPEED.name } }
            if (canPace) item(key = "m-pace") { SmallChip(stringResource(R.string.track_mode_pace), mode == TrackColorMode.PACE, "track_mode_pace") { modeName = TrackColorMode.PACE.name } }
            if (canCompare) item(key = "compare") {
                SmallChip(stringResource(R.string.track_compare), compare, "track_compare") { compare = !compare; picked = null }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val kart = MaterialTheme.kartColors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else kart.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { stateDescription = if (selected) "selected" else "" }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun infoText(geometry: TrackGeometry, scene: TrackScene, picked: Pair<Int, TrackPt>?, selection: TrackSelection): String {
    if (picked != null) {
        val speed = String.format(Locale.ROOT, "%.0f", picked.second.speedMps * 3.6f)
        return if (scene.fullTrace) {
            stringResource(R.string.track_pick_full, speed, LapTime.format(picked.second.tMs))
        } else {
            stringResource(R.string.track_pick_lap, picked.first, speed, LapTime.format(picked.second.tMs))
        }
    }
    if (scene.fullTrace) return stringResource(R.string.track_info_full, geometry.all.size)
    val single = scene.highlighted.singleOrNull() ?: return stringResource(R.string.track_info_all, scene.highlighted.size)
    val head = stringResource(R.string.lap_short, single.storedNumber) + "  " + LapTime.format(single.path.timeMs)
    val ref = scene.reference
    return if (ref != null) head + "  vs  " + stringResource(R.string.lap_short, ref.storedNumber) + "  " + LapTime.format(ref.path.timeMs) else head
}

@Composable
private fun Legend(mode: TrackColorMode, geometry: TrackGeometry, comparing: Boolean, selected: MatchedLap?, best: MatchedLap?) {
    val kart = MaterialTheme.kartColors
    val text = when {
        comparing && selected != null && best != null ->
            stringResource(R.string.track_legend_compare, stringResource(R.string.lap_short, best.storedNumber), stringResource(R.string.lap_short, selected.storedNumber))
        mode == TrackColorMode.SPEED ->
            stringResource(
                R.string.track_legend_speed,
                String.format(Locale.ROOT, "%.0f", geometry.minSpeedMps * 3.6f),
                String.format(Locale.ROOT, "%.0f", geometry.maxSpeedMps * 3.6f),
            )
        mode == TrackColorMode.PACE -> stringResource(R.string.track_legend_pace)
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.padding(top = 4.dp).testTag("track_legend"))
}

// ---------------------------------------------------------------- Lienzo

private const val PAD_DP = 14f

@Composable
private fun TrackCanvas(
    geometry: TrackGeometry,
    scene: TrackScene,
    mode: TrackColorMode,
    selectionText: String,
    bestPath: TrackLapPath?,
    picked: Pair<Int, TrackPt>?,
    onPick: (Pair<Int, TrackPt>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kart = MaterialTheme.kartColors
    val primary = MaterialTheme.colorScheme.primary
    val good = kart.telemetry
    val gateColor = MaterialTheme.colorScheme.onSurface

    // Puntos a dibujar: como mucho ~2000 en total (submuestreados), calculados una vez por selección.
    val drawn = remember(geometry, scene) {
        val groups = scene.highlighted.size + scene.faint.size + (if (scene.reference != null) 1 else 0)
        val budget = if (scene.fullTrace) TrackAnalysis.MAX_DRAW_POINTS else (TrackAnalysis.MAX_DRAW_POINTS / groups.coerceAtLeast(1)).coerceAtLeast(2)
        DrawnScene(
            highlighted = scene.highlighted.map { it.storedNumber to TrackAnalysis.decimate(it.path.points, budget) },
            reference = scene.reference?.let { it.storedNumber to TrackAnalysis.decimate(it.path.points, budget) },
            faint = scene.faint.map { it.storedNumber to TrackAnalysis.decimate(it.path.points, budget) },
            full = if (scene.fullTrace) TrackAnalysis.decimate(geometry.all, TrackAnalysis.MAX_DRAW_POINTS) else emptyList(),
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val state = "sel=$selectionText;mode=${mode.name};compare=${scene.reference != null}"

    Canvas(
        modifier
            .semantics {
                contentDescription = "Trazado"
                stateDescription = state
            }
            .testTag("track_canvas")
            .pointerInput(drawn) {
                detectTapGestures { tap ->
                    val fit = TrackAnalysis.fit(geometry.bounds, size.width.toFloat(), size.height.toFloat(), PAD_DP * density)
                    val candidates = buildList {
                        drawn.highlighted.forEach { (n, pts) -> pts.forEach { add(n to it) } }
                        drawn.reference?.let { (n, pts) -> pts.forEach { add(n to it) } }
                        if (drawn.highlighted.isEmpty() && drawn.reference == null) {
                            drawn.faint.forEach { (n, pts) -> pts.forEach { add(n to it) } }
                            drawn.full.forEach { add(0 to it) }
                        }
                    }
                    onPick(TrackAnalysis.nearest(candidates, fit, tap.x, tap.y, 28f * density))
                }
            },
    ) {
        val fit = TrackAnalysis.fit(geometry.bounds, size.width, size.height, PAD_DP * density)
        val stroke = 2.dp.toPx()
        val thick = 4.dp.toPx()

        // Vueltas tenues (fondo) y traza completa.
        drawn.faint.forEach { (_, pts) -> drawPath(pts, fit, onSurface(gateColor, 0.14f), stroke) }
        if (scene.fullTrace) drawColored(drawn.full, fit, mode.takeIf { it == TrackColorMode.SPEED } ?: TrackColorMode.LAP, geometry, good.copy(alpha = 0.9f), thick, null)

        when {
            scene.reference != null -> {
                drawn.reference?.let { (_, pts) -> drawPath(pts, fit, good, thick) }
                drawn.highlighted.forEach { (_, pts) -> drawPath(pts, fit, primary, thick) }
            }
            drawn.highlighted.size == 1 -> {
                val (_, pts) = drawn.highlighted.single()
                val deltas = if (mode == TrackColorMode.PACE) paceFor(scene, bestPath, pts) else null
                drawColored(pts, fit, mode, geometry, good, thick, deltas)
            }
            else -> drawn.highlighted.forEach { (_, pts) ->
                // «Todas»: trazas superpuestas con baja opacidad.
                drawColored(pts, fit, mode.takeIf { it == TrackColorMode.SPEED } ?: TrackColorMode.LAP, geometry, good.copy(alpha = 0.3f), stroke, null, alpha = 0.3f)
            }
        }

        // Meta: puerta y flecha de sentido.
        if (geometry.hasGate) {
            val a = Offset(fit.px(geometry.gateA.x.toFloat()), fit.py(geometry.gateA.y.toFloat()))
            val b = Offset(fit.px(geometry.gateB.x.toFloat()), fit.py(geometry.gateB.y.toFloat()))
            drawLine(gateColor, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            val center = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val dir = Offset(geometry.heading.x.toFloat(), -geometry.heading.y.toFloat())
            val len = 18.dp.toPx()
            val tip = Offset(center.x + dir.x * len, center.y + dir.y * len)
            drawLine(gateColor, center, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            val side = Offset(-dir.y, dir.x)
            val back = 7.dp.toPx()
            drawLine(gateColor, tip, Offset(tip.x - dir.x * back + side.x * back, tip.y - dir.y * back + side.y * back), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(gateColor, tip, Offset(tip.x - dir.x * back - side.x * back, tip.y - dir.y * back - side.y * back), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }

        // Punto tocado.
        picked?.let { (_, p) ->
            val c = Offset(fit.px(p.x), fit.py(p.y))
            drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
            drawCircle(primary, radius = 6.dp.toPx(), center = c)
        }
    }
}

private data class DrawnScene(
    val highlighted: List<Pair<Int, List<TrackPt>>>,
    val reference: Pair<Int, List<TrackPt>>?,
    val faint: List<Pair<Int, List<TrackPt>>>,
    val full: List<TrackPt>,
)

private fun onSurface(color: Color, alpha: Float) = color.copy(alpha = alpha)

/** Ritmo de la vuelta dibujada frente a la mejor de la tanda, punto a punto (mismo submuestreo que la traza). */
private fun paceFor(scene: TrackScene, best: TrackLapPath?, drawnPoints: List<TrackPt>): List<Long>? {
    val lap = scene.highlighted.singleOrNull() ?: return null
    if (best == null || best.number == lap.path.number) return null
    val deltas = TrackAnalysis.paceDeltasMs(lap.path, best)
    // Los puntos dibujados son un submuestreo de los de la vuelta: se busca cada uno por posición.
    val indexByPoint = HashMap<TrackPt, Int>(lap.path.points.size * 2)
    lap.path.points.forEachIndexed { i, p -> indexByPoint[p] = i }
    return drawnPoints.map { p -> indexByPoint[p]?.let { deltas[it] } ?: 0L }
}

private fun DrawScope.drawPath(pts: List<TrackPt>, fit: com.nacho.kartlog.domain.gps.ViewFit, color: Color, width: Float) {
    if (pts.size < 2) return
    val path = Path()
    pts.forEachIndexed { i, p ->
        val x = fit.px(p.x)
        val y = fit.py(p.y)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** Traza coloreada tramo a tramo: color fijo, gradiente de velocidad o diferencia de ritmo. */
private fun DrawScope.drawColored(
    pts: List<TrackPt>,
    fit: com.nacho.kartlog.domain.gps.ViewFit,
    mode: TrackColorMode,
    geometry: TrackGeometry,
    base: Color,
    width: Float,
    paceDeltas: List<Long>?,
    alpha: Float = 1f,
) {
    if (pts.size < 2) return
    if (mode == TrackColorMode.LAP || (mode == TrackColorMode.PACE && paceDeltas == null)) {
        drawPath(pts, fit, base, width)
        return
    }
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val color = when (mode) {
            TrackColorMode.SPEED -> speedColor(TrackAnalysis.speedFraction((a.speedMps + b.speedMps) / 2f, geometry.minSpeedMps, geometry.maxSpeedMps)).copy(alpha = alpha)
            else -> paceColor(((paceDeltas!![i] + paceDeltas[i - 1]) / 2f))
        }
        drawLine(color, Offset(fit.px(a.x), fit.py(a.y)), Offset(fit.px(b.x), fit.py(b.y)), strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** Lento = rojo, rápido = verde (pasando por amarillo). */
internal fun speedColor(fraction: Float): Color = Color.hsv(120f * fraction.coerceIn(0f, 1f), 0.85f, 1f)

/** Ganando tiempo (≤ 0) = verde; perdiendo = rojo; fondo de escala ±1 s. */
internal fun paceColor(deltaMs: Float): Color {
    val f = (deltaMs / 1_000f).coerceIn(-1f, 1f)
    return if (f <= 0f) androidx.compose.ui.graphics.lerp(Color(0xFFBDBDBD), Color(0xFF00E676), -f)
    else androidx.compose.ui.graphics.lerp(Color(0xFFBDBDBD), Color(0xFFFF4D4D), f)
}
