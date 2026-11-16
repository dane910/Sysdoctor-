package com.example.diagnostics

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIdx = 0
    while (value >= 1024.0 && unitIdx < units.lastIndex) {
        value /= 1024.0
        unitIdx++
    }
    return if (unitIdx <= 1) {
        String.format(Locale.US, "%.0f %s", value, units[unitIdx])
    } else {
        String.format(Locale.US, "%.2f %s", value, units[unitIdx])
    }
}
