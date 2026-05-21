package dev.migs.scan.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import androidx.core.content.FileProvider
import dev.migs.scan.data.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        val (files, mime) = sourcesFor(context, scan, format)
        val aliases = files.mapIndexed { i, src ->
            alias(context, scan, src, friendlyName(scan, format, i, files.size))
        }
        chooserIntent(aliases, mime, uriProvider)
    }

    /**
     * Builds an Intent for a quick-send preset: bypasses the chooser by
     * setting the target package, optionally pre-fills email recipients.
     * Callers should still wrap in [Intent.createChooser] if the preset's
     * packageName turns out to be invalid (handled below by callers).
     */
    suspend fun buildPresetIntent(
        context: Context,
        scan: Scan,
        preset: dev.migs.scan.settings.Preset,
        uriProvider: UriProvider = defaultUriProvider(context),
    ): Intent = withContext(Dispatchers.IO) {
        val (files, mime) = sourcesFor(context, scan, preset.format)
        val aliases = files.mapIndexed { i, src ->
            alias(context, scan, src, friendlyName(scan, preset.format, i, files.size))
        }
        val uris = ArrayList(aliases.map(uriProvider::uriFor))
        val base = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        base.apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            preset.packageName?.let { setPackage(it) }
            if (preset.emails.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, preset.emails.toTypedArray())
            }
            // Default subject for email apps; falls back gracefully elsewhere.
            putExtra(Intent.EXTRA_SUBJECT, scan.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun sourcesFor(context: Context, scan: Scan, format: ShareFormat): Pair<List<File>, String> =
        when (format) {
            ShareFormat.Pdf -> listOf(scan.pdf) to format.mime
            ShareFormat.Jpeg -> scan.pages to format.mime
            ShareFormat.Png -> encodePages(context, scan) to format.mime
        }

    private fun chooserIntent(files: List<File>, mime: String, uriProvider: UriProvider): Intent {
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

    /**
     * Returns a file under cacheDir/share/<scan-id>/ named [displayName] that
     * resolves to the same bytes as [source]. Uses a hard link if possible
     * (zero data copy, same filesystem) and falls back to a real copy if the
     * link syscall fails (e.g. SELinux denial on some OEM builds).
     *
     * The chooser preview shows the URI's last path segment as the filename,
     * so the alias's name is what users see in the share sheet.
     */
    private fun alias(context: Context, scan: Scan, source: File, displayName: String): File {
        val dir = File(context.cacheDir, "share/${scan.id}").apply { mkdirs() }
        val target = File(dir, displayName)
        if (target.exists() && target.length() == source.length()) return target
        target.delete()
        try {
            Os.link(source.absolutePath, target.absolutePath)
        } catch (_: Throwable) {
            // Cross-filesystem link / SELinux denial / etc — fall through.
        }
        // Verify the link actually landed. Robolectric's Os shadow no-ops
        // silently, and some OEM SELinux policies do the same on-device, so
        // a real copy is the only guaranteed path.
        if (!target.exists() || target.length() != source.length()) {
            source.copyTo(target, overwrite = true)
        }
        return target
    }

    private val NameDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm").withZone(ZoneId.systemDefault())

    /** Characters disallowed on common filesystems and by content providers. */
    private val UnsafeFsChars = Regex("""[/\\?%*|"<>:\x00-\x1F]""")

    internal fun friendlyName(scan: Scan, format: ShareFormat, index: Int, total: Int): String {
        val ext = when (format) {
            ShareFormat.Pdf -> "pdf"
            ShareFormat.Jpeg -> "jpg"
            ShareFormat.Png -> "png"
        }
        val base = scan.name
            .replace(UnsafeFsChars, " ")
            .trim()
            .ifEmpty { "Scan ${NameDateFormat.format(scan.createdAt)}" }
        return if (total > 1) "$base p${index + 1}.$ext" else "$base.$ext"
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
