package dev.migs.scan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.migs.scan.data.Scan
import dev.migs.scan.data.ScanStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ScanStore(app)
    private val _scans = MutableStateFlow<List<Scan>>(emptyList())
    val scans: StateFlow<List<Scan>> = _scans.asStateFlow()

    init {
        viewModelScope.launch {
            _scans.value = store.loadAll()
        }
    }

    fun onScanResult(result: GmsDocumentScanningResult) {
        viewModelScope.launch {
            val scan = store.persist(result)
            _scans.value = listOf(scan) + _scans.value
        }
    }

    fun delete(scan: Scan) {
        viewModelScope.launch {
            store.delete(scan)
            _scans.value = _scans.value.filterNot { it.id == scan.id }
        }
    }
}
