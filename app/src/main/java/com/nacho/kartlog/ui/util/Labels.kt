package com.nacho.kartlog.ui.util

import androidx.annotation.StringRes
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun SessionType.labelRes(): Int = when (this) {
    SessionType.FREE -> R.string.session_type_free
    SessionType.QUALIFYING -> R.string.session_type_qualifying
    SessionType.RACE -> R.string.session_type_race
}

@StringRes
fun TrackCondition.labelRes(): Int = when (this) {
    TrackCondition.DRY -> R.string.condition_dry
    TrackCondition.DAMP -> R.string.condition_damp
    TrackCondition.WET -> R.string.condition_wet
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "ES"))

fun formatDateTime(epochMs: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
