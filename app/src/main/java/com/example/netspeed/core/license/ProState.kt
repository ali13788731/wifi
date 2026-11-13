package com.example.netspeed.core.license

import android.content.Context
import com.example.netspeed.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "does this user have DualNet Pro?".
 *
 * The app itself is free. Pro is one non-consumable Cafe Bazaar in-app product. [LicenseManager]
 * talks to Bazaar and calls [grant] / [revoke]; everything else (download engine, settings, UI)
 * only reads [isPro] / [isProNow], so the limits also apply when downloads run in the background
 * service without any activity.
 *
 * A successful Bazaar check is remembered for [GRACE_DAYS] so Pro keeps working offline; each time
 * Bazaar is reachable the result is refreshed (a refund removes Pro). Debug builds are always Pro.
 */
@Singleton
class ProState @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lic", Context.MODE_PRIVATE)
    private val _isPro = MutableStateFlow(compute())
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private fun compute(): Boolean {
        if (!BuildConfig.LICENSE_REQUIRED) return true
        val t = prefs.getLong(KEY, 0L)
        return t > 0 && System.currentTimeMillis() - t < GRACE_DAYS * 24L * 3600 * 1000
    }

    /** Re-evaluates the offline grace period (called regularly by the download engine). */
    fun isProNow(): Boolean = compute().also { _isPro.value = it }

    /** Time of the last successful Bazaar check (0 = never). */
    fun verifiedAt(): Long = prefs.getLong(KEY, 0L)

    fun grant() { prefs.edit().putLong(KEY, System.currentTimeMillis()).apply(); _isPro.value = true }
    fun revoke() { prefs.edit().remove(KEY).apply(); _isPro.value = !BuildConfig.LICENSE_REQUIRED }

    companion object {
        const val GRACE_DAYS = 7
        private const val KEY = "ok_at"
    }
}
