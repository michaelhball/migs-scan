package dev.migs.scan.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class ScanStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val root: File by lazy {
        File(context.filesDir, "scans").apply { mkdirs() }
    }

    suspend fun persist(payload: ScanPayload): Scan = withContext(ioDispatcher) {
        val createdAt = Instant.now()
        val id = "${createdAt.toEpochMilli()}-${UUID.randomUUID().toString().take(8)}"
        val dir = File(root, id).apply { mkdirs() }

        val pdfUri = payload.pdf ?: error("ScanPayload has no PDF")
        val pdf = File(dir, "doc.pdf").also { copyFromUri(pdfUri, it) }

        val pages = payload.pages.mapIndexed { index, uri ->
            File(dir, "page-${index + 1}.jpg").also { copyFromUri(uri, it) }
        }

        val name = defaultName(createdAt)
        File(dir, NameFile).writeText(name)

        Scan(id = id, name = name, createdAt = createdAt, pdf = pdf, pages = pages, starred = false)
    }

    suspend fun loadAll(): List<Scan> = withContext(ioDispatcher) {
        root.listFiles()?.filter { it.isDirectory }
            ?.mapNotNull { dir -> loadOne(dir) }
            // Starred scans pin to the top; within each group, newest first.
            ?.sortedWith(compareByDescending<Scan> { it.starred }.thenByDescending { it.createdAt })
            ?: emptyList()
    }

    suspend fun delete(scan: Scan) = withContext(ioDispatcher) {
        scan.pdf.parentFile?.deleteRecursively()
    }

    suspend fun rename(scan: Scan, newName: String): Scan = withContext(ioDispatcher) {
        val trimmed = newName.trim().ifEmpty { defaultName(scan.createdAt) }
        File(scan.pdf.parentFile, NameFile).writeText(trimmed)
        scan.copy(name = trimmed)
    }

    suspend fun setStarred(scan: Scan, starred: Boolean): Scan = withContext(ioDispatcher) {
        val marker = File(scan.pdf.parentFile, StarredFile)
        if (starred) marker.createNewFile() else marker.delete()
        scan.copy(starred = starred)
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
        val name = File(dir, NameFile).takeIf { it.exists() }?.readText()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultName(createdAt)
        val starred = File(dir, StarredFile).exists()
        return Scan(
            id = dir.name,
            name = name,
            createdAt = createdAt,
            pdf = pdf,
            pages = pages,
            starred = starred,
        )
    }

    private fun copyFromUri(uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    companion object {
        private const val NameFile = "name.txt"
        private const val StarredFile = "starred"
        private val DefaultNameFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm").withZone(ZoneId.systemDefault())

        internal fun defaultName(createdAt: Instant): String = "Scan ${DefaultNameFormat.format(createdAt)}"
    }
}
