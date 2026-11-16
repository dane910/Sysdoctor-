package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.BatteryDailyRecord
import com.example.data.BatteryHistoryRepository
import com.example.diagnostics.HardwareToolkitRepository
import com.example.model.BatteryHardwareSnapshot
import com.example.model.BatteryTrendMetric
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.CleanerCategoryScan
import com.example.model.CleanerCategoryType
import com.example.model.CleanerDeepScanResult
import com.example.model.CleanerCandidateItem
import com.example.model.CleanupCategoryReport
import com.example.model.CleanupReport
import com.example.model.DisplayMetricsSnapshot
import com.example.model.DuplicateScanState
import com.example.model.HardwareTestStatus
import com.example.model.RecoveryScanState
import com.example.model.StorageSummary
import com.example.model.StorageVolumeDetail
import com.example.model.SystemIntegrityInfo
import com.example.model.WifiSecurityAudit
import com.example.ui.screens.CleanerScreen
import com.example.ui.screens.PulseOverviewScreen
import com.example.ui.screens.ToolkitScreen
import com.example.ui.theme.SysPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SysDoctor", appName)
  }

  @Test
  fun `architecture guides cover expected security topics`() {
    val ids = com.example.diagnostics.ArchitectureGuides.topics.map { it.id }
    assertTrue(ids.contains("bootable_usb"))
    assertTrue(ids.contains("partition_recovery"))
    assertTrue(ids.contains("network_auditing"))
    assertTrue(ids.contains("rooting_tradeoffs"))
  }

  @Test
  fun `seven day battery window fills missing days honestly`() {
    val today = 20100L
    val records = listOf(
      BatteryDailyRecord(
        epochDay = today - 2,
        dateLabel = "Nov 14",
        latestLevelPct = 78,
        avgTempC = 30.5f,
        maxTempC = 32.0f,
        voltageMv = 4150,
        healthLabel = "Good",
        healthScore = 96,
        sampleCount = 3,
        updatedAtMillis = 1000L
      ),
      BatteryDailyRecord(
        epochDay = today,
        dateLabel = "Nov 16",
        latestLevelPct = 88,
        avgTempC = 31.0f,
        maxTempC = 31.5f,
        voltageMv = 4210,
        healthLabel = "Good",
        healthScore = 98,
        sampleCount = 2,
        updatedAtMillis = 2000L
      )
    )
    val points = BatteryHistoryRepository.buildSevenDayWindow(records, today)
    assertEquals(7, points.size)
    assertEquals(2, points.count { it.hasSample })
    assertEquals("Today", points.last().dayLabel)
    assertEquals(98, points.last().healthScore)
  }

  @Test
  fun `entry dashboard displays storage status battery health and 7 day trend`() {
    val sampleStorage = StorageSummary(
      totalBytes = 128L * 1024 * 1024 * 1024,
      availableBytes = 64L * 1024 * 1024 * 1024,
      usedBytes = 64L * 1024 * 1024 * 1024,
      appCacheBytes = 12L * 1024 * 1024
    )
    val sampleSystem = SystemIntegrityInfo(
      manufacturer = "Google",
      model = "Pixel 9",
      androidVersion = "15",
      apiLevel = 35,
      securityPatch = "2026-10-05",
      cpuCores = 8,
      cpuUsagePct = 24,
      totalRamBytes = 8L * 1024 * 1024 * 1024,
      availableRamBytes = 4L * 1024 * 1024 * 1024,
      batteryLevelPct = 86,
      isBatteryCharging = true,
      batteryTempC = 31.4f,
      batteryVoltageMv = 4210,
      batteryHealthLabel = "Good"
    )
    val sampleWifi = WifiSecurityAudit(
      isConnected = true,
      transportLabel = "Wi-Fi",
      securityProtocol = "WPA3-SAE",
      auditScore = 92
    )
    val trendPoints = BatteryHistoryRepository.buildSevenDayWindow(
      listOf(
        BatteryDailyRecord(
          epochDay = 20100L,
          dateLabel = "Nov 16",
          latestLevelPct = 86,
          avgTempC = 31.4f,
          maxTempC = 31.4f,
          voltageMv = 4210,
          healthLabel = "Good",
          healthScore = 98,
          sampleCount = 1,
          updatedAtMillis = 1000L
        )
      ),
      todayEpochDay = 20100L
    )

    composeTestRule.setContent {
      SysPulseTheme(darkTheme = true) {
        PulseOverviewScreen(
          storage = sampleStorage,
          wifi = sampleWifi,
          systemInfo = sampleSystem,
          batteryTrend7Days = trendPoints,
          selectedBatteryMetric = BatteryTrendMetric.HEALTH_SCORE,
          onSelectBatteryMetric = {},
          isRefreshing = false,
          auditBanner = "Audit complete (01:40:00) • Storage 50% used",
          onDismissAuditBanner = {},
          onRefreshAll = {},
          onNavigateToCleaner = {},
          onNavigateToToolkit = {},
          onNavigateToAudit = {},
          onNavigateToLab = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .assertIsDisplayed()
      .performScrollToNode(hasTestTag("audit_complete_banner"))
    composeTestRule.onNodeWithTag("audit_complete_banner").assertIsDisplayed()

    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("diagnostics_dashboard"))

    composeTestRule.onNodeWithTag("dashboard_storage_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Storage Status").assertIsDisplayed()
    composeTestRule.onNodeWithText("50% Used").assertIsDisplayed()

    composeTestRule.onNodeWithTag("dashboard_battery_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Battery Health").assertIsDisplayed()
    composeTestRule.onNodeWithText("86%").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Good")[0].assertIsDisplayed()

    composeTestRule.onNodeWithTag("battery_trend_chart_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("7-Day Battery Trend").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vico_battery_trend_chart").assertDoesNotExist()

    composeTestRule.onNodeWithTag("battery_trend_toggle").performClick()
    composeTestRule.onNodeWithTag("vico_battery_trend_chart")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `cleaner screen renders advanced system cleaner and cleanup report`() {
    val scanResult = CleanerDeepScanResult(
      scannedFolderName = null,
      categories = listOf(
        CleanerCategoryScan(
          category = CleanerCategoryType.OLD_LOG_FILES,
          items = listOf(
            CleanerCandidateItem(
              id = "1",
              name = "crash_2026.log",
              pathOrUri = "/cache/crash_2026.log",
              sizeBytes = 2048L,
              category = CleanerCategoryType.OLD_LOG_FILES
            )
          )
        )
      ),
      scannedAtMillis = 1000L
    )
    val report = CleanupReport(
      timestampMillis = 2000L,
      totalItemsRemoved = 3,
      totalBytesFreed = 4096L,
      quarantinedCount = 1,
      categories = listOf(
        CleanupCategoryReport(
          category = CleanerCategoryType.OLD_LOG_FILES,
          itemsRemoved = 3,
          bytesFreed = 4096L,
          cleanedNames = listOf("old_trace.log")
        )
      ),
      systemNote = "Cleaned successfully."
    )

    composeTestRule.setContent {
      SysPulseTheme(darkTheme = true) {
        CleanerScreen(
          storage = StorageSummary(totalBytes = 10000L, availableBytes = 5000L, usedBytes = 5000L),
          duplicateState = DuplicateScanState.Idle,
          cleanupFeedback = null,
          cleanerScanResult = scanResult,
          isScanningCleaner = false,
          cleanupReport = report,
          quarantineBeforeDelete = true,
          onDismissFeedback = {},
          onClearAppCache = {},
          onFolderPicked = {},
          onToggleDuplicate = { _, _ -> },
          onDeleteSelectedDuplicates = {},
          onResetDuplicateScan = {},
          onSpeakCleanupSummary = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("cleanup_report_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("advanced_cleaner_card")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `toolkit screen switches between hardware recovery and partitions`() {
    composeTestRule.setContent {
      SysPulseTheme(darkTheme = true) {
        ToolkitScreen(
          batteryHardware = BatteryHardwareSnapshot(
            levelPct = 90,
            healthLabel = "Good",
            temperatureC = 30.2f,
            voltageMv = 4200,
            testStatus = HardwareTestStatus.PASSED
          ),
          displayMetrics = DisplayMetricsSnapshot(widthPx = 1080, heightPx = 2400, densityDpi = 420),
          sensors = emptyList(),
          activeSensorType = null,
          activeSensorReading = null,
          storageVolumes = listOf(
            StorageVolumeDetail(
              id = "part_data",
              description = "User Data Partition (/data)",
              mountPath = "/data",
              state = "mounted (rw)",
              isPrimary = true,
              isRemovable = false,
              isReadOnly = false,
              totalBytes = 64_000_000_000L,
              freeBytes = 32_000_000_000L,
              blockSizeBytes = 4096L
            )
          ),
          recoveryScanState = RecoveryScanState.Idle,
          quarantineFiles = emptyList(),
          toolkitFeedback = null,
          onRunBatteryTest = {},
          onUpdateDisplayTestStatus = {},
          onStartSensorTest = {},
          onStopSensorTest = {},
          onStartRecoveryScan = {},
          onRecoverFileToDestination = { _, _ -> },
          onResetRecoveryScan = {},
          onRestoreQuarantinedFile = { _, _ -> },
          onPurgeQuarantinedFile = {},
          onRefreshToolkit = {},
          onDismissFeedback = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("toolkit_battery_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolkit_tab_recovery").performClick()
    composeTestRule.onNodeWithTag("toolkit_recovery_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolkit_tab_partitions").performClick()
    composeTestRule.onNodeWithTag("partition_volume_part_data").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolkit_tab_bootable_usb").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("toolkit_bootable_usb_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolkit_screen")
      .performScrollToNode(hasTestTag("boot_image_inspector_card"))
    composeTestRule.onNodeWithTag("boot_image_inspector_card").assertIsDisplayed()
  }

  @Test
  fun `boot header parser detects hybrid ISO-9660 El Torito and MBR signatures`() {
    val bytes = ByteArray(36864)
    // MBR 0x55 0xAA
    bytes[510] = 0x55.toByte()
    bytes[511] = 0xAA.toByte()
    // ISO-9660 PVD at 32768
    bytes[32768] = 0x01.toByte()
    "CD001".toByteArray().copyInto(bytes, 32769)
    "UBUNTU_24_04_LTS".toByteArray().copyInto(bytes, 32808)
    // El Torito Boot Record at 34816
    bytes[34816] = 0x00.toByte()
    "CD001".toByteArray().copyInto(bytes, 34817)
    "EL TORITO SPECIFICATION".toByteArray().copyInto(bytes, 34823)

    val parsed = HardwareToolkitRepository.parseBootHeaderBytes(
      headerBytes = bytes,
      fileName = "ubuntu-24.04-desktop-amd64.iso",
      sizeBytes = 5_200_000_000L,
      sha256Hex = "abc123"
    )

    assertTrue(parsed.isIso9660)
    assertTrue(parsed.hasElToritoBoot)
    assertTrue(parsed.hasMbrSignature)
    assertTrue(parsed.exceedsFat32Limit)
    assertEquals("UBUNTU_24_04_LTS", parsed.volumeLabel)
    assertEquals("Hybrid Bootable ISO (BIOS/MBR + UEFI/El Torito)", parsed.bootCompatibilityLabel)
  }
}
