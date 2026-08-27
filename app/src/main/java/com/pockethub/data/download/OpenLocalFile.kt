package com.pockethub.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pockethub.R
import java.io.File

/**
 * Opens an arbitrary local file with the system intent handler:
 *  - `.apk` → triggers the system PackageInstaller via the FileProvider.
 *  - everything else → `ACTION_VIEW` with a mime type guessed by extension.
 *
 * Shared by the Downloads screen (release assets) and the workflow-run
 * artifacts file list, so APK-install + file-open behaviour stays in one place.
 */
fun openLocalFile(context: Context, file: File): Boolean {
    if (!file.exists() || !file.isFile) return false
    val authority = "${context.packageName}.fileprovider"
    val uri: Uri = try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (_: Exception) {
        // FileProvider not configured / file not exposed.
        return false
    }

    val intent = if (file.name.lowercase().endsWith(".apk")) {
        // ACTION_INSTALL_PACKAGE is deprecated post-24; use ACTION_VIEW + INSTALL.
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // Grant read perms to all resolvable handlers for safety (some intent handlers
    // only accept it if FLAG_GRANT_READ_URI_PERMISSION is set AND the resolver grants URI).
    val resolvers = context.packageManager.queryIntentActivities(intent, 0)
    for (r in resolvers) {
        context.grantUriPermission(
            r.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    val started = runCatching { context.startActivity(intent) }.isSuccess
    if (!started) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.download_open_failed),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
    return started
}

fun guessMime(fileName: String): String {
    val map = mapOf(
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "txt" to "text/plain",
        "json" to "application/json",
        "md" to "text/markdown",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "apk" to "application/vnd.android.package-archive",
    )
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return map[ext] ?: "*/*"
}

/** Compact human-readable byte size (used by download list + artifact cards). */
fun humanBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
