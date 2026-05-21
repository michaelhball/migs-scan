package dev.migs.scan.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

class ScanStore(private val context: Context) {

    private val root: File by lazy {
        File(context.filesDir, "scans").apply { mkdirs() }
    }

    suspend fun persist(result: GmsDocumentScanningResult): Scan = withContext(Dispatchers.IO) {
        val createdAt = Instant.now()
        val id = "${createdAt.toEpochMilli()}-${UUID.randomUUID().toString().take(8)}"
        val dir = File(root, id).apply { mkdirs() }

        val pdfUri = result.pdf?.uri
            ?: error("Document scanner returned no PDF")
        val pdf = File(dir, "doc.pdf").also { copyFromUri(pdfUri, it) }

        val pages = result.pages.orEmpty().mapIndexed { index, page ->
            File(dir, "page-${index + 1}.jpg").also { copyFromUri(page.imageUri, it) }
        }

        Scan(id = id, createdAt = createdAt, pdf = pdf, pages = pages)
    }

    suspend fun loadAll(): List<Scan> = withContext(Dispatchers.IO) {
        root.listFiles()?.filter { it.isDirectory }
            ?.mapNotNull { dir -> loadOne(dir) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    suspend fun delete(scan: Scan) = withContext(Dispatchers.IO) {
        scan.pdf.parentFile?.deleteRecursively()
    }

    private fun loadOne(dir: File): Scan? {
        val pdf = File(dir, "doc.pdf").takeIf { it.exists() } ?: return null
        // The id prefix is the epoch millis the scan was saved at — recover
        // the timestamp from there so the on-disk layout is the source of truth.
        val createdAt = dir.name.substringBefore('-').toLongOrNull()
            ?.let(Instant::ofEpochMilli)
            ?: Instant.ofEpochMilli(dir.lastModified())
        val pages = dir.listFiles { f -> f.name.startsWith("page-") && f.extension == "jpg" }
            ?.sortedBy { it.name }
            ?.toList()
            .orEmpty()
        return Scan(id = dir.name, createdAt = createdAt, pdf = pdf, pages = pages)
    }

    private fun copyFromUri(uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
