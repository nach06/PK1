package com.nacho.kartlog.data.gps

import com.nacho.kartlog.domain.gps.DraftStore
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.SessionDraft
import com.nacho.kartlog.domain.gps.TrackFormats
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Borrador en `{dir}/draft.partial.json` (preparación) y `{dir}/draft.partial.csv` (posiciones, una línea por fix,
 * añadidas con `append`). Si la app muere a mitad de una línea, el lector ignora esa última línea incompleta.
 */
class FileDraftStore(private val dir: File) : DraftStore {
    private val json get() = File(dir, "draft.partial.json")
    private val csv get() = File(dir, "draft.partial.csv")

    override suspend fun begin(setup: GpsSessionSetup) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        csv.writeText(TrackFormats.CSV_HEADER + "\n")
        json.writeText(setup.toJson().toString())
    }

    override suspend fun append(fixes: List<GpsFix>) = withContext(Dispatchers.IO) {
        if (fixes.isEmpty() || !json.exists()) return@withContext
        csv.appendText(
            fixes.joinToString(separator = "") { f ->
                String.format(Locale.ROOT, "%d;%.7f;%.7f;%.2f;%.1f\n", f.elapsedNs, f.lat, f.lon, f.speedMps, f.accuracyM)
            },
        )
    }

    override suspend fun load(): SessionDraft? = withContext(Dispatchers.IO) {
        runCatching {
            if (!json.exists() || !csv.exists()) return@runCatching null
            val setup = setupFromJson(JSONObject(json.readText()))
            // Una última línea cortada (muerte a mitad de escritura) no tiene 5 campos y se descarta sola.
            val fixes = TrackFormats.parseCsv(csv.readText())
            if (fixes.isEmpty()) null else SessionDraft(setup, fixes, csv.lastModified())
        }.getOrNull()
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            json.delete()
            csv.delete()
        }
    }

    private fun GpsSessionSetup.toJson() = JSONObject().apply {
        put("circuitId", circuitId)
        put("lat", finish.lat)
        put("lon", finish.lon)
        put("bearingDeg", finish.bearingDeg)
        put("gateWidthM", finish.gateWidthM)
        kartNumber?.let { put("kartNumber", it) }
        ballastKg?.let { put("ballastKg", it.toDouble()) }
        ambientTempC?.let { put("ambientTempC", it.toDouble()) }
        trackTempC?.let { put("trackTempC", it.toDouble()) }
        put("trackCondition", trackCondition.name)
        put("type", type.name)
        put("startedAtMs", startedAtMs)
    }

    private fun setupFromJson(o: JSONObject) = GpsSessionSetup(
        circuitId = o.getLong("circuitId"),
        finish = FinishLine(o.getDouble("lat"), o.getDouble("lon"), o.getDouble("bearingDeg"), o.getInt("gateWidthM")),
        kartNumber = if (o.has("kartNumber")) o.getInt("kartNumber") else null,
        ballastKg = if (o.has("ballastKg")) o.getDouble("ballastKg").toFloat() else null,
        ambientTempC = if (o.has("ambientTempC")) o.getDouble("ambientTempC").toFloat() else null,
        trackTempC = if (o.has("trackTempC")) o.getDouble("trackTempC").toFloat() else null,
        trackCondition = TrackCondition.valueOf(o.getString("trackCondition")),
        type = SessionType.valueOf(o.getString("type")),
        startedAtMs = o.getLong("startedAtMs"),
    )
}
