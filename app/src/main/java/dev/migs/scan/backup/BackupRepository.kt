package dev.migs.scan.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.migs.scan.data.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes scans into a user-chosen tree URI (Storage Access Framework).
 * Each scan becomes a single .zip containing every file in its scan
 * directory — doc.pdf, page-N.jpg, name.txt, text.txt, etc.
 *
 * Idempotent: a scan whose `<id>.zip` already exists in the target tree
 * is skipped on subsequent backups.
 */
class BackupRepository(private val context: Context) {

    /**
     * Writes any scans not already backed up. Returns the number of scans
     * actually written this run (i.e. skipped ones aren't counted).
     */
    suspend fun backupAll(treeUri: Uri, scans: List<Scan>): Int = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Backup folder no longer accessible (was it removed?)")
        require(tree.canWrite()) { "Backup folder is read-only" }

        var written = 0
        for (scan in scans) {
            val zipName = "${scan.id}.zip"
            if (tree.findFile(zipName) != null) continue  // already backed up
            val dir = scan.pdf.parentFile ?: continue  // can't happen, but be safe
            val target = tree.createFile("application/zip", zipName)
                ?: error("Could not create $zipName in the backup folder")
            try {
                context.contentResolver.openOutputStream(target.uri)?.use { os ->
                    ZipOutputStream(os).use { zip ->
                        // Include every file in the scan dir so a restore can
                        // recover name/text/star marker, not just the PDF.
                        dir.listFiles()?.forEach { file ->
                            if (!file.isFile) return@forEach
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Could not open output stream for $zipName")
                written++
            } catch (t: Throwable) {
                // Best-effort cleanup of a half-written zip on failure.
                runCatching { target.delete() }
                throw t
            }
        }
        written
    }

    /** Returns the human-readable name of the backup folder (or null if unset). */
    fun folderName(treeUri: Uri?): String? {
        if (treeUri == null) return null
        return DocumentFile.fromTreeUri(context, treeUri)?.name
    }
}
