package dev.migs.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.data.ScanPayload
import dev.migs.scan.data.ScanStore
import dev.migs.scan.share.ShareFormat
import dev.migs.scan.share.Sharing
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end: simulate an ML Kit result as a ScanPayload, persist it via the
 * real ScanStore, then build all three share intents using the real
 * FileProvider and confirm each one is readable through ContentResolver.
 *
 * This is the closest we can get to "did the share really work" without
 * launching another app to receive it.
 */
@RunWith(AndroidJUnit4::class)
class ScanFlowEndToEndTest {

    private lateinit var context: Context
    private lateinit var store: ScanStore
    private lateinit var sourceDir: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "scans").deleteRecursively()
        File(context.cacheDir, "png").deleteRecursively()
        sourceDir = File(context.cacheDir, "e2e-src").apply { deleteRecursively(); mkdirs() }
        store = ScanStore(context)
    }

    @After fun tearDown() {
        File(context.filesDir, "scans").deleteRecursively()
        File(context.cacheDir, "png").deleteRecursively()
        sourceDir.deleteRecursively()
    }

    @Test fun persistThenShareAllThreeFormats() = runTest {
        val payload = ScanPayload(
            pdf = Fixtures.fileUri(sourceDir, "doc.pdf", Fixtures.pdfBytes()),
            pages = listOf(
                Fixtures.fileUri(sourceDir, "p1.jpg", Fixtures.jpegBytes()),
                Fixtures.fileUri(sourceDir, "p2.jpg", Fixtures.jpegBytes()),
            ),
        )

        val scan = store.persist(payload)

        // Round-trip the PDF and both image formats through ContentResolver.
        for (format in ShareFormat.entries) {
            val chooser = Sharing.buildShareIntent(context, scan, format)
            val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent

            val streamUris: List<Uri> = when (format) {
                ShareFormat.Pdf -> listOf(inner.getParcelableExtra(Intent.EXTRA_STREAM)!!)
                ShareFormat.Jpeg, ShareFormat.Png ->
                    inner.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.toList()
            }

            assertThat(streamUris).isNotEmpty()
            streamUris.forEach { uri ->
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                assertThat(bytes).isNotEmpty()
            }
        }
    }
}
