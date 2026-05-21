package dev.migs.scan.data

import java.io.File
import java.time.Instant

data class Scan(
    val id: String,
    /** User-editable label; defaults to "Scan yyyy-MM-dd HHmm" at capture time. */
    val name: String,
    val createdAt: Instant,
    val pdf: File,
    val pages: List<File>,
)
