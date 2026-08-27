package com.pockethub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive column count for a content grid, based on the available width.
 * Phones (~<600dp) stay single-column; tablets / landscape get 2 columns and
 * large screens 3. Works with LazyVerticalGrid via GridCells.Fixed(columns).
 */
fun adaptiveColumnCount(maxWidth: Dp): Int = when {
    maxWidth >= 840.dp -> 3
    maxWidth >= 600.dp -> 2
    else -> 1
}

/** Adaptive grid cells: single column on phones, multi-column on larger widths. */
@Composable
fun adaptiveGridCells(contentWidth: Dp = 0.dp): androidx.compose.foundation.lazy.grid.GridCells {
    // If caller doesn't pass a measured width, fall back to a floor-based
    // Adaptive cell size so cards never get absurdly wide.
    return if (contentWidth > 0.dp) {
        androidx.compose.foundation.lazy.grid.GridCells.Fixed(adaptiveColumnCount(contentWidth))
    } else {
        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 320.dp)
    }
}

/**
 * Enhanced card with elevation, subtle gradient, and press animation.
 * Redesigned: hairline border + whisper gradient + spring press feedback,
 * implemented on top of the shared design system for a consistent look.
 */
@Composable
fun EnhancedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 2.dp,
    cornerRadius: Dp = 16.dp,
    gradientIntensity: Float = 0.15f,
    enablePressEffect: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enablePressEffect) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "card_scale"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = gradientIntensity * 0.6f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isPressed) 0.9f else 0.55f)
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        shape = shape,
        color = surfaceColor,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to primaryTint.copy(alpha = primaryTint.alpha * 0.35f),
                        1f to Color.Transparent,
                    )
                )
                .border(1.dp, borderColor, shape)
                .padding(16.dp)
                .let { m ->
                    if (onClick != null) m.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ) else m
                }
        ) {
            Column(content = content)
        }
    }
}

/**
 * Glassmorphism card with blur-like effect and semi-transparent background.
 * Best for overlay or hero content.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(20.dp)
        ) {
            Column(content = content)
        }
    }
}

/**
 * Neumorphic (soft UI) card with inner/outer shadows to create depth illusion.
 * Works best with light themes.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isPressed: Boolean = false,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseColor = MaterialTheme.colorScheme.surface
    val lightShadow = Color.White.copy(alpha = 0.8f)
    val darkShadow = Color.Black.copy(alpha = 0.15f)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(baseColor)
            .drawBehind {
                if (!isPressed) {
                    drawNeumorphicShadow(
                        cornerRadius = cornerRadius.toPx(),
                        lightColor = lightShadow,
                        darkColor = darkShadow
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

private fun DrawScope.drawNeumorphicShadow(
    cornerRadius: Float,
    lightColor: Color,
    darkColor: Color,
) {
    // Simplified neumorphic effect - light from top-left, dark from bottom-right
    val offset = 8f
    
    // Dark shadow (bottom-right)
    drawRoundRect(
        color = darkColor,
        topLeft = Offset(offset, offset),
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )
    
    // Light shadow (top-left) - simulated as highlight
    drawRoundRect(
        color = lightColor,
        topLeft = Offset(-offset, -offset),
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )
}

/**
 * Floating action button with gradient and glow effect.
 */
@Composable
fun GlowingFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "fab_scale"
    )
    
    val gradientColors = listOf(
        glowColor,
        glowColor.copy(alpha = 0.8f)
    )
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .scale(animatedScale)
            .size(56.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.radialGradient(gradientColors))
                .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Section header with subtle underline gradient.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor,
                            accentColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Shimmer loading placeholder with animated gradient.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(offset, 0f),
                    end = Offset(offset + 300f, 0f)
                )
            )
    )
}
