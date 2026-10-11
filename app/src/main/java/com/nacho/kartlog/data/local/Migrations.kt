package com.nacho.kartlog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (Sprint 3, importar tiempos):
 * - `sessions`: columnas nulables `label`, `durationMin` e `importedRaw`.
 * - Nuevas tablas `participants` y `participant_laps` (con borrado en cascada).
 *
 * El SQL es el exportado en `app/schemas/.../2.json`; si se cambia una entidad hay que regenerarlo.
 * Las filas existentes se conservan intactas. No se usa `fallbackToDestructiveMigration`.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `label` TEXT")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `durationMin` INTEGER")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `importedRaw` TEXT")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `participants` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`position` INTEGER, " +
                "`name` TEXT, " +
                "`kartNumber` INTEGER, " +
                "`isMe` INTEGER NOT NULL, " +
                "`bestMs` INTEGER, " +
                "`avgMs` INTEGER, " +
                "`lapCount` INTEGER, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_participants_sessionId` ON `participants` (`sessionId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `participant_laps` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`participantId` INTEGER NOT NULL, " +
                "`number` INTEGER NOT NULL, " +
                "`timeMs` INTEGER NOT NULL, " +
                "FOREIGN KEY(`participantId`) REFERENCES `participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_participant_laps_participantId_number` " +
                "ON `participant_laps` (`participantId`, `number`)",
        )
    }
}

/**
 * v2 → v3 (Sprint 4, cronometraje por GPS): la meta del circuito (`finishLat`, `finishLon`, `finishBearingDeg`)
 * y el ancho de la puerta (`gateWidthM`, 20 m por defecto). Los circuitos existentes quedan sin meta.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `circuits` ADD COLUMN `finishLat` REAL")
        db.execSQL("ALTER TABLE `circuits` ADD COLUMN `finishLon` REAL")
        db.execSQL("ALTER TABLE `circuits` ADD COLUMN `finishBearingDeg` REAL")
        db.execSQL("ALTER TABLE `circuits` ADD COLUMN `gateWidthM` INTEGER NOT NULL DEFAULT 20")
    }
}
