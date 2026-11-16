package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BatteryDailyRecord::class],
    version = 1,
    exportSchema = false
)
abstract class SysDoctorDatabase : RoomDatabase() {
    abstract fun batteryTelemetryDao(): BatteryTelemetryDao

    companion object {
        @Volatile
        private var instance: SysDoctorDatabase? = null

        fun getInstance(context: Context): SysDoctorDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SysDoctorDatabase::class.java,
                    "sysdoctor_telemetry.db"
                ).build().also { instance = it }
            }
        }
    }
}
