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

/** Resolves an on-disk [File] to a content URI safe to share with other apps. */
fun interface UriProvider {
    fun uriFor(file: File): Uri
}

object Sharing {

    /** Default: wrap the FileProvider declared in the app manifest. */
    fun defaultUriProvider(context: Context): UriProvider = UriProvider { file ->
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    suspend fun buildShareIntent(
        context: Context,
        scan: Scan,
        format: ShareFormat,
        uriProvider: UriProvider = defaultUriProvider(context),
    ): Intent = withContext(Dispatchers.IO) {
        when (format) {
            ShareFormat.Pdf -> singleFileIntent(scan.pdf, format.mime, uriProvider)
            ShareFormat.Jpeg -> multipleFileIntent(scan.pages, format.mime, uriProvider)
            ShareFormat.Png -> multipleFileIntent(encodePages(context, scan), format.mime, uriProvider)
        }
    }

    private fun singleFileIntent(file: File, mime: String, uriProvider: UriProvider): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriProvider.uriFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let(::asChooser)

    private fun multipleFileIntent(files: List<File>, mime: String, uriProvider: UriProvider): Intent {
        val uris = ArrayList(files.map(uriProvider::uriFor))
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
