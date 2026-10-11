package com.nacho.kartlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nacho.kartlog.data.local.dao.CircuitDao
import com.nacho.kartlog.data.local.dao.LapDao
import com.nacho.kartlog.data.local.dao.SessionDao
import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.local.entity.LapEntity
import com.nacho.kartlog.data.local.entity.SessionEntity

@Database(
    entities = [CircuitEntity::class, SessionEntity::class, LapEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KartLogDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao
    abstract fun sessionDao(): SessionDao
    abstract fun lapDao(): LapDao

    companion object {
        const val NAME = "kartlog.db"

        fun create(context: Context): KartLogDatabase =
            Room.databaseBuilder(context.applicationContext, KartLogDatabase::class.java, NAME).build()
    }
}
