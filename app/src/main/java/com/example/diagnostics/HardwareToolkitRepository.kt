package com.example.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.model.BatteryHardwareSnapshot
import com.example.model.BootImageInspection
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.DisplayMetricsSnapshot
import com.example.model.HardwareTestStatus
import com.example.model.LiveSensorReading
import com.example.model.RecoverableFileItem
import com.example.model.RecoveryScanState
import com.example.model.SensorDescriptor
import com.example.model.StorageVolumeDetail
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HardwareToolkitRepository(private val context: Context) {

    fun readBatteryHardwareSnapshot(evaluate: Boolean = false): BatteryHardwareSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { "Li-ion" } ?: "Li-ion"

        val pluggedInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedSource = when {
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "AC Adapter"
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB Port"
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Wireless"
            else -> "On Battery"
        }

        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusLabel = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Discharging"
        }

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthLabel = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNowUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE }
        val currentNowMa = currentNowUa?.let { it / 1000 }
        val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val chargeCounterMah = chargeCounterUah?.let { it / 1000 }

        val (testStatus, summary) = if (!evaluate) {
            HardwareTestStatus.NOT_RUN to "Tap Run Battery Diagnostic to verify voltage, temperature & health."
        } else {
            when {
                healthInt == BatteryManager.BATTERY_HEALTH_DEAD ||
                    healthInt == BatteryManager.BATTERY_HEALTH_OVERHEAT ||
                    healthInt == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ||
                    tempC >= 45f ->
                    HardwareTestStatus.FAILED to "Thermal or voltage fault detected ($healthLabel, ${tempC}°C, $voltage mV)."
                tempC >= 39f || pct <= 15 ->
                    HardwareTestStatus.ATTENTION to "Battery operational with elevated thermal or low-charge state (${tempC}°C, $pct%)."
                else ->
                    HardwareTestStatus.PASSED to "Cell health is $healthLabel at ${tempC}°C and $voltage mV."
            }
        }

        return BatteryHardwareSnapshot(
            levelPct = pct,
            healthLabel = healthLabel,
            pluggedSource = pluggedSource,
            technology = tech,
            temperatureC = tempC,
            voltageMv = voltage,
            currentNowMa = currentNowMa,
            chargeCounterMah = chargeCounterMah,
            statusLabel = statusLabel,
            testStatus = testStatus,
            summary = summary
        )
    }

    fun readDisplayMetricsSnapshot(previousStatus: HardwareTestStatus = HardwareTestStatus.NOT_RUN): DisplayMetricsSnapshot {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(dm)

        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull() ?: wm.defaultDisplay
        } else {
            wm.defaultDisplay
        }

        val refreshHz = display?.refreshRate ?: 60f
        val isHdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display?.isHdr == true
        } else {
            false
        }
        val isWideColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display?.isWideColorGamut == true
        } else {
            false
        }

        return DisplayMetricsSnapshot(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            densityDpi = dm.densityDpi,
            xdpi = dm.xdpi,
            ydpi = dm.ydpi,
            refreshRateHz = refreshHz,
            isHdrCapable = isHdr,
            isWideColorGamut = isWideColor,
            testStatus = previousStatus
        )
    }

    fun listHardwareSensors(): List<SensorDescriptor> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        return sm.getSensorList(Sensor.TYPE_ALL).map { sensor ->
            SensorDescriptor(
                type = sensor.type,
                name = sensor.name,
                vendor = sensor.vendor,
                powerMa = sensor.power,
                maxRange = sensor.maximumRange,
                typeLabel = mapSensorTypeLabel(sensor.type)
            )
        }
    }

    fun observeSensor(sensorType: Int): Flow<LiveSensorReading> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(sensorType)
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }

        var count = 0
        var lastAccuracy = "High"
        val unitLabel = when (sensorType) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
            Sensor.TYPE_GYROSCOPE -> "rad/s"
            Sensor.TYPE_MAGNETIC_FIELD -> "µT"
            Sensor.TYPE_LIGHT -> "lux"
            Sensor.TYPE_PROXIMITY -> "cm"
            Sensor.TYPE_PRESSURE -> "hPa"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
            else -> "units"
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                count++
                trySend(
                    LiveSensorReading(
                        sensorType = sensorType,
                        sensorName = sensor.name,
                        values = event.values.toList(),
                        unitLabel = unitLabel,
                        accuracyLabel = lastAccuracy,
                        sampleCount = count
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lastAccuracy = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                    else -> "Unreliable"
                }
            }
        }

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            sm.unregisterListener(listener)
        }
    }

    suspend fun readStorageVolumes(): List<StorageVolumeDetail> = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<StorageVolumeDetail>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sm.storageVolumes.forEachIndexed { idx, vol ->
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else if (vol.isPrimary) {
                    Environment.getExternalStorageDirectory()
                } else {
                    null
                }
                val stat = dir?.let { runCatching { StatFs(it.path) }.getOrNull() }
                val total = stat?.totalBytes ?: 0L
                val free = stat?.availableBytes ?: 0L
                val blockSize = stat?.blockSizeLong ?: 4096L
                volumes.add(
                    StorageVolumeDetail(
                        id = vol.uuid ?: "vol_$idx",
                        description = vol.getDescription(context),
                        mountPath = dir?.path ?: if (vol.isPrimary) "/storage/emulated/0" else "Removable Volume",
                        state = vol.state,
                        isPrimary = vol.isPrimary,
                        isRemovable = vol.isRemovable,
                        isReadOnly = vol.state != Environment.MEDIA_MOUNTED,
                        totalBytes = total,
                        freeBytes = free,
                        blockSizeBytes = blockSize
                    )
                )
            }
        }

        // Always include /data and /system partition geometry for partition transparency
        runCatching {
            val dataStat = StatFs(Environment.getDataDirectory().path)
            volumes.add(
                StorageVolumeDetail(
                    id = "part_data",
                    description = "User Data Partition (/data)",
                    mountPath = Environment.getDataDirectory().path,
                    state = "mounted (rw)",
                    isPrimary = false,
                    isRemovable = false,
                    isReadOnly = false,
                    totalBytes = dataStat.totalBytes,
                    freeBytes = dataStat.availableBytes,
                    blockSizeBytes = dataStat.blockSizeLong
                )
            )
        }
        runCatching {
            val sysStat = StatFs(Environment.getRootDirectory().path)
            volumes.add(
                StorageVolumeDetail(
                    id = "part_system",
                    description = "System OS Partition (/system)",
                    mountPath = Environment.getRootDirectory().path,
                    state = "mounted (ro)",
                    isPrimary = false,
                    isRemovable = false,
                    isReadOnly = true,
                    totalBytes = sysStat.totalBytes,
                    freeBytes = sysStat.availableBytes,
                    blockSizeBytes = sysStat.blockSizeLong
                )
            )
        }

        volumes
    }

    fun scanFolderForRecoverableFiles(treeUri: Uri): Flow<RecoveryScanState> = flow {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderName = rootDocId.substringAfterLast(':').ifBlank { "Selected Folder" }
        val candidates = mutableListOf<RecoverableFileItem>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to "")

        var scanned = 0
        while (queue.isNotEmpty() && scanned < 2000) {
            val (parentId, parentHint) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: "Unnamed"
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val size = cursor.getLong(sizeCol)
                    val modified = cursor.getLong(modCol)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val lowerDir = name.lowercase()
                        val dirHint = when {
                            lowerDir.contains("trash") || lowerDir.contains("recycle") -> "Trash Folder"
                            lowerDir.contains("thumb") -> "Thumbnail Cache"
                            lowerDir.contains("backup") -> "Backup Archive"
                            else -> parentHint
                        }
                        queue.add(docId to dirHint)
                    } else if (size > 0L) {
                        scanned++
                        if (scanned % 12 == 0) {
                            emit(RecoveryScanState.Scanning(scanned, name))
                        }
                        val lowerName = name.lowercase()
                        val reason = when {
                            lowerName.startsWith(".trashed-") -> "Android Soft-Deleted (.trashed)"
                            parentHint.isNotBlank() -> parentHint
                            lowerName.endsWith(".bak") || lowerName.endsWith(".old") -> "Backup / Renamed Artifact"
                            lowerName.endsWith(".tmp") || lowerName.endsWith(".part") -> "Partial / Temp File"
                            lowerName.startsWith(".") -> "Hidden File"
                            else -> ""
                        }
                        if (reason.isNotBlank()) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val signature = inspectMagicSignature(docUri)
                            candidates.add(
                                RecoverableFileItem(
                                    uriString = docUri.toString(),
                                    name = name,
                                    detectedSignature = signature,
                                    recoveryReason = reason,
                                    sizeBytes = size,
                                    lastModifiedMillis = modified,
                                    mimeType = mime
                                )
                            )
                        }
                    }
                }
            }
        }

        emit(
            RecoveryScanState.Results(
                folderName = folderName,
                totalScanned = scanned,
                candidates = candidates.sortedByDescending { it.lastModifiedMillis }
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun recoverDocumentToDestination(sourceUri: Uri, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    } ?: return@withContext false
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    private fun inspectMagicSignature(uri: Uri): String {
        return runCatching {
            val header = ByteArray(16)
            val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
            if (read < 4) return "Binary / Short ($read B)"

            when {
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() ->
                    "JPEG Image (FF D8 FF)"
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() ->
                    "PNG Image (89 50 4E 47)"
                header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte() ->
                    "PDF Document (%PDF)"
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() ->
                    "ZIP / Office Archive (PK)"
                read >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte() ->
                    "MP4 / ISO Media (ftyp)"
                header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte() ->
                    "MP3 Audio (ID3)"
                else -> "Raw Data (${header.take(4).joinToString(" ") { "%02X".format(it) }})"
            }
        }.getOrDefault("Unreadable Header")
    }

    private fun mapSensorTypeLabel(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
        Sensor.TYPE_LIGHT -> "Ambient Light"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_PRESSURE -> "Barometer"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Accel"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
        Sensor.TYPE_STEP_COUNTER -> "Step Counter"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
        else -> "Sensor #$type"
    }

    suspend fun inspectBootableUsbCapability(): BootableUsbCapability = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val hasUsbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val hasUsbAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val devices = runCatching { usbManager?.deviceList?.values?.toList().orEmpty() }.getOrDefault(emptyList())
        val massStorageCount = devices.count { dev ->
            dev.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
                (0 until dev.interfaceCount).any { idx ->
                    dev.getInterface(idx).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                }
        }

        val configFsDir = File("/config/usb_gadget")
        val configFsExists = runCatching { configFsDir.exists() }.getOrDefault(false)
        val configFsReadable = runCatching { configFsDir.canRead() && configFsDir.listFiles() != null }.getOrDefault(false)
        val massStorageFn = runCatching {
            val g1Funcs = File("/config/usb_gadget/g1/functions")
            val entries = g1Funcs.list()?.toList().orEmpty()
            entries.any { it.contains("mass_storage", ignoreCase = true) }
        }.getOrDefault(false)

        val suDetected = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/data/adb/ksu",
            "/data/adb/magisk"
        ).any { path -> runCatching { File(path).exists() }.getOrDefault(false) }

        val freeBytes = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)

        val (title, detail) = when {
            suDetected && massStorageFn ->
                "Kernel UMS Gadget Supported" to "mass_storage ConfigFS function and root paths detected. Direct ISO LUN binding is possible via rooted USB Gadget scripts."
            suDetected ->
                "Root Detected, No UMS Gadget Found" to "Stock kernels typically omit CONFIG_USB_CONFIGFS_MASS_STORAGE even on rooted devices; verify kernel config or use OTG flash media."
            else ->
                "Standard Android Sandbox (No Direct UMS Emulation)" to "SELinux enforces strict isolation on /config/usb_gadget and stock kernels disable mass_storage gadget. Use USB-C OTG external flash media (e.g., Ventoy) or stage & verify ISOs in SysDoctor."
        }

        BootableUsbCapability(
            hasUsbHostFeature = hasUsbHost,
            hasUsbAccessoryFeature = hasUsbAccessory,
            attachedUsbCount = devices.size,
            attachedMassStorageCount = massStorageCount,
            configFsPathExists = configFsExists,
            configFsReadable = configFsReadable,
            massStorageFunctionDetected = massStorageFn,
            suBinaryDetected = suDetected,
            stagingFreeBytes = freeBytes,
            verdictTitle = title,
            verdictDetail = detail
        )
    }

    fun inspectBootableImage(uri: Uri): Flow<BootImageInspectionState> = flow {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "image.iso"
        var reportedSize = -1L

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
            }
        }

        emit(BootImageInspectionState.Inspecting(displayName, 0L, reportedSize.coerceAtLeast(0L)))

        val headerWindow = ByteArray(36864) // First 36 KiB covers MBR (512B), GPT (1KiB), ISO PVD (32KiB), El Torito (34KiB)
        var headerFilled = 0
        var totalRead = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var lastEmitBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                if (headerFilled < headerWindow.size) {
                    val toCopy = minOf(n, headerWindow.size - headerFilled)
                    System.arraycopy(buffer, 0, headerWindow, headerFilled, toCopy)
                    headerFilled += toCopy
                }
                digest.update(buffer, 0, n)
                totalRead += n
                if (totalRead - lastEmitBytes >= 8L * 1024 * 1024) {
                    lastEmitBytes = totalRead
                    emit(BootImageInspectionState.Inspecting(displayName, totalRead, reportedSize.coerceAtLeast(totalRead)))
                }
            }
        } ?: throw IllegalStateException("Unable to open image stream.")

        val finalSize = if (reportedSize > 0L) reportedSize else totalRead
        val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
        val inspection = parseBootHeaderBytes(
            headerBytes = headerWindow.copyOf(headerFilled),
            fileName = displayName,
            sizeBytes = finalSize,
            sha256Hex = sha256Hex
        )
        emit(BootImageInspectionState.Ready(inspection))
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val FAT32_MAX_FILE_BYTES = 4_294_967_295L

        fun parseBootHeaderBytes(
            headerBytes: ByteArray,
            fileName: String,
            sizeBytes: Long,
            sha256Hex: String
        ): BootImageInspection {
            val hasMbr = headerBytes.size >= 512 &&
                headerBytes[510] == 0x55.toByte() &&
                headerBytes[511] == 0xAA.toByte()

            val hasGpt = headerBytes.size >= 520 &&
                String(headerBytes, 512, 8, StandardCharsets.US_ASCII) == "EFI PART"

            val isIso9660 = headerBytes.size >= 32774 &&
                headerBytes[32768] == 0x01.toByte() &&
                String(headerBytes, 32769, 5, StandardCharsets.US_ASCII) == "CD001"

            val volumeLabel = if (isIso9660 && headerBytes.size >= 32840) {
                String(headerBytes, 32808, 32, StandardCharsets.US_ASCII)
                    .trim { it <= ' ' }
                    .ifBlank { null }
            } else {
                null
            }

            val hasElTorito = headerBytes.size >= 34848 &&
                headerBytes[34816] == 0x00.toByte() &&
                String(headerBytes, 34817, 5, StandardCharsets.US_ASCII) == "CD001" &&
                String(headerBytes, 34823, 23, StandardCharsets.US_ASCII) == "EL TORITO SPECIFICATION"

            val exceedsFat32 = sizeBytes > FAT32_MAX_FILE_BYTES

            val compatibilityLabel = when {
                isIso9660 && hasElTorito && hasMbr -> "Hybrid Bootable ISO (BIOS/MBR + UEFI/El Torito)"
                isIso9660 && hasElTorito -> "El Torito Bootable ISO-9660"
                hasGpt && hasMbr -> "Hybrid GPT / MBR Disk Image"
                hasMbr -> "MBR Bootable Raw Image"
                isIso9660 -> "Data ISO-9660 (No El Torito Boot Record Found)"
                else -> "Unrecognized / Non-Bootable Header"
            }

            return BootImageInspection(
                fileName = fileName,
                sizeBytes = sizeBytes,
                isIso9660 = isIso9660,
                volumeLabel = volumeLabel,
                hasElToritoBoot = hasElTorito,
                hasMbrSignature = hasMbr,
                hasGptHeader = hasGpt,
                exceedsFat32Limit = exceedsFat32,
                sha256Hex = sha256Hex,
                bootCompatibilityLabel = compatibilityLabel
            )
        }
    }
}
