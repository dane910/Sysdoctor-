package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_daily_records")
data class BatteryDailyRecord(
    @PrimaryKey val epochDay: Long,
    val dateLabel: String,
    val latestLevelPct: Int,
    val avgTempC: Float,
    val maxTempC: Float,
    val voltageMv: Int,
    val healthLabel: String,
    val healthScore: Int,
    val sampleCount: Int,
    val updatedAtMillis: Long
)
