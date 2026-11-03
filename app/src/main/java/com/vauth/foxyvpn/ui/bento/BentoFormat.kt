package com.vauth.foxyvpn.ui.bento

/**
 * Display formatting for the Bento surfaces — Phase B1 of the Zed
 * overhaul. Values follow the reference design's format rules
 * (1024-based, one decimal under GB, two at GB+); the data layer's own
 * formatter (notifications, service) is untouched.
 */

fun formatRate(bytesPerSecond: Long): Pair<String, String> = when {
    bytesPerSecond <= 0 -> "0" to "B/s"
    bytesPerSecond < 1_024 -> "%d".format(bytesPerSecond) to "B/s"
    bytesPerSecond < 1_048_576 -> {
        val kb = bytesPerSecond / 1024.0
        (if (kb < 10) "%.1f".format(kb) else "%d".format(kb.toInt())) to "KB/s"
    }
    bytesPerSecond < 1_073_741_824 -> "%.1f".format(bytesPerSecond / 1_048_576.0) to "MB/s"
    else -> "%.2f".format(bytesPerSecond / 1_073_741_824.0) to "GB/s"
}

fun formatBytesBento(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1_024 -> "%d B".format(bytes)
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.2f GB".format(bytes / 1_073_741_824.0)
}

fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
