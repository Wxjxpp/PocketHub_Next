package com.pockethub.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A lightweight, dependency-free Markdown renderer (enhanced).
 *
 * Supports: H1-H6, bold (**), italic (*), strikethrough (~~), inline code, fenced code blocks,
 * ordered / unordered lists (with nesting), GitHub task lists (- [ ] / - [x]), blockquotes,
 * horizontal rules, paragraphs, GitHub-style pipe tables, images `![alt](src)`, wrapped badge
 * links `[![alt](src)](href)`, autolinks (`<url>` and bare URLs), GitHub-relative references
 * (#123 issue, @user, owner/repo, bare commit SHA), and common raw-HTML inline tags
 * (<strong>/<b>, <em>/<i>, <code>/<kbd>, <del>, <br>, <hr>, <img>).
 *
 * Images are loaded with Coil so README badges / banners / screenshots render properly inside
 * the Overview tab. Content images fill the column width at their natural aspect ratio (capped
 * for readability), while badge walls stay compact and inline. Relative image paths are resolved
 * to raw.githubusercontent.com using [repoContext] + [defaultBranch].
 *
 * Links are classified into kinds (see [LinkKind]) and rendered with distinct
 * color/icon/decoration so users can tell apart in-app GitHub destinations, downloadable assets,
 * image links, and external links at a glance.
 */

private const val LINK_TAG = "url"
private const val LINK_KIND_TAG = "kind"

/** Visual/logical kind of a clickable link. Lets the host screen route it appropriately. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    /** Current repo context — "owner/repo" — for resolving relative links/images. Null OK in non-repo contexts. */
    repoContext: String? = null,
    /** Default branch of the repo, used to resolve relative image paths to raw.githubusercontent.com. */
    defaultBranch: String? = null,
    /** Override link navigation. Default uses LocalUriHandler (system browser). Receives both
     *  the (already-resolved) URL and its [LinkKind], so the caller can route downloads, in-app
     *  navigation, and external opens differently. */
    onLinkClick: ((url: String, kind: LinkKind) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val downloadColor = MaterialTheme.colorScheme.tertiary
    val imageLinkColor = MaterialTheme.colorScheme.secondary
    val externalColor = MaterialTheme.colorScheme.primary

    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val blockShape = RoundedCornerShape(8.dp)
    val linkResolver = rememberLinkResolver(repoContext)
    val imageResolver = rememberImageResolver(repoContext, defaultBranch)
    val uriHandler = LocalUriHandler.current
    val imagePreviewer = com.pockethub.ui.components.LocalImagePreviewer.current

    val onTap: (String, LinkKind) -> Unit = { url, kind ->
        when {
            // Image links: prefer the in-app zoomable preview if the host screen has
            // registered one. Markdown README / issue / PR bodies carry screenshots /
            // diagrams which the web UI opens inline; routing them to the browser is
            // a worse experience, so we hijack the tap here. We only hijack when the
            // image pointer is the link target (wrapUrl set to the image itself, or the
            // inline image src with no wrapping link) — keeping wrapped-link cases
            // (an image wrapped around a click to another URL) routed through onLinkClick.
            (kind == LinkKind.IMAGE_URL || kind == LinkKind.IMAGE) && imagePreviewer != null -> {
                imagePreviewer(url)
            }
            onLinkClick != null -> onLinkClick(url, kind)
            else -> uriHandler.openUri(url)
        }
    }

    // Parse OFF the main thread: large documents (600KB+ awesome-lists) take
    // seconds-to-minutes synchronously and would ANR the Overview tab. Also
    // cap the input so pathological docs stay renderable.
    val parseResult by produceState<Result<List<MdBlock>>>(
        initialValue = Result.success(emptyList()),
        key1 = markdown,
    ) {
        value = runCatching {
            withContext(Dispatchers.Default) {
                parseMarkdown(cleanMarkdown(truncateOversized(markdown)))
            }
        }
    }
    Column(modifier = modifier) {
        parseResult.onFailure { MarkdownErrorBox(it) }
        parseResult.getOrNull()?.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        4 -> MaterialTheme.typography.titleMedium
                        5 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    if (block.level <= 2) Spacer(Modifier.height(if (block.level == 1) 10.dp else 6.dp))
                    // Render inline markdown (links, code, bold) inside headings so `## Getting `code``
                    // shows a code chip instead of literal backticks.
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RenderInlineParts(parts, style.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = when (block.level) {
                            1 -> 32.sp
                            2 -> 28.sp
                            else -> 24.sp
                        },
                    ), onTap)
                    if (block.level <= 2) Spacer(Modifier.height(2.dp))
                }

                is MdBlock.Paragraph -> {
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichParagraph(parts, onTap, paragraphSpacing = 4.dp)
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(blockShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, blockShape)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.Blockquote -> {
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichBlockquote(parts, accentColor, mutedColor, onTap)
                }

                is MdBlock.ListItem -> {
                    val bullet = when {
                        block.ordered -> "${block.index}. "
                        block.task == 'x' -> "☑ "
                        block.task == ' ' -> "☐ "
                        else -> "• "
                    }
                    val indent = (block.level - 1) * 14
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichListItem(bullet, parts, indent, mutedColor, onTap)
                }

                is MdBlock.Table -> {
                    TableBlock(
                        block,
                        linkResolver,
                        imageResolver,
                        codeBackgroundColor,
                        linkColor,
                        downloadColor,
                        imageLinkColor,
                        externalColor,
                        onTap,
                    )
                }

                is MdBlock.HorizontalRule -> {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun MarkdownErrorBox(error: Throwable) {
    val trace = androidx.compose.runtime.remember(error) {
        error.stackTraceToString().take(1500)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            "README 解析出错: ${error.javaClass.simpleName}: ${error.message ?: ""}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            trace,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// ── Block types ──────────────────────────────────────────────────────

internal sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    /** `task`: null = not a task item; ' ' = unchecked; 'x' = checked. */
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int, val task: Char? = null) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object HorizontalRule : MdBlock()
}

// ── Inline tokens (rich — can mix text + images in one paragraph) ─────

internal sealed class InlineToken {
    /** Flowable annotated text — clickable links live here. */
    data class Text(val span: AnnotatedString) : InlineToken()
    /** Standalone image. `wrapUrl` non-null → image is wrapped in a link (render with hover style). */
    data class Image(val src: String, val alt: String, val wrapUrl: String?) : InlineToken()
}

@Composable
