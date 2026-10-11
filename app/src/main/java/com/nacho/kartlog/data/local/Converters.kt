package com.nacho.kartlog.data.local

import androidx.room.TypeConverter
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition

class Converters {
    @TypeConverter
    fun sessionTypeToString(value: SessionType): String = value.name

    @TypeConverter
    fun stringToSessionType(value: String): SessionType = SessionType.valueOf(value)

    @TypeConverter
    fun trackConditionToString(value: TrackCondition): String = value.name

    @TypeConverter
    fun stringToTrackCondition(value: String): TrackCondition = TrackCondition.valueOf(value)

    @TypeConverter
    fun lapSourceToString(value: LapSource): String = value.name

    @TypeConverter
    fun stringToLapSource(value: String): LapSource = LapSource.valueOf(value)
}
