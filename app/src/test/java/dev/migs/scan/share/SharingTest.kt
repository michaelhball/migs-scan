package dev.migs.scan.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import dev.migs.scan.data.Scan
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SharingTest {

    private lateinit var context: Context
    private lateinit var scanDir: File

    // Skip FileProvider in Robolectric (its path strategy doesn't play nicely
    // with the macOS sandbox tmpdir layout); the on-device instrumented suite
    // exercises the real FileProvider. Here we just check the rest of the intent.
    private val fakeUriProvider = UriProvider { file ->
        Uri.parse("content://fake/${file.parentFile?.name}/${file.name}")
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scanDir = File(context.filesDir, "scans/test-scan").apply { deleteRecursively(); mkdirs() }
        File(context.cacheDir, "png").deleteRecursively()
    }

    @After fun tearDown() {
        scanDir.parentFile?.deleteRecursively()
        File(context.cacheDir, "png").deleteRecursively()
    }

    @Test fun `pdf share builds an ACTION_SEND chooser with the pdf mime type`() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        val chooser = Sharing.buildShareIntent(context, scan, ShareFormat.Pdf, fakeUriProvider)
        val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(inner.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(inner.type).isEqualTo("application/pdf")
        val streamUri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(streamUri.scheme).isEqualTo("content")
        // (Authority assertion lives in the on-device FileProviderShareTest.)
        assertThat(inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test fun `single-page jpeg share uses ACTION_SEND not ACTION_SEND_MULTIPLE`() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        val inner = innerOf(Sharing.buildShareIntent(context, scan, ShareFormat.Jpeg, fakeUriProvider))

        assertThat(inner.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(inner.type).isEqualTo("image/jpeg")
        assertThat(inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isNotNull()
    }

    @Test fun `multi-page jpeg share uses ACTION_SEND_MULTIPLE with all uris`() = runTest {
        val scan = oneFakeScan(pageCount = 3)

        val inner = innerOf(Sharing.buildShareIntent(context, scan, ShareFormat.Jpeg, fakeUriProvider))

        assertThat(inner.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(inner.type).isEqualTo("image/jpeg")
        val uris = inner.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uris).hasSize(3)
        assertThat(uris.map { it.scheme }.toSet()).containsExactly("content")
    }

    @Test fun `png share lazily re-encodes pages into the cache dir`() = runTest {
        val scan = oneFakeScan(pageCount = 2)
        val pngCache = File(context.cacheDir, "png/${scan.id}")
        assertThat(pngCache.exists()).isFalse()

        val inner = innerOf(Sharing.buildShareIntent(context, scan, ShareFormat.Png, fakeUriProvider))

        assertThat(inner.type).isEqualTo("image/png")
        assertThat(pngCache.exists()).isTrue()
        val pngFiles = pngCache.listFiles()!!.sortedBy { it.name }
        assertThat(pngFiles.map { it.name })
            .containsExactly("page-1.png", "page-2.png").inOrder()
        pngFiles.forEach { assertThat(it.length()).isGreaterThan(0L) }
    }

    @Test fun `png share is idempotent and reuses cached encodes`() = runTest {
        val scan = oneFakeScan(pageCount = 1)
        Sharing.buildShareIntent(context, scan, ShareFormat.Png, fakeUriProvider)
        val cached = File(context.cacheDir, "png/${scan.id}/page-1.png")
        val firstMtime = cached.lastModified()
        Thread.sleep(20)

        Sharing.buildShareIntent(context, scan, ShareFormat.Png, fakeUriProvider)

        assertThat(cached.lastModified()).isEqualTo(firstMtime)
    }

    /** Pulls the inner SEND/SEND_MULTIPLE out of an ACTION_CHOOSER. */
    private fun innerOf(chooser: Intent): Intent =
        chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent

    private fun oneFakeScan(pageCount: Int): Scan {
        val pdf = File(scanDir, "doc.pdf").apply { writeBytes(Fixtures.pdfBytes()) }
        val pages = (1..pageCount).map { i ->
            File(scanDir, "page-$i.jpg").apply { writeBytes(Fixtures.jpegBytes()) }
        }
        return Scan(
            id = scanDir.name,
            createdAt = Instant.now(),
            pdf = pdf,
            pages = pages,
        )
    }
}
