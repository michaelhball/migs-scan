package dev.migs.scan.share

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider subclass that synthesises thumbnails for the share chooser.
 *
 * The default FileProvider answers `openTypedAssetFile` with null, so the
 * system chooser ends up showing a generic icon next to the file name.
 * Here we return a downsampled JPEG instead — decoded from the source
 * image, or rendered from the first PDF page — cached on disk per
 * (URI, size) so the chooser scrolls smoothly.
 */
class ScanFileProvider : FileProvider() {

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?,
    ): AssetFileDescriptor? {
        if (!mimeTypeFilter.startsWith("image/") && mimeTypeFilter != "*/*") {
            return super.openTypedAssetFile(uri, mimeTypeFilter, opts, signal)
        }
        return runCatching { synthesiseThumbnail(uri, opts) }
            .onFailure { Log.w(TAG, "Thumbnail failed for $uri", it) }
            .getOrNull()
            ?: super.openTypedAssetFile(uri, mimeTypeFilter, opts, signal)
    }

    private fun synthesiseThumbnail(uri: Uri, opts: Bundle?): AssetFileDescriptor? {
        val ctx = context ?: return null
        val size = sizeHint(opts)
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()

        val cache = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
        val key = uri.toString().hashCode().toString().replace("-", "n")
        val out = File(cache, "$key-${size.x}x${size.y}.jpg")

        if (!out.exists() || out.length() == 0L) {
            val bitmap = openSource(uri).use { pfd ->
                when (ext) {
                    "pdf" -> renderPdfFirstPage(pfd, size)
                    "jpg", "jpeg", "png" -> decodeDownsampled(pfd, size)
                    else -> null
                }
            } ?: return null
            try {
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } finally {
                bitmap.recycle()
            }
        }

        val pfd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, out.length())
    }

    /** Reads the file behind [uri] via the underlying FileProvider plumbing. */
    private fun openSource(uri: Uri): ParcelFileDescriptor =
        super.openFile(uri, "r")
            ?: throw IllegalStateException("Could not open source for $uri")

    private fun sizeHint(opts: Bundle?): Point {
        val raw = opts?.let {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ContentResolver.EXTRA_SIZE, Point::class.java)
            } else {
                it.getParcelable(ContentResolver.EXTRA_SIZE)
            } as Point?
        }
        return raw ?: Point(DefaultThumbSize, DefaultThumbSize)
    }

    private fun decodeDownsampled(pfd: ParcelFileDescriptor, target: Point): Bitmap? {
        // First pass: read dimensions only.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = generateSequence(1) { it * 2 }
            .first { sub -> bounds.outWidth / sub <= target.x * 2 && bounds.outHeight / sub <= target.y * 2 }

        // PdfRenderer-style FDs need to be re-opened for the second decode; for an
        // image FD a reset to position 0 is enough.
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
    }

    private fun renderPdfFirstPage(pfd: ParcelFileDescriptor, target: Point): Bitmap? {
        val renderer = PdfRenderer(pfd)
        try {
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            try {
                val pageW = page.width.toFloat()
                val pageH = page.height.toFloat()
                val scale = minOf(target.x / pageW, target.y / pageH).coerceAtMost(1f)
                val outW = (pageW * scale).toInt().coerceAtLeast(1)
                val outH = (pageH * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
                    // PdfRenderer writes onto whatever pixels are already there;
                    // start from white so transparent PDF backgrounds don't
                    // surface as ghosting later in the chooser preview.
                    Canvas(this).drawColor(Color.WHITE)
                }
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }

    companion object {
        private const val TAG = "ScanFileProvider"
        private const val DefaultThumbSize = 512
    }
}
