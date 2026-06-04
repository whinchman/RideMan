package com.two17industries.rideman.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RideEntity::class, TrackPointEntity::class], version = 1, exportSchema = false)
abstract class RidemanDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var instance: RidemanDatabase? = null

        fun get(context: Context): RidemanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RidemanDatabase::class.java,
                    "rideman.db",
                ).build().also { instance = it }
            }
    }
}
