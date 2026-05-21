package dev.migs.scan.backup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.migs.scan.data.ScanStore
import dev.migs.scan.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed class BackupState {
    object Idle : BackupState()
    object InProgress : BackupState()
    data class Done(val written: Int, val total: Int) : BackupState()
    data class Failed(val message: String) : BackupState()
}

class BackupViewModel internal constructor(
    app: Application,
    private val store: ScanStore,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app = app,
        store = ScanStore(app),
        backup = BackupRepository(app),
        settings = SettingsRepository(app),
    )

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** Persists the [tree] URI and takes the persistable read+write permission. */
    fun chooseFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(tree, flags)
        }
        viewModelScope.launch { settings.setBackupFolderUri(tree.toString()) }
    }

    fun forgetFolder() {
        viewModelScope.launch { settings.setBackupFolderUri(null) }
    }

    /** Reconciles every scan into the chosen folder, skipping ones already there. */
    fun backupNow(treeUriString: String) {
        val tree = Uri.parse(treeUriString)
        viewModelScope.launch {
            _state.value = BackupState.InProgress
            try {
                val all = store.loadAll()
                val written = backup.backupAll(tree, all)
                settings.setLastBackupAt(Instant.now().toEpochMilli())
                _state.value = BackupState.Done(written = written, total = all.size)
            } catch (t: Throwable) {
                _state.value = BackupState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }
}
