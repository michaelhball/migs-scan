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
    private val textExtractor: TextExtractor = TextExtractor.mlKit(context),
    private val pdfBuilder: (pages: List<File>, outFile: File) -> Unit = PdfBuilder::build,
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

        val perPage = mutableListOf<String>()
        for ((index, page) in pages.withIndex()) {
            val txt = textExtractor.extractFrom(page)
            perPage.add(txt)
            // Per-page text files so reorder / delete edits can preserve OCR
            // without re-extracting kept pages.
            if (txt.isNotEmpty()) File(dir, "page-${index + 1}.txt").writeText(txt)
        }
        val text = perPage.joinToString("\n\n").trim()
        if (text.isNotEmpty()) File(dir, TextFile).writeText(text)

        Scan(id = id, name = name, createdAt = createdAt, pdf = pdf, pages = pages, starred = false, text = text)
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

    /** Reorders [scan]'s pages to match [newOrder] (a permutation of indices). */
    suspend fun reorderPages(scan: Scan, newOrder: List<Int>): Scan = withContext(ioDispatcher) {
        require(newOrder.toSet() == scan.pages.indices.toSet()) {
            "newOrder must be a permutation of the current page indices"
        }
        rebuild(scan, newOrder.map(PageSource::Existing))
    }

    /** Removes a single page; returns the updated Scan. */
    suspend fun deletePage(scan: Scan, pageIndex: Int): Scan = withContext(ioDispatcher) {
        require(pageIndex in scan.pages.indices) { "pageIndex out of range" }
        require(scan.pages.size > 1) {
            "Refusing to delete the only page — delete the whole scan instead."
        }
        val remaining = scan.pages.indices.toList() - pageIndex
        rebuild(scan, remaining.map(PageSource::Existing))
    }

    /** Appends every page in [payload] to the end of [scan]. */
    suspend fun appendPages(scan: Scan, payload: ScanPayload): Scan = withContext(ioDispatcher) {
        require(payload.pages.isNotEmpty()) { "No pages to append" }
        val existing = scan.pages.indices.map(PageSource::Existing)
        val added = payload.pages.map(PageSource::New)
        rebuild(scan, existing + added)
    }

    private sealed interface PageSource {
        data class Existing(val originalIndex: Int) : PageSource
        data class New(val uri: Uri) : PageSource
    }

    private suspend fun rebuild(scan: Scan, ordering: List<PageSource>): Scan {
        require(ordering.isNotEmpty()) { "A scan must have at least one page" }
        val dir = scan.pdf.parentFile ?: error("Scan dir missing")

        // 1. Materialise each source into a (currentFile, OCR text) pair.
        //    Existing kept pages reuse their stored OCR; new pages get extracted.
        val resolved = ordering.map { src ->
            when (src) {
                is PageSource.Existing -> {
                    val jpg = scan.pages[src.originalIndex]
                    val txt = pageTextFile(jpg).takeIf { it.exists() }?.readText().orEmpty()
                    jpg to txt
                }
                is PageSource.New -> {
                    val tmp = File(dir, "stage-new-${UUID.randomUUID().toString().take(8)}.jpg")
                    copyFromUri(src.uri, tmp)
                    val txt = textExtractor.extractFrom(tmp)
                    tmp to txt
                }
            }
        }

        // 2. Move every kept JPG into a stage-N.jpg slot so the rename to
        //    page-N.jpg below can't collide with a kept page's old name.
        val staged = resolved.mapIndexed { i, (jpg, txt) ->
            val stagedFile = File(dir, "stage-$i.jpg")
            check(jpg.renameTo(stagedFile)) { "Could not stage ${jpg.name}" }
            stagedFile to txt
        }

        // 3. Delete any leftover page-* files (jpg or txt) from the previous version.
        dir.listFiles { f -> f.name.matches(LeftoverPagePattern) }?.forEach { it.delete() }

        // 4. Move staged files into their final page-N.jpg / page-N.txt slots.
        val finalPages = staged.mapIndexed { i, (stagedFile, txt) ->
            val finalJpg = File(dir, "page-${i + 1}.jpg")
            check(stagedFile.renameTo(finalJpg)) { "Could not finalise page ${i + 1}" }
            if (txt.isNotEmpty()) File(dir, "page-${i + 1}.txt").writeText(txt)
            finalJpg
        }

        // 5. Rebuild PDF + combined text.
        pdfBuilder(finalPages, File(dir, "doc.pdf"))
        val combined = staged.joinToString("\n\n") { it.second }.trim()
        val textFile = File(dir, TextFile)
        if (combined.isNotEmpty()) textFile.writeText(combined) else textFile.delete()

        return scan.copy(pages = finalPages, text = combined)
    }

    private fun pageTextFile(jpg: File): File =
        File(jpg.parentFile, jpg.nameWithoutExtension + ".txt")

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
        val text = File(dir, TextFile).takeIf { it.exists() }?.readText().orEmpty()
        return Scan(
            id = dir.name,
            name = name,
            createdAt = createdAt,
            pdf = pdf,
            pages = pages,
            starred = starred,
            text = text,
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
        private const val TextFile = "text.txt"
        private val LeftoverPagePattern = Regex("""page-\d+\.(jpg|txt)""")
        private val DefaultNameFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm").withZone(ZoneId.systemDefault())

        internal fun defaultName(createdAt: Instant): String = "Scan ${DefaultNameFormat.format(createdAt)}"
    }
}
