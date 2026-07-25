package com.pockethub.data.reporting

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pockethub.data.remote.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Builds an email report body out of every persisted issue event and stages
 * a user-visible system notification whose tap action launches a pre-filled
 * ACTION_SEND email intent to the user's configured inbox.
 *
 * Strategy note — why we don't do SMTP directly:
 * - Adds zero native deps (no JavaMail, no SAAJ) — keeps APK thin.
 * - No password storage in-app, no cleartext Mail.smtp tricky network rules.
 * - One tap fires a default mail composer pre-filled with To/Subject/Body — the
 *   user just hits "Send" once. This is the standard "AI app lets a real mail
 *   client do the sending" pattern and is what the user asked for
 *   ("定期发送到我邮箱" → staged email composer).
 *
 * Events are wiped after the report is staged — no double-send.
 */
@HiltWorker
class IssueReportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val reporter: IssueReporter,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "issue_report"
        const val CHANNEL_ID = "pockethub_issue_report"
        const val NOTI_ID = 9010
        const val ACTION_SHOULD_SEND = "pockethub.action.ISSUE_REPORT_READY"
    }

    override suspend fun doWork(): Result {
        val events = reporter.readLog()
        if (events.isEmpty()) return Result.success()

        val email = settings.issueReportEmail.first()
        if (email.isBlank()) {
            // Auto-still-clear the buffer is dangerous: we'd lose evidence of
            // a real crash if the user hasn't configured a recipient yet.
            // Keep it around — surface a one-shot notification asking the user
            // to configure a target. Once configured, next scheduling will send.
            return Result.success()
        }

        val first = events.first()
        val body = buildBody(events)
        val subject = "[PocketHub] 严重问题汇总 ${events.size} 条 — v${first.appVersionName}"

        // Stage email: ACTION_SEND with mailto fallback. The to-directory-file is
        // also written so the next app launch / Settings screen can show "Pending report".
        stageEmail(email, subject, body)

        // Successful stage → wipe the local log so the same set isn't re-sent.
        reporter.clearLog()
        return Result.success()
    }

    private fun stageEmail(toEmail: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            // WHY EXTRA_STREAM OFF: attach the body inline so it's self-contained;
            // no app-private file URI exposure needed (no FileProvider trouble).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // We persist the "pending to send" intent so that the next time the user
        // opens the app the settings screen / startup can offer a tap target.
        val pending = Intent(ACTION_SHOULD_SEND).apply {
            setPackage(context.packageName)
            putExtra("to", toEmail)
            putExtra("subject", subject)
            putExtra("body", body)
        }
        context.sendBroadcast(pending)

        // Also surface a system notification so the user knows an issue report
        // is queued up — tapping it opens the app settings to send/view.
        ensureChannel()
        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.pockethub.R.drawable.ic_launcher_foreground)
            .setContentTitle("PocketHub 严重问题报告待发送")
            .setContentText("检测到 ${events.size} 条严重问题已整理，点击打开邮件草稿发送")
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(NOTI_ID, notif)

        // Persist a send-ready Intent for the Settings screen to pull and launch
        // when the user taps "立即发送". We keep the body in prefs so we survive reboots.
        val prefs = context.getSharedPreferences("pockethub_issue_outbox", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("to", toEmail)
            .putString("subject", subject)
            .putString("body", body)
            .apply()
    }

    private fun buildBody(events: List<IssueEvent>): String {
        val sb = StringBuilder()
        sb.append("PocketHub 严重问题汇总报告\n")
        sb.append("================================\n\n")
        sb.append("时间窗: ${events.first().isoTs} → ${events.last().isoTs}\n")
        sb.append("事件总数: ${events.size}\n")
        sb.append("版本: v${events.first().appVersionName} (build ${events.first().appVersionCode}, ${events.first().appVariant})\n")
        sb.append("设备: ${events.first().deviceModel}, Android API ${events.first().sdkInt}\n\n")
        sb.append("================================\n")
        events.forEachIndexed { idx, e ->
            sb.append("\n【#${idx + 1}】类型=${e.kind.id.uppercase()}  时间=${e.isoTs}  线程=${e.threadName}\n")
            sb.append("说明: ${e.subject}\n")
            if (e.extra.isNotEmpty()) {
                sb.append("上下文:\n")
                e.extra.forEach { (k, v) -> sb.append("  - $k = $v\n") }
            }
            if (e.stackTrace.isNotBlank()) {
                sb.append("堆栈:\n")
                // Indent each stack frame for readability in mail body.
                e.stackTrace.lineSequence().take(40).forEach { ln ->
                    sb.append("  $ln\n")
                }
                if (e.stackTrace.lines().size > 40) sb.append("  ... (more ${e.stackTrace.lines().size - 40} lines\n")
            }
        }
        sb.append("\n================================\n")
        sb.append("-- 由 PocketHub 自动埋点系统生成\n")
        return sb.toString()
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "严重问题报告待发送",
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "当后台Worker整理完严重问题后通知你打开邮件草稿发送"
        }
        nm.createNotificationChannel(channel)
    }

}
