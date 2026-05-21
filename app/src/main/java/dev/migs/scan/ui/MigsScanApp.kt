package dev.migs.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.migs.scan.data.Scan
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigsScanApp(vm: ScanViewModel = viewModel()) {
    val scans by vm.scans.collectAsStateWithLifecycle()
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
                ScanList(scans = scans, padding = padding)
            }
        }
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
            Spacer(Modifier.height(0.dp))
            Text("  Scan a document", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ScanList(scans: List<Scan>, padding: PaddingValues) {
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
        items(scans, key = { it.id }) { scan -> ScanRow(scan) }
    }
}

private val rowDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
    .withZone(ZoneId.systemDefault())

@Composable
private fun ScanRow(scan: Scan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
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
        }
    }
}
