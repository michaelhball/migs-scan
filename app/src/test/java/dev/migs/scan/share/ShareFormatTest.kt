package dev.migs.scan.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareFormatTest {
    @Test fun pdfMimeIsApplicationPdf() {
        assertThat(ShareFormat.Pdf.mime).isEqualTo("application/pdf")
    }

    @Test fun jpegMimeIsImageJpeg() {
        assertThat(ShareFormat.Jpeg.mime).isEqualTo("image/jpeg")
    }

    @Test fun pngMimeIsImagePng() {
        assertThat(ShareFormat.Png.mime).isEqualTo("image/png")
    }

    @Test fun coversAllFormats() {
        assertThat(ShareFormat.entries).hasSize(3)
    }
}
