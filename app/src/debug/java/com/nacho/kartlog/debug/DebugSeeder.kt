package com.nacho.kartlog.debug

import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.TrackAnalysis
import com.nacho.kartlog.domain.gps.TrackStore
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.ReplayLocationSource
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Datos de prueba realistas para validar visualmente listas y gráficas (solo builds debug).
 * 3 circuitos, 14 tandas repartidas en ~3 meses (un circuito con 7 y una tanda de 25 vueltas), tiempos
 * entre 0:45 y 1:30, vueltas de salida de boxes más lentas y alguna tanda sin vueltas. Determinista: mismo resultado cada vez.
 */
class DebugSeeder(
    private val database: KartLogDatabase,
    private val circuits: CircuitRepository,
    private val sessions: SessionRepository,
    private val laps: LapRepository,
    private val samples: SampleImages,
    private val trackStore: TrackStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : DebugTools {

    private data class Track(val name: String, val lengthMeters: Int, val baseMs: Long)

    private val tracks = listOf(
        Track("Karting Zuera", 1_100, 58_400),
        Track("Circuito Fernando Alonso", 1_400, 72_900),
        Track("Kartódromo Los Santos", 850, 47_600),
    )

    /** (circuito, días atrás, tipo, kart, estado, nº de vueltas). Una tanda queda sin vueltas. */
    private data class Plan(
        val track: Int,
        val daysAgo: Int,
        val type: SessionType,
        val kart: Int,
        val condition: TrackCondition,
        val laps: Int,
    )

    private val plans = listOf(
        Plan(0, 85, SessionType.FREE, 7, TrackCondition.DRY, 12),
        Plan(1, 78, SessionType.RACE, 12, TrackCondition.DRY, 15),
        Plan(2, 70, SessionType.FREE, 4, TrackCondition.DAMP, 10),
        Plan(0, 62, SessionType.QUALIFYING, 15, TrackCondition.DRY, 8),
        Plan(0, 55, SessionType.FREE, 7, TrackCondition.DRY, 14),
        Plan(0, 45, SessionType.FREE, 11, TrackCondition.DRY, 25), // 25 vueltas: valida las etiquetas del eje X
        Plan(1, 47, SessionType.FREE, 3, TrackCondition.WET, 9),
        Plan(2, 38, SessionType.RACE, 18, TrackCondition.DRY, 16),
        Plan(0, 30, SessionType.QUALIFYING, 20, TrackCondition.DAMP, 11),
        Plan(0, 18, SessionType.QUALIFYING, 3, TrackCondition.DRY, 9), // Zuera llega a 7 tandas
        Plan(1, 22, SessionType.FREE, 12, TrackCondition.DRY, 13),
        Plan(2, 14, SessionType.FREE, 9, TrackCondition.DRY, 0), // sin vueltas
        Plan(0, 7, SessionType.RACE, 7, TrackCondition.DRY, 18),
        Plan(1, 2, SessionType.QUALIFYING, 16, TrackCondition.DRY, 10),
    )

    override suspend fun seed(): Int {
        val random = Random(42)
        val ids = tracks.map { circuits.add(it.name, it.lengthMeters) }
        plans.forEachIndexed { index, plan ->
            val track = tracks[plan.track]
            val date = today().minusDays(plan.daysAgo.toLong())
            val dateTime = date.atTime(LocalTime.of(10 + random.nextInt(0, 8), random.nextInt(0, 4) * 15))
                .atZone(zone).toInstant().toEpochMilli()
            val damp = plan.condition == TrackCondition.DAMP
            val wet = plan.condition == TrackCondition.WET
            val sessionId = sessions.create(
                NewSession(
                    circuitId = ids[plan.track],
                    dateTime = dateTime,
                    type = plan.type,
                    kartNumber = plan.kart,
                    ballastKg = if (index % 3 == 0) null else (random.nextInt(0, 12)).toFloat(),
                    ambientTempC = (12 + random.nextInt(0, 18)).toFloat(),
                    trackTempC = if (index == 4) null else (15 + random.nextInt(0, 25)).toFloat(),
                    trackCondition = plan.condition,
                    notes = if (index % 4 == 1) "Presión neumáticos 0,9 bar" else "",
                ),
            )
            val conditionFactor = when {
                wet -> 1.06
                damp -> 1.03
                else -> 1.0
            }
            repeat(plan.laps) { lap ->
                laps.addManual(sessionId, lapTime(random, track.baseMs, conditionFactor, lap, plan.laps))
            }
        }
        return plans.size
    }

    /** Tiempo de una vuelta: salida de boxes lenta, mejora progresiva, ruido y algún error. */
    private fun lapTime(random: Random, baseMs: Long, conditionFactor: Double, lap: Int, total: Int): Long {
        val noise = 1.0 + (random.nextDouble() - 0.5) * 0.012 // ±0,6 %
        val progress = if (total <= 1) 1.0 else lap.toDouble() / (total - 1)
        val warmUp = 1.0 + 0.012 * (1.0 - progress.coerceAtMost(0.4) / 0.4) // las primeras vueltas, algo más lentas
        val outLap = if (lap == 0) 1.15 + random.nextDouble() * 0.05 else 1.0 // salida de boxes: +15-20 %
        val mistake = if (lap > 1 && random.nextInt(0, 9) == 0) 1.04 + random.nextDouble() * 0.03 else 1.0
        val ms = baseMs * conditionFactor * noise * warmUp * outLap * mistake
        return Math.round(ms).coerceIn(45_000L, 90_000L)
    }

    override suspend fun wipeAll() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        trackStore.deleteAll() // los trazados GPS viven en ficheros: sin esto quedarían huérfanos
    }

    override suspend fun seedGpsSession(): Long {
        val oval = OvalTrack()
        val existing = circuits.observeAll().first().firstOrNull { it.name == SIMULATED_CIRCUIT }
        val circuitId = existing?.id ?: circuits.add(SIMULATED_CIRCUIT, oval.perimeterM.toInt())
        circuits.setFinishLine(circuitId, oval.finishLine)
        val random = Random(11)
        val lapTimes = List(8) { i -> 58_000L + random.nextInt(-1_200, 1_200) + (if (i == 0) 2_500L else 0L) }
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 2.0, lateralNoiseM = 1.5, alongNoiseM = 0.8)
        // Las vueltas guardadas son las que recalcula el detector sobre el mismo trazado (así el selector de vuelta las empareja).
        val geometry = TrackAnalysis.build(sim.fixes, oval.finishLine)
        val sessionId = sessions.create(
            NewSession(circuitId, System.currentTimeMillis() - 3_600_000L, SessionType.FREE, 7, null, 21f, 28f, TrackCondition.DRY, "Tanda GPS de ejemplo"),
        )
        laps.addAll(sessionId, geometry.laps.map { it.timeMs }, LapSource.GPS)
        trackStore.save(sessionId, sim.fixes)
        return sessionId
    }

    override suspend fun loadTextSample(sample: TextSample): String? = samples.readText(sample.assetName)

    override fun sampleNames(): List<String> = samples.names()

    override fun sampleDiagnosis(): SampleDiagnosis = samples.diagnosis()

    override suspend fun prepareSample(name: String): String? = samples.prepare(name)

    override suspend fun prepareSimulation(): LocationSource {
        val oval = OvalTrack()
        val id = circuits.add(SIMULATED_CIRCUIT, (oval.perimeterM).toInt())
        circuits.setFinishLine(id, oval.finishLine)
        // 12 vueltas de ~58 s con variaciones reproducibles (la 1.ª, más lenta; alguna con error).
        val random = Random(7)
        val lapTimes = List(12) { i ->
            val slowdown = when (i) {
                0 -> 2_500L // primera vuelta, más lenta
                6 -> 1_800L // un error a mitad de tanda
                else -> 0L
            }
            58_000L + random.nextInt(-900, 900) + slowdown
        }
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0, lateralNoiseM = 1.5, alongNoiseM = 0.8)
        return ReplayLocationSource(sim.fixes, speedFactor = SIMULATION_SPEED)
    }

    private companion object {
        const val SIMULATED_CIRCUIT = "Óvalo simulado"
        const val SIMULATION_SPEED = 10.0
    }
}
