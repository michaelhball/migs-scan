package dev.migs.scan.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    companion object {
        private val KeyScannerUi = stringPreferencesKey("scanner_ui")
        private val KeyDefaultShareFormat = stringPreferencesKey("default_share_format")
    }
}
