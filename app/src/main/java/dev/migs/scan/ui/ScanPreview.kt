package dev.migs.scan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.migs.scan.data.Scan

/**
 * Full-screen viewer for a single [Scan]. Pages are arranged in a horizontal
 * pager so the user can swipe through them; the top bar exposes a back arrow
 * to return to the list and a kebab to open the action sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreview(
    scan: Scan,
    onClose: () -> Unit,
    onMoreActions: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val pagerState = rememberPagerState(pageCount = { scan.pages.size.coerceAtLeast(1) })
    val context = LocalContext.current
    // When any page is zoomed in, suppress the pager's horizontal swipe so
    // panning the zoomed page doesn't accidentally flip to the next one.
    var zoomedPageScale by remember { mutableFloatStateOf(1f) }
    val isZoomed = zoomedPageScale > 1.01f
    LaunchedEffect(pagerState.currentPage) {
        // Reset zoom whenever the user lands on a new page.
        zoomedPageScale = 1f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = scan.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        if (scan.pages.size > 1) {
                            Text(
                                text = "Page ${pagerState.currentPage + 1} of ${scan.pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "1 page",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onMoreActions) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color.Black,
        ) {
            if (scan.pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No pages to preview", color = Color.White)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isZoomed,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { pageIndex ->
                        ZoomablePage(
                            model = scan.pages[pageIndex],
                            contentDescription = "Page ${pageIndex + 1}",
                            onScaleChange = { newScale ->
                                if (pageIndex == pagerState.currentPage) {
                                    zoomedPageScale = newScale
                                }
                            },
                        )
                    }
                    if (scan.pages.size > 1) {
                        PagerDots(
                            count = scan.pages.size,
                            current = pagerState.currentPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One pager-friendly page: AsyncImage wrapped in pinch-to-zoom + pan, with
 * double-tap toggling between fit and 2.5x. Reports its current scale up to
 * the host so the pager can disable horizontal swiping while zoomed in.
 */
@Composable
private fun ZoomablePage(
    model: Any,
    contentDescription: String?,
    onScaleChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun setScale(newScale: Float) {
        scale = newScale.coerceIn(MinZoom, MaxZoom)
        if (scale <= 1.01f) offset = Offset.Zero
        onScaleChange(scale)
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        setScale(scale * zoom)
        if (scale > 1f) {
            offset = offset + pan
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        setScale(if (scale > 1.01f) 1f else DoubleTapZoom)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

private const val MinZoom = 1f
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val color = if (i == current) Color.White else Color.White.copy(alpha = 0.4f)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, shape = androidx.compose.foundation.shape.CircleShape),
            )
            if (i < count - 1) androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
    }
}
