package dev.migs.scan.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import dev.migs.scan.data.ScanPayload
import dev.migs.scan.data.ScanStore
import dev.migs.scan.data.TextExtractor
import dev.migs.scan.ui.theme.MigsScanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigsScanAppUiTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var sourceDir: File
    private lateinit var store: ScanStore

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, "scans").deleteRecursively()
        sourceDir = File(app.cacheDir, "ui-fixtures").apply { deleteRecursively(); mkdirs() }
        // Skip OCR in UI tests — Play Services isn't a fast-path on emulator startup.
        store = ScanStore(app, Dispatchers.Unconfined, TextExtractor.Empty)
    }

    @After fun tearDown() {
        File(app.filesDir, "scans").deleteRecursively()
        sourceDir.deleteRecursively()
    }

    @Test fun emptyStateShowsScanCopyAndCta() {
        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }

        composeRule.onNodeWithText("No scans yet").assertIsDisplayed()
        composeRule.onNodeWithText("Scan a document").assertIsDisplayed()
        // ExtendedFAB merges its child semantics, so reach into the unmerged
        // tree for the "Scan" label — and use assertExists so a tiny test
        // window that clips the FAB off-screen doesn't fail this test.
        composeRule.onNodeWithText("Scan", useUnmergedTree = true).assertExists()
    }

    @Test fun scanListRendersOneRowPerPersistedScan() {
        runBlocking {
            store.persist(payload(label = "a"))
            store.persist(payload(label = "b"))
            store.persist(payload(label = "c"))
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        // Empty state should not be visible.
        composeRule.onAllNodesWithText("No scans yet").fetchSemanticsNodes().also {
            assertThat(it).isEmpty()
        }
        // Each row shows the default "Scan yyyy-MM-dd HHmm" title.
        val titles = composeRule.onAllNodesWithText("Scan ", substring = true).fetchSemanticsNodes()
        assertThat(titles.size).isAtLeast(3)
    }

    @Test fun tappingAScanRowOpensTheActionSheet() {
        runBlocking { store.persist(payload(label = "only")) }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Share as PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Share as JPEG").assertIsDisplayed()
        composeRule.onNodeWithText("Share as PNG").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test fun searchFiltersTheListByScanName() {
        runBlocking {
            store.persist(payload(label = "a")).also { store.rename(it, "Passport") }
            store.persist(payload(label = "b")).also { store.rename(it, "Tax return") }
            store.persist(payload(label = "c")).also { store.rename(it, "Recipe — pasta") }
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        // All three visible before any search.
        composeRule.onNodeWithText("Passport").assertIsDisplayed()
        composeRule.onNodeWithText("Tax return").assertIsDisplayed()
        composeRule.onNodeWithText("Recipe — pasta").assertIsDisplayed()

        // Type a query — only matching scans remain.
        composeRule.onNodeWithText("Search scans").performTextInput("tax")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tax return").assertIsDisplayed()
        composeRule.onAllNodesWithText("Passport").fetchSemanticsNodes().also {
            assertThat(it).isEmpty()
        }

        // Clear via the X icon — list comes back.
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Passport").assertIsDisplayed()
    }

    @Test fun tappingAScanRowOpensThePreviewScreen() {
        runBlocking {
            val scan = store.persist(payload(label = "only"))
            store.rename(scan, "Lease agreement")
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        // Tap the row itself (its title, not the share icon).
        composeRule.onNodeWithText("Lease agreement").performClick()
        composeRule.waitForIdle()

        // Preview's TopAppBar shows the scan name + page count, and there's a Back button.
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("1 page").assertIsDisplayed()
    }

    @Test fun longPressEntersSelectionModeAndDeleteRemovesSelected() {
        runBlocking {
            store.persist(payload(label = "a")).also { store.rename(it, "Lease") }
            store.persist(payload(label = "b")).also { store.rename(it, "Insurance") }
            store.persist(payload(label = "c")).also { store.rename(it, "Permits") }
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        // Long-press "Lease" to enter selection mode.
        composeRule.onNodeWithText("Lease").performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Contextual top bar shows "1 selected".
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()

        // Tap "Insurance" to add it to the selection.
        composeRule.onNodeWithText("Insurance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()

        // Tap the delete-selected icon, then confirm.
        composeRule.onNodeWithContentDescription("Delete selected").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // Only "Permits" survives, both on screen and on disk.
        composeRule.onAllNodesWithText("Lease").fetchSemanticsNodes().also {
            assertThat(it).isEmpty()
        }
        composeRule.onAllNodesWithText("Insurance").fetchSemanticsNodes().also {
            assertThat(it).isEmpty()
        }
        composeRule.onNodeWithText("Permits").assertIsDisplayed()
        runBlocking {
            assertThat(store.loadAll().map { it.name }).containsExactly("Permits")
        }
    }

    @Test fun pageEditorReordersAndDeletesPages() {
        // Multi-page scan with deterministic per-page OCR so we can read back the
        // joined text and verify the reorder/delete really moved the pages.
        val pagedStore = ScanStore(
            app,
            Dispatchers.Unconfined,
            TextExtractor { f -> "OCR ${f.name.substringAfter("page-").substringBefore('.')}" },
            pdfBuilder = { pages, out -> out.writeText("pdf:${pages.size}") },
        )
        val scan = runBlocking {
            pagedStore.persist(
                ScanPayload(
                    pdf = Fixtures.fileUri(sourceDir, "src.pdf", Fixtures.pdfBytes()),
                    pages = (1..3).map { Fixtures.fileUri(sourceDir, "p$it.jpg", Fixtures.jpegBytes()) },
                ),
            ).also { pagedStore.rename(it, "Multi") }
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, pagedStore)) }
        }
        composeRule.waitForIdle()

        // Tap the row → preview → kebab → action sheet → "Edit pages".
        composeRule.onNodeWithText("Multi").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit pages").performClick()
        composeRule.waitForIdle()

        // Move "Page 1" down: tap its Move-down button (first row's down arrow).
        composeRule.onAllNodesWithContentDescription("Move down").fetchSemanticsNodes().also {
            assertThat(it).isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Move down")[0].performClick()
        composeRule.waitForIdle()

        // Delete what is now "Page 1" (the original page-2).
        composeRule.onAllNodesWithContentDescription("Delete page")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // On disk: only 2 pages remain (the original p1 + p3 in that order).
        val reloaded = runBlocking { pagedStore.loadAll().single { it.id == scan.id } }
        assertThat(reloaded.pages).hasSize(2)
        assertThat(reloaded.text).isEqualTo("OCR 1\n\nOCR 3")
    }

    @Test fun searchAlsoMatchesOcrText() {
        // First persist gets OCR text, second persist gets nothing — that way we
        // can search for a substring unique to the first scan's text.
        var call = 0
        val ocrStore = ScanStore(app, Dispatchers.Unconfined, TextExtractor {
            if (call++ == 0) "INVOICE 2024-05-21\nElectricity bill" else ""
        })
        runBlocking {
            ocrStore.persist(payload(label = "a")).also { ocrStore.rename(it, "Photo 1") }
            ocrStore.persist(payload(label = "b")).also { ocrStore.rename(it, "Photo 2") }
        }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, ocrStore)) }
        }
        composeRule.waitForIdle()

        // "electricity" doesn't appear in any name — only via OCR text.
        composeRule.onNodeWithText("Search scans").performTextInput("electricity")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Photo 1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Photo 2").fetchSemanticsNodes().also {
            assertThat(it).isEmpty()
        }
    }

    @Test fun renameDialogPersistsTheNewNameAndUpdatesTheList() {
        runBlocking { store.persist(payload(label = "only")) }

        composeRule.setContent {
            MigsScanTheme { MigsScanApp(vm = ScanViewModel(app, store)) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Name").performTextClearance()
        composeRule.onNodeWithText("Name").performTextInput("Passport application")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Passport application").assertIsDisplayed()
        // Disk reflects the new name too.
        val onDisk = runBlocking { store.loadAll().single() }
        assertThat(onDisk.name).isEqualTo("Passport application")
    }

    private fun payload(label: String): ScanPayload = ScanPayload(
        pdf = Fixtures.fileUri(sourceDir, "$label.pdf", Fixtures.pdfBytes()),
        pages = listOf(Fixtures.fileUri(sourceDir, "$label.jpg", Fixtures.jpegBytes())),
    )
}
