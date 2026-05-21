package dev.migs.scan.ui

import android.app.Application
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import dev.migs.scan.MainDispatcherRule
import dev.migs.scan.data.ScanPayload
import dev.migs.scan.data.ScanStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScanViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var app: Application
    private lateinit var sourceDir: File
    private lateinit var store: ScanStore

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, "scans").deleteRecursively()
        sourceDir = File(app.cacheDir, "vm-fixtures").apply { deleteRecursively(); mkdirs() }
        // Drive ScanStore's IO work through the same test scheduler the VM uses,
        // so advanceUntilIdle() actually waits for persistence to finish.
        store = ScanStore(app, mainDispatcherRule.testDispatcher)
    }

    @After fun tearDown() {
        File(app.filesDir, "scans").deleteRecursively()
        sourceDir.deleteRecursively()
    }

    @Test fun `scans starts empty when nothing on disk`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = ScanViewModel(app, store)
        advanceUntilIdle()

        assertThat(vm.scans.value).isEmpty()
    }

    @Test fun `onPayload persists the scan and prepends it to the list`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = ScanViewModel(app, store)
        advanceUntilIdle()

        vm.onPayload(payload(label = "doc"))
        advanceUntilIdle()

        assertThat(vm.scans.value).hasSize(1)
        assertThat(vm.scans.value.single().pdf.exists()).isTrue()
    }

    @Test fun `delete removes the scan from the list and disk`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = ScanViewModel(app, store)
        vm.onPayload(payload())
        advanceUntilIdle()
        val scan = vm.scans.value.single()

        vm.delete(scan)
        advanceUntilIdle()

        assertThat(vm.scans.value).isEmpty()
        assertThat(scan.pdf.parentFile?.exists()).isFalse()
    }

    @Test fun `rename updates the scan in the list with the new name`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = ScanViewModel(app, store)
        vm.onPayload(payload())
        advanceUntilIdle()
        val scan = vm.scans.value.single()

        vm.rename(scan, "Birth certificate")
        advanceUntilIdle()

        val updated = vm.scans.value.single()
        assertThat(updated.id).isEqualTo(scan.id)
        assertThat(updated.name).isEqualTo("Birth certificate")
    }

    @Test fun `init loads scans previously persisted on disk`() = runTest(mainDispatcherRule.testDispatcher) {
        val first = ScanViewModel(app, store).also { it.onPayload(payload(label = "old")) }
        advanceUntilIdle()
        assertThat(first.scans.value).hasSize(1)  // baseline

        val reborn = ScanViewModel(app, store)
        advanceUntilIdle()

        assertThat(reborn.scans.value).hasSize(1)
    }

    private fun payload(label: String = "p"): ScanPayload = ScanPayload(
        pdf = Fixtures.fileUri(sourceDir, "$label.pdf", Fixtures.pdfBytes()),
        pages = listOf(Fixtures.fileUri(sourceDir, "$label.jpg", Fixtures.jpegBytes(Color.WHITE))),
    )
}
