package dev.migs.scan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.migs.scan.data.Scan
import dev.migs.scan.data.ScanPayload
import dev.migs.scan.data.ScanStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel internal constructor(
    app: Application,
    private val store: ScanStore,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, ScanStore(app))

    private val _scans = MutableStateFlow<List<Scan>>(emptyList())
    val scans: StateFlow<List<Scan>> = _scans.asStateFlow()

    init {
        viewModelScope.launch {
            _scans.value = store.loadAll()
        }
    }

    fun onScanResult(result: GmsDocumentScanningResult) {
        onPayload(ScanPayload.fromMlKit(result))
    }

    internal fun onPayload(payload: ScanPayload) {
        viewModelScope.launch {
            val scan = store.persist(payload)
            _scans.value = (listOf(scan) + _scans.value).resort()
        }
    }

    fun delete(scan: Scan) {
        viewModelScope.launch {
            store.delete(scan)
            _scans.value = _scans.value.filterNot { it.id == scan.id }
        }
    }

    fun rename(scan: Scan, newName: String) {
        viewModelScope.launch {
            val renamed = store.rename(scan, newName)
            _scans.value = _scans.value.map { if (it.id == scan.id) renamed else it }
                .resort()
        }
    }

    fun setStarred(scan: Scan, starred: Boolean) {
        viewModelScope.launch {
            val updated = store.setStarred(scan, starred)
            _scans.value = _scans.value.map { if (it.id == scan.id) updated else it }
                .resort()
        }
    }

    private fun List<Scan>.resort(): List<Scan> =
        sortedWith(compareByDescending<Scan> { it.starred }.thenByDescending { it.createdAt })
}
