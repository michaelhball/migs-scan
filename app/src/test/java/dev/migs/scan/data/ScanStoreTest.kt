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
        store = ScanStore(context)
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
