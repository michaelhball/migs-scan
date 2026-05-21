package dev.migs.scan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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

    val launchScanner = rememberDocumentScannerLauncher { result ->
        if (result != null) vm.onScanResult(result)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("MigsScan") }) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = launchScanner,
                    icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                    text = { Text("Scan") },
                )
            },
        ) { padding ->
            if (scans.isEmpty()) {
                EmptyState(padding = padding, onScanClick = launchScanner)
            } else {
                ScanList(scans = scans, padding = padding, onSelect = { openedScan = it })
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
            onDelete = {
                vm.delete(scan)
                openedScan = null
            },
        )
    }
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
            Text("  Scan a document", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ScanList(
    scans: List<Scan>,
    padding: PaddingValues,
    onSelect: (Scan) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(scans, key = { it.id }) { scan -> ScanRow(scan, onClick = { onSelect(scan) }) }
    }
}

private val rowDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
    .withZone(ZoneId.systemDefault())

@Composable
private fun ScanRow(scan: Scan, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${scan.pages.size} page${if (scan.pages.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rowDateFormat.format(scan.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanActionSheet(
    scan: Scan,
    onDismiss: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "${scan.pages.size}-page scan · ${rowDateFormat.format(scan.createdAt)}",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
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
                icon = Icons.Filled.Delete,
                label = "Delete",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
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

