package dev.migs.scan.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.migs.scan.settings.ScannerUi

/**
 * Returns a callback that launches the ML Kit document scanner. Caller
 * gets the parsed result via [onResult]; null means cancelled or failed.
 */
@Composable
fun rememberDocumentScannerLauncher(
    scannerUi: ScannerUi = ScannerUi.Full,
    onResult: (GmsDocumentScanningResult?) -> Unit,
): () -> Unit {
    val activity = LocalContext.current as? ComponentActivity
        ?: error("rememberDocumentScannerLauncher must be hosted inside a ComponentActivity")

    val scanner: GmsDocumentScanner = remember(scannerUi) {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(
                when (scannerUi) {
                    ScannerUi.Full -> SCANNER_MODE_FULL
                    ScannerUi.Quick -> SCANNER_MODE_BASE
                },
            )
            .setGalleryImportAllowed(true)
            .setPageLimit(if (scannerUi == ScannerUi.Quick) 1 else 50)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onResult(GmsDocumentScanningResult.fromActivityResultIntent(result.data))
        } else {
            onResult(null)
        }
    }

    return {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { onResult(null) }
    }
}
