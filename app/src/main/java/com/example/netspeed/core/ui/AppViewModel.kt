package com.example.netspeed.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netspeed.core.common.LinkDetector
import com.example.netspeed.core.common.Settings
import com.example.netspeed.core.common.SettingsRepository
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.downloader.DownloadManager
import com.example.netspeed.core.downloader.NetworkMode
import com.example.netspeed.core.downloader.ProbeResult
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.license.ProState
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
    /** [code]: INVALID_URL, NO_WIFI, NO_MOBILE, NO_BOTH, AUTH, UNREACHABLE, RANGE_UNSUPPORTED, RANGE_INVALID, HTTP_<n> */
    data class Error(val code: String) : AddState()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    val manager: DownloadManager,
    private val netManager: NetworkManager,
    private val settingsRepo: SettingsRepository,
    val storage: StorageHelper,
    val pro: ProState
) : ViewModel() {

    /** true = DualNet Pro is active (see [ProState]). */
    val isPro: StateFlow<Boolean> = pro.isPro

    val settings: StateFlow<Settings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val downloads: StateFlow<List<DownloadEntity>> =
        manager.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val live = manager.live
    val net = netManager.state
    val speedHistory = manager.speedHistory

    var addState by mutableStateOf<AddState>(AddState.Idle)
        private set

    /** null when both networks are on; otherwise the code of what is missing (see Texts.blocked). */
    fun missingNetwork(): String? = manager.missingNetwork()

    /** Set by dialogs/sheets that want to send the user to a settings page (e.g. "downloads"). */
    var settingsRequest by mutableStateOf<String?>(null)

    private fun norm(u: String) = u.trim().substringBefore('#').trimEnd('/')

    /** True when this link is already in the list (finished ones count only while their file still exists). */
    private fun isDuplicate(vararg urls: String): Boolean {
        val set = urls.map { norm(it) }.toSet()
        return downloads.value.any { e ->
            (norm(e.url) in set || norm(e.finalUrl) in set) && when (e.status) {
                "CANCELED" -> false
                "COMPLETED" -> storage.exists(e.resultUri)
                else -> true
            }
        }
    }

    fun resetAdd() { addState = AddState.Idle }

    fun inspect(url: String, opts: RequestOptions) {
        val u = url.trim()
        if (!(u.startsWith("http://", true) || u.startsWith("https://", true)) ||
            u.length < 11 || u.contains(' ')) {
            addState = AddState.Error("INVALID_URL"); return
        }
        if (isDuplicate(u)) { addState = AddState.Error("DUPLICATE"); return }
        // Needs Wi-Fi AND mobile data (or one of them if the user allowed it): say exactly what is missing.
        manager.missingNetwork()?.let { addState = AddState.Error(it); return }
        addState = AddState.Checking
        viewModelScope.launch {
            val p = manager.inspect(u, opts)
            addState = when {
                p.authRequired -> AddState.Error("AUTH")
                isDuplicate(u, p.finalUrl) -> AddState.Error("DUPLICATE")
                // No verified HTTP Range = no dual-network download = the app refuses the link.
                !p.canMultiPart -> AddState.Error(if (p.code == "OK") "RANGE_UNSUPPORTED" else p.code)
                else -> AddState.Ready(u, p, opts)
            }
        }
    }

    /** Only called after the user explicitly confirmed in the add sheet. */
    fun confirmAdd(r: AddState.Ready, name: String, delayMs: Long): Boolean {
        if (isDuplicate(r.url, r.probe.finalUrl)) { addState = AddState.Error("DUPLICATE"); return false }
        viewModelScope.launch {
            manager.add(
                r.url, r.probe, r.opts, nameOverride = name.ifBlank { null },
                requestedAt = if (delayMs > 0) System.currentTimeMillis() + delayMs else 0
            )
            addState = AddState.Idle
        }
        return true
    }

    /**
     * Silent check used for links found in the clipboard: true only when the link is really a
     * downloadable file (known file type, or the server answers with a non-HTML file).
     */
    suspend fun isDownloadable(url: String): Boolean {
        if (LinkDetector.looksLikeFile(url)) return true
        val p = runCatching { manager.inspect(url, RequestOptions()) }.getOrNull() ?: return false
        if (p.authRequired) return true
        val html = p.mimeType?.contains("html", ignoreCase = true) == true
        val fileLike = p.canMultiPart || (p.totalBytes > 0 && p.code in setOf("OK", "RANGE_UNSUPPORTED", "RANGE_INVALID"))
        return !html && fileLike
    }

    fun set(block: suspend SettingsRepository.() -> Unit) { viewModelScope.launch { settingsRepo.block() } }
}
