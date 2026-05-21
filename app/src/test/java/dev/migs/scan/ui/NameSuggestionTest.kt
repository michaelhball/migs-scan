package dev.migs.scan.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameSuggestionTest {

    @Test fun `returns null for blank text`() {
        assertThat(suggestedNameFromOcr("", "anything")).isNull()
        assertThat(suggestedNameFromOcr("   \n\n  ", "anything")).isNull()
    }

    @Test fun `returns the first non-trivial line`() {
        val text = """
            a
            xx
            INVOICE 2024
            Other line
        """.trimIndent()
        assertThat(suggestedNameFromOcr(text, "Scan 2026")).isEqualTo("INVOICE 2024")
    }

    @Test fun `skips lines that are only punctuation or whitespace`() {
        val text = """
            ----
            ----
            Real text here
        """.trimIndent()
        assertThat(suggestedNameFromOcr(text, "Scan")).isEqualTo("Real text here")
    }

    @Test fun `collapses internal whitespace and caps length`() {
        val long = "A very long line".repeat(20)
        val text = "Tabs\there\tand    spaces    here\n$long"
        val suggestion = suggestedNameFromOcr(text, "Scan")!!
        assertThat(suggestion).isEqualTo("Tabs here and spaces here")
    }

    @Test fun `returns null if suggestion equals current name`() {
        assertThat(suggestedNameFromOcr("Passport renewal", "Passport renewal")).isNull()
        assertThat(suggestedNameFromOcr("Passport Renewal", "passport renewal")).isNull()  // case-insensitive
    }

    @Test fun `caps a single very long line at 60 chars`() {
        val text = "A".repeat(200)
        val suggestion = suggestedNameFromOcr(text, "Scan")!!
        assertThat(suggestion.length).isAtMost(60)
    }
}
