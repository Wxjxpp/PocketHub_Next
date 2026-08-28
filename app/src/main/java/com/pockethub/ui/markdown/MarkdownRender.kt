package com.pockethub.ui.markdown

// Markdown rendering composables: inline parts, paragraphs, images, quotes,
// lists, tables + annotated-string inline builder. Split out of MarkdownText.kt.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.pockethub.ui.LocalAppImageLoader

@Composable
internal fun RenderInlineParts(parts: List<InlineToken>, style: androidx.compose.ui.text.TextStyle, onTap: (String, LinkKind) -> Unit) {
    parts.forEach { part ->
        when (part) {
            is InlineToken.Text -> ClickableText(
                text = part.span,
                style = style.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    part.span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                        val kind = part.span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                            .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                            ?: LinkKind.EXTERNAL
                        onTap(annotation.item, kind)
                    }
                },
            )
            is InlineToken.Image -> RenderImageRun(listOf(part), onTap)
        }
    }
}

@Composable
internal fun RichParagraph(parts: List<InlineToken>, onTap: (String, LinkKind) -> Unit, paragraphSpacing: androidx.compose.ui.unit.Dp = 3.dp) {
    // Inline-aligned images: collect adjacent images into a run, then split the run into
    // badge walls (compact, inline) and content images (full-width). Text tokens get rendered
    // as standalone ClickableText below.
    var i = 0
    Column(Modifier.padding(top = paragraphSpacing, bottom = paragraphSpacing)) {
        while (i < parts.size) {
            val run = mutableListOf<InlineToken.Image>()
            while (i < parts.size && parts[i] is InlineToken.Image) {
                run.add(parts[i] as InlineToken.Image)
                i++
            }
            if (run.isNotEmpty()) {
                RenderImageRun(run, onTap)
                continue
            }
            val txt = parts[i] as InlineToken.Text
            ClickableText(
                text = txt.span,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(vertical = 2.dp),
                onClick = { offset ->
                    txt.span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                        val kind = txt.span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                            .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                            ?: LinkKind.EXTERNAL
                        onTap(annotation.item, kind)
                    }
                },
            )
            i++
        }
    }
}

/**
 * Render a run of adjacent images. Badge-style images (shields.io, CI status, etc.) are grouped
 * into a compact [BadgesRow]; everything else is shown as a full-width [ContentImage] so README
 * screenshots and banners are legible on a phone instead of squished to a strip.
 */
@Composable
internal fun RenderImageRun(images: List<InlineToken.Image>, onTap: (String, LinkKind) -> Unit) {
    var j = 0
    while (j < images.size) {
        if (isBadgeUrl(images[j].src)) {
            val badges = mutableListOf<InlineToken.Image>()
            while (j < images.size && isBadgeUrl(images[j].src)) {
                badges.add(images[j])
                j++
            }
            BadgesRow(badges, onTap)
        } else {
            ContentImage(images[j], onTap)
            j++
        }
    }
}

/** A content image shown at a readable size with loading / error states and tap-to-open.
 *  Uses SubcomposeAsyncImage so Coil resolves the request against the component's bounded
 *  layout size (never Size.ORIGINAL) — large README screenshots decode downsampled, no OOM. */
@Composable
internal fun ContentImage(img: InlineToken.Image, onTap: (String, LinkKind) -> Unit) {
    val clickTarget = img.wrapUrl ?: img.src
    val kind = if (img.wrapUrl != null) classifyLink(img.wrapUrl) else LinkKind.IMAGE_URL
    SubcomposeAsyncImage(
        model = img.src,
        imageLoader = LocalAppImageLoader.current,
        contentDescription = img.alt.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 360.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onTap(clickTarget, kind) },
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (img.alt.isNotBlank()) {
                    Text(
                        img.alt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    img.src,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BadgesRow(images: List<InlineToken.Image>, onTap: (String, LinkKind) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        images.forEach { img ->
            val clickableModifier = if (img.wrapUrl != null) {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.wrapUrl, classifyLink(img.wrapUrl)) }
            } else {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.src, LinkKind.IMAGE_URL) }
            }
            Box(modifier = clickableModifier) {
                AsyncImage(
                    model = img.src,
                    imageLoader = LocalAppImageLoader.current,
                    contentDescription = img.alt.takeIf { it.isNotBlank() },
                    modifier = Modifier
                        .heightIn(min = 16.dp, max = 40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
internal fun RichBlockquote(
    parts: List<InlineToken>,
    accentColor: Color,
    mutedColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    val hasOnlyText = parts.all { it is InlineToken.Text }
    if (hasOnlyText) {
        // fast path — render whole as one ClickableText
        val span = buildAnnotatedString {
            parts.forEach { append((it as InlineToken.Text).span) }
        }
        ClickableText(
            text = span,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                color = mutedColor,
            ),
            modifier = Modifier
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .drawBehind {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                },
            onClick = { offset ->
                span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                    val kind = span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                        .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                        ?: LinkKind.EXTERNAL
                    onTap(annotation.item, kind)
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        return
    }
    // has images too — render paragraph-like
    Column(
        Modifier
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .drawBehind {
                drawLine(
                    color = accentColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
    ) { RichParagraph(parts, onTap) }
}

@Composable
internal fun RichListItem(
    bullet: String,
    parts: List<InlineToken>,
    indent: Int,
    mutedColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    Column(Modifier.padding(start = (4 + indent).dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(bullet, color = mutedColor, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f)) { RichParagraph(parts, onTap) }
        }
    }
}

// ── Tables ───────────────────────────────────────────────────────────

@Composable
internal fun TableBlock(
    table: MdBlock.Table,
    resolver: LinkResolver,
    imageResolver: ImageResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val colCount = table.headers.size.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.fillMaxWidth().background(headerBg)) {
            table.headers.forEach { cell ->
                val parts = renderRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                TableCell(parts, Modifier.width(0.dp).weight(1f), bold = true, onTap = onTap)
            }
        }
        HorizontalDivider(color = borderColor)
        table.rows.forEach { row ->
            val padded = (row + List((colCount - row.size).coerceAtLeast(0)) { "" }).take(colCount)
            Row(Modifier.fillMaxWidth()) {
                padded.forEach { cell ->
                    val parts = renderRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    TableCell(parts, Modifier.width(0.dp).weight(1f), bold = false, onTap = onTap)
                }
            }
        }
    }
}

@Composable
internal fun TableCell(
    parts: List<InlineToken>,
    modifier: Modifier,
    bold: Boolean,
    onTap: (String, LinkKind) -> Unit,
) {
    val span = buildAnnotatedString {
        parts.forEach { if (it is InlineToken.Text) append(it.span) }
    }
    ClickableText(
        text = span,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = modifier.padding(horizontal = 6.dp, vertical = 5.dp),
        onClick = { offset ->
            span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { a ->
                val kind = span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                    .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                    ?: LinkKind.EXTERNAL
                onTap(a.item, kind)
            }
        },
    )
}

// ── Markdown cleaning ────────────────────────────────────────────────

internal fun renderRichInline(
    text: String,
    resolver: LinkResolver,
    imageResolver: ImageResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): List<InlineToken> {
    val out = mutableListOf<InlineToken>()
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val str = stringFromSource(
                textBuffer.toString(),
                resolver,
                codeBackgroundColor,
                linkColor,
                downloadColor,
                imageLinkColor,
                externalColor,
            )
            out.add(InlineToken.Text(str))
            textBuffer.clear()
        }
    }

    var i = 0
    val len = text.length
    while (i < len) {
        val rest = text.substring(i)
        // Try wrapped image link [![alt](src)](href) — only if it begins at i.
        val wrappedMatch = WRAPPED_IMG_PATTERN.find(rest)
        if (wrappedMatch != null) {
            flushText()
            val alt = wrappedMatch.groupValues[1]
            val src = imageResolver(wrappedMatch.groupValues[2].trim())
            val href = wrappedMatch.groupValues[3].trim()
            val resolvedHref = resolver(href) ?: href
            out.add(InlineToken.Image(src = src, alt = alt, wrapUrl = resolvedHref))
            i += wrappedMatch.value.length
            continue
        }
        // Try standalone image ![alt](src)
        val imgMatch = STANDALONE_IMG_PATTERN.find(rest)
        if (imgMatch != null) {
            flushText()
            val alt = imgMatch.groupValues[1]
            val src = imageResolver(imgMatch.groupValues[2].trim())
            out.add(InlineToken.Image(src = src, alt = alt, wrapUrl = null))
            i += imgMatch.value.length
            continue
        }
        // Otherwise accumulate to text buffer (raw chars preserved so a later
        // markdown link [text](url) is fully visible to stringFromSource).
        textBuffer.append(text[i])
        i++
    }
    flushText()
    return out
}

internal fun stringFromSource(
    src: String,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        // Autolink <url>
        if (src[i] == '<') {
            val close = src.indexOf('>', i + 1)
            if (close != -1) {
                val inner = src.substring(i + 1, close)
                if (inner.startsWith("http://") || inner.startsWith("https://")) {
                    appendLink(inner, inner, classifyLink(inner), linkColor, downloadColor, imageLinkColor, externalColor)
                    i = close + 1; continue
                }
            }
        }
        // Markdown link [text](url)
        if (src[i] == '[') {
            val closeBracket = src.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < src.length && src[closeBracket + 1] == '(') {
                val closeParen = src.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = src.substring(i + 1, closeBracket)
                    val linkUrl = src.substring(closeBracket + 2, closeParen).trim()
                    val url = resolver(linkUrl)
                    if (url != null) {
                        appendLink(linkText, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                    } else {
                        append(linkText)
                    }
                    i = closeParen + 1; continue
                }
            }
        }
        // Bare URL
        if (src.regionMatches(i, "https://", 0, 8, ignoreCase = false) ||
            src.regionMatches(i, "http://", 0, 7, ignoreCase = false)) {
            val end = findUrlEnd(src, i)
            if (end > i) {
                val url = src.substring(i, end)
                appendLink(url, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                i = end; continue
            }
        }
        // GitHub shortcut #123 / @user
        if (src[i] == '#' || src[i] == '@') {
            val m = if (src[i] == '#') Regex("^#(\\d+)").find(src.substring(i))
            else Regex("^@[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})").find(src.substring(i))
            if (m != null) {
                val ref = m.value
                val url = resolver(ref)
                if (url != null) {
                    val displayText = ref
                    appendLink(displayText, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                    i += m.range.last + 1; continue
                }
            }
        }
        // Bold **text**
        if (i + 1 < src.length && src[i] == '*' && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, end)) }
                i = end + 2; continue
            }
        }
        // Italic *text*
        if (src[i] == '*') {
            val end = src.indexOf('*', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(i + 1, end)) }
                i = end + 1; continue
            }
        }
        // Strikethrough ~~text~~
        if (i + 1 < src.length && src[i] == '~' && src[i + 1] == '~') {
            val end = src.indexOf("~~", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(src.substring(i + 2, end)) }
                i = end + 2; continue
            }
        }
        // Inline code `text`
        if (src[i] == '`') {
            val end = src.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor)) {
                    append(src.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        append(src[i]); i++
    }
}

internal fun AnnotatedString.Builder.appendLink(
    displayText: String,
    url: String,
    kind: LinkKind,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    @Suppress("UNUSED_PARAMETER") externalColor: Color,
) {
    // Tiny textual cue (emoji-free) for downloadable links — rendered before the styled span.
    val prefix = when (kind) {
        LinkKind.DOWNLOADABLE -> "⬇ "
        else -> ""
    }
    if (prefix.isNotEmpty()) append(prefix)
    // Now mark the actual link span with annotations + styles.
    val start = length
    addStringAnnotation(LINK_TAG, url, start, start + displayText.length)
    addStringAnnotation(LINK_KIND_TAG, kind.name, start, start + displayText.length)
    val style = when (kind) {
        LinkKind.DOWNLOADABLE -> SpanStyle(color = downloadColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)
        LinkKind.IMAGE_URL -> SpanStyle(color = imageLinkColor, textDecoration = TextDecoration.Underline)
        LinkKind.GITHUB_REPO, LinkKind.GITHUB_USER, LinkKind.GITHUB_ISSUE, LinkKind.GITHUB_COMMIT,
        LinkKind.IMAGE, LinkKind.EXTERNAL -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    }
    addStyle(style, start, start + displayText.length)
    append(displayText)
}

internal fun findUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        if (c.isWhitespace() || c in setOf(')', ']', '}', '<', '>', '"', '\'', '|')) break
        end++
    }
    while (end > start + 1 && text[end - 1] in setOf('.', ',', ';', ':', '!', '?')) end--
    return end
}
