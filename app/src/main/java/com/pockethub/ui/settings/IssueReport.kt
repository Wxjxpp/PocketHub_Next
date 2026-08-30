package com.pockethub.ui.settings

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val subject = com.pockethub.data.reporting.IssueReportFormat.emailSubject(events)
    openChooser(
        context,
        DEVELOPER_EMAIL,
        subject,
        com.pockethub.data.reporting.IssueReportFormat.plainEmailBody(events),
        com.pockethub.data.reporting.IssueReportFormat.htmlEmailBody(events),
    )
}

internal fun openChooser(context: android.content.Context, email: String, subject: String, body: String, html: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        html?.let { putExtra(Intent.EXTRA_HTML_TEXT, it) }
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
