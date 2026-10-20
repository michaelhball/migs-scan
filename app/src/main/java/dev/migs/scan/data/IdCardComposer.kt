package dev.migs.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import kotlin.math.min

/**
 * Lays the sides of an ID card out on one A4-proportioned white page and
 * writes the result as a single JPEG — the way a photocopier would, rather
 * than one full sheet per side.
 *
 * Landscape sides (the common case: card held the normal way round) are
 * stacked vertically; two portrait sides go side by side. Each side is
 * scaled to about 1.5× real ID-1 card size, which reads comfortably on a
 * printout without looking like a poster.
 */
object IdCardComposer {

    /** A4 at 300 dpi. */
    internal const val PageWidthPx = 2480
    internal const val PageHeightPx = 3508

    /** 20 mm page margin and 15 mm gap between sides, at 300 dpi. */
    internal const val MarginPx = 236
    internal const val GapPx = 177

    /** Longest edge of a side, ≈130 mm — about 1.5× a real card's 85.6 mm. */
    internal const val MaxSideLongEdgePx = 1535

    private const val JpegQuality = 92

    data class Size(val width: Int, val height: Int) {
        val isPortrait: Boolean get() = height > width
    }

    fun compose(sides: List<File>, outFile: File) {
        require(sides.isNotEmpty()) { "An ID card scan needs at least one side" }
        require(sides.size <= 2) { "An ID card has at most two sides" }
        val slots = layout(sides.map(::dimensionsOf))
        val page = Bitmap.createBitmap(PageWidthPx, PageHeightPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(page)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            sides.zip(slots).forEach { (file, slot) ->
                val bmp = decodeScaled(file, slot.width(), slot.height())
                try {
                    canvas.drawBitmap(bmp, null, slot, paint)
                } finally {
                    bmp.recycle()
                }
            }
            val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
            tmp.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, JpegQuality, it) }
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
        } finally {
            page.recycle()
        }
    }

    /**
     * Pure layout: the page rectangle each side lands in, given its source
     * pixel size. Sides keep their aspect ratio and the group is centred on
     * the page. Exposed for tests.
     */
    internal fun layout(sizes: List<Size>): List<Rect> {
        require(sizes.isNotEmpty())
        val contentW = PageWidthPx - 2 * MarginPx
        val contentH = PageHeightPx - 2 * MarginPx
        val gaps = GapPx * (sizes.size - 1)
        val sideBySide = sizes.size == 2 && sizes.all { it.isPortrait }

        val (boxW, boxH) = if (sideBySide) {
            (contentW - gaps) / sizes.size to min(contentH, MaxSideLongEdgePx)
        } else {
            min(contentW, MaxSideLongEdgePx) to (contentH - gaps) / sizes.size
        }
        val fitted = sizes.map { (w, h) ->
            val scale = min(boxW.toFloat() / w, boxH.toFloat() / h)
            Size((w * scale).toInt(), (h * scale).toInt())
        }

        return if (sideBySide) {
            val rowW = fitted.sumOf { it.width } + gaps
            val rowH = fitted.maxOf { it.height }
            var left = (PageWidthPx - rowW) / 2
            fitted.map { (w, h) ->
                val top = (PageHeightPx - rowH) / 2 + (rowH - h) / 2
                Rect(left, top, left + w, top + h).also { left += w + GapPx }
            }
        } else {
            val colH = fitted.sumOf { it.height } + gaps
            var top = (PageHeightPx - colH) / 2
            fitted.map { (w, h) ->
                val left = (PageWidthPx - w) / 2
                Rect(left, top, left + w, top + h).also { top += h + GapPx }
            }
        }
    }

    private fun dimensionsOf(file: File): Size {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read ${file.name}" }
        return Size(bounds.outWidth, bounds.outHeight)
    }

    /**
     * Decodes [file] with the largest power-of-two subsampling that still
     * leaves it at least [targetW]×[targetH], so a 12 MP capture never has
     * to be fully materialised just to be drawn at a quarter of its size.
     */
    private fun decodeScaled(file: File, targetW: Int, targetH: Int): Bitmap {
        val (w, h) = dimensionsOf(file)
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: error("Could not decode ${file.name}")
    }
}
