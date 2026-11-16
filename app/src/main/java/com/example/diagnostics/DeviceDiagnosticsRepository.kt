package com.example.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.model.AuditFinding
import com.example.model.DuplicateFileItem
import com.example.model.DuplicateGroup
import com.example.model.DuplicateScanState
import com.example.model.PartitionMountInfo
import com.example.model.SeverityLevel
import com.example.model.StorageSummary
import com.example.model.SystemIntegrityInfo
import com.example.model.UsbDeviceInfo
import com.example.model.WifiSecurityAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class DeviceDiagnosticsRepository(private val context: Context) {

    fun getStorageSummary(): StorageSummary {
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val used = (total - avail).coerceAtLeast(0L)

        val internalCache = dirSizeBytes(context.cacheDir)
        val externalCache = context.externalCacheDir?.let { dirSizeBytes(it) } ?: 0L
        val appFiles = dirSizeBytes(context.filesDir)

        return StorageSummary(
            totalBytes = total,
            availableBytes = avail,
            usedBytes = used,
            appCacheBytes = internalCache,
            externalCacheBytes = externalCache,
            appFilesBytes = appFiles
        )
    }

    suspend fun clearAppCache(): Long = withContext(Dispatchers.IO) {
        val before = dirSizeBytes(context.cacheDir) + (context.externalCacheDir?.let { dirSizeBytes(it) } ?: 0L)
        deleteDirContents(context.cacheDir)
        context.externalCacheDir?.let { deleteDirContents(it) }
        val after = dirSizeBytes(context.cacheDir) + (context.externalCacheDir?.let { dirSizeBytes(it) } ?: 0L)
        (before - after).coerceAtLeast(0L)
    }

    private fun dirSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                } else {
                    total += child.length()
                }
            }
        }
        return total
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteDirContents(child)
                child.delete()
            } else {
                child.delete()
            }
        }
    }

    private data class RawTreeFile(
        val uri: Uri,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val mimeType: String
    )

    fun scanFolderForDuplicates(treeUri: Uri): Flow<DuplicateScanState> = flow {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderName = rootDocId.substringAfterLast(':').ifBlank { "Selected Folder" }

        val collected = mutableListOf<RawTreeFile>()
        val queue = ArrayDeque<String>()
        queue.add(rootDocId)

        var enumerated = 0
        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
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
                        queue.add(docId)
                    } else if (size > 0L) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        collected.add(RawTreeFile(docUri, name, size, modified, mime))
                        enumerated++
                        if (enumerated % 15 == 0) {
                            emit(DuplicateScanState.Scanning(enumerated, 0, 0, name))
                        }
                    }
                }
            }
        }

        val sizeGroups = collected.groupBy { it.size }.filterValues { it.size > 1 }
        val totalCandidates = sizeGroups.size
        var hashedCount = 0

        val hashBuckets = mutableMapOf<String, MutableList<RawTreeFile>>()
        for ((_, filesWithSameSize) in sizeGroups) {
            for (file in filesWithSameSize) {
                emit(DuplicateScanState.Scanning(enumerated, totalCandidates, hashedCount, file.name))
                val hash = computeSha256(file.uri)
                hashedCount++
                if (hash != null) {
                    hashBuckets.getOrPut(hash) { mutableListOf() }.add(file)
                }
            }
        }

        val duplicateGroups = hashBuckets
            .filterValues { it.size > 1 }
            .entries
            .sortedByDescending { (_, files) -> files.first().size * (files.size - 1) }
            .map { (hash, files) ->
                val sorted = files.sortedBy { it.lastModified }
                DuplicateGroup(
                    hash = hash,
                    fileSizeBytes = sorted.first().size,
                    files = sorted.mapIndexed { idx, f ->
                        val keep = idx == 0
                        DuplicateFileItem(
                            uriString = f.uri.toString(),
                            name = f.name,
                            sizeBytes = f.size,
                            lastModified = f.lastModified,
                            mimeType = f.mimeType,
                            sha256Short = hash.take(12),
                            isSuggestedKeep = keep,
                            isSelectedForDelete = !keep
                        )
                    }
                )
            }

        emit(
            DuplicateScanState.Results(
                folderName = folderName,
                totalFilesScanned = collected.size,
                groups = duplicateGroups
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun computeSha256(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            context.contentResolver.openInputStream(uri)?.use { input ->
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteDuplicateUris(uris: List<String>): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedBytes = 0L
        for (uriStr in uris) {
            try {
                val uri = Uri.parse(uriStr)
                var fileSize = 0L
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fileSize = cursor.getLong(0)
                    }
                }
                if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    deletedCount++
                    freedBytes += fileSize
                }
            } catch (_: Exception) {
                // Skip locked or already deleted entries gracefully
            }
        }
        deletedCount to freedBytes
    }

    suspend fun auditWifiAndNetwork(): WifiSecurityAudit = withContext(Dispatchers.IO) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = hasFineLocation || hasCoarseLocation

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        if (activeNetwork == null || caps == null) {
            return@withContext WifiSecurityAudit(
                isConnected = false,
                transportLabel = "Offline",
                hasLocationPermission = hasLocation,
                auditScore = 0,
                findings = listOf(
                    AuditFinding(
                        "No Active Network",
                        "Connect to Wi-Fi or cellular data to perform a live network security audit.",
                        SeverityLevel.INFO
                    )
                )
            )
        }

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        val transportLabel = buildList {
            if (isWifi) add("Wi-Fi")
            if (isCellular) add("Cellular")
            if (isEthernet) add("Ethernet")
            if (isVpn) add("VPN Tunnel")
        }.joinToString(" + ").ifBlank { "Other" }

        val localIp = linkProps?.linkAddresses
            ?.mapNotNull { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
            ?: linkProps?.linkAddresses?.firstOrNull()?.address?.hostAddress
            ?: "—"

        val gatewayIp = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress
            ?: "—"

        val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val isPrivateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.isPrivateDnsActive == true
        } else {
            false
        }
        val privateDnsHost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.privateDnsServerName
        } else {
            null
        }

        var ssid = if (hasLocation) "Unknown SSID" else "Hidden (Grant location permission to reveal)"
        var bssid = if (hasLocation) "Unavailable" else "Redacted"
        var securityProtocol = if (isWifi) "WPA2/WPA3 (Check System Wi-Fi)" else "Carrier / Wired"
        var securitySeverity = SeverityLevel.INFO
        var freqMhz = 0
        var linkSpeed = 0
        var rssi = 0

        if (isWifi) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps.transportInfo as? WifiInfo) ?: @Suppress("DEPRECATION") wm.connectionInfo
            } else {
                @Suppress("DEPRECATION") wm.connectionInfo
            }

            if (wifiInfo != null) {
                freqMhz = wifiInfo.frequency
                linkSpeed = wifiInfo.linkSpeed
                rssi = wifiInfo.rssi
                if (hasLocation) {
                    val rawSsid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
                    if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                        ssid = rawSsid
                    }
                    val rawBssid = wifiInfo.bssid
                    if (!rawBssid.isNullOrBlank() && rawBssid != "02:00:00:00:00:00") {
                        bssid = rawBssid
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val secInfo = mapSecurityType(wifiInfo.currentSecurityType)
                    securityProtocol = secInfo.first
                    securitySeverity = secInfo.second
                }
            }
        }

        val bandLabel = when {
            freqMhz in 2400..2500 -> "2.4 GHz"
            freqMhz in 4900..5900 -> "5 GHz"
            freqMhz >= 5925 -> "6 GHz (Wi-Fi 6E/7)"
            else -> "—"
        }

        val gatewayLatency = if (gatewayIp != "—") measureReachabilityMs(gatewayIp, 80) ?: probePingMs(gatewayIp) else null
        val dnsLatency = dnsServers.firstOrNull()?.let { measureTcpOrReachabilityMs(it, 53) }

        val findings = mutableListOf<AuditFinding>()
        var score = 100

        if (isWifi) {
            when (securitySeverity) {
                SeverityLevel.ALERT -> {
                    score -= 40
                    findings.add(
                        AuditFinding(
                            "Weak or Open Wi-Fi Encryption ($securityProtocol)",
                            "Traffic on this Wi-Fi network can be intercepted locally. Switch to WPA3/WPA2 or enable a trusted VPN immediately.",
                            SeverityLevel.ALERT
                        )
                    )
                }
                SeverityLevel.CAUTION -> {
                    score -= 10
                    findings.add(
                        AuditFinding(
                            "Standard Encryption ($securityProtocol)",
                            "WPA2-PSK is widely compatible, though WPA3-SAE offers stronger protection against offline dictionary attacks.",
                            SeverityLevel.CAUTION
                        )
                    )
                }
                SeverityLevel.SAFE -> {
                    findings.add(
                        AuditFinding(
                            "Modern Encryption Active ($securityProtocol)",
                            "Your Wi-Fi link uses modern cryptographic authentication and key exchange.",
                            SeverityLevel.SAFE
                        )
                    )
                }
                SeverityLevel.INFO -> {
                    findings.add(
                        AuditFinding(
                            "Wi-Fi Security Standard",
                            "OS reported $securityProtocol. Confirm WPA2 or WPA3 in system Wi-Fi details on older API levels.",
                            SeverityLevel.INFO
                        )
                    )
                }
            }
        }

        if (isPrivateDns) {
            findings.add(
                AuditFinding(
                    "Encrypted Private DNS Enabled",
                    "DNS queries are encrypted via DNS-over-TLS${privateDnsHost?.let { " ($it)" } ?: ""}.",
                    SeverityLevel.SAFE
                )
            )
        } else {
            score -= 18
            findings.add(
                AuditFinding(
                    "Unencrypted DNS Resolution",
                    "Private DNS (DoT) is off. Local routers or ISPs may observe DNS lookups. Enable Private DNS in Android Network Settings.",
                    SeverityLevel.CAUTION
                )
            )
        }

        if (isVpn) {
            findings.add(
                AuditFinding(
                    "Active VPN Tunnel Detected",
                    "Outbound traffic is encapsulated inside an encrypted VPN interface.",
                    SeverityLevel.SAFE
                )
            )
        } else if (isWifi) {
            score -= 7
            findings.add(
                AuditFinding(
                    "No VPN Tunnel Active",
                    "On untrusted or public Wi-Fi networks, routing through a reputable VPN adds transport-layer protection.",
                    SeverityLevel.INFO
                )
            )
        }

        if (!hasLocation && isWifi) {
            findings.add(
                AuditFinding(
                    "SSID & BSSID Redacted by Android Privacy",
                    "Android hides Wi-Fi SSID and BSSID from apps until foreground Location permission is granted.",
                    SeverityLevel.INFO
                )
            )
        }

        WifiSecurityAudit(
            isConnected = true,
            transportLabel = transportLabel,
            isWifi = isWifi,
            ssid = ssid,
            bssid = bssid,
            hasLocationPermission = hasLocation,
            securityProtocol = securityProtocol,
            securitySeverity = securitySeverity,
            frequencyMhz = freqMhz,
            bandLabel = bandLabel,
            linkSpeedMbps = linkSpeed,
            rssiDbm = rssi,
            localIp = localIp,
            gatewayIp = gatewayIp,
            dnsServers = dnsServers,
            isPrivateDnsActive = isPrivateDns,
            privateDnsHost = privateDnsHost,
            isVpnActive = isVpn,
            gatewayLatencyMs = gatewayLatency,
            dnsLatencyMs = dnsLatency,
            auditScore = score.coerceIn(0, 100),
            findings = findings
        )
    }

    private fun mapSecurityType(type: Int): Pair<String, SeverityLevel> {
        return when (type) {
            WifiInfo.SECURITY_TYPE_OPEN -> "Open (Unencrypted)" to SeverityLevel.ALERT
            WifiInfo.SECURITY_TYPE_WEP -> "WEP (Broken Legacy)" to SeverityLevel.ALERT
            WifiInfo.SECURITY_TYPE_PSK -> "WPA2-PSK" to SeverityLevel.CAUTION
            WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise (EAP)" to SeverityLevel.SAFE
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3-SAE" to SeverityLevel.SAFE
            WifiInfo.SECURITY_TYPE_OWE -> "OWE (Enhanced Open)" to SeverityLevel.SAFE
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise" to SeverityLevel.SAFE
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise 192-bit" to SeverityLevel.SAFE
            else -> "Protected / Unknown ($type)" to SeverityLevel.INFO
        }
    }

    private fun measureTcpOrReachabilityMs(host: String, port: Int): Long? {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 550)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            probePingMs(host)
        }
    }

    private fun measureReachabilityMs(host: String, port: Int): Long? {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 450)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }

    private fun probePingMs(host: String): Long? {
        return try {
            val start = System.nanoTime()
            val reachable = InetAddress.getByName(host).isReachable(500)
            if (reachable) ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSystemIntegrityInfo(): SystemIntegrityInfo = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusInt == BatteryManager.BATTERY_STATUS_FULL
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

        val buildTags = Build.TAGS ?: "unknown"
        val isTestKeys = buildTags.contains("test-keys", ignoreCase = true)
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val cores = Runtime.getRuntime().availableProcessors()
        val (cpuPct, cpuSource) = readCpuTelemetry(cores)
        val partitions = readPartitionMounts()
        val usbDevices = readAttachedUsbDevices()

        SystemIntegrityInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Pre-M",
            buildType = Build.TYPE,
            buildTags = buildTags,
            kernelVersion = System.getProperty("os.version") ?: "Linux",
            cpuAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            cpuCores = cores,
            cpuUsagePct = cpuPct,
            cpuMetricSource = cpuSource,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            isLowMemory = memInfo.lowMemory,
            batteryLevelPct = pct,
            isBatteryCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            batteryVoltageMv = voltage,
            batteryHealthLabel = healthLabel,
            isTestKeys = isTestKeys,
            isDebuggableBuild = isDebuggable,
            partitions = partitions,
            usbDevices = usbDevices
        )
    }

    private fun readCpuTelemetry(cores: Int): Pair<Int?, String> {
        // 1. Try /proc/stat two-sample delta where SELinux permits
        try {
            val first = readProcStatTotals()
            if (first != null) {
                Thread.sleep(160)
                val second = readProcStatTotals()
                if (second != null) {
                    val totalDelta = second.first - first.first
                    val idleDelta = second.second - first.second
                    if (totalDelta > 0L) {
                        val usage = (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0)
                            .toInt().coerceIn(0, 100)
                        return usage to "Kernel /proc/stat"
                    }
                }
            }
        } catch (_: Exception) {
            // Restricted on modern Android SELinux
        }

        // 2. Fallback: read CPU frequency scaling across cores
        try {
            var totalRatio = 0.0
            var readableCores = 0
            for (core in 0 until cores) {
                val curFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                if (curFile.canRead() && maxFile.canRead()) {
                    val cur = curFile.readText().trim().toLongOrNull()
                    val max = maxFile.readText().trim().toLongOrNull()
                    if (cur != null && max != null && max > 0L) {
                        totalRatio += (cur.toDouble() / max.toDouble()).coerceIn(0.0, 1.0)
                        readableCores++
                    }
                }
            }
            if (readableCores > 0) {
                val avgPct = ((totalRatio / readableCores) * 100.0).toInt().coerceIn(0, 100)
                return avgPct to "Clock Scaling ($readableCores cores)"
            }
        } catch (_: Exception) {
            // Restricted on some vendor kernels
        }

        // 3. Fallback: measure app process CPU utilization ratio cleanly
        return try {
            val cpuStart = android.os.Process.getElapsedCpuTime()
            val wallStart = android.os.SystemClock.elapsedRealtime()
            Thread.sleep(100)
            val cpuDelta = (android.os.Process.getElapsedCpuTime() - cpuStart).coerceAtLeast(0L)
            val wallDelta = (android.os.SystemClock.elapsedRealtime() - wallStart).coerceAtLeast(1L)
            val pct = ((cpuDelta.toDouble() / (wallDelta * cores.coerceAtLeast(1)).toDouble()) * 100.0)
                .toInt().coerceIn(0, 100)
            pct to "Process CPU Load"
        } catch (_: Exception) {
            null to "Sandbox Restricted"
        }
    }

    private fun readProcStatTotals(): Pair<Long, Long>? {
        val file = File("/proc/stat")
        if (!file.canRead()) return null
        val line = file.bufferedReader().use { it.readLine() } ?: return null
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size < 5 || tokens[0] != "cpu") return null
        val values = tokens.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        val total = values.sum()
        return total to idle
    }

    private fun readPartitionMounts(): List<PartitionMountInfo> {
        val targetDirs = listOf(
            "/" to "System Root",
            Environment.getRootDirectory().path to "System (/system)",
            Environment.getDataDirectory().path to "User Data (/data)"
        )
        val mountLookup = mutableMapOf<String, Pair<String, String>>()
        try {
            File("/proc/mounts").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(' ')
                    if (parts.size >= 4) {
                        val mountPoint = parts[1]
                        val fs = parts[2]
                        val opts = parts[3]
                        val access = if (opts.split(',').contains("ro")) "Read-Only (ro)" else "Read-Write (rw)"
                        mountLookup[mountPoint] = fs to access
                    }
                }
            }
        } catch (_: Exception) {
            // SELinux may restrict /proc/mounts on some vendor ROMs; fall back cleanly to StatFs
        }

        return targetDirs.mapNotNull { (path, label) ->
            try {
                val stat = StatFs(path)
                val lookup = mountLookup[path]
                PartitionMountInfo(
                    mountPoint = label,
                    fileSystem = lookup?.first ?: "ext4/f2fs",
                    accessMode = lookup?.second ?: if (path == Environment.getDataDirectory().path) "Read-Write (App Sandbox)" else "Read-Only (Verified)",
                    totalBytes = stat.totalBytes,
                    freeBytes = stat.availableBytes
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readAttachedUsbDevices(): List<UsbDeviceInfo> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return try {
            usbManager.deviceList.values.map { dev ->
                val classLabel = when (dev.deviceClass) {
                    UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage (Drive)"
                    UsbConstants.USB_CLASS_HUB -> "USB Hub"
                    UsbConstants.USB_CLASS_HID -> "HID Input"
                    UsbConstants.USB_CLASS_COMM -> "Communications / CDC"
                    UsbConstants.USB_CLASS_VIDEO -> "Video Device"
                    UsbConstants.USB_CLASS_AUDIO -> "Audio Device"
                    UsbConstants.USB_CLASS_PER_INTERFACE -> "Composite / Per-Interface"
                    else -> "Class ${dev.deviceClass}"
                }
                UsbDeviceInfo(
                    deviceName = dev.deviceName,
                    productName = dev.productName ?: "USB Device",
                    vendorIdHex = "0x%04X".format(dev.vendorId),
                    productIdHex = "0x%04X".format(dev.productId),
                    deviceClassLabel = classLabel,
                    interfaceCount = dev.interfaceCount
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
