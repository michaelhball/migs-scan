package dev.migs.scan.ui

/**
 * Picks a reasonable suggested name from OCR text — the first non-trivial
 * line, trimmed and length-capped. Returns null if nothing useful is found
 * or if the candidate matches [currentName] (no point suggesting what's
 * already there).
 */
internal fun suggestedNameFromOcr(text: String, currentName: String): String? {
    if (text.isBlank()) return null
    val candidate = text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { line -> line.length >= MinUsefulLength && line.any { it.isLetterOrDigit() } }
        ?.let { line ->
            // Squash whitespace runs; cap length to keep filenames sane.
            line.replace(WhitespaceRun, " ").take(MaxSuggestedLength).trim()
        }
        ?: return null
    return candidate.takeIf { it.isNotEmpty() && !it.equals(currentName, ignoreCase = true) }
}

private const val MinUsefulLength = 4
private const val MaxSuggestedLength = 60
private val WhitespaceRun = Regex("\\s+")
