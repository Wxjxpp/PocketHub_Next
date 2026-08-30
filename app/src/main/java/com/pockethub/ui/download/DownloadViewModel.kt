package com.pockethub.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.download.DownloadManager
import com.pockethub.data.local.DownloadEntity
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val manager: DownloadManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val activeList: StateFlow<List<DownloadEntity>> = manager.activeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneList: StateFlow<List<DownloadEntity>> = manager.doneFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** User-chosen SAF download folder (null = default app directory). */
    val downloadFolderUri: StateFlow<String?> = settings.downloadFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setDownloadFolder(uri: String?) {
        viewModelScope.launch { settings.setDownloadFolderUri(uri) }
    }

    fun enqueue(req: DownloadManager.EnqueueRequest) {
        viewModelScope.launch { manager.enqueue(req) }
    }

    fun retry(url: String) = viewModelScope.launch { manager.retry(url) }
    fun cancel(url: String) = viewModelScope.launch { manager.cancel(url) }
    fun removeCompleted(url: String) = viewModelScope.launch { manager.removeCompleted(url) }
}

enum class DownloadTab { ACTIVE, DONE }
