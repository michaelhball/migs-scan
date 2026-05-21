package dev.migs.scan.data

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File

/**
 * Builds a PDF from a list of page JPEGs. Each page is sized to A4 (595x842
 * points) with the bitmap scaled to fit while preserving aspect ratio and
 * letterboxed on a white background. Output is written to [outFile]
 * atomically (write to .tmp, then rename).
 */
object PdfBuilder {

    private const val A4WidthPt = 595
    private const val A4HeightPt = 842

    fun build(pages: List<File>, outFile: File) {
        require(pages.isNotEmpty()) { "PDF must have at least one page" }
        val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, jpg ->
                val bmp = BitmapFactory.decodeFile(jpg.absolutePath)
                    ?: error("Could not decode ${jpg.name}")
                try {
                    val pageInfo = PdfDocument.PageInfo
                        .Builder(A4WidthPt, A4HeightPt, index + 1)
                        .create()
                    val page = pdf.startPage(pageInfo)
                    try {
                        val canvas: Canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        // Fit the bitmap inside the A4 page, preserving aspect ratio.
                        val pageAspect = A4WidthPt.toFloat() / A4HeightPt
                        val bmpAspect = bmp.width.toFloat() / bmp.height
                        val dst: Rect = if (bmpAspect > pageAspect) {
                            // Bitmap is wider than page → fit width.
                            val h = (A4WidthPt / bmpAspect).toInt()
                            val top = (A4HeightPt - h) / 2
                            Rect(0, top, A4WidthPt, top + h)
                        } else {
                            // Bitmap is taller than page → fit height.
                            val w = (A4HeightPt * bmpAspect).toInt()
                            val left = (A4WidthPt - w) / 2
                            Rect(left, 0, left + w, A4HeightPt)
                        }
                        canvas.drawBitmap(bmp, null, dst, null)
                    } finally {
                        pdf.finishPage(page)
                    }
                } finally {
                    bmp.recycle()
                }
            }
            tmp.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        if (!tmp.renameTo(outFile)) {
            // Same-filesystem rename failure (rare) — fall back to copy + delete.
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
    }
}
