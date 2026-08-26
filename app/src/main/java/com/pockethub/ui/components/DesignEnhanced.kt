package com.pockethub.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enhanced card with elevation, subtle gradient, and press animation.
 * Provides a more premium visual feel compared to plain Surface.
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
    
    // Animated elevation on press
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed && enablePressEffect) elevation * 0.5f else elevation,
        animationSpec = spring(stiffness = 400f),
        label = "card_elevation"
    )
    
    // Animated scale on press
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enablePressEffect) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "card_scale"
    )
    
    // Gradient colors based on theme
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = gradientIntensity)
    
    val gradient = Brush.verticalGradient(
        colors = listOf(
            surfaceColor.copy(alpha = 0.6f),
            surfaceColor.copy(alpha = 0.9f)
        )
    )
    
    Surface(
        modifier = modifier
            .scale(animatedScale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = animatedElevation,
        tonalElevation = animatedElevation / 2,
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(16.dp)
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
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
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
