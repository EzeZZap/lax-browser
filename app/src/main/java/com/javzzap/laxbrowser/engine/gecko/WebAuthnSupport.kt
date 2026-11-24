package com.javzzap.laxbrowser.engine.gecko

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime

/**
 * Host wiring for website passkeys.
 *
 * On Android 14+, GeckoView talks to the system Credential Manager (with the website origin set
 * via the host `CREDENTIAL_MANAGER_*` permissions). That is what surfaces external password
 * managers the user has enabled as credential providers — 1Password, Bitwarden, Google Password
 * Manager, and others. This class does not store passkeys; the chosen provider does.
 *
 * The [GeckoRuntime.ActivityDelegate] here covers the older GMS FIDO2 fallback when Credential
 * Manager is unavailable or declines the request.
 *
 * Attach from every Activity that shows web content. The most recently resumed Activity owns the
 * runtime delegate so passkeys work in the main browser, Custom Tabs, and installed web apps.
 */
class WebAuthnSupport(
    activity: ComponentActivity,
    private val engine: GeckoEngine,
) : DefaultLifecycleObserver {

    private var pending: GeckoResult<Intent>? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val gecko = pending ?: return@registerForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) {
            gecko.complete(result.data)
        } else {
            gecko.completeExceptionally(RuntimeException("WebAuthn activity did not succeed"))
        }
    }

    private val delegate = GeckoRuntime.ActivityDelegate { intent: PendingIntent ->
        engine.exitFullScreen()
        val result = GeckoResult<Intent>()
        pending = result
        try {
            launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } catch (e: Exception) {
            pending = null
            result.completeExceptionally(e)
        }
        result
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        engine.setWebAuthnActivityDelegate(this, delegate)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        engine.clearWebAuthnActivityDelegate(this)
        pending?.completeExceptionally(RuntimeException("Host activity destroyed"))
        pending = null
    }
}
