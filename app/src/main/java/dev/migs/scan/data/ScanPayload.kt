package dev.migs.scan.data

import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * A scanner-agnostic input to [ScanStore.persist]. Lets us persist scans
 * driven by ML Kit in production and by file:// URIs in tests.
 */
data class ScanPayload(
    val pdf: Uri?,
    val pages: List<Uri>,
) {
    companion object {
        fun fromMlKit(result: GmsDocumentScanningResult): ScanPayload =
            ScanPayload(
                pdf = result.pdf?.uri,
                pages = result.pages.orEmpty().map { it.imageUri },
            )
    }
}
