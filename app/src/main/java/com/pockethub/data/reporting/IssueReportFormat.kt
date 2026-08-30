package com.pockethub.data.reporting

/**
 * Formatting for the severe-issue digest, shared by the GitHub-issue and
 * email delivery paths.
 *
 * Design goals (developer reading a wall of crashes):
 *  1. Identical failures are GROUPED (kind + normalized subject + top app
 *     frame) with a ×N badge — 40 events become 3 blocks.
 *  2. Every block answers, in order: what broke, when, where (screen +
 *     network trail), then the stack — scannable top-down.
 *  3. Long content (full stack, long trails) is collapsed/capped, never
 *     allowed to bury the next block.
 *
 * Three renderers:
 *  - [markdownBody]  → GitHub issue (tables, <details> collapse)
 *  - [htmlEmailBody] → email via EXTRA_HTML_TEXT (colors, badges, spacing)
 *  - [plainEmailBody]→ email fallback via EXTRA_TEXT (unicode rules/indents)
 */
object IssueReportFormat {

    private val KIND_ORDER = listOf(IssueKind.CRASH, IssueKind.ANR, IssueKind.ERROR)

    /** Emoji + zh label per kind, used by all renderers. */
    private fun kindBadge(kind: IssueKind): String = when (kind) {
        IssueKind.CRASH -> "🔥 崩溃"
        IssueKind.ANR -> "⏱ 卡死"
        IssueKind.ERROR -> "⚠️ 错误"
    }

    private fun kindColorHex(kind: IssueKind): String = when (kind) {
        IssueKind.CRASH -> "#D1242F"
        IssueKind.ANR -> "#BF8700"
        IssueKind.ERROR -> "#57606A"
    }

    /** One deduplicated failure family. */
    data class Group(
        val kind: IssueKind,
        val subject: String,
        val count: Int,
        val latest: IssueEvent,
        val occurrences: List<String>, // newest-first iso timestamps, capped
    )

    /**
     * Normalize a subject so "same crash, different pointer/address" collapses:
     * strip hex addresses, big numbers and quoted file paths.
     */
    private fun normalize(subject: String): String = subject
        .replace(Regex("0x[0-9a-fA-F]+"), "0x…")
        .replace(Regex("\\d{3,}"), "N")
        .replace(Regex("/[A-Za-z0-9_./%-]+\\.(kt|java|xml)"), "<file>")
        .take(140)

    private fun firstAppFrame(e: IssueEvent): String =
        e.stackTrace.lineSequence()
            .firstOrNull { it.contains("com.pockethub") && !it.contains("IssueReporter") }
            ?.substringBefore("(")?.trim().orEmpty()

    fun group(events: List<IssueEvent>): List<Group> {
        data class Acc(val events: MutableList<IssueEvent>)

        val buckets = LinkedHashMap<String, Acc>()
        for (e in events) { // input is oldest-first; iterate newest-last so the
            // LAST put for a key holds the newest representative.
            val key = e.kind.id + "|" + normalize(e.subject) + "|" + firstAppFrame(e)
            buckets.getOrPut(key) { Acc(mutableListOf()) }.events.add(e)
        }
        return buckets.values.map { acc ->
            val list = acc.events
            val latest = list.last()
            Group(
                kind = latest.kind,
                subject = latest.subject.ifBlank { list.first().subject },
                count = list.size,
                latest = latest,
                occurrences = list.map { it.isoTs }.takeLast(5).asReversed(),
            )
        }.sortedWith(
            compareByDescending<Group> { KIND_ORDER.indexOf(it.kind) }
                .thenByDescending { it.latest.ts },
        )
    }

    private fun appFrames(e: IssueEvent, max: Int = 5): List<String> =
        e.stackTrace.lineSequence()
            .filter { it.contains("com.pockethub") && !it.contains("IssueReporter") }
            .take(max).map { it.trim() }.toList()

    private fun breadcrumbs(e: IssueEvent): List<String> =
        e.extra[IssueReporter.BKEY_BREADCRUMBS]
            ?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()

    private fun headerRange(events: List<IssueEvent>): Pair<String, String> =
        events.first().isoTs to events.last().isoTs

    private fun counts(events: List<IssueEvent>): String =
        KIND_ORDER.mapNotNull { k ->
            events.count { it.kind == k }.takeIf { it > 0 }?.let { "${kindBadge(k)} ×$it" }
        }.joinToString(" · ")

    // ── GitHub issue (markdown) ─────────────────────────────────────────

    fun markdownBody(events: List<IssueEvent>): String {
        val sb = StringBuilder()
        val first = events.first()
        val (t0, t1) = headerRange(events)
        val groups = group(events)

        sb.appendLine("## 📋 PocketHub 严重问题报告")
        sb.appendLine()
        sb.appendLine("| 设备 | 系统 | 版本 | 构建 | 时间范围 |")
        sb.appendLine("|---|---|---|---|---|")
        sb.appendLine("| ${first.deviceModel} | Android ${first.sdkInt} | v${first.appVersionName} | ${first.appVariant} | $t0 ~ $t1 |")
        sb.appendLine()
        sb.appendLine("**${groups.size} 组 · 共 ${events.size} 次** — ${counts(events)}")
        sb.appendLine()

        groups.forEachIndexed { i, g ->
            sb.appendLine("---")
            sb.appendLine()
            val badge = if (g.count > 1) " ×${g.count}" else ""
            sb.appendLine("### ${kindBadge(g.kind)} #$i · ${g.subject.take(160)}$badge")
            sb.appendLine()
            sb.appendLine("- **最近发生**: ${g.latest.isoTs}")
            if (g.count > 1) sb.appendLine("- **发生时间**: ${g.occurrences.joinToString("、")}")
            sb.appendLine("- **线程**: `${g.latest.threadName}`")
            val crumbs = breadcrumbs(g.latest)
            if (crumbs.isNotEmpty()) {
                sb.appendLine("- **现场轨迹**(最近 ${minOf(crumbs.size, 8)} 步,旧 → 新):")
                sb.appendLine()
                sb.appendLine("```text")
                crumbs.takeLast(8).forEach { sb.appendLine(it) }
                sb.appendLine("```")
            }
            val frames = appFrames(g.latest)
            if (frames.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("<details><summary>堆栈关键帧(app 帧)</summary>")
                sb.appendLine()
                sb.appendLine("```text")
                frames.forEach { sb.appendLine("at $it") }
                sb.appendLine("```")
                sb.appendLine()
                sb.appendLine("</details>")
            }
            val full = g.latest.stackTrace
            if (full.isNotBlank()) {
                sb.appendLine()
                sb.appendLine("<details><summary>完整堆栈</summary>")
                sb.appendLine()
                sb.appendLine("```text")
                full.lineSequence().take(120).forEach { sb.appendLine(it) }
                if (full.lineSequence().count() > 120) sb.appendLine("…(截断)")
                sb.appendLine("```")
                sb.appendLine()
                sb.appendLine("</details>")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    // ── Email HTML (EXTRA_HTML_TEXT) ────────────────────────────────────

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun htmlEmailBody(events: List<IssueEvent>): String {
        val first = events.first()
        val (t0, t1) = headerRange(events)
        val groups = group(events)
        val mono = "font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace"

        val sb = StringBuilder()
        sb.append("<div style=\"font-family:-apple-system,'Segoe UI',Roboto,'Helvetica Neue',sans-serif;color:#1F2328;max-width:680px\">")
        sb.append("<h2 style=\"margin:0 0 4px\">🛟 PocketHub 严重问题报告</h2>")
        sb.append("<div style=\"color:#57606A;font-size:12px;margin-bottom:14px\">")
        sb.append("${esc(first.deviceModel)} · Android ${first.sdkInt} · <b>v${first.appVersionName}</b> (${esc(first.appVariant)}) · $t0 ~ $t1")
        sb.append("</div>")
        // Summary chips
        sb.append("<div style=\"margin-bottom:6px\">")
        groups.take(8).forEach { g ->
            sb.append(
                "<span style=\"display:inline-block;background:${kindColorHex(g.kind)}14;color:${kindColorHex(g.kind)};" +
                    "border:1px solid ${kindColorHex(g.kind)}55;border-radius:999px;padding:2px 10px;font-size:12px;margin:0 6px 6px 0\">" +
                    "${kindBadge(g.kind)}${if (g.count > 1) " ×${g.count}" else ""} · ${esc(g.subject.take(46))}</span>",
            )
        }
        sb.append("</div>")

        groups.forEachIndexed { i, g ->
            val color = kindColorHex(g.kind)
            sb.append("<div style=\"border:1px solid #D0D7DE;border-left:4px solid $color;border-radius:8px;padding:12px 14px;margin:14px 0\">")
            sb.append("<div style=\"font-size:14px;font-weight:600;margin-bottom:6px\">")
            sb.append("${kindBadge(g.kind)} #$i")
            if (g.count > 1) {
                sb.append(" <span style=\"background:${color}14;color:$color;border-radius:999px;padding:1px 8px;font-size:11px;margin-left:6px\">×${g.count}</span>")
            }
            sb.append("</div>")
            sb.append("<div style=\"font-size:13px;font-weight:600;word-break:break-word;margin-bottom:6px\">${esc(g.subject.take(200))}</div>")
            sb.append("<div style=\"color:#57606A;font-size:12px;margin-bottom:8px\">")
            sb.append("最近 ${esc(g.latest.isoTs)} · 线程 <span style=\"$mono\">${esc(g.latest.threadName)}</span>")
            if (g.count > 1) sb.append(" · 共 ${g.occurrences.joinToString(", ") { it.take(19) }} 次(列出新至旧,最多 5)")
            sb.append("</div>")
            val crumbs = breadcrumbs(g.latest)
            if (crumbs.isNotEmpty()) {
                sb.append("<div style=\"font-size:11px;color:#57606A;margin-bottom:2px\">现场轨迹(旧 → 新)</div>")
                sb.append("<div style=\"$mono;font-size:11px;color:#57606A;background:#F6F8FA;border-radius:6px;padding:8px 10px;margin-bottom:8px;word-break:break-all\">")
                crumbs.takeLast(8).forEachIndexed { j, c ->
                    if (j > 0) sb.append("<br>")
                    sb.append(esc(c))
                }
                sb.append("</div>")
            }
            val frames = appFrames(g.latest)
            if (frames.isNotEmpty()) {
                sb.append("<div style=\"font-size:11px;color:#57606A;margin-bottom:2px\">堆栈关键帧</div>")
                sb.append("<pre style=\"$mono;font-size:11px;line-height:1.5;background:#F6F8FA;border-radius:6px;padding:8px 10px;margin:0 0 8px;white-space:pre-wrap;word-break:break-all\">")
                sb.append(frames.joinToString("\n") { "at " + esc(it) })
                sb.append("</pre>")
            }
            val full = g.latest.stackTrace
            if (full.isNotBlank()) {
                val lines = full.lineSequence().toList()
                val shown = lines.take(40)
                sb.append("<div style=\"font-size:11px;color:#57606A;margin-bottom:2px\">完整堆栈${if (lines.size > 40) "(前 40 行)" else ""}</div>")
                sb.append("<pre style=\"$mono;font-size:11px;line-height:1.5;color:#57606A;background:#FBFBFC;border:1px dashed #D0D7DE;border-radius:6px;padding:8px 10px;margin:0;white-space:pre-wrap;word-break:break-all\">")
                sb.append(esc(shown.joinToString("\n")))
                if (lines.size > 40) sb.append("\n…(截断)")
                sb.append("</pre>")
            }
            sb.append("</div>")
        }
        sb.append("<div style=\"color:#8C959F;font-size:11px;margin-top:12px\">由 PocketHub 自动生成 · 相同堆栈已合并为一组</div>")
        sb.append("</div>")
        return sb.toString()
    }

    // ── Email plain text (EXTRA_TEXT fallback) ──────────────────────────

    fun plainEmailBody(events: List<IssueEvent>): String {
        val first = events.first()
        val (t0, t1) = headerRange(events)
        val groups = group(events)
        val sb = StringBuilder()
        sb.appendLine("🛟 PocketHub 严重问题报告 · ${groups.size} 组 / 共 ${events.size} 次")
        sb.appendLine("${first.deviceModel} · Android ${first.sdkInt} · v${first.appVersionName}(${first.appVariant})")
        sb.appendLine("$t0 ~ $t1")
        groups.forEachIndexed { i, g ->
            sb.appendLine()
            sb.appendLine("━━━ #${i + 1} ${kindBadge(g.kind)}${if (g.count > 1) " ×${g.count}" else ""} ━━━━━━━━━━")
            sb.appendLine("问题: ${g.subject.take(200)}")
            sb.appendLine("最近: ${g.latest.isoTs} · 线程: ${g.latest.threadName}")
            if (g.count > 1) sb.appendLine("发生: ${g.occurrences.joinToString(" → ")}")
            val crumbs = breadcrumbs(g.latest)
            if (crumbs.isNotEmpty()) {
                sb.appendLine("现场(旧 → 新):")
                crumbs.takeLast(8).forEach { sb.appendLine("   $it") }
            }
            val frames = appFrames(g.latest)
            if (frames.isNotEmpty()) {
                sb.appendLine("堆栈关键帧:")
                frames.forEach { sb.appendLine("   at $it") }
            }
        }
        return sb.toString()
    }

    /** Email subject line, shared by both delivery entry points. */
    fun emailSubject(events: List<IssueEvent>): String {
        val groups = group(events)
        return if (groups.size == 1) {
            "[PocketHub] ${kindBadge(groups[0].kind)} — ${groups[0].subject.take(60)}"
        } else {
            "[PocketHub] 严重问题 ${groups.size} 组 / 共 ${events.size} 次 · v${events.first().appVersionName}"
        }
    }
}
