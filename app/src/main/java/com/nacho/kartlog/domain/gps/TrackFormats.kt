package com.nacho.kartlog.domain.gps

import java.time.Instant
import java.util.Locale
import kotlin.math.hypot

/** Lectura y escritura de trazados: CSV propio (`tracks/{id}.csv`) y GPX (`<trkpt lat lon><time>`). */
object TrackFormats {
    /** Versión 2 (Sprint 8): registro bruto con rumbo, proveedor y marca de cruce. Los ficheros v1 (5 columnas) se siguen leyendo. */
    const val CSV_VERSION_LINE = "#kartlog-track v2"
    const val CSV_HEADER = "elapsedNs;lat;lon;speedMps;accuracyM;bearingDeg;provider;crossingNs"

    /** Una línea de datos v2 (sin salto de línea). */
    fun csvLine(f: GpsFix): String = String.format(
        Locale.ROOT, "%d;%.7f;%.7f;%.2f;%.1f;%s;%s;%s",
        f.elapsedNs, f.lat, f.lon, f.speedMps, f.accuracyM,
        f.bearingDeg?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty(),
        f.provider.orEmpty().replace(';', ',').replace('\n', ' '),
        f.crossingNs?.toString().orEmpty(),
    )

    fun toCsv(fixes: List<GpsFix>): String = buildString {
        appendLine(CSV_VERSION_LINE)
        appendLine(CSV_HEADER)
        fixes.forEach { appendLine(csvLine(it)) }
    }

    /** Detecta el formato: si contiene `<gpx` o `<trkpt` es GPX; si no, CSV. */
    fun parse(text: String): List<GpsFix> =
        if (text.contains("<trkpt", ignoreCase = true)) parseGpx(text) else parseCsv(text)

    /** Lee v1 (cabecera de una línea, 5 columnas) y v2 (línea `#kartlog-track v2` + cabecera + 8 columnas); ignora líneas cortadas. */
    fun parseCsv(text: String): List<GpsFix> = text.lineSequence().mapNotNull { line ->
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("elapsedNs")) return@mapNotNull null
        val p = t.split(';')
        if (p.size < 5) return@mapNotNull null
        runCatching {
            GpsFix(
                p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[4].toFloat(),
                bearingDeg = p.getOrNull(5)?.takeIf { it.isNotBlank() }?.toFloat(),
                provider = p.getOrNull(6)?.takeIf { it.isNotBlank() },
                crossingNs = p.getOrNull(7)?.takeIf { it.isNotBlank() }?.toLong(),
            )
        }.getOrNull()
    }.toList()

    private val trkpt = Regex("""<trkpt\b([^>]*)>(.*?)</trkpt>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val latAttr = Regex("""lat\s*=\s*["']([-+0-9.eE]+)["']""")
    private val lonAttr = Regex("""lon\s*=\s*["']([-+0-9.eE]+)["']""")
    private val timeTag = Regex("""<time>\s*([^<\s]+)\s*</time>""", RegexOption.IGNORE_CASE)

    /**
     * GPX: la hora de cada punto da el reloj; la velocidad se deduce de la distancia al punto anterior y la
     * precisión se fija en 5 m (el GPX no la trae). Los puntos sin hora se ignoran.
     */
    fun parseGpx(text: String): List<GpsFix> {
        val raw = trkpt.findAll(text).mapNotNull { m ->
            val lat = latAttr.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = lonAttr.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val time = timeTag.find(m.groupValues[2])?.groupValues?.get(1)?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            Triple(lat, lon, time.epochSecond * 1_000_000_000L + time.nano)
        }.toList()
        if (raw.isEmpty()) return emptyList()
        val frame = LocalFrame(raw.first().first, raw.first().second)
        return raw.mapIndexed { i, (lat, lon, ns) ->
            val speed = if (i == 0) 0f else {
                val a = frame.toXY(raw[i - 1].first, raw[i - 1].second)
                val b = frame.toXY(lat, lon)
                val dt = (ns - raw[i - 1].third) / 1e9
                if (dt > 0) (hypot(b.x - a.x, b.y - a.y) / dt).toFloat() else 0f
            }
            GpsFix(ns, lat, lon, speed, 5f)
        }
    }
}
