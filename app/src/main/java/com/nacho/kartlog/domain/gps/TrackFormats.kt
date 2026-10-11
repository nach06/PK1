package com.nacho.kartlog.domain.gps

import java.time.Instant
import java.util.Locale
import kotlin.math.hypot

/** Lectura y escritura de trazados: CSV propio (`tracks/{id}.csv`) y GPX (`<trkpt lat lon><time>`). */
object TrackFormats {
    const val CSV_HEADER = "elapsedNs;lat;lon;speedMps;accuracyM"

    fun toCsv(fixes: List<GpsFix>): String = buildString {
        appendLine(CSV_HEADER)
        fixes.forEach { f ->
            appendLine(String.format(Locale.ROOT, "%d;%.7f;%.7f;%.2f;%.1f", f.elapsedNs, f.lat, f.lon, f.speedMps, f.accuracyM))
        }
    }

    /** Detecta el formato: si contiene `<gpx` o `<trkpt` es GPX; si no, CSV. */
    fun parse(text: String): List<GpsFix> =
        if (text.contains("<trkpt", ignoreCase = true)) parseGpx(text) else parseCsv(text)

    fun parseCsv(text: String): List<GpsFix> = text.lineSequence().drop(1).mapNotNull { line ->
        val p = line.trim().split(';')
        if (p.size < 5) return@mapNotNull null
        runCatching { GpsFix(p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[4].toFloat()) }.getOrNull()
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
