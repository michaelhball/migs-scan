package dev.migs.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/** Same fixtures as the local test suite, repeated here so androidTest stays self-contained. */
object Fixtures {
    fun fileUri(dir: File, name: String, bytes: ByteArray): Uri {
        dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f)
    }

    fun pdfBytes(): ByteArray =
        ("%PDF-1.4\n" +
            "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
            "2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\n" +
            "xref\n0 3\n0000000000 65535 f\n0000000009 00000 n\n0000000053 00000 n\n" +
            "trailer<</Size 3/Root 1 0 R>>\nstartxref\n96\n%%EOF\n").toByteArray()

    fun jpegBytes(color: Int = Color.WHITE, size: Int = 8): ByteArray {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        bmp.recycle()
        return baos.toByteArray()
    }
}
