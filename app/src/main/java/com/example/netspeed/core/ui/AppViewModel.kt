package com.example.netspeed.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netspeed.core.common.Settings
import com.example.netspeed.core.common.SettingsRepository
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.downloader.DownloadManager
import com.example.netspeed.core.downloader.NetworkMode
import com.example.netspeed.core.downloader.ProbeResult
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.network.NetworkManager
import com.example.netspeed.core.storage.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddState {
    object Idle : AddState()
    object Checking : AddState()
    data class Ready(val url: String, val probe: ProbeResult, val opts: RequestOptions) : AddState()
    data class Error(val message: String) : AddState()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    val manager: DownloadManager,
    netManager: NetworkManager,
    private val settingsRepo: SettingsRepository,
    val storage: StorageHelper
) : ViewModel() {

    val settings: StateFlow<Settings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val downloads: StateFlow<List<DownloadEntity>> =
        manager.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val live = manager.live
    val net = netManager.state

    var addState by mutableStateOf<AddState>(AddState.Idle)
        private set

    fun resetAdd() { addState = AddState.Idle }

    fun inspect(url: String, opts: RequestOptions) {
        val u = url.trim()
        if (!(u.startsWith("http://", true) || u.startsWith("https://", true)) ||
            u.length < 11 || u.contains(' ')) {
            addState = AddState.Error("invalid"); return
        }
        addState = AddState.Checking
        viewModelScope.launch {
            val p = manager.inspect(u, opts)
            addState = when {
                p.authRequired -> AddState.Error(p.message)
                p.totalBytes <= 0 && !p.rangeVerified && p.message.startsWith("Cannot reach") -> AddState.Error(p.message)
                else -> AddState.Ready(u, p, opts)
            }
        }
    }

    /** Only called after the user explicitly confirmed in the add sheet. */
    fun confirmAdd(r: AddState.Ready, mode: NetworkMode, name: String, delayMs: Long) {
        viewModelScope.launch {
            manager.add(
                r.url, r.probe, r.opts, mode, nameOverride = name.ifBlank { null },
                scheduledAt = if (delayMs > 0) System.currentTimeMillis() + delayMs else 0
            )
            addState = AddState.Idle
        }
    }

    fun set(block: suspend SettingsRepository.() -> Unit) { viewModelScope.launch { settingsRepo.block() } }
}
