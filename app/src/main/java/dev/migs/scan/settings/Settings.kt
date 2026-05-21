package dev.migs.scan.settings

import dev.migs.scan.share.ShareFormat

data class Settings(
    val scannerUi: ScannerUi = ScannerUi.Full,
    /** Null means "ask each time" (open the action sheet). */
    val defaultShareFormat: ShareFormat? = null,
)

/**
 * Which ML Kit scanner UI to launch.
 * - [Full] is the default — multi-page batch, auto/manual capture toggle,
 *   editing (crop/rotate/filter), gallery import.
 * - [Quick] is a minimal single-page snap — no auto-capture, no editing.
 *   Good when you scan one-pagers all day and the full UI feels heavy.
 */
enum class ScannerUi { Full, Quick }
