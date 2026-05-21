package dev.migs.scan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.migs.scan.data.Scan

/**
 * Page-management screen. The user can move pages up/down, delete pages,
 * or append new ones via the document scanner. Every action goes through
 * the supplied callbacks; the host wires those into [ScanViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPageEditor(
    scan: Scan,
    onClose: () -> Unit,
    onMovePage: (from: Int, to: Int) -> Unit,
    onDeletePage: (index: Int) -> Unit,
    onAddPages: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var confirmDeleteIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit pages", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = scan.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPages,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add page") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(scan.pages, key = { _, file -> file.absolutePath }) { index, file ->
                PageEditorRow(
                    index = index,
                    pageFile = file,
                    totalPages = scan.pages.size,
                    onMoveUp = { onMovePage(index, index - 1) },
                    onMoveDown = { onMovePage(index, index + 1) },
                    onDelete = {
                        if (scan.pages.size > 1) confirmDeleteIndex = index
                    },
                    isDeleteEnabled = scan.pages.size > 1,
                )
            }
        }
    }

    confirmDeleteIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { confirmDeleteIndex = null },
            title = { Text("Delete page ${index + 1}?") },
            text = { Text("The remaining pages will be renumbered.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteIndex = null
                    onDeletePage(index)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIndex = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PageEditorRow(
    index: Int,
    pageFile: java.io.File,
    totalPages: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    isDeleteEnabled: Boolean,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(pageFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "of $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = index < totalPages - 1) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
            }
            IconButton(onClick = onDelete, enabled = isDeleteEnabled) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete page",
                    tint = if (isDeleteEnabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
