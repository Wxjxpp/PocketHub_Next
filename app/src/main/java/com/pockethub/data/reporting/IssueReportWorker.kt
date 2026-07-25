package com.pockethub.data.reporting

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pockethub.data.remote.GitHubApi
import com.pockethub.data.remote.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Drains the local severe-event ring buffer ([IssueReporter]) into a delivery
 * channel the user picked:
 *
 *  - "email"  → stage a ready-to-send email draft (ACTION_SEND). No SMTP relay,
 *    no native deps — the user's mail client actually sends.
 *  - "github" → POST a new issue on the target repo via [GitHubApi.createIssue].
 *    Fully automatic, lets an external script/AI close the loop by scraping
 *    labelled issues over the public GitHub API.
 *
 * The local ring buffer is wiped after a successful drain so we never re-send
 * the same batch.
 */
@HiltWorker
class IssueReportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val reporter: IssueReporter,
    private val settings: SettingsRepository,
    private val githubApi: GitHubApi,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "issue_report"
        const val CHANNEL_ID = "pockethub_issue_report"
        const val NOTI_ID = 9010
        const val ACTION_SHOULD_SEND = "pockethub.action.ISSUE_REPORT_READY"
        const val GH_LABEL = "severe-issue-audit"
    }

    override suspend fun doWork(): Result {
        val events = reporter.readLog()
        if (events.isEmpty()) return Result.success()

        val mode = settings.issueReportMode.first()
        return try {
            when (mode) {
                "github" -> deliverGithub(events)
                else     -> deliverEmail(events)
            }
            reporter.clearLog()
            Result.success()
        } catch (e: Exception) {
            // Network failure → retry the next WorkManager slot; don't drop
            // local events so this batch survives across retries.
            Result.retry()
        }
    }

    // ── GitHub Issues delivery ───────────────────────────────────────────
    private suspend fun deliverGithub(events: List<IssueEvent>) {
        val targetRepo = settings.issueReportTargetRepo.first()
        require(targetRepo.isNotBlank()) { "issue_report_target_repo not set; cannot post" }
        val parts = targetRepo.split("/", limit = 2)
        require(parts.size == 2 && parts.all { it.isNotBlank() }) { "target repo must be owner/repo, got: $targetRepo" }
        val (owner, repo) = parts

        val subject = "[PocketHub] 严重问题汇总 ${events.size} 条 — v${events.first().appVersionName}"
        val body = buildBody(events, githubMarkdown = true)

        val request = GitHubApi.IssueCreateRequest(
            title = subject,
            body = body,
            labels = listOf(GH_LABEL),
            assignees = emptyList(),
            milestone = null,
        )
        githubApi.createIssue(owner, repo, request)
    }

    // ── Email draft delivery ────────────────────────────────────────────
    private fun deliverEmail(events: List<IssueEvent>) {
        val email = settings.issueReportEmail.first()
        // Even if blank we leave the ring intact — see doWork's guard.
        require(email.isNotBlank()) { "issue_report_email not set; cannot stage" }

        val subject = "[PocketHub] 严重问题汇总 ${events.size} 条 — v${events.first().appVersionName}"
        val body = buildBody(events, githubMarkdown = false)

        stageEmailIntoOutbox(email, subject, body)
        postStagedNotification(events.size)
    }

    /**
     * Drop the ACTION_SEND staging artefacts into SharedPreferences so the
     * Settings screen can always re-(open) the draft for the user, plus fire
     * a broadcast so an in-app listener (if registered) can react.
     */
    private fun stageEmailIntoOutbox(toEmail: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "选择邮件客户端发送").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        val pending = Intent(ACTION_SHOULD_SEND).apply {
            setPackage(context.packageName)
            putExtra("to", toEmail)
            putExtra("subject", subject)
            putExtra("body", body)
        }
        context.sendBroadcast(pending)

        val prefs = context.getSharedPreferences("pockethub_issue_outbox", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("to", toEmail)
            .putString("subject", subject)
            .putString("body", body)
            .apply()
    }

    private fun postStagedNotification(count: Int) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "严重问题报告待发送",
                android.app.NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "当后台Worker整理完严重问题后通知你打开邮件草稿发送" }
            nm.createNotificationChannel(channel)
        }
        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.pockethub.R.drawable.ic_launcher_foreground)
            .setContentTitle("PocketHub 严重问题报告待发送")
            .setContentText("检测到 $count 条严重问题，邮件草稿已暂存在系统通知")
            .setAutoCancel(true)
            .build()
        nm.notify(NOTI_ID, notif)
    }

    // ── Body rendering ───────────────────────────────────────────────────
    private fun buildBody(events: List<IssueEvent>, githubMarkdown: Boolean): String {
        val first = events.first()
        val last = events.last()
        val crash = events.count { it.kind == IssueKind.CRASH }
        val anr = events.count { it.kind == IssueKind.ANR }
        val err = events.count { it.kind == IssueKind.ERROR }

        val sb = StringBuilder()
        sb.append(if (githubMarkdown) "**PocketHub 严重问题汇总报告**\n\n" else "PocketHub 严重问题汇总报告\n")
        sb.append(if (githubMarkdown) "```\n" else "================================\n")
        sb.append("时间窗: ${first.isoTs} → ${last.isoTs}\n")
        sb.append("事件总数: ${events.size}  (崩溃=$crash, ANR=$anr, 其他=$err)\n")
        sb.append("版本: v${first.appVersionName} (build ${first.appVersionCode}, ${first.appVariant})\n")
        sb.append("设备: ${first.deviceModel}, Android API ${first.sdkInt}\n")
        sb.append(if (githubMarkdown) "```\n\n" else "================================\n")

        if (githubMarkdown) {
            sb.append("## 事件列表\n\n")
        }
        events.forEachIndexed { idx, e ->
            sb.append(if (githubMarkdown) "### #${idx + 1} " else "\n【#${idx + 1}】")
            sb.append(if (githubMarkdown) "**类型=${e.kind.id.uppercase()}**  时间=${e.isoTs}  线程=${e.threadName}\n\n" else "类型=${e.kind.id.uppercase()}  时间=${e.isoTs}  线程=${e.threadName}\n")
            sb.append(if (githubMarkdown) "**说明:** ${e.subject}\n\n" else "说明: ${e.subject}\n")
            if (e.extra.isNotEmpty()) {
                sb.append(if (githubMarkdown) "**上下文:**\n\n" else "上下文:\n")
                e.extra.forEach { (k, v) ->
                    sb.append(if (githubMarkdown) "- $k = $v\n" else "  - $k = $v\n")
                }
                if (githubMarkdown) sb.append("\n")
            }
            if (e.stackTrace.isNotBlank()) {
                sb.append(if (githubMarkdown) "**堆栈:**\n\n```\n" else "堆栈:\n")
                // For markdown wrap stack frames in code fence; for plain email indent them.
                val frames = e.stackTrace.lineSequence().take(40).toList()
                frames.forEach { ln ->
                    sb.append(if (githubMarkdown) "$ln\n" else "  $ln\n")
                }
                val total = e.stackTrace.lines().size
                if (total > 40) sb.append(if (githubMarkdown) "... (+${total - 40} lines)\n```\n\n" else "  ... (+${total - 40} lines\n")
                else sb.append(if (githubMarkdown) "```\n\n" else "")
            }
        }
        sb.append(if (githubMarkdown) "---\n_由 PocketHub 自动埋点系统生成_\n" else "================================\n-- 由 PocketHub 自动埋点系统生成\n")
        return sb.toString()
    }
}
