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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { pageIndex ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(scan.pages[pageIndex])
                                .crossfade(true)
                                .build(),
                            contentDescription = "Page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
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
