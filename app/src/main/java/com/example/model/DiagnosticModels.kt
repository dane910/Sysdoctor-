package com.example.model

enum class SeverityLevel {
    SAFE,
    INFO,
    CAUTION,
    ALERT
}

enum class ToolkitCategory(
    val title: String,
    val subtitle: String
) {
    DIAGNOSTICS(
        title = "Hardware Diagnostics",
        subtitle = "Battery health test, display pixel/touch check & live sensors"
    ),
    USB_TOOLS(
        title = "USB Tools & Bootable OTG",
        subtitle = "Attached USB OTG devices, bootable USB probe & .ISO/.IMG inspector"
    ),
    CLEANER(
        title = "Storage Cleaner & Duplicates",
        subtitle = "System/app cache, old logs, residual files & SHA-256 duplicate finder"
    ),
    NETWORK_AUDIT(
        title = "Network & System Audit",
        subtitle = "Wi-Fi WPA3/DNS/VPN posture, OS integrity & storage partitions"
    ),
    RECOVERY(
        title = "File Recovery & Quarantine",
        subtitle = "SAF .trashed / header signature scanner & quarantine vault"
    )
}

data class StorageSummary(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val appCacheBytes: Long = 0L,
    val externalCacheBytes: Long = 0L,
    val appFilesBytes: Long = 0L
) {
    val totalAppCacheBytes: Long get() = appCacheBytes + externalCacheBytes
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DuplicateFileItem(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val sha256Short: String,
    val isSuggestedKeep: Boolean,
    val isSelectedForDelete: Boolean
)

data class DuplicateGroup(
    val hash: String,
    val fileSizeBytes: Long,
    val files: List<DuplicateFileItem>
) {
    val wastedBytes: Long get() = fileSizeBytes * (files.size - 1).coerceAtLeast(0)
}

sealed interface DuplicateScanState {
    data object Idle : DuplicateScanState
    data class Scanning(
        val filesEnumerated: Int,
        val candidateGroups: Int,
        val filesHashed: Int,
        val currentFileName: String
    ) : DuplicateScanState

    data class Results(
        val folderName: String,
        val totalFilesScanned: Int,
        val groups: List<DuplicateGroup>,
        val lastFreedBytes: Long = 0L,
        val lastDeletedCount: Int = 0
    ) : DuplicateScanState {
        val totalWastedBytes: Long get() = groups.sumOf { it.wastedBytes }
        val selectedDeleteCount: Int get() = groups.sumOf { g -> g.files.count { it.isSelectedForDelete } }
        val selectedDeleteBytes: Long get() = groups.sumOf { g -> g.files.filter { it.isSelectedForDelete }.sumOf { it.sizeBytes } }
    }

    data class Error(val message: String) : DuplicateScanState
}

enum class CleanerCategoryType(val title: String, val subtitle: String) {
    SYSTEM_AND_APP_CACHE(
        "System & App Cache",
        "Internal cache, external cache, compiled shader/code cache"
    ),
    OLD_LOG_FILES(
        "Old Log & Trace Files",
        "Stale .log, .anr, .crash, and diagnostic dump artifacts"
    ),
    RESIDUAL_APP_DATA(
        "Residual & Temp App Data",
        "Orphaned .tmp, .bak, .part files and empty leftover directories"
    )
}

data class CleanerCandidateItem(
    val id: String,
    val name: String,
    val pathOrUri: String,
    val sizeBytes: Long,
    val category: CleanerCategoryType,
    val isSafDocument: Boolean = false
)

data class CleanerCategoryScan(
    val category: CleanerCategoryType,
    val items: List<CleanerCandidateItem>,
    val isSelected: Boolean = true
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val itemCount: Int get() = items.size
}

data class CleanerDeepScanResult(
    val scannedFolderName: String?,
    val categories: List<CleanerCategoryScan>,
    val scannedAtMillis: Long
) {
    val totalReclaimableBytes: Long get() = categories.sumOf { it.totalBytes }
    val selectedReclaimableBytes: Long get() = categories.filter { it.isSelected }.sumOf { it.totalBytes }
    val selectedItemCount: Int get() = categories.filter { it.isSelected }.sumOf { it.itemCount }
}

data class CleanupCategoryReport(
    val category: CleanerCategoryType,
    val itemsRemoved: Int,
    val bytesFreed: Long,
    val cleanedNames: List<String>
)

data class CleanupReport(
    val timestampMillis: Long,
    val totalItemsRemoved: Int,
    val totalBytesFreed: Long,
    val quarantinedCount: Int,
    val categories: List<CleanupCategoryReport>,
    val systemNote: String
)

data class QuarantinedFile(
    val id: String,
    val originalName: String,
    val category: String,
    val sizeBytes: Long,
    val quarantinedAtMillis: Long,
    val filePath: String
)

data class AuditFinding(
    val title: String,
    val detail: String,
    val severity: SeverityLevel
)

data class WifiSecurityAudit(
    val isConnected: Boolean = false,
    val transportLabel: String = "Disconnected",
    val isWifi: Boolean = false,
    val ssid: String = "Hidden (Grant location permission to reveal)",
    val bssid: String = "Unavailable",
    val hasLocationPermission: Boolean = false,
    val securityProtocol: String = "Unknown",
    val securitySeverity: SeverityLevel = SeverityLevel.INFO,
    val frequencyMhz: Int = 0,
    val bandLabel: String = "—",
    val linkSpeedMbps: Int = 0,
    val rssiDbm: Int = 0,
    val localIp: String = "—",
    val gatewayIp: String = "—",
    val dnsServers: List<String> = emptyList(),
    val isPrivateDnsActive: Boolean = false,
    val privateDnsHost: String? = null,
    val isVpnActive: Boolean = false,
    val gatewayLatencyMs: Long? = null,
    val dnsLatencyMs: Long? = null,
    val auditScore: Int = 0,
    val findings: List<AuditFinding> = emptyList()
)

data class PartitionMountInfo(
    val mountPoint: String,
    val fileSystem: String,
    val accessMode: String,
    val totalBytes: Long,
    val freeBytes: Long
)

data class StorageVolumeDetail(
    val id: String,
    val description: String,
    val mountPath: String,
    val state: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isReadOnly: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
    val blockSizeBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class UsbDeviceInfo(
    val deviceName: String,
    val productName: String,
    val vendorIdHex: String,
    val productIdHex: String,
    val deviceClassLabel: String,
    val interfaceCount: Int
)

data class SystemIntegrityInfo(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val securityPatch: String = "",
    val buildType: String = "",
    val buildTags: String = "",
    val kernelVersion: String = "",
    val cpuAbis: String = "",
    val cpuCores: Int = 0,
    val cpuUsagePct: Int? = null,
    val cpuMetricSource: String = "Frequency Scaling",
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val isLowMemory: Boolean = false,
    val batteryLevelPct: Int = 0,
    val isBatteryCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val batteryHealthLabel: String = "Unknown",
    val isTestKeys: Boolean = false,
    val isDebuggableBuild: Boolean = false,
    val partitions: List<PartitionMountInfo> = emptyList(),
    val usbDevices: List<UsbDeviceInfo> = emptyList()
) {
    val usedRamBytes: Long get() = (totalRamBytes - availableRamBytes).coerceAtLeast(0L)
    val ramUsedFraction: Float
        get() = if (totalRamBytes > 0L) (usedRamBytes.toFloat() / totalRamBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class HardwareTestStatus(val label: String) {
    NOT_RUN("Not Run"),
    PASSED("Passed"),
    ATTENTION("Attention"),
    FAILED("Issue Detected")
}

data class BatteryHardwareSnapshot(
    val levelPct: Int = 0,
    val healthLabel: String = "Unknown",
    val pluggedSource: String = "Battery",
    val technology: String = "Li-ion",
    val temperatureC: Float = 0f,
    val voltageMv: Int = 0,
    val currentNowMa: Int? = null,
    val chargeCounterMah: Int? = null,
    val statusLabel: String = "Discharging",
    val testStatus: HardwareTestStatus = HardwareTestStatus.NOT_RUN,
    val summary: String = "Run test to evaluate thermal, voltage, and current health."
)

data class DisplayMetricsSnapshot(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val densityDpi: Int = 0,
    val xdpi: Float = 0f,
    val ydpi: Float = 0f,
    val refreshRateHz: Float = 60f,
    val isHdrCapable: Boolean = false,
    val isWideColorGamut: Boolean = false,
    val testStatus: HardwareTestStatus = HardwareTestStatus.NOT_RUN
)

data class SensorDescriptor(
    val type: Int,
    val name: String,
    val vendor: String,
    val powerMa: Float,
    val maxRange: Float,
    val typeLabel: String
)

data class LiveSensorReading(
    val sensorType: Int,
    val sensorName: String,
    val values: List<Float>,
    val unitLabel: String,
    val accuracyLabel: String,
    val sampleCount: Int
)

data class RecoverableFileItem(
    val uriString: String,
    val name: String,
    val detectedSignature: String,
    val recoveryReason: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String
)

sealed interface RecoveryScanState {
    data object Idle : RecoveryScanState
    data class Scanning(val filesScanned: Int, val currentFileName: String) : RecoveryScanState
    data class Results(
        val folderName: String,
        val totalScanned: Int,
        val candidates: List<RecoverableFileItem>
    ) : RecoveryScanState
    data class Error(val message: String) : RecoveryScanState
}

data class BootableUsbCapability(
    val hasUsbHostFeature: Boolean = false,
    val hasUsbAccessoryFeature: Boolean = false,
    val attachedUsbCount: Int = 0,
    val attachedMassStorageCount: Int = 0,
    val configFsPathExists: Boolean = false,
    val configFsReadable: Boolean = false,
    val massStorageFunctionDetected: Boolean = false,
    val suBinaryDetected: Boolean = false,
    val stagingFreeBytes: Long = 0L,
    val verdictTitle: String = "Sandbox Probe Pending",
    val verdictDetail: String = ""
)

data class BootImageInspection(
    val fileName: String,
    val sizeBytes: Long,
    val isIso9660: Boolean,
    val volumeLabel: String?,
    val hasElToritoBoot: Boolean,
    val hasMbrSignature: Boolean,
    val hasGptHeader: Boolean,
    val exceedsFat32Limit: Boolean,
    val sha256Hex: String,
    val bootCompatibilityLabel: String
)

sealed interface BootImageInspectionState {
    data object Idle : BootImageInspectionState
    data class Inspecting(val fileName: String, val bytesProcessed: Long, val totalBytes: Long) : BootImageInspectionState
    data class Ready(val inspection: BootImageInspection) : BootImageInspectionState
    data class Error(val message: String) : BootImageInspectionState
}

enum class FeasibilityLevel(val label: String) {
    STANDARD_SANDBOX("Possible in Standard Sandbox"),
    ADB_OR_OEM("Requires ADB / Fastboot / OEM Tools"),
    REQUIRES_ROOT_KERNEL("Requires Unlocked Bootloader & Kernel/Root")
}

data class ArchitectureTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val feasibility: FeasibilityLevel,
    val summary: String,
    val sandboxReality: String,
    val actionableSteps: List<String>,
    val speechScript: String
)

enum class TtsVoiceOption(val voiceId: String, val displayName: String, val tone: String) {
    KORE("Kore", "Kore", "Crisp & Balanced"),
    PUCK("Puck", "Puck", "Energetic & Clear"),
    CHARON("Charon", "Charon", "Deep & Technical"),
    AOEDE("Aoede", "Aoede", "Warm & Analytical"),
    FENRIR("Fenrir", "Fenrir", "Bold & Direct")
}

sealed interface TtsPlaybackState {
    data object Idle : TtsPlaybackState
    data class Generating(val label: String, val voice: TtsVoiceOption) : TtsPlaybackState
    data class Playing(val label: String, val voice: TtsVoiceOption) : TtsPlaybackState
    data class Paused(val label: String, val voice: TtsVoiceOption) : TtsPlaybackState
    data class Error(val message: String) : TtsPlaybackState
}

sealed interface AdvisorUiState {
    data object Idle : AdvisorUiState
    data object Loading : AdvisorUiState
    data class Streaming(val text: String) : AdvisorUiState
    data class Success(val text: String) : AdvisorUiState
    data class Error(val message: String) : AdvisorUiState
}

enum class BatteryTrendMetric(val label: String, val unit: String) {
    HEALTH_SCORE("Health", "pts"),
    TEMPERATURE("Temp", "°C"),
    CHARGE_LEVEL("Charge", "%"),
    VOLTAGE("Voltage", "V")
}

data class BatteryTrendPoint(
    val epochDay: Long,
    val dayIndex: Int,
    val dayLabel: String,
    val dateLabel: String,
    val hasSample: Boolean,
    val healthScore: Int,
    val avgTempC: Float,
    val maxTempC: Float,
    val levelPct: Int,
    val voltageMv: Int,
    val healthLabel: String,
    val sampleCount: Int
)
