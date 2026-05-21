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
        scanDir = File(context.filesDir, "scans/1779358000000-deadbeef").apply { deleteRecursively(); mkdirs() }
        File(context.cacheDir, "png").deleteRecursively()
        File(context.cacheDir, "share").deleteRecursively()
    }

    @After fun tearDown() {
        scanDir.parentFile?.deleteRecursively()
        File(context.cacheDir, "png").deleteRecursively()
        File(context.cacheDir, "share").deleteRecursively()
    }

    @Test fun `friendlyName uses Scan + timestamp + extension for a single-file share`() {
        val scan = oneFakeScan(pageCount = 1)
        val name = Sharing.friendlyName(scan, ShareFormat.Pdf, index = 0, total = 1)
        assertThat(name).startsWith("Scan ")
        assertThat(name).endsWith(".pdf")
        assertThat(name).doesNotContain("p1")  // no page suffix for single-file shares
    }

    @Test fun `friendlyName appends page suffix for multi-file shares`() {
        val scan = oneFakeScan(pageCount = 3)
        val name = Sharing.friendlyName(scan, ShareFormat.Jpeg, index = 1, total = 3)
        assertThat(name).endsWith(" p2.jpg")
    }

    @Test fun `pdf share aliases the source under cacheDir share with friendly name`() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        Sharing.buildShareIntent(context, scan, ShareFormat.Pdf, fakeUriProvider)

        val aliasDir = File(context.cacheDir, "share/${scan.id}")
        assertThat(aliasDir.exists()).isTrue()
        val aliases = aliasDir.listFiles()!!.map { it.name }
        assertThat(aliases).hasSize(1)
        assertThat(aliases.single()).startsWith("Scan ")
        assertThat(aliases.single()).endsWith(".pdf")
    }

    @Test fun `multi-page jpeg share creates one alias per page`() = runTest {
        val scan = oneFakeScan(pageCount = 3)

        Sharing.buildShareIntent(context, scan, ShareFormat.Jpeg, fakeUriProvider)

        val aliasDir = File(context.cacheDir, "share/${scan.id}")
        val aliases = aliasDir.listFiles()!!.map { it.name }.sorted()
        assertThat(aliases).hasSize(3)
        aliases.forEachIndexed { i, name ->
            assertThat(name).endsWith(" p${i + 1}.jpg")
        }
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
