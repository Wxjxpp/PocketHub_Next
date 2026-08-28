package com.pockethub.ui.components

// Themed async image with skeleton-shimmer placeholder, error state and
// fade-in. Replaces raw coil AsyncImage across the app so every image shows a
// themed loading placeholder instead of popping in (or blank space).

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/**
 * App-standard async image:
 * - While loading: shimmering [SkeletonBox] placeholder (themed).
 * - On failure: quiet broken-image glyph on surfaceVariant.
 * - On success: content fades in.
 *
 * Signature mirrors coil's AsyncImage so call sites migrate by name only.
 * Default [contentScale] matches AsyncImage (Fit) to keep behavior unchanged.
 */
@Composable
fun PhAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoader? = null,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        imageLoader = imageLoader ?: coil.Coil.imageLoader(androidx.compose.ui.platform.LocalContext.current),
    ) {
        when (painter.state) {
            is coil.compose.AsyncImagePainter.State.Loading ->
                SkeletonBox(Modifier.fillMaxSize(), cornerRadius = 8.dp)
            is coil.compose.AsyncImagePainter.State.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxSize(0.5f),
                )
            }
            else -> {
                // Fade the loaded image in so the skeleton-to-content swap is soft.
                var shown by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) { shown = 1f }
                val alpha by animateFloatAsState(
                    targetValue = shown,
                    animationSpec = tween(220),
                    label = "ph_image_fade",
                )
                SubcomposeAsyncImageContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}
