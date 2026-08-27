package com.pockethub.util

import java.util.Date

/** Human-readable file size, largest unit first (GB → B). */
fun humanBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Parse an ISO-8601 timestamp; falls back to [Date()] on malformed input. */
fun parseIso(iso: String): Date = parseIsoSafe(iso) ?: Date()

/** Parse an ISO-8601 timestamp, or null if malformed. */
fun parseIsoSafe(iso: String): Date? = runCatching {
    Date.from(java.time.OffsetDateTime.parse(iso.trim().replace(" ", "T")).toInstant())
}.getOrNull()
