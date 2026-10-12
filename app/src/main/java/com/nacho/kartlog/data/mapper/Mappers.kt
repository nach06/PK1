package com.nacho.kartlog.data.mapper

import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.local.entity.LapEntity
import com.nacho.kartlog.data.local.entity.ParticipantEntity
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.data.local.entity.SessionSummaryRow
import com.nacho.kartlog.data.local.entity.SessionWithCircuit
import com.nacho.kartlog.data.local.entity.StintEntity
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Stint
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.model.SessionSummary

fun CircuitEntity.toDomain() = Circuit(
    id = id,
    name = name,
    lengthMeters = lengthMeters,
    finishLine = if (finishLat != null && finishLon != null && finishBearingDeg != null) {
        FinishLine(finishLat, finishLon, finishBearingDeg, gateWidthM)
    } else {
        null
    },
)

fun SessionEntity.toDomain() = Session(
    id = id,
    circuitId = circuitId,
    dateTime = dateTime,
    type = type,
    kartNumber = kartNumber,
    ballastKg = ballastKg,
    ambientTempC = ambientTempC,
    trackTempC = trackTempC,
    trackCondition = trackCondition,
    notes = notes,
    label = label,
    durationMin = durationMin,
    signalScore = signalScore,
)

fun NewSession.toEntity() = SessionEntity(
    circuitId = circuitId,
    dateTime = dateTime,
    type = type,
    kartNumber = kartNumber,
    ballastKg = ballastKg,
    ambientTempC = ambientTempC,
    trackTempC = trackTempC,
    trackCondition = trackCondition,
    notes = notes,
    label = label,
    durationMin = durationMin,
    importedRaw = importedRaw,
    signalScore = signalScore,
)

fun LapEntity.toDomain() = Lap(
    id = id,
    sessionId = sessionId,
    number = number,
    timeMs = timeMs,
    source = source,
    stintId = stintId,
)

fun StintEntity.toDomain() = Stint(
    id = id,
    sessionId = sessionId,
    startLap = startLap,
    endLap = endLap,
    driverName = driverName,
    pitMs = pitMs,
)

fun SessionWithCircuit.toDomain() = SessionDetail(
    session = session.toDomain(),
    circuit = circuit.toDomain(),
)

fun SessionSummaryRow.toDomain() = SessionSummary(
    session = session.toDomain(),
    circuit = circuit.toDomain(),
    lapCount = lapCount,
    bestLapMs = bestLapMs,
)

fun ParticipantEntity.toDomain() = Participant(
    id = id,
    sessionId = sessionId,
    position = position,
    name = name,
    kartNumber = kartNumber,
    isMe = isMe,
    bestMs = bestMs,
    avgMs = avgMs,
    lapCount = lapCount,
)
