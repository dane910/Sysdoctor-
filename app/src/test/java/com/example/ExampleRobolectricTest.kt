package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.diagnostics.FileBrowserRepository
import com.example.diagnostics.HardwareToolkitRepository
import com.example.model.BatteryHardwareSnapshot
import com.example.model.BatteryTrendMetric
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.BrowserEntry
import com.example.model.CleanerCandidateItem
import com.example.model.CleanerCategoryScan
import com.example.model.CleanerCategoryType
import com.example.model.CleanerDeepScanResult
import com.example.model.CleanupCategoryReport
import com.example.model.CleanupReport
import com.example.model.DeepFileScanState
import com.example.model.DeepFindingCategory
import com.example.model.DeepScanFinding
import com.example.model.DisplayMetricsSnapshot
import com.example.model.DuplicateScanState
import com.example.model.FileBrowserUiState
import com.example.model.FilePreviewContent
import com.example.model.FilePreviewState
import com.example.model.HardwareTestStatus
import com.example.model.RecoveryScanState
import com.example.model.SeverityLevel
import com.example.model.StorageBreadcrumb
import com.example.model.StorageRootKind
import com.example.model.StorageSummary
import com.example.model.StorageVolumeDetail
import com.example.model.SystemIntegrityInfo
import com.example.model.ToolkitCategory
import com.example.model.WifiSecurityAudit
import com.example.ui.components.toolkit.FileNavigatorCard
import com.example.ui.components.toolkit.FilePreviewModal
import com.example.ui.components.toolkit.UnifiedDeepScanCard
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
  fun `dashboard displays collapsible telemetry and collapsible graphs`() {
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

    var openedCategory: ToolkitCategory? = null
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
          onOpenToolkitCategory = { openedCategory = it }
        )
      }
    }

    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .assertIsDisplayed()
      .performScrollToNode(hasTestTag("audit_complete_banner"))
    composeTestRule.onNodeWithTag("audit_complete_banner").assertIsDisplayed()

    // Expandable telemetry & graphs default to collapsed, while Quick Toolkit is expanded on startup
    composeTestRule.onNodeWithTag("dashboard_storage_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("quick_cleaner_card"))
    composeTestRule.onNodeWithTag("quick_cleaner_card").assertIsDisplayed()

    // Expand telemetry section and verify clickable storage & battery cards appear
    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("dashboard_telemetry_toggle"))
    composeTestRule.onNodeWithTag("dashboard_telemetry_toggle").performClick()

    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("dashboard_storage_card"))
    composeTestRule.onNodeWithTag("dashboard_storage_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Storage Status").assertIsDisplayed()
    composeTestRule.onNodeWithText("50% Used").assertIsDisplayed()

    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("dashboard_battery_card"))
    composeTestRule.onNodeWithTag("dashboard_battery_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Battery Health").assertIsDisplayed()
    composeTestRule.onNodeWithText("86%").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Good")[0].assertIsDisplayed()

    // Tapping storage card navigates to Cleaner / Storage File Explorer
    composeTestRule.onNodeWithTag("sysdoctor_entry_dashboard")
      .performScrollToNode(hasTestTag("dashboard_storage_card"))
    composeTestRule.onNodeWithTag("dashboard_storage_card").performClick()
    assertEquals(ToolkitCategory.CLEANER, openedCategory)

    // Expand Graphs section cleanly
    composeTestRule.onNodeWithTag("dashboard_graphs_toggle")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()
  }

  @Test
  fun `toolkit category list expands with scan and repair actions`() {
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
        var selectedCategory by remember { mutableStateOf<ToolkitCategory?>(null) }
        ToolkitScreen(
          selectedCategory = selectedCategory,
          onSelectCategory = { selectedCategory = it },
          storage = StorageSummary(totalBytes = 10000L, availableBytes = 5000L, usedBytes = 5000L),
          wifi = WifiSecurityAudit(isConnected = true, transportLabel = "Wi-Fi", securityProtocol = "WPA3-SAE", auditScore = 92),
          systemInfo = SystemIntegrityInfo(manufacturer = "Google", model = "Pixel 9"),
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
          duplicateState = DuplicateScanState.Idle,
          cleanupFeedback = null,
          cleanerScanResult = scanResult,
          isScanningCleaner = false,
          cleanupReport = report,
          quarantineBeforeDelete = true,
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
          onToggleCleanerCategory = {},
          onToggleQuarantineBeforeDelete = {},
          onRescanCleaner = {},
          onExecuteAdvancedClean = {},
          onDismissCleanupReport = {},
          onDismissCleanupFeedback = {},
          onClearAppCache = {},
          onFolderPickedForDuplicates = {},
          onToggleDuplicate = { _, _ -> },
          onDeleteSelectedDuplicates = {},
          onResetDuplicateScan = {},
          onSpeakCleanupSummary = {},
          onRefreshNetwork = {},
          onRefreshToolkit = {},
          onDismissFeedback = {},
          bootableUsbCapability = BootableUsbCapability(hasUsbHostFeature = true),
          bootImageInspectionState = BootImageInspectionState.Idle
        )
      }
    }

    // Category list visible
    composeTestRule.onNodeWithTag("toolkit_category_list").assertIsDisplayed()
    // Tap diagnostics category to expand action buttons
    composeTestRule.onNodeWithTag("toolkit_category_diagnostics").performClick()
    composeTestRule.onNodeWithTag("expand_action_scan_diagnostics").assertIsDisplayed()
    composeTestRule.onNodeWithTag("expand_action_repair_diagnostics").assertIsDisplayed()
    composeTestRule.onNodeWithTag("expand_action_open_diagnostics").performClick()
    composeTestRule.onNodeWithTag("toolkit_battery_card").assertIsDisplayed()
  }

  @Test
  fun `boot header parser detects hybrid ISO-9660 El Torito and MBR signatures`() {
    val bytes = ByteArray(36864)
    bytes[510] = 0x55.toByte()
    bytes[511] = 0xAA.toByte()
    bytes[32768] = 0x01.toByte()
    "CD001".toByteArray().copyInto(bytes, 32769)
    "UBUNTU_24_04_LTS".toByteArray().copyInto(bytes, 32808)
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

  @Test
  fun `deep scan signature analyzer detects disguised malware corrupted headers and safe files`() {
    val elfHeader = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01)
    val elfDisguised = FileBrowserRepository.analyzeHeaderAndName("statement.pdf", 2048L, elfHeader)
    assertEquals(DeepFindingCategory.THREAT_SUSPICIOUS, elfDisguised.findingCategory)
    assertEquals(SeverityLevel.ALERT, elfDisguised.severity)

    val doubleExt = FileBrowserRepository.analyzeHeaderAndName("receipt.pdf.apk", 4096L, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    assertEquals(DeepFindingCategory.THREAT_SUSPICIOUS, doubleExt.findingCategory)

    val badJpg = FileBrowserRepository.analyzeHeaderAndName("broken.jpg", 128L, "not a jpeg".toByteArray())
    assertEquals(DeepFindingCategory.CORRUPTED, badJpg.findingCategory)
    assertEquals(SeverityLevel.CAUTION, badJpg.severity)

    val zeroByte = FileBrowserRepository.analyzeHeaderAndName("empty.log", 0L, ByteArray(0))
    assertEquals(DeepFindingCategory.CORRUPTED, zeroByte.findingCategory)

    val validJpg = FileBrowserRepository.analyzeHeaderAndName("photo.jpg", 5000L, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
    assertEquals(null, validJpg.findingCategory)
    assertEquals(SeverityLevel.SAFE, validJpg.severity)
  }

  @Test
  fun `file navigator unified deep scan and preview modal render and filter interactively`() {
    val browserState = FileBrowserUiState(
      rootKind = StorageRootKind.APP_FILES,
      rootLabel = "App Files",
      breadcrumbs = listOf(StorageBreadcrumb("/files", "App Files", "/files", false)),
      entries = listOf(
        BrowserEntry("dir1", "logs", "/files/logs", true, 2L, 1000L, "vnd.android.document/directory", false),
        BrowserEntry("f1", "notes.txt", "/files/notes.txt", false, 512L, 2000L, "text/plain", false)
      )
    )
    val deepResults = DeepFileScanState.Results(
      scopeLabel = "App Scoped Storage",
      totalFilesScanned = 12,
      findings = listOf(
        DeepScanFinding(
          id = "t1",
          name = "payload.pdf.apk",
          pathOrUri = "/files/payload.pdf.apk",
          sizeBytes = 2048L,
          lastModifiedMillis = 1000L,
          mimeType = "application/vnd.android.package-archive",
          isSafDocument = false,
          category = DeepFindingCategory.THREAT_SUSPICIOUS,
          severity = SeverityLevel.ALERT,
          verdictTitle = "Double-Extension Payload",
          reasonDetail = "Suspicious spoofed extension",
          isSelectedForDelete = true
        ),
        DeepScanFinding(
          id = "c1",
          name = "broken.jpg",
          pathOrUri = "/files/broken.jpg",
          sizeBytes = 64L,
          lastModifiedMillis = 1000L,
          mimeType = "image/jpeg",
          isSafDocument = false,
          category = DeepFindingCategory.CORRUPTED,
          severity = SeverityLevel.CAUTION,
          verdictTitle = "Malformed Header",
          reasonDetail = "Expected JPEG header",
          isSelectedForDelete = true
        )
      )
    )
    val previewState = FilePreviewState.Ready(
      id = "f1",
      name = "notes.txt",
      pathOrUri = "/files/notes.txt",
      sizeBytes = 512L,
      lastModifiedMillis = 2000L,
      mimeType = "text/plain",
      isSafDocument = false,
      detectedFormat = "UTF-8 / Text",
      integritySeverity = SeverityLevel.SAFE,
      integrityVerdict = "Verified File Integrity",
      integrityDetail = "Clean UTF-8 text",
      sha256Hex = "a1b2c3d4e5f6",
      content = FilePreviewContent.Text("SysDoctor diagnostic sample line", truncated = false)
    )

    composeTestRule.setContent {
      SysPulseTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Column {
          FileNavigatorCard(
            state = browserState,
            onSelectRoot = {},
            onSelectMountedSafRoot = {},
            onPickSafFolder = {},
            onNavigateBreadcrumb = {},
            onOpenFolder = {},
            onPreviewFile = {},
            onDeleteEntry = {},
            onChangeSort = {},
            onRefreshDirectory = {}
          )
          UnifiedDeepScanCard(
            scanState = deepResults,
            quarantineBeforeDelete = true,
            onToggleQuarantine = {},
            onStartAppDeepScan = {},
            onPickSafFolderForDeepScan = {},
            onSelectFilter = {},
            onToggleFindingSelection = {},
            onSelectAllFiltered = {},
            onInspectFinding = {},
            onDeleteSelected = {},
            onResetScan = {}
          )
          FilePreviewModal(
            state = previewState,
            onDismiss = {},
            onQuarantineFile = {},
            onDeleteFile = {}
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("file_navigator_card").assertExists()
    composeTestRule.onNodeWithTag("browser_entry_notes.txt").assertExists()
    composeTestRule.onNodeWithTag("unified_deep_scan_card").assertExists()
    composeTestRule.onNodeWithTag("deep_finding_row_payload.pdf.apk").assertExists()
    composeTestRule.onNodeWithTag("file_preview_modal").assertIsDisplayed()
    composeTestRule.onNodeWithText("SysDoctor diagnostic sample line").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_quarantine_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_delete_button").assertIsDisplayed()
  }
}
