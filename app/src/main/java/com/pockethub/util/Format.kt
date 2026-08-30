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

/**
 * GitHub-style relative time, e.g. "now", "3 minutes ago", "2 months ago".
 * Falls back to the original string for anything older than a year or malformed.
 */
fun relativeTime(iso: String): String {
    val then = parseIsoSafe(iso) ?: return iso
    val diffMs = System.currentTimeMillis() - then.time
    if (diffMs < 0) return "now"
    val sec = diffMs / 1000
    if (sec < 60) return "now"
    val min = sec / 60
    if (min < 60) return if (min == 1L) "1 minute ago" else "$min minutes ago"
    val hours = min / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = hours / 24
    if (days < 30) return if (days == 1L) "1 day ago" else "$days days ago"
    val months = days / 30
    if (months < 12) return if (months == 1L) "1 month ago" else "$months months ago"
    val years = days / 365
    return if (years == 1L) "1 year ago" else "$years years ago"
}

/** 12345 -> "12.3k", 1234567 -> "1.2M" — star/fork 等计数缩写. */
fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
