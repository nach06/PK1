package com.nacho.kartlog.data.gps

import com.nacho.kartlog.domain.gps.DraftStore
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.PitRequest
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
    private val pits get() = File(dir, "draft.partial.pits")

    override suspend fun begin(setup: GpsSessionSetup) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        csv.writeText(TrackFormats.CSV_VERSION_LINE + "\n" + TrackFormats.CSV_HEADER + "\n")
        pits.delete()
        json.writeText(setup.toJson().toString())
    }

    override suspend fun append(fixes: List<GpsFix>) = withContext(Dispatchers.IO) {
        if (fixes.isEmpty() || !json.exists()) return@withContext
        csv.appendText(
            fixes.joinToString(separator = "") { f -> TrackFormats.csvLine(f) + "\n" },
        )
    }

    override suspend fun appendPit(request: PitRequest) = withContext(Dispatchers.IO) {
        if (!json.exists()) return@withContext
        // `elapsedNs;nombre` (el nombre sin saltos de línea ni «;»).
        pits.appendText("${request.atElapsedNs};${request.driverName.orEmpty().replace(';', ',').replace('\n', ' ')}\n")
    }

    override suspend fun load(): SessionDraft? = withContext(Dispatchers.IO) {
        runCatching {
            if (!json.exists() || !csv.exists()) return@runCatching null
            val setup = setupFromJson(JSONObject(json.readText()))
            // Una última línea cortada (muerte a mitad de escritura) no tiene 5 campos y se descarta sola.
            val fixes = TrackFormats.parseCsv(csv.readText())
            if (fixes.isEmpty()) null else SessionDraft(setup, fixes, csv.lastModified(), readPits())
        }.getOrNull()
    }

    private fun readPits(): List<PitRequest> =
        if (!pits.exists()) emptyList() else pits.readLines().mapNotNull { line ->
            val at = line.substringBefore(';').toLongOrNull() ?: return@mapNotNull null
            if (!line.contains(';')) return@mapNotNull null
            PitRequest(at, line.substringAfter(';').ifBlank { null })
        }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            json.delete()
            csv.delete()
            pits.delete()
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
