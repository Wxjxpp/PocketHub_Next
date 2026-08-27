package com.pockethub.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockethub.R
import com.pockethub.data.remote.UpdateChecker
import java.util.Locale

/**
 * In-place updater flow: prompt → download (with progress) → install, without
 * leaving the app. The dialog never opens the browser; the APK is fetched into
 * cache and handed to the system PackageInstaller via a FileProvider URI.
 *
 * The layout uses [Dialog] (not AlertDialog) so the body can grow taller and the
 * buttons wrap on narrow screens via [FlowRow], fixing text-overflow on small
 * devices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    downloadState: UpdateViewModel.DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (path: String) -> Unit,
    onRetry: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Update icon plate
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "↑",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.update_available_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.update_version_line, info.latestVersionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                info.publishedAt?.let {
                    Text(
                        text = stringResource(R.string.update_published, formatPublishedDate(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    val items = parseChangelogItems(
                        notes,
                        tags = ChangelogTags(
                            new = stringResource(R.string.tag_new),
                            fix = stringResource(R.string.tag_fix),
                            improved = stringResource(R.string.tag_improved),
                            faster = stringResource(R.string.tag_faster),
                            reverted = stringResource(R.string.tag_reverted),
                            update = stringResource(R.string.tag_update),
                        ),
                    ).take(8)
                    if (items.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = item.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = item.tagColor,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(item.tagColor.copy(alpha = 0.13f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Download progress / status surface — only rendered when relevant.
                when (val ds = downloadState) {
                    is UpdateViewModel.DownloadState.Running -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { ds.progressPct / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(99.dp)),
                            )
                            val status = if (ds.totalBytes > 0) {
                                "${humanBytes(ds.downloadedBytes)} / ${humanBytes(ds.totalBytes)}  ·  ${ds.progressPct}%"
                            } else {
                                "${humanBytes(ds.downloadedBytes)}  ·  ${ds.progressPct}%"
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is UpdateViewModel.DownloadState.Done -> {
                        Text(
                            text = stringResource(R.string.update_downloaded_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is UpdateViewModel.DownloadState.Failed -> {
                        Text(
                            text = stringResource(R.string.update_download_failed, ds.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> Unit
                }

                Spacer(Modifier.size(2.dp))

                // Actions — primary CTA full-width, secondary actions as a
                // compact text row underneath. No cramped wrapping.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (val ds = downloadState) {
                        is UpdateViewModel.DownloadState.Running -> {
                            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        is UpdateViewModel.DownloadState.Done -> {
                            Button(onClick = { onInstall(ds.path) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_install))
                            }
                            TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(stringResource(R.string.action_remind_later))
                            }
                        }
                        is UpdateViewModel.DownloadState.Failed -> {
                            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_retry))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onLater) {
                                    Text(stringResource(R.string.action_remind_later))
                                }
                                TextButton(onClick = onIgnore) {
                                    Text(stringResource(R.string.action_ignore_version))
                                }
                            }
                        }
                        else -> {
                            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_download))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onLater) {
                                    Text(stringResource(R.string.action_remind_later))
                                }
                                TextButton(onClick = onIgnore) {
                                    Text(stringResource(R.string.action_ignore_version))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Parse an ISO-8601 timestamp into a short localized "yyyy-MM-dd HH:mm" string. */
private fun formatPublishedDate(iso: String): String = try {
    val zdt = java.time.OffsetDateTime.parse(iso.trim().replace("Z", "+00:00")).toInstant()
        .atZone(java.time.ZoneId.systemDefault())
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt)
} catch (_: Exception) {
    iso.take(16).replace('T', ' ')
}

// Helper: display bytes with a single-decimal unit string (e.g. "8.5 MB").
private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** A skimmable changelog line shown in the update dialog. */
private data class ChangeItem(
    val tag: String,
    val text: String,
    val tagColor: Color,
)

/** Localized changelog category tags (resolved from string resources). */
private data class ChangelogTags(
    val new: String,
    val fix: String,
    val improved: String,
    val faster: String,
    val reverted: String,
    val update: String,
)

/**
 * Parse raw release notes into a flat list of short skimmable items.
 *
 * Recognises the conventional-commit prefix (`feat(scope):` / `fix:` / `chore:` …)
 * and converts each line into a friendly Chinese category tag plus the rest of
 * the message. Lines without a recognisable prefix get a "更新" tag.
 *
 * Only bullet / `- ` lines or pure-message lines are kept; HTML or section
 * headers (lines starting with `#`) are dropped, since we want a tall vertical
 * list rather than a markdown essay.
 */
private fun parseChangelogItems(notes: String, tags: ChangelogTags): List<ChangeItem> {
    val featColor = Color(0xFF3FB950)
    val fixColor = Color(0xFF58A6FF)
    val refactorColor = Color(0xFFD29922)
    val choreColor = Color(0xFF8B949E)
    val otherColor = Color(0xFF8B949E)

    return notes.lines().mapNotNull { rawLine ->
        // Strip leading "- " / "* " bullet.
        val line = rawLine.trim().removePrefix("-").removePrefix("*").trim()
        if (line.isEmpty()) return@mapNotNull null
        // Drop markdown section headers like "## v0.1.44".
        if (line.startsWith("#")) return@mapNotNull null

        // Conventional-commit prefix match: "type(scope): message" or "type: message".
        val match = Regex("^(feat|fix|chore|refactor|docs|style|test|perf|build|ci|revert)(\\([^)]+\\))?[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(line)
        if (match != null) {
            val type = match.groupValues[1].lowercase()
            val msg = match.groupValues[3].trim()
            // Friendly, plain-language tags instead of dev jargon — users should
            // see what kind of change it is at a glance.
            val tagText = when (type) {
                "feat" -> tags.new
                "fix" -> tags.fix
                "refactor", "refact" -> tags.improved
                "perf" -> tags.faster
                "revert" -> tags.reverted
                else -> tags.improved
            }
            val color = when (type) {
                "feat" -> featColor
                "fix" -> fixColor
                "refactor", "perf" -> refactorColor
                "chore", "docs", "style", "test", "ci", "build" -> choreColor
                "revert" -> fixColor
                else -> otherColor
            }
            return@mapNotNull ChangeItem(tagText, msg, color)
        }
        // Plain line — show with a neutral tag.
        ChangeItem(tags.update, line, otherColor)
    }
}
