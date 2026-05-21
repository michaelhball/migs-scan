package dev.migs.scan.share

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Verifies the chooser-thumbnail path end-to-end via the real FileProvider
 * resolver. Uses ContentResolver.openTypedAssetFileDescriptor rather than
 * touching the provider class directly, so we exercise the exact code path
 * the system chooser does.
 */
@RunWith(AndroidJUnit4::class)
class ScanFileProviderThumbnailTest {

    private lateinit var context: Context
    private lateinit var shareDir: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shareDir = File(context.cacheDir, "share/1779359000000-thumbtest").apply {
            deleteRecursively(); mkdirs()
        }
        File(context.cacheDir, "thumbs").deleteRecursively()
    }

    @After fun tearDown() {
        shareDir.parentFile?.deleteRecursively()
        File(context.cacheDir, "thumbs").deleteRecursively()
    }

    @Test fun jpegThumbnailIsReturnedAndDownsampled() {
        val src = File(shareDir, "Lease.jpg").apply {
            writeBytes(bigJpegBytes(width = 1024, color = Color.RED))
        }
        val uri = uriFor(src)

        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", sizeOpts(256, 256))!!.use { afd ->
            val bmp = BitmapFactory.decodeFileDescriptor(afd.fileDescriptor)
            assertThat(bmp).isNotNull()
            assertThat(bmp.width).isAtMost(512)
            assertThat(bmp.height).isAtMost(512)
        }
    }

    @Test fun pdfThumbnailRendersAndDoesNotComeBackEmpty() {
        // Render a tiny valid PDF — too involved to inline, so we drive
        // through Android's own PdfRenderer via a generated single-page PDF.
        val pdf = File(shareDir, "Doc.pdf").apply { writeBytes(tinyPdfBytes()) }
        val uri = uriFor(pdf)

        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", sizeOpts(256, 256))!!.use { afd ->
            val bmp = BitmapFactory.decodeFileDescriptor(afd.fileDescriptor)
            assertThat(bmp).isNotNull()
            assertThat(bmp.width).isGreaterThan(0)
            assertThat(bmp.height).isGreaterThan(0)
        }
    }

    @Test fun secondCallUsesCachedThumbnail() {
        val src = File(shareDir, "Lease.jpg").apply { writeBytes(Fixtures.jpegBytes()) }
        val uri = uriFor(src)
        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", sizeOpts(256, 256))!!.close()

        val cacheFile = File(context.cacheDir, "thumbs").listFiles()!!.single()
        val firstMtime = cacheFile.lastModified()
        Thread.sleep(15)

        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", sizeOpts(256, 256))!!.close()

        assertThat(cacheFile.lastModified()).isEqualTo(firstMtime)
    }

    @Test fun unsupportedSourceFallsThroughToTheBaseProvider() {
        val src = File(shareDir, "Notes.txt").apply { writeText("hello world") }
        val uri = uriFor(src)

        // For an unrecognised extension we let super handle the call. The
        // base FileProvider's behaviour is to throw FileNotFoundException
        // (it has no thumbnail to offer), which the chooser catches and
        // turns into its default placeholder — that's exactly what we want.
        val thrown = runCatching {
            context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", sizeOpts(256, 256))
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(java.io.FileNotFoundException::class.java)
    }

    private fun uriFor(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun sizeOpts(w: Int, h: Int): Bundle =
        Bundle().apply { putParcelable(ContentResolver.EXTRA_SIZE, Point(w, h)) }

    private fun bigJpegBytes(width: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        bmp.recycle()
        return baos.toByteArray()
    }

    /** Minimal one-page PDF (8.5x11pt, single empty page) that PdfRenderer accepts. */
    private fun tinyPdfBytes(): ByteArray {
        // Hand-rolled. Each xref offset must match the byte position of each obj.
        val body = """
            %PDF-1.4
            1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
            2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
            3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<<>>>>endobj
            4 0 obj<</Length 0>>stream

            endstream endobj
        """.trimIndent() + "\n"
        val bytes = body.toByteArray()
        // Compute xref offsets.
        fun offsetOf(marker: String) = bytes.toString(Charsets.US_ASCII).indexOf(marker)
        val o1 = offsetOf("1 0 obj")
        val o2 = offsetOf("2 0 obj")
        val o3 = offsetOf("3 0 obj")
        val o4 = offsetOf("4 0 obj")
        val xrefOffset = bytes.size
        val xref = "xref\n0 5\n" +
            "0000000000 65535 f \n" +
            "%010d 00000 n \n".format(o1) +
            "%010d 00000 n \n".format(o2) +
            "%010d 00000 n \n".format(o3) +
            "%010d 00000 n \n".format(o4) +
            "trailer<</Size 5/Root 1 0 R>>\nstartxref\n$xrefOffset\n%%EOF\n"
        return bytes + xref.toByteArray()
    }
}
