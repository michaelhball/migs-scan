package dev.migs.scan.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dev.migs.scan.data.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Sharing {

    suspend fun buildShareIntent(context: Context, scan: Scan, format: ShareFormat): Intent =
        withContext(Dispatchers.IO) {
            when (format) {
                ShareFormat.Pdf -> singleFileIntent(context, scan.pdf, format.mime)
                ShareFormat.Jpeg -> multipleFileIntent(context, scan.pages, format.mime)
                ShareFormat.Png -> multipleFileIntent(context, encodePages(context, scan), format.mime)
            }
        }

    private fun singleFileIntent(context: Context, file: File, mime: String): Intent {
        val uri = uriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let(::asChooser)
    }

    private fun multipleFileIntent(context: Context, files: List<File>, mime: String): Intent {
        val uris = ArrayList(files.map { uriFor(context, it) })
        val base = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        return base.apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let(::asChooser)
    }

    private fun asChooser(intent: Intent): Intent =
        Intent.createChooser(intent, "Share scan").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

    private fun encodePages(context: Context, scan: Scan): List<File> {
        val dir = File(context.cacheDir, "png/${scan.id}").apply { mkdirs() }
        return scan.pages.mapIndexed { index, jpg ->
            val target = File(dir, "page-${index + 1}.png")
            if (!target.exists() || target.lastModified() < jpg.lastModified()) {
                val bmp: Bitmap = BitmapFactory.decodeFile(jpg.absolutePath)
                    ?: error("Could not decode ${jpg.name}")
                target.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
            target
        }
    }
}
