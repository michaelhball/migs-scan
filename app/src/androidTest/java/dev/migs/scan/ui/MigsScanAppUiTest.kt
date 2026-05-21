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
