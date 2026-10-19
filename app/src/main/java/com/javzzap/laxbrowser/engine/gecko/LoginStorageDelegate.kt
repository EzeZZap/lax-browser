package com.javzzap.laxbrowser.engine.gecko

import com.javzzap.laxbrowser.data.LoginStore
import com.javzzap.laxbrowser.domain.SavedLogin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * Gives Gecko the saved logins it needs to fill forms, and stores logins the user agreed to save
 * (Gecko calls [onLoginSave] only after the save prompt was confirmed). Does nothing while saving
 * logins is turned off.
 */
class LoginStorageDelegate(
    private val store: LoginStore,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) : Autocomplete.StorageDelegate {

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> = fetch { store.forDomain(domain) }

    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> = fetch { store.all() }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        if (!enabled()) return
        val origin = login.origin ?: return
        val password = login.password?.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            runCatching { store.save(login.guid, origin, login.formActionOrigin, login.httpRealm, login.username.orEmpty(), password) }
        }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        if (enabled()) store.markUsed(login.guid)
    }

    private fun fetch(query: suspend () -> List<SavedLogin>): GeckoResult<Array<Autocomplete.LoginEntry>> {
        if (!enabled()) return GeckoResult.fromValue(emptyArray())
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        scope.launch {
            val logins = runCatching { query() }.getOrDefault(emptyList())
            result.complete(logins.map(::toEntry).toTypedArray())
        }
        return result
    }

    private fun toEntry(login: SavedLogin): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
        .guid(login.guid)
        .origin(login.origin)
        .formActionOrigin(login.formActionOrigin)
        .httpRealm(login.httpRealm)
        .username(login.username)
        .password(login.password)
        .build()
}
