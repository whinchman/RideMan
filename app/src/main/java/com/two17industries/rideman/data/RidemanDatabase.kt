package com.two17industries.rideman.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RideEntity::class, TrackPointEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RidemanDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var instance: RidemanDatabase? = null

        /** Adds the nullable planRideId column introduced for plan tracking. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN planRideId TEXT")
            }
        }

        /** Adds Strava upload tracking columns. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaState TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaActivityId INTEGER")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaExternalId TEXT")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaError TEXT")
            }
        }

        fun get(context: Context): RidemanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RidemanDatabase::class.java,
                    "rideman.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
