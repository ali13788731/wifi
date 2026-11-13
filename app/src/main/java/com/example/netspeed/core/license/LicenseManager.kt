package com.example.netspeed.core.license

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.netspeed.BuildConfig
import com.example.netspeed.core.notify.PurchaseReminder
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest

/**
 * Talks to Cafe Bazaar (Poolakey) about the ONE non-consumable "Pro" product ([BuildConfig.BAZAAR_SKU]).
 *
 * The app is FREE and never blocked. This class only decides whether [ProState] is granted:
 * ownership is checked against the Bazaar account signed in on THIS phone, so an APK copied from
 * someone else is simply the free version. The result of [ProState] is what the rest of the app reads.
 */
class LicenseManager(private val activity: ComponentActivity, private val pro: ProState) {

    enum class State { CHECKING, LICENSED, UNLICENSED, NO_BAZAAR, ERROR }

    var state by mutableStateOf(if (pro.isProNow()) State.LICENSED else State.CHECKING)
        private set

    /** Last problem worth showing in the Pro dialog (only set after the user pressed buy / re-check). */
    var problem by mutableStateOf<State?>(null)
        private set

    private var userInitiated = false
    private var connection: Connection? = null
    private val payment: Payment? by lazy {
        if (!BuildConfig.LICENSE_REQUIRED) null
        else Payment(activity, PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = BuildConfig.BAZAAR_RSA_KEY)))
    }

    private fun grant() {
        pro.grant(); state = State.LICENSED; problem = null
        PurchaseReminder.onLicensed(activity.applicationContext)      // bought: stop the reminders
    }

    private fun revoke() {
        pro.revoke(); state = State.UNLICENSED
        if (userInitiated) problem = State.UNLICENSED
        PurchaseReminder.onUnlicensed(activity.applicationContext)    // Bazaar confirmed: not bought -> remind later
    }

    /** Bazaar could not be reached: a recent successful check keeps Pro on, otherwise stay free. */
    private fun unreachable(noBazaar: Boolean) {
        val kept = pro.isProNow()
        state = if (kept) State.LICENSED else if (noBazaar) State.NO_BAZAAR else State.ERROR
        if (!kept && userInitiated) problem = state
    }

    /** Silent at app start ([byUser] = false); shows a message in the Pro dialog when the user asked. */
    fun check(byUser: Boolean = false) {
        val p = payment ?: return
        userInitiated = byUser
        if (byUser) problem = null
        if (state != State.LICENSED) state = State.CHECKING
        connection?.disconnect()
        connection = p.connect {
            connectionSucceed {
                p.getPurchasedProducts {
                    querySucceed { list -> if (list.any { it.productId == BuildConfig.BAZAAR_SKU }) grant() else revoke() }
                    queryFailed { unreachable(false) }
                }
            }
            connectionFailed { unreachable(true) }
            disconnected { }
        }
    }

    fun buy() {
        val p = payment ?: return
        userInitiated = true
        problem = null
        p.purchaseProduct(activity.activityResultRegistry, PurchaseRequest(productId = BuildConfig.BAZAAR_SKU, payload = "")) {
            purchaseFlowBegan { }
            failedToBeginFlow { state = State.NO_BAZAAR; problem = State.NO_BAZAAR }
            purchaseSucceed { grant() }
            purchaseCanceled { }
            purchaseFailed { state = State.ERROR; problem = State.ERROR }
        }
    }

    fun clearProblem() { problem = null }

    fun release() { connection?.disconnect(); connection = null }
}
