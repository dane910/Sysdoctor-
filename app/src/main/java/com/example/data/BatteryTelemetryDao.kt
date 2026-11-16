package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryTelemetryDao {

    @Query("SELECT * FROM battery_daily_records WHERE epochDay >= :minEpochDay ORDER BY epochDay ASC")
    fun observeRecentDays(minEpochDay: Long): Flow<List<BatteryDailyRecord>>

    @Query("SELECT * FROM battery_daily_records WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByEpochDay(epochDay: Long): BatteryDailyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BatteryDailyRecord)

    @Query("DELETE FROM battery_daily_records WHERE epochDay < :minEpochDay")
    suspend fun pruneOlderThan(minEpochDay: Long)
}
