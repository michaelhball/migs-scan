package dev.migs.scan.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.migs.scan.share.ShareFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "migs_settings")

class SettingsRepository(context: Context) {

    private val store = context.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            scannerUi = prefs[KeyScannerUi]?.let { runCatching { ScannerUi.valueOf(it) }.getOrNull() }
                ?: ScannerUi.Full,
            defaultShareFormat = prefs[KeyDefaultShareFormat]
                ?.let { runCatching { ShareFormat.valueOf(it) }.getOrNull() },
            presets = prefs[KeyPresets]?.let(::decodePresets).orEmpty(),
            backupFolderUri = prefs[KeyBackupFolderUri],
            lastBackupAt = prefs[KeyLastBackupAt],
        )
    }

    suspend fun setScannerUi(mode: ScannerUi) {
        store.edit { it[KeyScannerUi] = mode.name }
    }

    suspend fun setDefaultShareFormat(format: ShareFormat?) {
        store.edit { prefs ->
            if (format == null) prefs.remove(KeyDefaultShareFormat)
            else prefs[KeyDefaultShareFormat] = format.name
        }
    }

    suspend fun addPreset(preset: Preset) {
        store.edit { prefs ->
            val current = prefs[KeyPresets]?.let(::decodePresets).orEmpty()
            prefs[KeyPresets] = encodePresets(current + preset)
        }
    }

    suspend fun removePreset(presetId: String) {
        store.edit { prefs ->
            val current = prefs[KeyPresets]?.let(::decodePresets).orEmpty()
            prefs[KeyPresets] = encodePresets(current.filterNot { it.id == presetId })
        }
    }

    suspend fun setBackupFolderUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(KeyBackupFolderUri) else prefs[KeyBackupFolderUri] = uri
        }
    }

    suspend fun setLastBackupAt(epochMs: Long) {
        store.edit { it[KeyLastBackupAt] = epochMs }
    }

    companion object {
        private val KeyScannerUi = stringPreferencesKey("scanner_ui")
        private val KeyDefaultShareFormat = stringPreferencesKey("default_share_format")
        private val KeyPresets = stringPreferencesKey("presets")
        private val KeyBackupFolderUri = stringPreferencesKey("backup_folder_uri")
        private val KeyLastBackupAt = longPreferencesKey("last_backup_at")

        // One preset per line. Fields tab-separated, in fixed order:
        //   id<TAB>label<TAB>format<TAB>packageName<TAB>emails-comma-separated
        // packageName empty = null. emails empty = no recipients. label fields
        // sanitised to strip TAB / NEWLINE.
        internal fun encodePresets(presets: List<Preset>): String =
            presets.joinToString("\n") { p ->
                listOf(
                    p.id,
                    p.label.sanitise(),
                    p.format.name,
                    (p.packageName ?: "").sanitise(),
                    p.emails.joinToString(",") { it.sanitise() },
                ).joinToString("\t")
            }

        internal fun decodePresets(encoded: String): List<Preset> =
            encoded.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 5) return@mapNotNull null
                    val format = runCatching { ShareFormat.valueOf(parts[2]) }.getOrNull()
                        ?: return@mapNotNull null
                    Preset(
                        id = parts[0],
                        label = parts[1],
                        format = format,
                        packageName = parts[3].ifEmpty { null },
                        emails = parts[4].split(',').filter { it.isNotBlank() },
                    )
                }
                .toList()

        private fun String.sanitise(): String = this.replace('\t', ' ').replace('\n', ' ')
    }
}
