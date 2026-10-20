package dev.migs.scan.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.migs.scan.Fixtures
import dev.migs.scan.data.CardComposer.PageHeightPx
import dev.migs.scan.data.CardComposer.PageWidthPx
import dev.migs.scan.data.CardComposer.Side
import dev.migs.scan.data.CardComposer.Size
import dev.migs.scan.data.CardComposer.Spec
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
class CardComposerTest {

    private lateinit var dir: File

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        dir = File(context.cacheDir, "idcard").apply { deleteRecursively(); mkdirs() }
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private val IdCardEdge = Spec.IdCard.maxLongEdgePx

    // Real ID-1 card proportions (85.6 × 54 mm) at a plausible capture size.
    private val landscapeCard = Size(1712, 1080)
    private val portraitCard = Size(1080, 1712)

    // Passport photo page (ID-3, 125 × 88 mm) at a plausible capture size.
    private val passportPage = Size(2500, 1760)

    @Test fun `two landscape sides stack vertically, centred, front above back`() {
        val (front, back) = CardComposer.layout(listOf(landscapeCard, landscapeCard), IdCardEdge)

        // Both sides come out the same size, at the capped long edge.
        assertThat(front.width()).isEqualTo(IdCardEdge)
        assertThat(back.width()).isEqualTo(front.width())
        assertThat(back.height()).isEqualTo(front.height())
        // Front is on top, separated by the gap.
        assertThat(back.top - front.bottom).isEqualTo(CardComposer.GapPx)
        // Horizontally centred (integer rounding allows ±1).
        assertThat(abs(front.centerX() - PageWidthPx / 2)).isAtMost(1)
        assertThat(abs(back.centerX() - PageWidthPx / 2)).isAtMost(1)
        // Vertically centred as a group: top margin ≈ bottom margin.
        assertThat(abs(front.top - (PageHeightPx - back.bottom))).isAtMost(1)
    }

    @Test fun `two portrait sides go side by side`() {
        val (front, back) = CardComposer.layout(listOf(portraitCard, portraitCard), IdCardEdge)

        assertThat(front.top).isEqualTo(back.top)
        assertThat(back.left - front.right).isEqualTo(CardComposer.GapPx)
        assertThat(abs((front.left + back.right) / 2 - PageWidthPx / 2)).isAtMost(1)
        assertThat(front.height()).isAtMost(IdCardEdge)
    }

    @Test fun `a single side is centred on the page`() {
        val only = CardComposer.layout(listOf(landscapeCard), IdCardEdge).single()

        assertThat(abs(only.centerX() - PageWidthPx / 2)).isAtMost(1)
        assertThat(abs(only.centerY() - PageHeightPx / 2)).isAtMost(1)
        assertThat(only.width()).isEqualTo(IdCardEdge)
    }

    @Test fun `sides keep their aspect ratio and stay inside the margins`() {
        val sizes = listOf(Size(3000, 1000), Size(500, 2000))  // very wide + very tall

        val slots = CardComposer.layout(sizes, IdCardEdge)

        sizes.zip(slots).forEach { (src, slot) ->
            val srcAspect = src.width.toFloat() / src.height
            val slotAspect = slot.width().toFloat() / slot.height()
            assertThat(abs(srcAspect - slotAspect)).isLessThan(0.01f)
            assertThat(slot.left).isAtLeast(CardComposer.MarginPx)
            assertThat(slot.top).isAtLeast(CardComposer.MarginPx)
            assertThat(slot.right).isAtMost(PageWidthPx - CardComposer.MarginPx)
            assertThat(slot.bottom).isAtMost(PageHeightPx - CardComposer.MarginPx)
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

        CardComposer.compose(listOf(Side(front), Side(back)), Spec.IdCard, out)

        val page = BitmapFactory.decodeFile(out.absolutePath)
        assertThat(page).isNotNull()
        assertThat(page.width).isEqualTo(PageWidthPx)
        assertThat(page.height).isEqualTo(PageHeightPx)

        val (frontSlot, backSlot) = CardComposer.layout(listOf(Size(160, 100), Size(160, 100)), IdCardEdge)
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

        for (sides in listOf(emptyList(), listOf(Side(side), Side(side), Side(side)))) {
            try {
                CardComposer.compose(sides, Spec.IdCard, out)
                error("expected IllegalArgumentException for ${sides.size} sides")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
        assertThat(out.exists()).isFalse()
    }

    @Test fun `passport spec lays the photo page out landscape at a touch over real size`() {
        val only = CardComposer.layout(listOf(passportPage), Spec.Passport.maxLongEdgePx).single()

        assertThat(only.width()).isEqualTo(Spec.Passport.maxLongEdgePx)
        // 1700 px at 300 dpi ≈ 144 mm, against a real page's 125 mm: bigger, but not by much.
        assertThat(only.width() / 300f * 25.4f).isWithin(1f).of(144f)
        assertThat(only.width()).isGreaterThan(only.height())
        assertThat(abs(only.centerX() - PageWidthPx / 2)).isAtMost(1)
        assertThat(abs(only.centerY() - PageHeightPx / 2)).isAtMost(1)
    }

    @Test fun `a rotated side is laid out with its rotated size`() {
        assertThat(Size(1080, 1712).rotated(90)).isEqualTo(Size(1712, 1080))
        assertThat(Size(1080, 1712).rotated(270)).isEqualTo(Size(1712, 1080))
        assertThat(Size(1080, 1712).rotated(180)).isEqualTo(Size(1080, 1712))
        assertThat(Size(1080, 1712).rotated(0)).isEqualTo(Size(1080, 1712))
    }

    @Test fun `compose draws a side rotated clockwise by its rotation`() {
        // Portrait source: red on top, blue underneath.
        val side = File(dir, "sideways.jpg").apply {
            writeBytes(Fixtures.twoToneJpegBytes(Color.RED, Color.BLUE, width = 100, height = 160))
        }
        val out = File(dir, "page-1.jpg")

        CardComposer.compose(listOf(Side(side, rotation = 90)), Spec.Passport, out)

        val page = BitmapFactory.decodeFile(out.absolutePath)
        val slot = CardComposer.layout(listOf(Size(160, 100)), Spec.Passport.maxLongEdgePx).single()
        // A quarter turn clockwise puts what was on top on the right.
        val leftPx = page.getPixel(slot.left + slot.width() / 4, slot.centerY())
        val rightPx = page.getPixel(slot.right - slot.width() / 4, slot.centerY())
        val abovePx = page.getPixel(slot.centerX(), slot.top - 40)
        page.recycle()

        assertThat(Color.blue(leftPx)).isAtLeast(200)
        assertThat(Color.red(leftPx)).isAtMost(80)
        assertThat(Color.red(rightPx)).isAtLeast(200)
        assertThat(Color.blue(rightPx)).isAtMost(80)
        assertThat(Color.red(abovePx)).isAtLeast(240)
        assertThat(Color.blue(abovePx)).isAtLeast(240)
    }

    @Test fun `writeRotatedPreview shrinks and turns the capture`() {
        val side = File(dir, "big.jpg").apply {
            writeBytes(Fixtures.twoToneJpegBytes(Color.RED, Color.BLUE, width = 1000, height = 1600))
        }
        val out = File(dir, "preview.jpg")

        CardComposer.writeRotatedPreview(side, rotation = 270, outFile = out, longEdgePx = 400)

        val preview = BitmapFactory.decodeFile(out.absolutePath)
        assertThat(preview.width).isEqualTo(400)
        assertThat(preview.height).isEqualTo(250)
        // Anticlockwise puts what was on top on the left.
        assertThat(Color.red(preview.getPixel(50, 125))).isAtLeast(200)
        assertThat(Color.blue(preview.getPixel(350, 125))).isAtLeast(200)
        preview.recycle()
    }

    @Test fun `a side rejects anything but quarter turns`() {
        val side = File(dir, "s.jpg").apply { writeBytes(Fixtures.jpegBytes()) }
        try {
            Side(side, rotation = 45)
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }
}
