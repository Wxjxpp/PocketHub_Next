package com.pockethub.ui.markdown

// Markdown preprocessing + block parser: cleaning, table detection,
// truncation and block segmentation. Split out of MarkdownText.kt.

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun cleanMarkdown(markdown: String): String {
    return markdown
            // Convert common standalone raw-HTML <img src> into markdown ![](...) so our
            // image rendering kicks in. (<img> tags inside <a> won't convert cleanly here, but
            // those are far less common than markdown form below.)
            .replace(
                Regex(
                    "<\\s*img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?(?:alt\\s*=\\s*[\"']([^\"']*)[\"'])?[^>]*?/?>",
                    RegexOption.IGNORE_CASE,
                )
            ) { m ->
                val src = m.groupValues[1]
                val alt = m.groupValues[2]
                "![${alt}](${src})"
            }
            // Strip common HTML block/inline tags (leave text between pairs) — but keep <a href>
            // as markdown so we don't lose navigation context for legacy README HTML.
            .replace(
                Regex(
                    "<\\s*a\\s+[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)<\\s*/\\s*a\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                "[${m.groupValues[2]}](${m.groupValues[1]})"
            }
            // ── New block-level handlers for tags the original cleaner did not
            // cover. Converting them to markdown keeps README prose scannable
            // instead of leaking literal <h1>/<li>/<blockquote> etc. Runs before
            // the inline-emphasis step so nested <strong>/<em> inside a heading
            // still get styled — the heading text is processed right after.
            // <h1>…<h6> → ATX headings ("# title").
            .replace(
                Regex(
                    "<\\s*h([1-6])\\b[^>]*>(.*?)<\\s*/\\s*h[1-6]\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                val level = m.groupValues[1].toInt()
                "\n${"#".repeat(level.coerceIn(1, 6))} ${m.groupValues[2].trim()}\n"
            }
            // <blockquote> → markdown "> " prefix per line so the existing
            // blockquote block parser picks it up.
            .replace(
                Regex(
                    "<\\s*blockquote\\b[^>]*>(.*?)<\\s*/\\s*blockquote\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                m.groupValues[1].trimIndent().lines().joinToString("\n") { "> ${it}".trimEnd() }
            }
            // <li> → markdown list item ("- item"). The <ol>/<ul> wrappers are
            // stripped later; this keeps each bullet on its own line so the
            // bullet parser renders it.
            .replace(
                Regex(
                    "<\\s*li\\b[^>]*>(.*?)<\\s*/\\s*li\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m -> "\n- ${m.groupValues[1].trim()}" }
            // <script>/<style> → drop block + contents so raw JS/CSS doesn't
            // leak into rendered text. Must come before the catch-all tag strip.
            .replace(
                Regex(
                    "<\\s*(?:script|style)\\b[^>]*>.*?<\\s*/\\s*(?:script|style)\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
            // Convert raw-HTML inline emphasis/code/keystroke/strikethrough into markdown so it
            // renders styled instead of leaking raw tags. Must run before the generic tag strip.
            .replace(
                Regex("<\\s*(?:strong|b)\\b[^>]*>(.*?)<\\s*/\\s*(?:strong|b)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "**${it.groupValues[1]}**" }
            .replace(
                Regex("<\\s*(?:em|i)\\b[^>]*>(.*?)<\\s*/\\s*(?:em|i)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "*${it.groupValues[1]}*" }
            .replace(
                Regex("<\\s*(?:code|kbd)\\b[^>]*>(.*?)<\\s*/\\s*(?:code|kbd)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "`${it.groupValues[1]}`" }
            .replace(
                Regex("<\\s*(?:del|s|strike)\\b[^>]*>(.*?)<\\s*/\\s*(?:del|s|strike)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "~~${it.groupValues[1]}~~" }
            // Collapsible-section titles → bold heading so <details> blocks stay scannable.
            .replace(
                Regex("<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "\n**${it.groupValues[1].trim()}**\n" }
            // Inline tags with no markdown equivalent — drop the tag, keep inner text.
            .replace(Regex("<\\s*/?(?:u|mark|small|big|font|sub|sup)\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            // Block-level line breaks / rules → markdown forms (before the void-tag strip below).
            .replace(Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<\\s*hr\\s*/?>", RegexOption.IGNORE_CASE), "\n\n---\n\n")
            .replace(
                Regex("<\\s*(/?)\\s*(div|span|p|details|summary|center|section|article|figure|figcaption|picture|source|video|audio|table|thead|tbody|tr|td|th|pre|ol|ul|dl|dt|dd|caption|address)(\\s[^>]*)?>", RegexOption.IGNORE_CASE),
                "",
            )
            // Self-closing / void tags (img/br/hr already converted above; keep others stripped)
            .replace(
                Regex("<\\s*(br|hr|input|meta|link|area|base|col|embed|param|track|wbr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE),
                "",
            )
            // ── New — strip the remaining common HTML block/inline tags the
            // original list above didn't cover, so the Overview README never
            // shows literal tags. We name them explicitly rather than using a
            // broad `<tag>` catch-all so that README prose like "if (a <b) …"
            // isn't misinterpreted as a tag and stripped. HTML in fenced code
            // blocks stays visible because cleanMarkdown's list doesn't
            // include the bare-forward bracket rule.
            .replace(
                Regex(
                    "<\\s*/?(?:iframe|canvas|noscript|ruby|rp|rt|form|fieldset|legend|label|button|select|option|optgroup|object|embed|var|samp|cite|q|abbr|dfn|time|ins|datalist|output|progress|meter|template|slot|dialog|menu|nav|header|footer|main|aside|hgroup|bdi|bdo|wbr|colgroup|col|map|area|math|svg|use|template|portal|slot)\\b[^>]*>",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            // Decode a few common HTML entities
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("'", "'")
            .replace("&rarr;", "→")
            .replace("&larr;", "←")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "…")
            .replace("&times;", "×")
            .replace("&divide;", "÷")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace("&bull;", "•")
            .replace("&middot;", "·")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            // ── New numeric / hex entity decode (&#8230; / &#x2026;) — single
            // pass; out-of-range codepoints fall back to the original text.
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.let { code ->
                    if (code in 0..0x10FFFF) {
                        runCatching { Char(code).toString() }.getOrDefault(m.value)
                    } else {
                        m.value
                    }
                } ?: m.value
            }
            .replace(Regex("&#x([0-9A-Fa-f]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { code ->
                    if (code in 0..0x10FFFF) {
                        runCatching { Char(code).toString() }.getOrDefault(m.value)
                    } else {
                        m.value
                    }
                } ?: m.value
            }
            // Collapse multiple blank lines left by tag removal
            .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
}

// ── Parsing ─────────────────────────────────────────────────────────

internal val TABLE_SEP_REGEX = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")

internal fun isTableSeparator(line: String): Boolean {
    val l = line.trim()
    return l.contains("-") && l.contains("|") && TABLE_SEP_REGEX.matches(l)
}

internal fun looksLikeTableRow(line: String): Boolean {
    val l = line.trim()
    return l.isNotBlank() && (l.startsWith("|") || l.count { it == '|' } >= 2)
}

internal fun splitTableRow(line: String): List<String> {
    val raw = line.trim()
    val hasLeading = raw.startsWith("|")
    val hasTrailing = raw.endsWith("|")
    var cells = raw.split("|").map { it.trim() }
    if (hasLeading && cells.isNotEmpty()) cells = cells.drop(1)
    if (hasTrailing && cells.isNotEmpty()) cells = cells.dropLast(1)
    return cells
}

/** Max raw characters fed into the renderer; larger docs are truncated. */
private const val MAX_MARKDOWN_CHARS = 200_000

/**
 * Hard cap for pathological documents (e.g. 600KB+ awesome lists). The cap is
 * generous enough for any normal README/issue body; oversized docs keep their
 * beginning (title + intro + usually the first content sections) and get a
 * visible truncation marker instead of stalling the UI.
 */
internal fun truncateOversized(markdown: String): String {
    if (markdown.length <= MAX_MARKDOWN_CHARS) return markdown
    // Cut at a line boundary so we don't split a construct mid-way.
    val cut = markdown.lastIndexOf('\n', MAX_MARKDOWN_CHARS).takeIf { it > 0 }
        ?: MAX_MARKDOWN_CHARS
    return markdown.substring(0, cut) + "\n\n<!-- truncated -->\n\n*[Content too large — showing the first part]*"
}

internal fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0

    fun listLevel(line: String): Int {
        val leading = line.takeWhile { it == ' ' }.length
        return (leading / 2) + 1
    }

    /** A table header begins at [idx] when [idx] is a pipe row and [idx]+1 is a separator. */
    fun isTableHeaderAt(idx: Int): Boolean =
        idx + 1 < lines.size && looksLikeTableRow(lines[idx]) && isTableSeparator(lines[idx + 1])

    val isBlockStart: (String) -> Boolean = { l ->
        l.isBlank() || l.startsWith("#") || l.trim().startsWith("```") ||
            l.trimStart().startsWith(">") ||
            l.matches(Regex("^\\s*[-*+]\\s+.+")) || l.matches(Regex("^\\s*\\d+\\.\\s+.+")) ||
            l.matches(Regex("^-{3,}\\s*$")) || l.matches(Regex("^\\*{3,}\\s*$"))
    }

    while (i < lines.size) {
        val line = lines[i]

        if (line.isBlank()) { i++; continue }

        if (line.matches(Regex("^-{3,}\\s*$")) || line.matches(Regex("^\\*{3,}\\s*$"))) {
            blocks.add(MdBlock.HorizontalRule); i++; continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)").matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(MdBlock.Heading(level, headingMatch.groupValues[2].trim()))
            i++; continue
        }

        // Setext heading: non-blank line followed by === (H1) or --- (H2)
        if (i + 1 < lines.size && line.isNotBlank() && !line.startsWith("#")) {
            val next = lines[i + 1]
            if (next.matches(Regex("^=+\\s*$")) && line.isNotBlank()) {
                blocks.add(MdBlock.Heading(1, line.trim()))
                i += 2; continue
            }
            if (next.matches(Regex("^-+\\s*$")) && line.isNotBlank() && !line.matches(Regex("^-{3,}\\s*$"))) {
                blocks.add(MdBlock.Heading(2, line.trim()))
                i += 2; continue
            }
        }

        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quoteLines.add(lines[i].trimStart().removePrefix(">").trim())
                i++
            }
            blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // Ordered list
        if (line.matches(Regex("^\\s*\\d+\\.\\s+.+"))) {
            var orderedIndex = 0
            while (i < lines.size && lines[i].matches(Regex("^\\s*\\d+\\.\\s+.+"))) {
                orderedIndex++
                val text = lines[i].trim().substringAfter(". ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = true, index = orderedIndex, level = listLevel(lines[i])))
                i++
            }
            continue
        }

        // Unordered list (with optional GitHub task-list checkbox)
        if (line.matches(Regex("^\\s*[-*+]\\s+.+"))) {
            while (i < lines.size && lines[i].matches(Regex("^\\s*[-*+]\\s+.+"))) {
                val raw = lines[i].trim().substringAfter(" ").trim()
                val taskMatch = Regex("^\\[([ xX])]\\s+(.*)").matchEntire(raw)
                val (text, task) = if (taskMatch != null) {
                    val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                    taskMatch.groupValues[2] to (if (checked) 'x' else ' ')
                } else {
                    raw to null
                }
                blocks.add(MdBlock.ListItem(text, ordered = false, index = 0, level = listLevel(lines[i]), task = task))
                i++
            }
            continue
        }

        // GitHub-style pipe table
        if (isTableHeaderAt(i)) {
            val headers = splitTableRow(lines[i])
            i += 2 // skip header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && looksLikeTableRow(lines[i]) && !isTableSeparator(lines[i]) && !lines[i].isBlank()) {
                rows.add(splitTableRow(lines[i]))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // Paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size && !isBlockStart(lines[i]) && !isTableHeaderAt(i)) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return blocks
}

// ── Link resolver ────────────────────────────────────────────────────

/**
 * Resolve a raw GitHub reference to an absolute URL.
 *  - absolute http(s) → returned as-is
 *  - `#123`            → https://github.com/<owner/repo>/issues/123  (needs repoContext)
 *  - `@user`           → https://github.com/<user>
 *  - `owner/repo` or `owner/repo#123` → https://github.com/...
 *  - 40-hex-char SHA   → https://github.com/<repo>/commit/<sha>  (needs repoContext)
 *  - otherwise         → null (will be rendered as plain text)
 */
