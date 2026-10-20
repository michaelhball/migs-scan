package dev.migs.scan.data

/**
 * What the pages of a [ScanPayload] represent, which decides how they are
 * laid out on disk.
 * - [Document]: one JPEG and one PDF page per captured sheet (the default).
 * - [IdCard]: the captured sides (front, back) are composed onto a single
 *   A4-proportioned page, so a card doesn't eat a whole sheet per side.
 * - [Passport]: one capture of the photo page, laid landscape on a portrait
 *   page at roughly real size, and turned upright if it was shot sideways.
 */
enum class ScanKind { Document, IdCard, Passport }
