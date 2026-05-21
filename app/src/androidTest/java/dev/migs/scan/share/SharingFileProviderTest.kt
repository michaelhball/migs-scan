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

/**
 * On-device complement to the local SharingTest: exercises the real FileProvider
 * (the local suite uses a fake UriProvider to dodge a macOS path-canonicalization
 * issue inside Robolectric). If this passes, we know our authority and
 * file_paths.xml are wired correctly.
 */
@RunWith(AndroidJUnit4::class)
class SharingFileProviderTest {

    private lateinit var context: Context
    private lateinit var scanDir: File
    private val scanId = "1779357811544-72fe645e"  // realistic <epoch>-<hex> form

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scanDir = File(context.filesDir, "scans/$scanId").apply { deleteRecursively(); mkdirs() }
        File(context.cacheDir, "png").deleteRecursively()
    }

    @After fun tearDown() {
        scanDir.parentFile?.deleteRecursively()
        File(context.cacheDir, "png").deleteRecursively()
    }

    @Test fun pdfShareUsesFileProviderAuthority() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        val chooser = Sharing.buildShareIntent(context, scan, ShareFormat.Pdf)
        val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent
        val streamUri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!

        assertThat(streamUri.scheme).isEqualTo("content")
        assertThat(streamUri.authority).isEqualTo("${context.packageName}.fileprovider")
    }

    @Test fun pdfShareUriOpensAndReadsBackTheOriginalBytes() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        val chooser = Sharing.buildShareIntent(context, scan, ShareFormat.Pdf)
        val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent
        val streamUri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!

        val bytes = context.contentResolver.openInputStream(streamUri)!!.use { it.readBytes() }
        assertThat(bytes).isEqualTo(Fixtures.pdfBytes())
    }

    @Test fun shareUriEndsInTheScanName() = runTest {
        val scan = oneFakeScan(pageCount = 1)

        val chooser = Sharing.buildShareIntent(context, scan, ShareFormat.Pdf)
        val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent
        val streamUri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!

        // The chooser preview reads the URI's last path segment to show
        // the filename — assert that's the friendly form, not "doc.pdf".
        val lastSegment = streamUri.lastPathSegment!!
        assertThat(lastSegment).isEqualTo("${scan.name}.pdf")
    }

    @Test fun pngShareUriResolvesFromCacheDir() = runTest {
        val scan = oneFakeScan(pageCount = 2)

        val chooser = Sharing.buildShareIntent(context, scan, ShareFormat.Png)
        val inner = chooser.extras!!.get(Intent.EXTRA_INTENT) as Intent
        val uris = inner.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!

        assertThat(uris).hasSize(2)
        uris.forEach { uri ->
            assertThat(uri.authority).isEqualTo("${context.packageName}.fileprovider")
            // Verify each URI is actually openable through the real provider.
            context.contentResolver.openInputStream(uri)!!.close()
        }
    }

    private fun oneFakeScan(pageCount: Int): Scan {
        val pdf = File(scanDir, "doc.pdf").apply { writeBytes(Fixtures.pdfBytes()) }
        val pages = (1..pageCount).map { i ->
            File(scanDir, "page-$i.jpg").apply { writeBytes(Fixtures.jpegBytes()) }
        }
        return Scan(id = scanDir.name, name = "Test scan", createdAt = Instant.now(), pdf = pdf, pages = pages)
    }
}
