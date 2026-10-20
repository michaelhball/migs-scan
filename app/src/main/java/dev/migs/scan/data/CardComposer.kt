package dev.migs.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Lays the sides of a card-sized document (ID card, passport photo page)
 * out on one A4-proportioned white page and writes the result as a single
 * JPEG — the way a photocopier would, rather than one full sheet per side.
 *
 * Landscape sides (the common case: card held the normal way round) are
 * stacked vertically; two portrait sides go side by side. Each side is
 * scaled to the [Spec]'s long-edge cap, which keeps the result close to
 * real size rather than blowing a card up to fill the sheet.
 */
object CardComposer {

    /** A4 at 300 dpi. */
    internal const val PageWidthPx = 2480
    internal const val PageHeightPx = 3508

    /** 20 mm page margin and 15 mm gap between sides, at 300 dpi. */
    internal const val MarginPx = 236
    internal const val GapPx = 177

    private const val JpegQuality = 92

    /** How large a side may come out. [maxLongEdgePx] is at 300 dpi. */
    data class Spec(val maxLongEdgePx: Int) {
        companion object {
            /** ≈130 mm — about 1.5× a real ID-1 card's 85.6 mm, easy to read on paper. */
            val IdCard = Spec(maxLongEdgePx = 1535)

            /** ≈144 mm — a passport photo page is 125 mm wide, so a touch over real size. */
            val Passport = Spec(maxLongEdgePx = 1700)
        }
    }

    /** One captured side and the clockwise rotation to draw it with (0, 90, 180 or 270). */
    data class Side(val file: File, val rotation: Int = 0) {
        init {
            require(rotation in setOf(0, 90, 180, 270)) { "rotation must be a quarter turn, got $rotation" }
        }
    }

    data class Size(val width: Int, val height: Int) {
        val isPortrait: Boolean get() = height > width

        /** The size this becomes after a clockwise quarter-turn rotation. */
        fun rotated(degrees: Int): Size = if (degrees % 180 == 0) this else Size(height, width)
    }

    fun compose(sides: List<Side>, spec: Spec, outFile: File) {
        require(sides.isNotEmpty()) { "A card scan needs at least one side" }
        require(sides.size <= 2) { "A card has at most two sides" }
        val slots = layout(sides.map { dimensionsOf(it.file).rotated(it.rotation) }, spec.maxLongEdgePx)
        val page = Bitmap.createBitmap(PageWidthPx, PageHeightPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(page)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            sides.zip(slots).forEach { (side, slot) ->
                val upright = side.rotation % 180 == 0
                // The slot is in page orientation; the decode target is in source orientation.
                val bmp = if (upright) decodeScaled(side.file, slot.width(), slot.height())
                else decodeScaled(side.file, slot.height(), slot.width())
                try {
                    canvas.save()
                    canvas.rotate(side.rotation.toFloat(), slot.exactCenterX(), slot.exactCenterY())
                    // After rotating the canvas about the slot's centre, draw the
                    // source-oriented bitmap into a rect of its own shape, same centre.
                    val dst = if (upright) RectF(slot) else RectF(
                        slot.exactCenterX() - slot.height() / 2f,
                        slot.exactCenterY() - slot.width() / 2f,
                        slot.exactCenterX() + slot.height() / 2f,
                        slot.exactCenterY() + slot.width() / 2f,
                    )
                    canvas.drawBitmap(bmp, null, dst, paint)
                    canvas.restore()
                } finally {
                    bmp.recycle()
                }
            }
            writeJpeg(page, outFile)
        } finally {
            page.recycle()
        }
    }

    /**
     * Pure layout: the page rectangle each side lands in, given its size as
     * it will be drawn (i.e. already rotated). Sides keep their aspect ratio
     * and the group is centred on the page. Exposed for tests.
     */
    internal fun layout(sizes: List<Size>, maxLongEdgePx: Int): List<Rect> {
        require(sizes.isNotEmpty())
        val contentW = PageWidthPx - 2 * MarginPx
        val contentH = PageHeightPx - 2 * MarginPx
        val gaps = GapPx * (sizes.size - 1)
        val sideBySide = sizes.size == 2 && sizes.all { it.isPortrait }

        val (boxW, boxH) = if (sideBySide) {
            (contentW - gaps) / sizes.size to min(contentH, maxLongEdgePx)
        } else {
            min(contentW, maxLongEdgePx) to (contentH - gaps) / sizes.size
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

    /**
     * Writes a small copy of [file], rotated clockwise by [rotation] and
     * scaled so its long edge is about [longEdgePx], to [outFile]. Used to
     * ask OCR which way up a sideways capture reads best without touching
     * the full-resolution image more than once.
     */
    internal fun writeRotatedPreview(file: File, rotation: Int, outFile: File, longEdgePx: Int = 1400) {
        val (w, h) = dimensionsOf(file)
        val target = longEdgePx.toFloat() / max(w, h)
        val src = decodeScaled(file, (w * target).toInt(), (h * target).toInt())
        try {
            val matrix = Matrix().apply {
                val scale = longEdgePx.toFloat() / max(src.width, src.height)
                if (scale < 1f) postScale(scale, scale)
                postRotate(rotation.toFloat())
            }
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            try {
                writeJpeg(out, outFile, quality = 85)
            } finally {
                if (out !== src) out.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    internal fun dimensionsOf(file: File): Size {
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

    /** Atomic write: encode to a sibling tmp file, then rename into place. */
    private fun writeJpeg(bitmap: Bitmap, outFile: File, quality: Int = JpegQuality) {
        val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
    }
}
