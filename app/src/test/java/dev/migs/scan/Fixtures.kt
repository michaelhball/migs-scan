package dev.migs.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object Fixtures {
    /** Writes the given bytes to a tmp file in [dir] and returns a file:// URI. */
    fun fileUri(dir: File, name: String, bytes: ByteArray): Uri {
        dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f)
    }

    /** Tiny but valid PDF (no real page content — just enough header to satisfy a reader). */
    fun pdfBytes(): ByteArray {
        // Minimal PDF skeleton: 1 catalog + 1 pages object. Real PDFs we ship come
        // from ML Kit; this is only to confirm the bytes round-trip through ScanStore.
        return ("%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\n" +
                "xref\n0 3\n0000000000 65535 f\n0000000009 00000 n\n0000000053 00000 n\n" +
                "trailer<</Size 3/Root 1 0 R>>\nstartxref\n96\n%%EOF\n").toByteArray()
    }

    /** Tiny solid-colour JPEG encoded via Android's Bitmap. */
    fun jpegBytes(color: Int = Color.WHITE, size: Int = 8): ByteArray {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        bmp.recycle()
        return baos.toByteArray()
    }
}
