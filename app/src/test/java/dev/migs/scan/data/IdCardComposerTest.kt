package dev.migs.scan.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import dev.migs.scan.data.IdCardComposer.PageHeightPx
import dev.migs.scan.data.IdCardComposer.PageWidthPx
import dev.migs.scan.data.IdCardComposer.Size
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
// Real pixels: legacy graphics mode decodes bitmaps as blank placeholders,
// which would make the colour assertions below meaningless.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdCardComposerTest {

    private lateinit var dir: File

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        dir = File(context.cacheDir, "idcard").apply { deleteRecursively(); mkdirs() }
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    // Real ID-1 card proportions (85.6 × 54 mm) at a plausible capture size.
    private val landscapeCard = Size(1712, 1080)
    private val portraitCard = Size(1080, 1712)

    @Test fun `two landscape sides stack vertically, centred, front above back`() {
        val (front, back) = IdCardComposer.layout(listOf(landscapeCard, landscapeCard))

        // Both sides come out the same size, at the capped long edge.
        assertThat(front.width()).isEqualTo(IdCardComposer.MaxSideLongEdgePx)
        assertThat(back.width()).isEqualTo(front.width())
        assertThat(back.height()).isEqualTo(front.height())
        // Front is on top, separated by the gap.
        assertThat(back.top - front.bottom).isEqualTo(IdCardComposer.GapPx)
        // Horizontally centred (integer rounding allows ±1).
        assertThat(abs(front.centerX() - PageWidthPx / 2)).isAtMost(1)
        assertThat(abs(back.centerX() - PageWidthPx / 2)).isAtMost(1)
        // Vertically centred as a group: top margin ≈ bottom margin.
        assertThat(abs(front.top - (PageHeightPx - back.bottom))).isAtMost(1)
    }

    @Test fun `two portrait sides go side by side`() {
        val (front, back) = IdCardComposer.layout(listOf(portraitCard, portraitCard))

        assertThat(front.top).isEqualTo(back.top)
        assertThat(back.left - front.right).isEqualTo(IdCardComposer.GapPx)
        assertThat(abs((front.left + back.right) / 2 - PageWidthPx / 2)).isAtMost(1)
        assertThat(front.height()).isAtMost(IdCardComposer.MaxSideLongEdgePx)
    }

    @Test fun `a single side is centred on the page`() {
        val only = IdCardComposer.layout(listOf(landscapeCard)).single()

        assertThat(abs(only.centerX() - PageWidthPx / 2)).isAtMost(1)
        assertThat(abs(only.centerY() - PageHeightPx / 2)).isAtMost(1)
        assertThat(only.width()).isEqualTo(IdCardComposer.MaxSideLongEdgePx)
    }

    @Test fun `sides keep their aspect ratio and stay inside the margins`() {
        val sizes = listOf(Size(3000, 1000), Size(500, 2000))  // very wide + very tall

        val slots = IdCardComposer.layout(sizes)

        sizes.zip(slots).forEach { (src, slot) ->
            val srcAspect = src.width.toFloat() / src.height
            val slotAspect = slot.width().toFloat() / slot.height()
            assertThat(abs(srcAspect - slotAspect)).isLessThan(0.01f)
            assertThat(slot.left).isAtLeast(IdCardComposer.MarginPx)
            assertThat(slot.top).isAtLeast(IdCardComposer.MarginPx)
            assertThat(slot.right).isAtMost(PageWidthPx - IdCardComposer.MarginPx)
            assertThat(slot.bottom).isAtMost(PageHeightPx - IdCardComposer.MarginPx)
        }
    }

    @Test fun `compose writes an A4-proportioned JPEG with the front on top and the back below`() {
        val front = File(dir, "front.jpg").apply {
            writeBytes(Fixtures.jpegBytes(Color.RED, width = 160, height = 100))
        }
        val back = File(dir, "back.jpg").apply {
            writeBytes(Fixtures.jpegBytes(Color.BLUE, width = 160, height = 100))
        }
        val out = File(dir, "page-1.jpg")

        IdCardComposer.compose(listOf(front, back), out)

        val page = BitmapFactory.decodeFile(out.absolutePath)
        assertThat(page).isNotNull()
        assertThat(page.width).isEqualTo(PageWidthPx)
        assertThat(page.height).isEqualTo(PageHeightPx)

        val (frontSlot, backSlot) = IdCardComposer.layout(listOf(Size(160, 100), Size(160, 100)))
        val frontPx = page.getPixel(frontSlot.centerX(), frontSlot.centerY())
        val backPx = page.getPixel(backSlot.centerX(), backSlot.centerY())
        val cornerPx = page.getPixel(20, 20)
        val betweenPx = page.getPixel(PageWidthPx / 2, (frontSlot.bottom + backSlot.top) / 2)
        page.recycle()

        // JPEG is lossy, so compare loosely against the solid source colours.
        assertThat(Color.red(frontPx)).isAtLeast(200)
        assertThat(Color.blue(frontPx)).isAtMost(80)
        assertThat(Color.blue(backPx)).isAtLeast(200)
        assertThat(Color.red(backPx)).isAtMost(80)
        for (px in listOf(cornerPx, betweenPx)) {
            assertThat(Color.red(px)).isAtLeast(240)
            assertThat(Color.green(px)).isAtLeast(240)
            assertThat(Color.blue(px)).isAtLeast(240)
        }
        assertThat(File(dir, "page-1.jpg.tmp").exists()).isFalse()
    }

    @Test fun `compose rejects zero or more than two sides`() {
        val side = File(dir, "s.jpg").apply { writeBytes(Fixtures.jpegBytes()) }
        val out = File(dir, "out.jpg")

        for (sides in listOf(emptyList(), listOf(side, side, side))) {
            try {
                IdCardComposer.compose(sides, out)
                error("expected IllegalArgumentException for ${sides.size} sides")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
        assertThat(out.exists()).isFalse()
    }
}
