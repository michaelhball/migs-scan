package dev.migs.scan.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        store = ScanStore(app, Dispatchers.Unconfined)
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
