package com.example.netspeed.core.common

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("settings")

data class Settings(
    val defaultMode: String = "DUAL",            // DUAL / WIFI_ONLY / MOBILE_ONLY / AUTO
    val maxConcurrent: Int = 2,
    val connectionsPerNetwork: Int = 3,
    val speedLimitBps: Long = 0,                 // 0 = unlimited
    val mobileLimitBytes: Long = 0,              // monthly mobile-data cap, 0 = unlimited
    val mobileWarning: Boolean = false,
    val autoRetry: Boolean = true,
    val theme: String = "system",                // system / light / dark
    val language: String = "system",             // system / fa / en
    val saveTree: String? = null,                // SAF tree uri
    val overwrite: Boolean = false,
    val statusBarSpeed: Boolean = true,          // live speed icon in the status bar
    val clipboardDetect: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val mode = stringPreferencesKey("mode")
        val maxC = intPreferencesKey("maxConcurrent")
        val conns = intPreferencesKey("conns")
        val speed = longPreferencesKey("speedLimit")
        val mobLimit = longPreferencesKey("mobileLimit")
        val mobWarn = booleanPreferencesKey("mobileWarn")
        val retry = booleanPreferencesKey("retry")
        val theme = stringPreferencesKey("theme")
        val lang = stringPreferencesKey("lang")
        val tree = stringPreferencesKey("tree")
        val overwrite = booleanPreferencesKey("overwrite")
        val sbSpeed = booleanPreferencesKey("sbSpeed")
        val clip = booleanPreferencesKey("clip")
    }

    val flow: Flow<Settings> = ctx.store.data.map { p ->
        val d = Settings()
        Settings(
            p[K.mode] ?: d.defaultMode, p[K.maxC] ?: d.maxConcurrent, p[K.conns] ?: d.connectionsPerNetwork,
            p[K.speed] ?: d.speedLimitBps, p[K.mobLimit] ?: d.mobileLimitBytes,
            p[K.mobWarn] ?: d.mobileWarning, p[K.retry] ?: d.autoRetry, p[K.theme] ?: d.theme,
            p[K.lang] ?: d.language, p[K.tree], p[K.overwrite] ?: d.overwrite,
            p[K.sbSpeed] ?: d.statusBarSpeed, p[K.clip] ?: d.clipboardDetect
        )
    }

    private suspend fun edit(f: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ctx.store.edit { f(it) }
    }

    suspend fun setMode(v: String) = edit { it[K.mode] = v }
    suspend fun setMaxConcurrent(v: Int) = edit { it[K.maxC] = v.coerceIn(1, 5) }
    suspend fun setConnections(v: Int) = edit { it[K.conns] = v.coerceIn(1, 8) }
    suspend fun setSpeedLimit(v: Long) = edit { it[K.speed] = v.coerceAtLeast(0) }
    suspend fun setMobileLimit(v: Long) = edit { it[K.mobLimit] = v.coerceAtLeast(0) }
    suspend fun setMobileWarning(v: Boolean) = edit { it[K.mobWarn] = v }
    suspend fun setAutoRetry(v: Boolean) = edit { it[K.retry] = v }
    suspend fun setTheme(v: String) = edit { it[K.theme] = v }
    suspend fun setLanguage(v: String) = edit { it[K.lang] = v }
    suspend fun setTree(v: String?) = edit { if (v == null) it.remove(K.tree) else it[K.tree] = v }
    suspend fun setOverwrite(v: Boolean) = edit { it[K.overwrite] = v }
    suspend fun setStatusBarSpeed(v: Boolean) = edit { it[K.sbSpeed] = v }
    suspend fun setClipboard(v: Boolean) = edit { it[K.clip] = v }
}
