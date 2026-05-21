package dev.migs.scan.settings

import com.google.common.truth.Truth.assertThat
import dev.migs.scan.share.ShareFormat
import dev.migs.scan.settings.SettingsRepository.Companion.decodePresets
import dev.migs.scan.settings.SettingsRepository.Companion.encodePresets
import org.junit.Test

class SettingsRepositoryTest {

    @Test fun `encode then decode round-trips a preset`() {
        val presets = listOf(
            Preset(id = "abc", label = "Email to dad", format = ShareFormat.Pdf, packageName = "com.google.android.gm", emails = listOf("dad@example.com")),
            Preset(id = "xyz", label = "WhatsApp", format = ShareFormat.Jpeg, packageName = "com.whatsapp", emails = emptyList()),
            Preset(id = "def", label = "Print", format = ShareFormat.Png, packageName = null, emails = emptyList()),
        )

        val decoded = decodePresets(encodePresets(presets))

        assertThat(decoded).isEqualTo(presets)
    }

    @Test fun `decode skips malformed lines`() {
        val bad = "abc\tlabel\tPdf\tcom.x\t\nshort\n\tonly two\t fields"

        val decoded = decodePresets(bad)

        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().label).isEqualTo("label")
    }

    @Test fun `encode sanitises tabs and newlines in labels`() {
        val preset = Preset(id = "abc", label = "Hello\ttab\nnewline", format = ShareFormat.Pdf, packageName = null)

        val encoded = encodePresets(listOf(preset))

        // After sanitisation the label can't include either separator.
        val decoded = decodePresets(encoded)
        assertThat(decoded.single().label).isEqualTo("Hello tab newline")
    }
}
