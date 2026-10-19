package com.javzzap.laxbrowser.pwa

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.javzzap.laxbrowser.ui.prompts.WebPromptHost
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.engine.ExternalLinkHandler
import com.javzzap.laxbrowser.ui.theme.LaxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/**
 * Runs an installed web app in standalone mode, in the container recorded at install time.
 *
 * - In-scope navigation is shown without browser UI.
 * - Out-of-scope navigation in the same window stays here (so sign-in flows keep their cookies)
 *   but shows a bar with the current origin and a way back to the app.
 * - Links that open a new window are handed to the main browser as a new tab.
 * - Back goes back in session history, then closes the window.
 * - Non-web links go through [ExternalLinkHandler].
 */
class WebAppActivity : AppCompatActivity() {
    private val viewModel: WebAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appId = WebAppInstaller.appIdFrom(intent?.data)
        if (appId == null) {
            finish()
            return
        }
        viewModel.load(appId)
        setContent {
            val browserState by appContainer.controller.state.collectAsStateWithLifecycle()
            LaxTheme(wallpaperColors = browserState.data.settings.wallpaperColors) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                LaunchedEffect(ui.app, ui.icon) { ui.app?.let { applyTaskDescription(it, ui.icon) } }
                WebAppScreen(
                    ui = ui,
                    onBack = { if (!viewModel.goBack()) finish() },
                    onReturnToApp = viewModel::returnToApp,
                    onOpenInBrowser = { url -> openInBrowser(url) },
                    onClose = ::finish,
                )
                val engine = appContainer.engine
                WebPromptHost(engine.prompts.prompts, engine.permissions.requests, accepts = { it == viewModel.promptTabId })
            }
        }
        viewModel.openInBrowser = ::openInBrowser
        viewModel.externalLinks = appContainer.engine.externalLinks
    }

    override fun onDestroy() {
        viewModel.openInBrowser = null
        super.onDestroy()
    }

    private fun openInBrowser(url: String) {
        if (!UrlInput.isWebUrl(url)) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(url.toUri()),
        )
    }

    @Suppress("DEPRECATION")
    private fun applyTaskDescription(app: WebApp, icon: Bitmap?) {
        val color = app.themeColor?.let { it or 0xFF000000.toInt() } ?: 0
        setTaskDescription(ActivityManager.TaskDescription(app.name, icon, color))
    }
}

data class WebAppUi(
    val app: WebApp? = null,
    val icon: Bitmap? = null,
    val session: GeckoSession? = null,
    @param:StringRes val error: Int? = null,
    val currentUrl: String? = null,
    val outOfScope: Boolean = false,
    val canGoBack: Boolean = false,
)

class WebAppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val mutableUi = MutableStateFlow(WebAppUi())
    val ui: StateFlow<WebAppUi> = mutableUi.asStateFlow()

    var openInBrowser: ((String) -> Unit)? = null
    var externalLinks: ExternalLinkHandler? = null
    private var loading = false

    /** Identifies this window's web page dialogs and permission requests. */
    val promptTabId = "webapp:" + java.util.UUID.randomUUID()

    fun load(appId: String) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val state = container.controller.state.first { it.isLoaded }
            val app = state.data.webApps.firstOrNull { it.id == appId }
            if (app == null) {
                mutableUi.value = WebAppUi(error = R.string.web_app_error_removed)
                return@launch
            }
            if (state.container(app.containerId) == null) {
                mutableUi.value = WebAppUi(app = app, error = R.string.web_app_error_container)
                return@launch
            }
            val icon = container.webAppInstaller.loadIcon(app)
            val session = createSession(app)
            mutableUi.value = WebAppUi(app = app, icon = icon, session = session, currentUrl = app.startUrl)
        }
    }

    fun goBack(): Boolean {
        val current = mutableUi.value
        val session = current.session ?: return false
        if (!current.canGoBack) return false
        session.goBack()
        return true
    }

    fun returnToApp() {
        val current = mutableUi.value
        current.session?.loadUri(current.app?.startUrl ?: return)
    }

    private fun createSession(app: WebApp): GeckoSession {
        val engine = container.engine
        val displayMode = when (app.display) {
            "fullscreen" -> GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN
            "minimal-ui" -> GeckoSessionSettings.DISPLAY_MODE_MINIMAL_UI
            else -> GeckoSessionSettings.DISPLAY_MODE_STANDALONE
        }
        val session = engine.sessionFactory.create(app.containerId, displayMode)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            private var errorPage: String? = null

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> =
                GeckoResult.fromValue(engine.errorPages.pageFor(uri, error).also { errorPage = it })

            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<ContentPermission>, hasUserGesture: Boolean) {
                if (url == null || url == errorPage) return
                mutableUi.value = mutableUi.value.copy(
                    currentUrl = url,
                    outOfScope = url != "about:blank" && !WebAppEligibility.isInScope(url, app.scope),
                )
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                mutableUi.value = mutableUi.value.copy(canGoBack = canGoBack)
            }

            override fun onLoadRequest(session: GeckoSession, request: LoadRequest): GeckoResult<AllowOrDeny>? {
                val links = externalLinks ?: engine.externalLinks
                if (!links.isWebScheme(request.uri)) {
                    if (request.hasUserGesture) {
                        val result = links.open(request.uri)
                        if (result is ExternalLinkHandler.Result.Fallback) session.loadUri(result.url)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    if (WebAppEligibility.isInScope(request.uri, app.scope)) {
                        session.loadUri(request.uri)
                    } else {
                        openInBrowser?.invoke(request.uri)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                openInBrowser?.invoke(uri)
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onWebAppManifest(session: GeckoSession, manifest: JSONObject) {
                val parsed = runCatching { WebAppManifestParser.parse(manifest) }.getOrNull() ?: return
                updateFromManifest(app, parsed)
            }

            override fun onCloseRequest(session: GeckoSession) {
                mutableUi.value = mutableUi.value.copy(error = R.string.web_app_error_closed, session = null)
            }

            override fun onCrash(session: GeckoSession) {
                mutableUi.value = mutableUi.value.copy(error = R.string.web_app_error_crashed, session = null)
            }

            override fun onKill(session: GeckoSession) {
                mutableUi.value = mutableUi.value.copy(error = R.string.web_app_error_killed, session = null)
            }
        }
        session.promptDelegate = engine.prompts.delegateFor { promptTabId }
        session.permissionDelegate = engine.permissions.delegateFor { promptTabId }
        session.open(engine.runtime)
        session.loadUri(app.startUrl)
        return session
    }

    /** Keeps stored metadata in sync when the site's manifest changes, without changing identity. */
    private fun updateFromManifest(app: WebApp, manifest: com.javzzap.laxbrowser.domain.WebAppManifest) {
        val sameApp = manifest.startUrl == app.startUrl || manifest.scope == app.scope
        if (!sameApp || UrlInput.originOf(manifest.startUrl) != UrlInput.originOf(app.startUrl)) return
        val name = manifest.displayName ?: return
        val updated = app.copy(
            name = name,
            shortName = manifest.shortName,
            themeColor = manifest.themeColor,
            backgroundColor = manifest.backgroundColor,
            updatedAt = container.clock.now(),
        )
        if (updated.copy(updatedAt = app.updatedAt) == app) return
        container.controller.upsertWebApp(updated)
        viewModelScope.launch {
            container.webAppInstaller.updateShortcut(updated, container.webAppInstaller.loadIcon(updated))
        }
        mutableUi.value = mutableUi.value.copy(app = updated)
    }

    override fun onCleared() {
        container.engine.prompts.cancelFor(promptTabId)
        container.engine.permissions.cancelFor(promptTabId)
        mutableUi.value.session?.let { if (it.isOpen) it.close() }
    }
}

@Composable
private fun WebAppScreen(
    ui: WebAppUi,
    onBack: () -> Unit,
    onReturnToApp: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        when {
            ui.error != null -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(ui.error), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            ui.session != null -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (ui.outOfScope && ui.currentUrl != null) {
                    OutOfScopeBar(ui.currentUrl, onReturnToApp, { onOpenInBrowser(ui.currentUrl) })
                }
                val session = ui.session
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        GeckoView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { view -> if (view.session !== session) view.setSession(session) },
                    onRelease = { it.releaseSession() },
                )
            }
        }
    }
}

@Composable
private fun OutOfScopeBar(url: String, onReturnToApp: () -> Unit, onOpenInBrowser: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onReturnToApp) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.web_app_return)) }
            Text(
                UrlInput.originOf(url) ?: url,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenInBrowser) { Text(stringResource(R.string.web_app_open_in_browser)) }
        }
    }
}
