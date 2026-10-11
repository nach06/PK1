package com.nacho.kartlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nacho.kartlog.data.local.dao.CircuitDao
import com.nacho.kartlog.data.local.dao.ImportDao
import com.nacho.kartlog.data.local.dao.LapDao
import com.nacho.kartlog.data.local.dao.ParticipantDao
import com.nacho.kartlog.data.local.dao.SessionDao
import com.nacho.kartlog.data.local.dao.StintDao
import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.local.entity.LapEntity
import com.nacho.kartlog.data.local.entity.ParticipantEntity
import com.nacho.kartlog.data.local.entity.ParticipantLapEntity
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.data.local.entity.StintEntity

@Database(
    entities = [
        CircuitEntity::class,
        SessionEntity::class,
        LapEntity::class,
        ParticipantEntity::class,
        ParticipantLapEntity::class,
        StintEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KartLogDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao
    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao
    abstract fun participantDao(): ParticipantDao
    abstract fun importDao(): ImportDao
    abstract fun stintDao(): StintDao

    companion object {
        const val NAME = "kartlog.db"

        fun create(context: Context): KartLogDatabase =
            Room.databaseBuilder(context.applicationContext, KartLogDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // migración explícita; nunca fallbackToDestructiveMigration
                .build()
    }
}
