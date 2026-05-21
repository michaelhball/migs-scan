package dev.migs.scan.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.migs.scan.share.ShareFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    BackHandler(onBack = onClose)
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { SectionHeader("Scanner") }
            items(ScannerUi.entries) { ui ->
                RadioRow(
                    label = ui.label,
                    description = ui.description,
                    selected = settings.scannerUi == ui,
                    onSelect = { vm.setScannerUi(ui) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item { SectionHeader("Default share format") }
            item {
                RadioRow(
                    label = "Ask each time",
                    description = "Tap the share icon and pick a format from the sheet.",
                    selected = settings.defaultShareFormat == null,
                    onSelect = { vm.setDefaultShareFormat(null) },
                )
            }
            items(ShareFormat.entries) { format ->
                RadioRow(
                    label = format.label,
                    description = "Skip the sheet and share as ${format.label} when tapping the share icon.",
                    selected = settings.defaultShareFormat == format,
                    onSelect = { vm.setDefaultShareFormat(format) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider()
}

@Composable
private fun RadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ScannerUi.label: String
    get() = when (this) {
        ScannerUi.Full -> "Full editor"
        ScannerUi.Quick -> "Quick capture"
    }

private val ScannerUi.description: String
    get() = when (this) {
        ScannerUi.Full -> "Multi-page, auto/manual toggle, crop + filter editing."
        ScannerUi.Quick -> "One snap and done — no editor, no multi-page."
    }

private val ShareFormat.label: String
    get() = when (this) {
        ShareFormat.Pdf -> "PDF"
        ShareFormat.Jpeg -> "JPEG"
        ShareFormat.Png -> "PNG"
    }

