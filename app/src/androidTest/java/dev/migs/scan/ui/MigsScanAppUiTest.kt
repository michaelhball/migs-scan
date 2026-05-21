package dev.migs.scan.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        // Three rows, all showing "1 page".
        val pageLabels = composeRule.onAllNodesWithText("1 page").fetchSemanticsNodes()
        assertThat(pageLabels).hasSize(3)
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
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    private fun payload(label: String): ScanPayload = ScanPayload(
        pdf = Fixtures.fileUri(sourceDir, "$label.pdf", Fixtures.pdfBytes()),
        pages = listOf(Fixtures.fileUri(sourceDir, "$label.jpg", Fixtures.jpegBytes())),
    )
}
