package com.pockethub.ui.settings

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun buildIssueReportBody(events: List<com.pockethub.data.reporting.IssueEvent>): String? {
    if (events.isEmpty()) return null
    return buildString {
        appendLine("PocketHub 严重问题报告")
        appendLine("==================================")
        appendLine()
        appendLine("共 ${events.size} 条事件（最新在前）")
        appendLine()
        events.forEachIndexed { i, e ->
            appendLine("---- #${i + 1} [${e.kind}] ----")
            appendLine("时间:   ${e.isoTs}")
            appendLine("版本:   ${e.appVersionName} (${e.appVersionCode}) ${e.appVariant}")
            appendLine("设备:   ${e.deviceModel} · Android ${e.sdkInt}")
            e.threadName.takeIf { it.isNotBlank() }?.let { appendLine("线程:   $it") }
            e.subject.takeIf { it.isNotBlank() }?.let { appendLine("摘要:   $it") }
            e.stackTrace.takeIf { it.isNotBlank() }?.let {
                appendLine("堆栈:")
                // Cap each trace so a single huge crash can't flood the email body.
                val lines = it.lines().take(30)
                lines.forEach { l -> appendLine("  $l") }
                if (it.lines().size > 30) appendLine("  …(截断)")
            }
            if (e.extra.isNotEmpty()) {
                appendLine("附加信息:")
                e.extra.forEach { (k, v) -> appendLine("  $k: $v") }
            }
            appendLine()
        }
    }
}

/**
 * Open the system mail composer addressed to [DEVELOPER_EMAIL] with a
 * formatted report of [events]. The caller checks emptiness first and shows
 * a reminder instead — this function assumes non-empty input.
 */
internal fun sendIssueReportByEmail(
    context: android.content.Context,
    events: List<com.pockethub.data.reporting.IssueEvent>,
) {
    val count = events.size
    val first = events.firstOrNull()
    val subject = if (count == 1 && first != null) {
        "[PocketHub] ${first.kind} — ${first.isoTs.take(10)} · v${first.appVersionName}"
    } else {
        "[PocketHub] 严重问题汇总 ×$count"
    }
    openChooser(context, DEVELOPER_EMAIL, subject, buildIssueReportBody(events).orEmpty())
}

internal fun openChooser(context: android.content.Context, email: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        val chooser = Intent.createChooser(intent, "选择邮件客户端发送").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@Composable
internal fun localeLabel(locale: AppLocale): String = when (locale) {
    AppLocale.SYSTEM -> stringResource(R.string.locale_system)
    AppLocale.ENGLISH -> stringResource(R.string.locale_english)
    AppLocale.CHINESE -> stringResource(R.string.locale_chinese)
}

@Composable
internal fun notificationCadenceLabel(minutes: Int): String = when (minutes) {
    0    -> stringResource(R.string.notification_cadence_manual)
    15   -> stringResource(R.string.notification_cadence_15m)
    60   -> stringResource(R.string.notification_cadence_1h)
    1440 -> stringResource(R.string.notification_cadence_1d)
    else -> stringResource(R.string.notification_cadence_min, minutes)
}

internal fun openAppNotificationSettings(context: android.content.Context) {
    val intent = Intent().apply {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> {
                action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
