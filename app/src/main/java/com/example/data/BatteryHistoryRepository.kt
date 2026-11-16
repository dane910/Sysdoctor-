package com.example.data

import com.example.model.BatteryTrendPoint
import com.example.model.SystemIntegrityInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BatteryHistoryRepository(
    private val dao: BatteryTelemetryDao
) {

    fun observeLast7Days(nowMillis: Long = System.currentTimeMillis()): Flow<List<BatteryTrendPoint>> {
        val todayEpoch = localEpochDay(nowMillis)
        val startEpoch = todayEpoch - 6L
        return dao.observeRecentDays(startEpoch).map { records ->
            buildSevenDayWindow(records, todayEpoch)
        }
    }

    suspend fun recordSnapshot(
        systemInfo: SystemIntegrityInfo,
        nowMillis: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val todayEpoch = localEpochDay(nowMillis)
        val existing = dao.getByEpochDay(todayEpoch)
        val score = computeBatteryHealthScore(systemInfo)
        val dateLabel = formatDateLabel(todayEpoch)

        val updated = if (existing == null) {
            BatteryDailyRecord(
                epochDay = todayEpoch,
                dateLabel = dateLabel,
                latestLevelPct = systemInfo.batteryLevelPct,
                avgTempC = systemInfo.batteryTempC,
                maxTempC = systemInfo.batteryTempC,
                voltageMv = systemInfo.batteryVoltageMv,
                healthLabel = systemInfo.batteryHealthLabel,
                healthScore = score,
                sampleCount = 1,
                updatedAtMillis = nowMillis
            )
        } else {
            val newCount = existing.sampleCount + 1
            val runningAvgTemp = ((existing.avgTempC * existing.sampleCount) + systemInfo.batteryTempC) / newCount
            val runningAvgScore = ((existing.healthScore * existing.sampleCount) + score) / newCount
            existing.copy(
                dateLabel = dateLabel,
                latestLevelPct = systemInfo.batteryLevelPct,
                avgTempC = (runningAvgTemp * 10f).toInt() / 10f,
                maxTempC = maxOf(existing.maxTempC, systemInfo.batteryTempC),
                voltageMv = systemInfo.batteryVoltageMv,
                healthLabel = systemInfo.batteryHealthLabel,
                healthScore = runningAvgScore,
                sampleCount = newCount,
                updatedAtMillis = nowMillis
            )
        }

        dao.upsert(updated)
        dao.pruneOlderThan(todayEpoch - 30L)
    }

    companion object {
        fun buildSevenDayWindow(
            records: List<BatteryDailyRecord>,
            todayEpochDay: Long
        ): List<BatteryTrendPoint> {
            val byEpoch = records.associateBy { it.epochDay }
            val startEpoch = todayEpochDay - 6L
            return (0..6).map { idx ->
                val dayEpoch = startEpoch + idx
                val rec = byEpoch[dayEpoch]
                val dayLabel = if (dayEpoch == todayEpochDay) "Today" else formatWeekdayLabel(dayEpoch)
                val dateLabel = formatDateLabel(dayEpoch)
                if (rec != null) {
                    BatteryTrendPoint(
                        epochDay = dayEpoch,
                        dayIndex = idx,
                        dayLabel = dayLabel,
                        dateLabel = dateLabel,
                        hasSample = true,
                        healthScore = rec.healthScore,
                        avgTempC = rec.avgTempC,
                        maxTempC = rec.maxTempC,
                        levelPct = rec.latestLevelPct,
                        voltageMv = rec.voltageMv,
                        healthLabel = rec.healthLabel,
                        sampleCount = rec.sampleCount
                    )
                } else {
                    BatteryTrendPoint(
                        epochDay = dayEpoch,
                        dayIndex = idx,
                        dayLabel = dayLabel,
                        dateLabel = dateLabel,
                        hasSample = false,
                        healthScore = 0,
                        avgTempC = 0f,
                        maxTempC = 0f,
                        levelPct = 0,
                        voltageMv = 0,
                        healthLabel = "No Data",
                        sampleCount = 0
                    )
                }
            }
        }

        fun computeBatteryHealthScore(info: SystemIntegrityInfo): Int {
            var score = when {
                info.batteryHealthLabel.equals("Good", ignoreCase = true) -> 98
                info.batteryHealthLabel.equals("Normal", ignoreCase = true) -> 90
                info.batteryHealthLabel.equals("Cold", ignoreCase = true) -> 76
                info.batteryHealthLabel.equals("Overheat", ignoreCase = true) -> 42
                info.batteryHealthLabel.equals("Over Voltage", ignoreCase = true) -> 38
                info.batteryHealthLabel.equals("Dead", ignoreCase = true) -> 15
                else -> 85
            }
            if (info.batteryTempC >= 43f) score -= 20
            else if (info.batteryTempC >= 38f) score -= 10
            else if (info.batteryTempC in 1f..10f) score -= 8

            if (info.batteryLevelPct <= 10) score -= 10
            else if (info.batteryLevelPct <= 20) score -= 5

            return score.coerceIn(10, 100)
        }

        fun localEpochDay(timeMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timeMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val offsetMillis = cal.timeZone.getOffset(cal.timeInMillis)
            return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis + offsetMillis)
        }

        private fun epochDayToMidnightDate(epochDay: Long): Date {
            val cal = Calendar.getInstance()
            val offsetMillis = cal.timeZone.getOffset(TimeUnit.DAYS.toMillis(epochDay))
            return Date(TimeUnit.DAYS.toMillis(epochDay) - offsetMillis + TimeUnit.HOURS.toMillis(12))
        }

        private fun formatWeekdayLabel(epochDay: Long): String {
            val fmt = SimpleDateFormat("EEE", Locale.getDefault())
            return fmt.format(epochDayToMidnightDate(epochDay))
        }

        private fun formatDateLabel(epochDay: Long): String {
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            return fmt.format(epochDayToMidnightDate(epochDay))
        }
    }
}
