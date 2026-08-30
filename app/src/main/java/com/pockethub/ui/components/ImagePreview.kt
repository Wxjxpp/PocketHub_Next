package com.pockethub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.pockethub.R
import com.pockethub.data.download.DownloadManager
import com.pockethub.ui.LocalAppImageLoader
import com.pockethub.ui.download.DownloadViewModel
import kotlinx.coroutines.launch

/**
 * Composition-local providing a function that opens a full-screen zoomable
 * preview over [urls], starting at [startIndex]. Screens rendering user-facing
 * markdown copy should route `LinkKind.IMAGE_URL` / `IMAGE` taps through this
 * instead of the browser.
 *
 * Default is a no-op so leaf composables don't crash if a screen forgets to provide
 * it (root AppNavigation wires the real implementation).
 */
val LocalImagePreviewer = staticCompositionLocalOf<((urls: List<String>, startIndex: Int) -> Unit)?> { null }

/**
 * Full-screen image preview over [imageUrls] with left/right swiping between
 * images (ViewPager-style), pinch + double-tap zoom per page, and a download
 * button that enqueues the current image into the app's download manager.
 *
 * - At 1x: single tap exits, horizontal swipe changes image, double tap zooms to 2x.
 * - At >1x: single tap resets to 1x, pan/zoom gestures are captured by the
 *   image so the pager doesn't swipe mid-zoom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onBack: () -> Unit,
    downloadVm: DownloadViewModel = hiltViewModel(),
) {
    val urls = imageUrls.ifEmpty { listOf("") }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.lastIndex),
        pageCount = { urls.size },
    )
    val context = LocalContext.current

    fun enqueueCurrent() {
        val url = urls.getOrNull(pagerState.currentPage) ?: return
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "image" }
        downloadVm.enqueue(
            DownloadManager.EnqueueRequest(
                url = url,
                fileName = name,
                contentType = com.pockethub.ui.repo.guessAssetMime(name),
                sizeBytes = 0L,
                repoKey = "common",
                releaseTag = "",
            )
        )
        android.widget.Toast.makeText(context, context.getString(R.string.queued_download), android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = urls.getOrNull(pagerState.currentPage)
                        ?.substringAfterLast('/')?.substringBefore('?').orEmpty().ifBlank { urls.firstOrNull() ?: "" }
                    Text(
                        if (urls.size > 1) "$name  (${pagerState.currentPage + 1}/${urls.size})" else name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = ::enqueueCurrent) {
                        Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.action_download), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
            key = { urls[it] },
        ) { page ->
            ZoomableImage(
                url = urls[page],
                onExit = onBack,
            )
        }
    }
}

/**
 * One pager page: a fit-to-screen zoomable image. Zoom/pan gestures are only
 * attached while zoomed in, so at 1x the parent pager owns horizontal swipes.
 */
@Composable
private fun ZoomableImage(url: String, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scale = remember(url) { Animatable(1f) }
    val offsetX = remember(url) { Animatable(0f) }
    val offsetY = remember(url) { Animatable(0f) }
    // Transform gestures (pinch/pan) are enabled only when zoomed — at 1x the
    // image lets the pager consume horizontal drags.
    var zoomed by remember(url) { mutableStateOf(false) }

    fun animateTo(target: Float, dx: Float = 0f, dy: Float = 0f) {
        scope.launch { scale.animateTo(target, tween(180)) }
        scope.launch { offsetX.animateTo(dx, tween(180)) }
        scope.launch { offsetY.animateTo(dy, tween(180)) }
    }

    val current = scale.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(url) {
                detectTapGestures(
                    onTap = {
                        if (current > 1.05f) {
                            zoomed = false
                            animateTo(1f)
                        } else {
                            onExit()
                        }
                    },
                    onDoubleTap = {
                        // Toggle between 1x and 2x — centered pan stays at 0,0 for predictable UX.
                        val target = if (current > 1.05f) 1f else 2f
                        zoomed = target > 1.05f
                        animateTo(target)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            imageLoader = LocalAppImageLoader.current,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                }
                .then(
                    if (zoomed) {
                        Modifier.pointerInput(url) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scope.launch {
                                    val next = (scale.value * zoom).coerceIn(1f, 6f)
                                    scale.snapTo(next)
                                    if (next > 1f) {
                                        launch { offsetX.snapTo(offsetX.value + pan.x) }
                                        launch { offsetY.snapTo(offsetY.value + pan.y) }
                                    } else {
                                        zoomed = false
                                        launch { offsetX.snapTo(0f) }
                                        launch { offsetY.snapTo(0f) }
                                    }
                                }
                            }
                        }
                    } else Modifier
                ),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                }
            },
            error = {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.BrokenImage, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text(
                            url,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }
            },
        )
    }
}
