package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.util.LapTime
import java.time.Instant
import java.util.Locale

/**
 * Exportación del trazado propio a GPX 1.1 (un `<trk>` por vuelta, con el tiempo en `<name>`) y a CSV. Solo contiene **tus** posiciones:
 * nunca datos de rivales. Dominio puro.
 */
/** Lo que hace falta para exportar un trazado. */
class TrackExportData(val fixes: List<GpsFix>, val crossingsNs: List<Long>)

object TrackExport {
    /** Instantes de cruce de meta: las marcas del registro bruto (v2) o, en ficheros viejos, recalculados con la meta. */
    fun crossingsOf(fixes: List<GpsFix>, finish: FinishLine?): List<Long> {
        val marked = fixes.mapNotNull { it.crossingNs }
        if (marked.isNotEmpty() || finish == null) return marked
        val detector = GateDetector(finish)
        return fixes.mapNotNull { detector.feed(it)?.crossing?.elapsedNs }
    }

    /**
     * @param startWallMs hora real (epoch ms) del primer fix; la de los demás se deduce del reloj monótono.
     * Sin cruces el trazado va entero en un solo `<trk>`.
     */
    fun gpx(title: String, startWallMs: Long, fixes: List<GpsFix>, crossingsNs: List<Long>): String {
        require(fixes.isNotEmpty()) { "sin posiciones" }
        val t0 = fixes.first().elapsedNs
        fun time(ns: Long) = Instant.ofEpochMilli(startWallMs + (ns - t0) / 1_000_000L).toString()
        fun pt(f: GpsFix) = String.format(Locale.ROOT, "      <trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time></trkpt>\n", f.lat, f.lon, time(f.elapsedNs))
        fun trk(name: String, points: List<GpsFix>) = if (points.size < 2) "" else buildString {
            append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
            points.forEach { append(pt(it)) }
            append("    </trkseg>\n  </trk>\n")
        }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"KartLog\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <metadata><name>").append(escape(title)).append("</name></metadata>\n")
            if (crossingsNs.size < 2) {
                append(trk(title, fixes))
            } else {
                append(trk("Salida", fixes.filter { it.elapsedNs < crossingsNs.first() }))
                for (i in 0 until crossingsNs.size - 1) {
                    val lapMs = Math.round((crossingsNs[i + 1] - crossingsNs[i]) / 1_000_000.0)
                    val pts = fixes.filter { it.elapsedNs >= crossingsNs[i] && it.elapsedNs < crossingsNs[i + 1] }
                    append(trk("V${i + 1} ${LapTime.format(lapMs)}", pts))
                }
                append(trk("Entrada", fixes.filter { it.elapsedNs >= crossingsNs.last() }))
            }
            append("</gpx>\n")
        }
    }

    /** CSV del registro bruto (el mismo formato v2 que guarda la app). */
    fun csv(fixes: List<GpsFix>): String = TrackFormats.toCsv(fixes)

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
