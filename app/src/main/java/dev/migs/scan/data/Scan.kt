package dev.migs.scan.data

import java.io.File
import java.time.Instant

data class Scan(
    val id: String,
    val createdAt: Instant,
    val pdf: File,
    val pages: List<File>,
)
