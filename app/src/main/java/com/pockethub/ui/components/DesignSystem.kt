package com.pockethub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PocketHub motion tokens. One place to keep every animation consistent:
 * springs for interactions (press / selection), tweens for entrances.
 */
object Motion {
    /** Interactive press / toggle springs — snappy with a slight bounce. */
    fun press() = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun settle() = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)

    /** Screen / element entrance tweens. */
    fun enter(millis: Int = 260) = tween<Float>(millis, easing = FastOutSlowInEasing)

    /** Stagger step between list items, ms. */
    const val STAGGER_STEP_MS = 45
    /** Cap for the per-item entrance delay so long lists stay responsive. */
    const val MAX_STAGGER_MS = 360
}

/**
 * Press feedback: the content scales down slightly while pressed and springs
 * back on release. Apply to any clickable element for a tactile feel.
 */
@Composable
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "press_scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The signature card of the redesigned UI: a softly-lit surface with a hairline
 * border, a whisper of vertical light and spring press feedback. Replaces
 * flat/boxed list items everywhere.
 */
@Composable
fun PhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 18.dp,
    container: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = Motion.press(),
        label = "ph_card_press",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val border by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(120),
        label = "ph_card_border",
    )

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        color = container,
        shadowElevation = if (pressed) 0.dp else 0.dp,
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                        1f to Color.Transparent,
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            borderColor.copy(alpha = 0.9f + 0.1f * border),
                            borderColor.copy(alpha = 0.35f),
                        )
                    ),
                    shape = shape,
                )
                .let { m ->
                    if (onClick != null) m.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ) else m
                },
        ) {
            content()
        }
    }
}

/** Soft circular icon plate used in cards, empty states and headers. */
@Composable
fun IconPlate(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    tint: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 14.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Staggered entrance: fades + slides an item up as it first composes. Give each
 * list item an [index] and the whole list animates in a gentle cascade.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val delay = (index * Motion.STAGGER_STEP_MS).coerceAtMost(Motion.MAX_STAGGER_MS)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220, delayMillis = delay)) +
            slideInVertically(tween(260, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 6 },
    ) {
        content()
    }
}

// ── Skeleton loading ─────────────────────────────────────────────────────────

/** A single shimmering block. Compose your own skeletons from these. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    shape: Shape? = null,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton_shift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val shapeOrDefault = shape ?: RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .clip(shapeOrDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(shift * 600f, 0f),
                    end = androidx.compose.ui.geometry.Offset((shift + 1f) * 600f, 250f),
                )
            )
    )
}

/** One skeleton list row that mirrors the redesigned card layout. */
@Composable
fun SkeletonCardRow(modifier: Modifier = Modifier) {
    PhCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(44.dp), shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.55f).height(15.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.85f).height(11.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.35f).height(11.dp))
            }
        }
    }
}

/** Full-screen skeleton list — the default "loading" look of the app. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    topPadding: Dp = 8.dp,
) {
    Column(
        modifier.fillMaxWidth().padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) { SkeletonCardRow() }
    }
}

// ── Small functional atoms ───────────────────────────────────────────────────

/** Rounded count/tag pill (stars, forks, unread…). */
@Composable
fun CountPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/** Colored language dot with its name. */
@Composable
fun LanguageDot(name: String, color: Color?, modifier: Modifier = Modifier) {
    if (name.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(9.dp).clip(CircleShape).background(
                color ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Redesigned empty state: icon on a soft plate, scale-in, friendly copy.
 */
@Composable
fun EmptyStateV2(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "empty_scale",
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale
            }
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Section header with an accent tick — consistent section titles app-wide. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}
