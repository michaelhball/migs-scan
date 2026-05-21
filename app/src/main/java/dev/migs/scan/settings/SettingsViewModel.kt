package dev.migs.scan.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.migs.scan.share.ShareFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel internal constructor(
    app: Application,
    private val repo: SettingsRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, SettingsRepository(app))

    val settings: StateFlow<Settings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setScannerUi(mode: ScannerUi) {
        viewModelScope.launch { repo.setScannerUi(mode) }
    }

    fun setDefaultShareFormat(format: ShareFormat?) {
        viewModelScope.launch { repo.setDefaultShareFormat(format) }
    }

    fun addPreset(preset: Preset) {
        viewModelScope.launch { repo.addPreset(preset) }
    }

    fun removePreset(presetId: String) {
        viewModelScope.launch { repo.removePreset(presetId) }
    }
}
