package dev.migs.scan.settings

import dev.migs.scan.share.ShareFormat
import java.util.UUID

data class Preset(
    val id: String = UUID.randomUUID().toString(),
    /** Display name shown as the tile label. */
    val label: String,
    val format: ShareFormat,
    /** Target app package (e.g. "com.google.android.gm"). Null = OS chooser. */
    val packageName: String?,
    /** Pre-fills Intent.EXTRA_EMAIL when set. Only meaningful for email apps. */
    val emails: List<String> = emptyList(),
)
