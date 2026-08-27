package com.pockethub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * Neumorphic (soft UI) card with inner/outer shadows to create depth illusion.
 * Works best with light themes.
 */


/**
 * Floating action button with gradient and glow effect.
 */

/**
 * Section header with subtle underline gradient.
 */

/**
 * Shimmer loading placeholder with animated gradient.
 */
