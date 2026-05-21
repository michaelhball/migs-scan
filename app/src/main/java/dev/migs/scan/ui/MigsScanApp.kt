package dev.migs.scan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.migs.scan.data.Scan
import dev.migs.scan.share.ShareFormat
import dev.migs.scan.share.Sharing
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigsScanApp(vm: ScanViewModel = viewModel()) {
    val scans by vm.scans.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openedScan by remember { mutableStateOf<Scan?>(null) }
    var renamingScan by remember { mutableStateOf<Scan?>(null) }
    var previewScan by remember { mutableStateOf<Scan?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val inSelectionMode = selectedIds.isNotEmpty()
    BackHandler(enabled = inSelectionMode) { selectedIds = emptySet() }

    // Keep the previewed scan in sync if it gets renamed underneath us.
    val livePreviewScan = remember(previewScan, scans) {
        previewScan?.let { p -> scans.firstOrNull { it.id == p.id } ?: p }
    }
    val filtered = remember(scans, query) {
        val q = query.trim()
        if (q.isEmpty()) scans else scans.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.text.contains(q, ignoreCase = true)
        }
    }

    val launchScanner = rememberDocumentScannerLauncher { result ->
        if (result != null) vm.onScanResult(result)
    }

    if (livePreviewScan != null) {
        ScanPreview(
            scan = livePreviewScan,
            onClose = { previewScan = null },
            onMoreActions = { openedScan = livePreviewScan },
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    if (inSelectionMode) {
                        SelectionTopBar(
                            count = selectedIds.size,
                            onCancel = { selectedIds = emptySet() },
                            onDelete = { confirmDeleteAll = true },
                        )
                    } else {
                        TopAppBar(
                            title = { Text("MigsScan") },
                            // surfaceContainer reads as a subtle "bar" tier above the
                            // page background, so the title doesn't float in space.
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        )
                    }
                },
                floatingActionButton = {
                    // Hide the Scan FAB while selecting — its position competes
                    // with the contextual top bar's mental model.
                    if (!inSelectionMode) {
                        ExtendedFloatingActionButton(
                            onClick = launchScanner,
                            icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                            text = { Text("Scan") },
                        )
                    }
                },
            ) { padding ->
                when {
                    scans.isEmpty() -> EmptyState(padding = padding, onScanClick = launchScanner)
                    else -> ScanListWithSearch(
                        scans = scans,
                        filtered = filtered,
                        query = query,
                        onQueryChange = { query = it },
                        padding = padding,
                        selectedIds = selectedIds,
                        onOpenPreview = {
                            if (inSelectionMode) {
                                selectedIds = selectedIds.toggle(it.id)
                            } else {
                                previewScan = it
                            }
                        },
                        onOpenActions = { onClicked ->
                            if (inSelectionMode) {
                                selectedIds = selectedIds.toggle(onClicked.id)
                            } else {
                                openedScan = onClicked
                            }
                        },
                        onToggleStar = {
                            if (inSelectionMode) {
                                selectedIds = selectedIds.toggle(it.id)
                            } else {
                                vm.setStarred(it, !it.starred)
                            }
                        },
                        onLongPress = { selectedIds = selectedIds + it.id },
                    )
                }
            }
        }
    }

    openedScan?.let { scan ->
        ScanActionSheet(
            scan = scan,
            onDismiss = { openedScan = null },
            onShare = { format ->
                scope.launch {
                    val intent = Sharing.buildShareIntent(context, scan, format)
                    context.startActivity(intent)
                    openedScan = null
                }
            },
            onRename = {
                openedScan = null
                renamingScan = scan
            },
            onToggleStar = {
                vm.setStarred(scan, !scan.starred)
                openedScan = null
            },
            onDelete = {
                vm.delete(scan)
                openedScan = null
            },
        )
    }

    renamingScan?.let { scan ->
        RenameDialog(
            scan = scan,
            onConfirm = { newName ->
                vm.rename(scan, newName)
                renamingScan = null
            },
            onDismiss = { renamingScan = null },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete ${selectedIds.size} scan${if (selectedIds.size == 1) "" else "s"}?") },
            text = { Text("This permanently removes the scans from this device. Files already shared elsewhere are unaffected.") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedIds
                    confirmDeleteAll = false
                    selectedIds = emptySet()
                    vm.deleteAll(toDelete)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun EmptyState(padding: PaddingValues, onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No scans yet", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Tap below to capture your first document.")
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Scan a document", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ScanListWithSearch(
    scans: List<Scan>,
    filtered: List<Scan>,
    query: String,
    onQueryChange: (String) -> Unit,
    padding: PaddingValues,
    selectedIds: Set<String>,
    onOpenPreview: (Scan) -> Unit,
    onOpenActions: (Scan) -> Unit,
    onToggleStar: (Scan) -> Unit,
    onLongPress: (Scan) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding()),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search scans") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No scans match \"${query.trim()}\"",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${scans.size} total scan${if (scans.size == 1) "" else "s"} — try a different search",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { scan ->
                    ScanRow(
                        scan = scan,
                        selected = scan.id in selectedIds,
                        onClick = { onOpenPreview(scan) },
                        onLongClick = { onLongPress(scan) },
                        onShareClick = { onOpenActions(scan) },
                        onStarClick = { onToggleStar(scan) },
                    )
                }
            }
        }
    }
}

private val rowDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanRow(
    scan: Scan,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShareClick: () -> Unit,
    onStarClick: () -> Unit,
) {
    val border = if (selected) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
    } else {
        Modifier
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(border)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ScanThumbnail(scan)
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${scan.pages.size} page${if (scan.pages.size == 1) "" else "s"} · ${rowDateFormat.format(scan.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onStarClick) {
                if (scan.starred) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Unstar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = "Star",
                    )
                }
            }
            IconButton(onClick = onShareClick) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
        }
    }
}

@Composable
private fun ScanThumbnail(scan: Scan, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val firstPage = scan.pages.firstOrNull()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            // Subtle backdrop so blank/missing scans don't look like a layout hole.
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (firstPage != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(firstPage)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanActionSheet(
    scan: Scan,
    onDismiss: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onRename: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(
                    text = scan.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${scan.pages.size}-page scan · ${rowDateFormat.format(scan.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
            SheetAction(
                icon = Icons.Filled.PictureAsPdf,
                label = "Share as PDF",
                onClick = { onShare(ShareFormat.Pdf) },
            )
            SheetAction(
                icon = Icons.Filled.Image,
                label = "Share as JPEG",
                onClick = { onShare(ShareFormat.Jpeg) },
            )
            SheetAction(
                icon = Icons.Filled.Image,
                label = "Share as PNG",
                onClick = { onShare(ShareFormat.Png) },
            )
            HorizontalDivider()
            SheetAction(
                icon = if (scan.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                label = if (scan.starred) "Unstar" else "Star",
                onClick = onToggleStar,
            )
            SheetAction(
                icon = Icons.Filled.DriveFileRenameOutline,
                label = "Rename",
                onClick = onRename,
            )
            SheetAction(
                icon = Icons.Filled.Delete,
                label = "Delete",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RenameDialog(
    scan: Scan,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(scan.id) { mutableStateOf(scan.name) }
    val suggestion = remember(scan.id, text) { suggestedNameFromOcr(scan.text, text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename scan") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestion != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "From scan: ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { text = suggestion },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(
                            text = "“$suggestion”",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

