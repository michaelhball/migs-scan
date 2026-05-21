package dev.migs.scan.data

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanStoreTest {

    private lateinit var context: Context
    private lateinit var store: ScanStore
    private lateinit var sourceDir: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use a fake extractor so we never instantiate ML Kit in Robolectric.
        store = ScanStore(context, textExtractor = TextExtractor.Empty)
        sourceDir = File(context.cacheDir, "fixtures").apply { deleteRecursively(); mkdirs() }
        File(context.filesDir, "scans").deleteRecursively()
    }

    @After fun tearDown() {
        sourceDir.deleteRecursively()
        File(context.filesDir, "scans").deleteRecursively()
    }

    @Test fun `persist copies pdf and pages into a fresh directory`() = runTest {
        val payload = ScanPayload(
            pdf = Fixtures.fileUri(sourceDir, "src.pdf", Fixtures.pdfBytes()),
            pages = listOf(
                Fixtures.fileUri(sourceDir, "p1.jpg", Fixtures.jpegBytes(Color.WHITE)),
                Fixtures.fileUri(sourceDir, "p2.jpg", Fixtures.jpegBytes(Color.BLACK)),
            ),
        )

        val scan = store.persist(payload)

        assertThat(scan.pdf.exists()).isTrue()
        assertThat(scan.pdf.readBytes()).isEqualTo(Fixtures.pdfBytes())
        assertThat(scan.pages).hasSize(2)
        assertThat(scan.pages.map { it.name }).containsExactly("page-1.jpg", "page-2.jpg").inOrder()
        assertThat(scan.pdf.parentFile?.name).isEqualTo(scan.id)
    }

    @Test fun `persisted scan id encodes the createdAt epoch millis`() = runTest {
        val payload = ScanPayload(
            pdf = Fixtures.fileUri(sourceDir, "src.pdf", Fixtures.pdfBytes()),
            pages = emptyList(),
        )

        val scan = store.persist(payload)

        val epochFromId = scan.id.substringBefore('-').toLong()
        assertThat(epochFromId).isEqualTo(scan.createdAt.toEpochMilli())
    }

    @Test fun `loadAll returns persisted scans newest first`() = runTest {
        val first = store.persist(payload(name = "a"))
        Thread.sleep(2)  // guarantee distinct epoch millis
        val second = store.persist(payload(name = "b"))

        val loaded = store.loadAll()

        assertThat(loaded.map { it.id }).containsExactly(second.id, first.id).inOrder()
    }

    @Test fun `loadAll skips directories that have no pdf`() = runTest {
        File(context.filesDir, "scans/${System.currentTimeMillis()}-junk").mkdirs()
        store.persist(payload())

        val loaded = store.loadAll()

        assertThat(loaded).hasSize(1)
    }

    @Test fun `delete removes the entire scan directory`() = runTest {
        val scan = store.persist(payload())
        val dir = scan.pdf.parentFile!!

        store.delete(scan)

        assertThat(dir.exists()).isFalse()
        assertThat(store.loadAll()).isEmpty()
    }

    @Test fun `persist assigns a default name based on createdAt`() = runTest {
        val scan = store.persist(payload())

        assertThat(scan.name).startsWith("Scan ")
        assertThat(File(scan.pdf.parentFile, "name.txt").readText()).isEqualTo(scan.name)
    }

    @Test fun `rename updates name on disk and returns updated Scan`() = runTest {
        val scan = store.persist(payload())

        val renamed = store.rename(scan, "Tax return 2024")

        assertThat(renamed.name).isEqualTo("Tax return 2024")
        assertThat(File(scan.pdf.parentFile, "name.txt").readText()).isEqualTo("Tax return 2024")
        // Reloading from disk picks up the new name.
        assertThat(store.loadAll().single().name).isEqualTo("Tax return 2024")
    }

    @Test fun `rename to blank or whitespace falls back to the default name`() = runTest {
        val scan = store.persist(payload())
        store.rename(scan, "Something")

        val reverted = store.rename(scan, "   ")

        assertThat(reverted.name).startsWith("Scan ")
    }

    @Test fun `loadAll reads back the persisted custom name`() = runTest {
        val scan = store.persist(payload())
        store.rename(scan, "Passport")

        val loaded = store.loadAll().single()

        assertThat(loaded.name).isEqualTo("Passport")
    }

    @Test fun `setStarred true creates marker file and reflects on Scan`() = runTest {
        val scan = store.persist(payload())
        assertThat(scan.starred).isFalse()

        val starred = store.setStarred(scan, true)

        assertThat(starred.starred).isTrue()
        assertThat(File(scan.pdf.parentFile, "starred").exists()).isTrue()
    }

    @Test fun `setStarred false removes marker file`() = runTest {
        val scan = store.persist(payload())
        store.setStarred(scan, true)

        val unstarred = store.setStarred(scan, false)

        assertThat(unstarred.starred).isFalse()
        assertThat(File(scan.pdf.parentFile, "starred").exists()).isFalse()
    }

    @Test fun `loadAll pins starred scans to the top regardless of date`() = runTest {
        val older = store.persist(payload(name = "old"))
        Thread.sleep(2)
        val newer = store.persist(payload(name = "new"))
        store.setStarred(older, true)

        val loaded = store.loadAll()

        // Starred (older) first, then unstarred newer.
        assertThat(loaded.map { it.id }).containsExactly(older.id, newer.id).inOrder()
    }

    @Test fun `persist runs the text extractor on each page and joins with blank lines`() = runTest {
        val capturedFiles = mutableListOf<File>()
        val fakeExtractor = TextExtractor { file ->
            capturedFiles.add(file)
            "Text from ${file.name}"
        }
        val ocrStore = ScanStore(context, textExtractor = fakeExtractor)
        val payload = ScanPayload(
            pdf = Fixtures.fileUri(sourceDir, "src.pdf", Fixtures.pdfBytes()),
            pages = listOf(
                Fixtures.fileUri(sourceDir, "p1.jpg", Fixtures.jpegBytes()),
                Fixtures.fileUri(sourceDir, "p2.jpg", Fixtures.jpegBytes()),
            ),
        )

        val scan = ocrStore.persist(payload)

        assertThat(capturedFiles.map { it.name }).containsExactly("page-1.jpg", "page-2.jpg").inOrder()
        assertThat(scan.text).isEqualTo("Text from page-1.jpg\n\nText from page-2.jpg")
        assertThat(File(scan.pdf.parentFile, "text.txt").readText()).isEqualTo(scan.text)
    }

    @Test fun `persist with empty extractor leaves text empty and skips writing the file`() = runTest {
        val scan = store.persist(payload())  // uses TextExtractor.Empty

        assertThat(scan.text).isEmpty()
        assertThat(File(scan.pdf.parentFile, "text.txt").exists()).isFalse()
    }

    @Test fun `loadAll reads the persisted OCR text back into the Scan`() = runTest {
        val ocrStore = ScanStore(context, textExtractor = TextExtractor { "Page text" })
        ocrStore.persist(payload())

        val loaded = ocrStore.loadAll().single()

        assertThat(loaded.text).isEqualTo("Page text")
    }

    @Test fun `loadAll falls back to default name when name file is missing`() = runTest {
        val scan = store.persist(payload())
        File(scan.pdf.parentFile, "name.txt").delete()

        val loaded = store.loadAll().single()

        assertThat(loaded.name).startsWith("Scan ")
    }

    @Test fun `persist with no pdf throws`() = runTest {
        try {
            store.persist(ScanPayload(pdf = null, pages = emptyList()))
            error("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertThat(expected).hasMessageThat().contains("PDF")
        }
    }

    private fun payload(name: String = "src"): ScanPayload = ScanPayload(
        pdf = Fixtures.fileUri(sourceDir, "$name.pdf", Fixtures.pdfBytes()),
        pages = listOf(Fixtures.fileUri(sourceDir, "$name.jpg", Fixtures.jpegBytes())),
    )
}
