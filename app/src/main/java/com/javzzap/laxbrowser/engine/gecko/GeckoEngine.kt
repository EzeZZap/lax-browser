package com.javzzap.laxbrowser.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.javzzap.laxbrowser.data.LoginStore
import com.javzzap.laxbrowser.data.SessionStateStore
import com.javzzap.laxbrowser.domain.AutoplayPolicy
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.downloads.DownloadRequest
import com.javzzap.laxbrowser.domain.ContextMenuTarget
import com.javzzap.laxbrowser.domain.EngineCommand
import com.javzzap.laxbrowser.domain.EngineSettings
import com.javzzap.laxbrowser.domain.FaviconSelector
import com.javzzap.laxbrowser.domain.HistoryPolicy
import com.javzzap.laxbrowser.domain.PRIVATE_CONTEXT_ID
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.TrackingProtection
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebsiteAppearance
import com.javzzap.laxbrowser.engine.AppLinkMode
import com.javzzap.laxbrowser.engine.AppLinks
import com.javzzap.laxbrowser.engine.BrowserEngine
import com.javzzap.laxbrowser.engine.LinkApp
import com.javzzap.laxbrowser.engine.EngineListener
import com.javzzap.laxbrowser.engine.ExternalLinkHandler
import com.javzzap.laxbrowser.pwa.WebAppManifestParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.InputStream
import java.util.UUID

/** Result of a find-in-page step: 1-based [current] match of [total] (-1 when unknown). */
data class FindResult(val query: String, val current: Int, val total: Int)

/**
 * Owns the process-wide [GeckoRuntime] and maps app tab IDs to [GeckoSession]s. Sessions are
 * created only through [sessionFactory]; screens never own the runtime or sessions.
 */
class GeckoEngine(
    context: Context,
    private val sessionStates: SessionStateStore,
    private val scope: CoroutineScope,
    val externalLinks: ExternalLinkHandler,
    private val loginStore: LoginStore,
    val appLinks: AppLinks,
) : BrowserEngine {

    private val appContext = context.applicationContext
    private var runtimeInstance: GeckoRuntime? = null
    private var desiredSettings: EngineSettings? = null
    private var appliedSettings: EngineSettings? = null

    val runtime: GeckoRuntime
        get() = runtimeInstance ?: GeckoRuntime.create(
            appContext,
            GeckoRuntimeSettings.Builder()
                .consoleOutput(false)
                .aboutConfigEnabled(false)
                .loginAutofillEnabled(desiredSettings?.saveLogins != false)
                .build(),
        ).also { created ->
            runtimeInstance = created
            iconExtension.install(created)
            extensions.start(created)
            created.webNotificationDelegate = notifications
            created.autocompleteStorageDelegate = LoginStorageDelegate(loginStore, scope) { desiredSettings?.saveLogins != false }
            desiredSettings?.let { applySettings(created, it) }
            requestedLocales?.let { created.settings.setLocales(it) }
        }

    val sessionFactory = GeckoSessionFactory()

    private var requestedLocales: Array<String>? = null

    /** Languages websites are asked for (Accept-Language), most preferred first. */
    fun setRequestedLocales(tags: List<String>) {
        val locales = tags.filter { it.isNotBlank() }.toTypedArray().takeIf { it.isNotEmpty() } ?: return
        if (requestedLocales.contentEquals(locales)) return
        requestedLocales = locales
        runtimeInstance?.settings?.setLocales(locales)
    }

    val extensions: ExtensionsManager = ExtensionsManager(
        object : ExtensionsManager.Host {
            override val runtime: GeckoRuntime get() = this@GeckoEngine.runtime

            override fun sessions(): List<GeckoSession> = entries.values.map { it.session }

            override fun openExtensionTab(url: String?, active: Boolean): GeckoSession? = createExtensionTab(url, active)

            override fun openInNewTab(url: String) {
                mainHandler.post { listener?.onOpenInNewTab(url) }
            }

            override fun selectTab(session: GeckoSession) {
                val tabId = entryFor(session)?.tabId ?: return
                mainHandler.post { listener?.onExtensionSelectTab(tabId) }
            }

            override fun closeTab(session: GeckoSession): Boolean {
                val tabId = entryFor(session)?.tabId ?: return false
                return listener?.onExtensionCloseTab(tabId) == true
            }

            override fun createPopupSession(): GeckoSession = sessionFactory.createExtensionPopup().also { popup ->
                popup.contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onCloseRequest(session: GeckoSession) = extensions.dismissPopup()
                }
                popup.navigationDelegate = object : GeckoSession.NavigationDelegate {
                    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                        if (UrlInput.isWebUrl(uri)) {
                            listener?.onOpenInNewTab(uri)
                            extensions.dismissPopup()
                        }
                        return null
                    }
                }
                popup.open(this@GeckoEngine.runtime)
            }
        },
    )

    val favicons = FaviconRepository(appContext, { runtime }, scope)

    private val iconExtension = IconExtension(
        onIcons = { session, pageUrl, candidates ->
            val entry = entries.values.firstOrNull { it.session === session }
            val iconUrl = FaviconSelector.best(candidates, pageUrl)
            if (entry != null && iconUrl != null) listener?.onFavicon(entry.tabId, pageUrl, iconUrl)
        },
        zoomFor = { pageUrl -> listener?.zoomFor(pageUrl) ?: 100 },
    )

    override var listener: EngineListener? = null

    val prompts = WebPromptHandler(appContext, scope).also { handler ->
        handler.loginStore = loginStore
        handler.isPrivateTab = { tabId -> entries[tabId]?.containerId == PRIVATE_CONTEXT_ID }
        handler.loginsEnabled = { desiredSettings?.saveLogins != false }
    }

    val permissions = SitePermissionsHandler(appContext, { runtime }, { desiredSettings?.autoplay ?: AutoplayPolicy.BLOCK_AUDIBLE })

    val media = MediaSessions()

    val notifications = WebNotifications(appContext)

    /** Receives files that pages download (responses Gecko cannot display). */
    var downloadListener: ((DownloadRequest) -> Unit)? = null

    private val mutableFullScreen = MutableStateFlow<String?>(null)

    /** The tab whose page is in fullscreen (for example a video), or null. */
    val fullScreenTabId: StateFlow<String?> = mutableFullScreen.asStateFlow()

    private val mutableTranslations = MutableStateFlow<Map<String, TranslationInfo>>(emptyMap())

    /** Detected language and translation state per tab. */
    val translations: StateFlow<Map<String, TranslationInfo>> = mutableTranslations.asStateFlow()

    private val mutableGlance = MutableStateFlow<GlanceState?>(null)

    /** The link preview shown over the page (Zen's Glance), or null. */
    val glance: StateFlow<GlanceState?> = mutableGlance.asStateFlow()

    /** Receives the vertical scroll position of the active tab's page. */
    var scrollListener: ((tabId: String, scrollY: Int) -> Unit)? = null

    /** Looks up which URLs are in history, so Gecko can style visited links. */
    var visitedLookup: (suspend (List<String>) -> Set<String>)? = null

    private val generation = MutableStateFlow(0)
    override val sessionGeneration: StateFlow<Int> = generation.asStateFlow()

    private class Entry(val tabId: String, val containerId: String, val session: GeckoSession) {
        var activeApplied: Boolean? = null
        var initialLoadPending = true

        /** The last error page shown, so its data: address never replaces the tab's real address. */
        var errorPage: String? = null
    }

    val errorPages = ErrorPages(appContext)

    /** A link an installed app can open, waiting for the user to choose ("Open in YouTube?"). */
    data class AppLinkRequest(val tabId: String, val url: String, val app: LinkApp, val isPrivate: Boolean)

    private val mutableAppLinkRequest = MutableStateFlow<AppLinkRequest?>(null)
    val appLinkRequest: StateFlow<AppLinkRequest?> = mutableAppLinkRequest.asStateFlow()

    /** Apps declined in a tab, so following more links to the same app there does not ask again. */
    private val declinedApps = HashSet<Pair<String, String>>()

    /**
     * Sends a tapped link to an installed app (not a browser) when the user wants that: always,
     * after asking, or never, with per-app remembered choices. Links within the same site are left
     * alone so browsing a site does not keep asking. Returns true when the load was taken over.
     */
    private fun interceptAppLink(entry: Entry, url: String): Boolean {
        if (appLinks.mode == AppLinkMode.NEVER) return false
        val current = lastState?.tab(entry.tabId)?.url
        if (current != null && UrlInput.hostOf(current).removePrefix("www.") == UrlInput.hostOf(url).removePrefix("www.")) return false
        val app = appLinks.appFor(url) ?: return false
        if (entry.tabId to app.packageName in declinedApps) return false
        val choice = appLinks.choiceFor(app.packageName)
        when {
            choice == false -> return false
            choice == true || appLinks.mode == AppLinkMode.ALWAYS -> mainHandler.post {
                if (!appLinks.open(app, url)) entries[entry.tabId]?.session?.loadUri(url)
            }
            else -> mutableAppLinkRequest.value = AppLinkRequest(entry.tabId, url, app, entry.containerId == PRIVATE_CONTEXT_ID)
        }
        return true
    }

    /** Answers the "Open in app?" prompt. Staying loads the page in its tab. */
    fun answerAppLink(openInApp: Boolean, remember: Boolean) {
        val request = mutableAppLinkRequest.value ?: return
        mutableAppLinkRequest.value = null
        if (remember) appLinks.rememberChoice(request.app.packageName, openInApp)
        if (openInApp && appLinks.open(request.app, request.url)) return
        if (!openInApp) declinedApps += request.tabId to request.app.packageName
        // Loads requested by the app are direct navigations, so they are not intercepted again.
        entries[request.tabId]?.session?.loadUri(request.url)
    }

    private val entries = LinkedHashMap<String, Entry>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastState: BrowserState? = null

    fun session(tabId: String): GeckoSession? = entries[tabId]?.session

    override fun hasSession(tabId: String): Boolean = entries.containsKey(tabId)

    override fun reconcile(state: BrowserState) {
        lastState = state
        val settings = state.data.settings.engineSettings
        desiredSettings = settings
        runtimeInstance?.let { if (appliedSettings != settings) applySettings(it, settings) }

        val desired = state.allTabs
            .filter { it.id !in state.quarantinedTabIds && it.kind == TabKind.WEB && state.runtime(it.id).loaded && !state.isBlocked(it) }
            .associateBy { it.id }

        var changed = false
        for (entry in entries.values.toList()) {
            val tab = desired[entry.tabId]
            if (tab == null || tab.containerId != entry.containerId) {
                closeEntry(entry)
                changed = true
            }
        }
        for (tab in desired.values) {
            if (tab.id !in entries) {
                openTab(tab, state.runtime(tab.id).desktopMode)
                changed = true
            }
        }

        // Both halves of split view are on screen, so both sessions are active.
        val visible = state.visibleTabIds
        for (entry in entries.values) {
            val active = entry.tabId in visible
            if (entry.session.isOpen && entry.activeApplied != active) {
                entry.session.setActive(active)
                // Tells extensions which tab is active (tabs.query({ active: true })).
                runtimeInstance?.webExtensionController?.setTabActive(entry.session, active)
                entry.activeApplied = active
            }
        }
        if (changed) generation.value++
    }

    override fun execute(command: EngineCommand) {
        when (command) {
            is EngineCommand.Load -> entries[command.tabId]?.let {
                it.initialLoadPending = false
                it.session.loadUri(command.url)
            }
            is EngineCommand.GoBack -> entries[command.tabId]?.session?.goBack()
            is EngineCommand.GoForward -> entries[command.tabId]?.session?.goForward()
            is EngineCommand.Reload -> entries[command.tabId]?.session?.reload()
            is EngineCommand.Stop -> entries[command.tabId]?.session?.stop()
            is EngineCommand.ClearContainerData -> runtime.storageController.clearDataForSessionContext(command.containerId)
            EngineCommand.RefreshZoom -> iconExtension.refreshZoom()
            is EngineCommand.SetDesktopMode -> entries[command.tabId]?.session?.let { session ->
                session.settings.setUserAgentMode(GeckoSessionFactory.userAgentMode(command.enabled))
                session.settings.setViewportMode(GeckoSessionFactory.viewportMode(command.enabled))
                session.reload()
            }
        }
    }

    override fun closeSession(tabId: String): Boolean {
        entries[tabId]?.let {
            closeEntry(it)
            generation.value++
        }
        return !entries.containsKey(tabId)
    }

    /** Finds [query] in the tab's page, or the next/previous match when [query] is null. */
    fun find(tabId: String, query: String?, backwards: Boolean, onResult: (FindResult) -> Unit) {
        val session = entries[tabId]?.session ?: return
        session.finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        val flags = if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD
        session.finder.find(query, flags).accept { result ->
            if (result != null) onResult(FindResult(result.searchString, result.current, result.total))
        }
    }

    fun clearFind(tabId: String) {
        entries[tabId]?.session?.finder?.clear()
    }

    private fun applySettings(runtime: GeckoRuntime, settings: EngineSettings) {
        val runtimeSettings = runtime.settings
        runtimeSettings.setPreferredColorScheme(
            when (settings.websiteAppearance) {
                WebsiteAppearance.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                WebsiteAppearance.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                WebsiteAppearance.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            },
        )
        if (settings.textScalePercent == 100) {
            runtimeSettings.setAutomaticFontSizeAdjustment(true)
        } else {
            runtimeSettings.setAutomaticFontSizeAdjustment(false)
            runtimeSettings.setFontSizeFactor(settings.textScalePercent / 100f)
        }
        runtimeSettings.setAllowInsecureConnections(
            if (settings.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL,
        )
        runtimeSettings.contentBlocking.setEnhancedTrackingProtectionLevel(
            when (settings.trackingProtection) {
                TrackingProtection.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
                TrackingProtection.STRICT -> ContentBlocking.EtpLevel.STRICT
                TrackingProtection.OFF -> ContentBlocking.EtpLevel.NONE
            },
        )
        runtimeSettings.setGlobalPrivacyControl(settings.globalPrivacyControl)
        runtimeSettings.setLoginAutofillEnabled(settings.saveLogins)
        val previous = appliedSettings
        // A new autoplay default replaces the answers sites got under the old one.
        if (previous != null && previous.autoplay != settings.autoplay) scope.launch { runCatching { permissions.resetAutoplay() } }
        appliedSettings = settings
    }

    /** Leaves page fullscreen (for example when the user presses back). */
    fun exitFullScreen() {
        val tabId = mutableFullScreen.value ?: return
        entries[tabId]?.session?.exitFullScreen()
    }

    /** Translates the tab's page with Gecko's on-device translation, downloading models as needed. */
    fun translate(tabId: String, from: String, to: String, onError: () -> Unit) {
        val translation = entries[tabId]?.session?.sessionTranslation ?: return onError()
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder().downloadModel(true).build()
        updateTranslation(tabId) { it.copy(inProgress = true, error = false) }
        translation.translate(from, to, options).accept(
            { updateTranslation(tabId) { it.copy(inProgress = false, translatedTo = to) } },
            {
                updateTranslation(tabId) { it.copy(inProgress = false, error = true) }
                onError()
            },
        )
    }

    fun restoreOriginal(tabId: String) {
        entries[tabId]?.session?.sessionTranslation?.restoreOriginalPage()
        updateTranslation(tabId) { it.copy(translatedTo = null, inProgress = false) }
    }

    /** Languages the translation engine supports, or empty when it cannot run on this device. */
    suspend fun translationLanguages(): TranslationLanguages {
        val supported = runCatching { TranslationsController.RuntimeTranslation.isTranslationsEngineSupported().await() }.getOrNull() == true
        if (!supported) return TranslationLanguages(emptyList(), emptyList())
        val list = runCatching { TranslationsController.RuntimeTranslation.listSupportedLanguages().await() }.getOrNull()
            ?: return TranslationLanguages(emptyList(), emptyList())
        fun map(languages: List<TranslationsController.Language>?) =
            languages.orEmpty().map { TranslationLanguage(it.code, it.localizedDisplayName ?: it.code) }
        return TranslationLanguages(map(list.fromLanguages), map(list.toLanguages))
    }

    private fun updateTranslation(tabId: String, transform: (TranslationInfo) -> TranslationInfo) {
        val current = mutableTranslations.value
        mutableTranslations.value = current + (tabId to transform(current[tabId] ?: TranslationInfo()))
    }

    /** Renders the tab's page as a PDF, for printing or saving. */
    suspend fun saveAsPdf(tabId: String): InputStream? {
        val session = entries[tabId]?.session ?: return null
        return runCatching { session.saveAsPdf().await() }.getOrNull()
    }

    /**
     * Downloads [url] (for example "Save image"). The request sends no cookies or credentials, so
     * nothing from any container is shared with the server.
     */
    @SuppressLint("WrongConstant")
    fun saveUrl(url: String, isPrivate: Boolean, onFailed: () -> Unit) {
        if (!UrlInput.isWebUrl(url)) return onFailed()
        val request = WebRequest.Builder(url).build()
        val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (isPrivate) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else 0)
        executor.fetch(request, flags).accept(
            { response ->
                val body = response?.body
                if (response == null || body == null || response.statusCode !in 200..299) {
                    onFailed()
                } else {
                    downloadListener?.invoke(downloadRequest(response, body, isPrivate)) ?: onFailed()
                }
            },
            { onFailed() },
        )
    }

    private val executor by lazy { GeckoWebExecutor(runtime) }

    private fun downloadRequest(response: WebResponse, body: InputStream, isPrivate: Boolean): DownloadRequest {
        val headers = response.headers.mapKeys { it.key.lowercase() }
        return DownloadRequest(
            url = response.uri,
            contentType = headers["content-type"],
            contentDisposition = headers["content-disposition"],
            contentLength = headers["content-length"]?.toLongOrNull(),
            body = body,
            isPrivate = isPrivate,
        )
    }

    // region Glance

    /**
     * Opens [url] in a floating preview over the opener's page, in the opener's container (or
     * private browsing). The preview can become a normal tab with [promoteGlance].
     */
    fun openGlance(openerTabId: String, url: String) {
        val l = listener ?: return
        if (!UrlInput.isWebUrl(url)) return
        val containerId = l.glanceContainer(openerTabId) ?: return l.onOpenInNewTab(url)
        closeGlance()
        val isPrivate = containerId == PRIVATE_CONTEXT_ID
        val session = if (isPrivate) sessionFactory.createPrivate() else sessionFactory.create(containerId)
        val state = GlanceState(openerTabId, containerId, isPrivate, session, url, title = UrlInput.displayHost(url), progress = 0)
        mutableGlance.value = state
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<ContentPermission>, hasUserGesture: Boolean) {
                if (url != null) updateGlance(session) { it.copy(url = url) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = updateGlance(session) { it.copy(canGoBack = canGoBack) }

            override fun onLoadRequest(session: GeckoSession, request: LoadRequest): GeckoResult<AllowOrDeny>? {
                if (externalLinks.isWebScheme(request.uri)) return null
                if (request.hasUserGesture) externalLinks.open(request.uri)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                listener?.onOpenInNewTab(uri)
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) = updateGlance(session) { it.copy(progress = progress) }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) updateGlance(session) { it.copy(title = title) }
            }

            override fun onCloseRequest(session: GeckoSession) = closeGlance()

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                response.body?.let { downloadListener?.invoke(downloadRequest(response, it, isPrivate)) }
            }
        }
        session.promptDelegate = prompts.delegateFor { openerTabId }
        session.permissionDelegate = permissions.delegateFor { openerTabId }
        session.open(runtime)
        session.loadUri(url)
    }

    private fun updateGlance(session: GeckoSession, transform: (GlanceState) -> GlanceState) {
        val current = mutableGlance.value ?: return
        if (current.session === session) mutableGlance.value = transform(current)
    }

    fun glanceBack() {
        mutableGlance.value?.session?.goBack()
    }

    fun closeGlance() {
        val current = mutableGlance.value ?: return
        mutableGlance.value = null
        current.session.navigationDelegate = null
        current.session.progressDelegate = null
        current.session.contentDelegate = null
        if (current.session.isOpen) current.session.close()
    }

    /** Turns the preview into a tab next to its opener, keeping the loaded page and its history. */
    fun promoteGlance() {
        val current = mutableGlance.value ?: return
        val l = listener ?: return closeGlance()
        mutableGlance.value = null
        val newTabId = UUID.randomUUID().toString()
        val entry = Entry(newTabId, current.containerId, current.session).apply { initialLoadPending = false }
        attachDelegates(entry)
        entries[newTabId] = entry
        if (!l.onGlancePromoted(newTabId, current.openerTabId, current.url) || !isCurrent(entry)) {
            // The opener is gone or changed container: open the page as an ordinary new tab instead.
            if (isCurrent(entry)) closeEntry(entry)
            l.onOpenInNewTab(current.url)
            return
        }
        generation.value++
        lastState?.let(::reconcile)
    }

    // endregion

    private fun openTab(tab: Tab, desktopMode: Boolean) {
        val session = if (tab.isPrivate) {
            sessionFactory.createPrivate(desktopMode)
        } else {
            sessionFactory.create(tab.containerId, desktopMode = desktopMode)
        }
        val entry = Entry(tab.id, tab.containerId, session)
        attachDelegates(entry)
        entries[tab.id] = entry
        session.open(runtime)
        val url = tab.url
        scope.launch {
            val saved = sessionStates.get(tab.id)
            if (entries[tab.id] !== entry || !entry.initialLoadPending) return@launch
            entry.initialLoadPending = false
            val restored = saved?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
            if (restored != null && restored.isNotEmpty()) {
                session.restoreState(restored)
            } else {
                session.loadUri(url.ifEmpty { "about:blank" })
            }
        }
    }

    private fun closeEntry(entry: Entry) {
        entries.remove(entry.tabId)
        entry.session.navigationDelegate = null
        entry.session.progressDelegate = null
        entry.session.contentDelegate = null
        entry.session.scrollDelegate = null
        entry.session.historyDelegate = null
        entry.session.promptDelegate = null
        entry.session.permissionDelegate = null
        entry.session.mediaSessionDelegate = null
        entry.session.setTranslationsSessionDelegate(null)
        if (entry.session.isOpen) entry.session.close()
        forgetTab(entry.tabId)
    }

    /** Drops per-tab state that belonged to a session that no longer exists. */
    private fun forgetTab(tabId: String) {
        prompts.cancelFor(tabId)
        permissions.cancelFor(tabId)
        declinedApps.removeAll { it.first == tabId }
        if (mutableAppLinkRequest.value?.tabId == tabId) mutableAppLinkRequest.value = null
        media.clear(tabId)
        if (mutableFullScreen.value == tabId) mutableFullScreen.value = null
        if (tabId in mutableTranslations.value) mutableTranslations.value = mutableTranslations.value - tabId
        if (mutableGlance.value?.openerTabId == tabId) closeGlance()
    }

    private fun isCurrent(entry: Entry) = entries[entry.tabId] === entry

    private fun entryFor(session: GeckoSession) = entries.values.firstOrNull { it.session === session }

    private inline fun ifCurrent(entry: Entry, block: (EngineListener) -> Unit) {
        val l = listener ?: return
        if (isCurrent(entry)) block(l)
    }

    private fun attachDelegates(entry: Entry) {
        val session = entry.session
        iconExtension.attach(session)
        extensions.attach(session)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (url != null && url == entry.errorPage) return
                ifCurrent(entry) { it.onLocationChange(entry.tabId, url) }
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (!isCurrent(entry)) return null
                val page = errorPages.pageFor(uri, error)
                entry.errorPage = page
                return GeckoResult.fromValue(page)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(canGoBack = canGoBack) } }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(canGoForward = canGoForward) } }

            override fun onLoadRequest(session: GeckoSession, request: LoadRequest): GeckoResult<AllowOrDeny>? {
                if (isExtensionPackage(request.uri)) {
                    // Only a user's tap installs; Gecko then shows the permission prompt.
                    if (request.hasUserGesture || request.isDirectNavigation) mainHandler.post { extensions.install(request.uri) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                // A link to a site that always opens in another Space opens there instead.
                if (request.hasUserGesture && !request.isDirectNavigation && UrlInput.isWebUrl(request.uri) &&
                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
                ) {
                    val spaceId = listener?.linkRuleSpace(entry.tabId, request.uri)
                    if (spaceId != null) {
                        mainHandler.post { listener?.openInSpace(spaceId, request.uri) }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    // "Open in app?" for links an installed app (YouTube, Maps, ...) can open.
                    if (interceptAppLink(entry, request.uri)) return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (externalLinks.isWebScheme(request.uri)) return null
                if (request.hasUserGesture || request.isDirectNavigation) {
                    val result = externalLinks.open(request.uri)
                    if (result is ExternalLinkHandler.Result.Fallback) session.loadUri(result.url)
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            override fun onSubframeLoadRequest(session: GeckoSession, request: LoadRequest): GeckoResult<AllowOrDeny>? =
                if (externalLinks.isWebScheme(request.uri)) null else GeckoResult.fromValue(AllowOrDeny.DENY)

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? =
                createContentSession(entry, uri)
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(isLoading = true, progress = 0) } }

            override fun onPageStop(session: GeckoSession, success: Boolean) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(isLoading = false, progress = 100) } }

            override fun onProgressChange(session: GeckoSession, progress: Int) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(progress = progress) } }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) =
                ifCurrent(entry) { it.onRuntimeChange(entry.tabId) { r -> r.copy(isSecure = securityInfo.isSecure) } }

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) =
                ifCurrent(entry) { it.onSessionStateChange(entry.tabId, sessionState.toString()) }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) =
                ifCurrent(entry) { it.onTitleChange(entry.tabId, title) }

            override fun onWebAppManifest(session: GeckoSession, manifest: JSONObject) =
                ifCurrent(entry) { it.onManifest(entry.tabId, runCatching { WebAppManifestParser.parse(manifest) }.getOrNull()) }

            override fun onCloseRequest(session: GeckoSession) = ifCurrent(entry) { it.onCloseRequest(entry.tabId) }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (!isCurrent(entry)) return
                if (fullScreen) {
                    mutableFullScreen.value = entry.tabId
                } else if (mutableFullScreen.value == entry.tabId) {
                    mutableFullScreen.value = null
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                if (!isCurrent(entry)) return
                val body = response.body ?: return
                downloadListener?.invoke(downloadRequest(response, body, entry.containerId == PRIVATE_CONTEXT_ID))
            }

            override fun onCrash(session: GeckoSession) = onSessionGone(entry, crashed = true)

            override fun onKill(session: GeckoSession) = onSessionGone(entry, crashed = false)

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) = ifCurrent(entry) { l ->
                val link = element.linkUri?.takeIf { UrlInput.isWebUrl(it) }
                val image = element.srcUri?.takeIf {
                    element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && UrlInput.isWebUrl(it)
                }
                if (link != null || image != null) {
                    l.onContextMenu(ContextMenuTarget(entry.tabId, link, image, element.title ?: element.altText))
                }
            }
        }

        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                if (isCurrent(entry)) scrollListener?.invoke(entry.tabId, scrollY)
            }
        }

        val currentTabId = { entry.tabId.takeIf { isCurrent(entry) } }
        session.promptDelegate = prompts.delegateFor(currentTabId)
        session.permissionDelegate = permissions.delegateFor(currentTabId)
        session.mediaSessionDelegate = media.delegateFor(currentTabId, entry.containerId == PRIVATE_CONTEXT_ID)
        session.setTranslationsSessionDelegate(object : TranslationsController.SessionTranslation.Delegate {
            override fun onTranslationStateChange(session: GeckoSession, translationState: TranslationsController.SessionTranslation.TranslationState?) {
                if (!isCurrent(entry)) return
                val detected = translationState?.detectedLanguages
                updateTranslation(entry.tabId) { info ->
                    info.copy(
                        pageLanguage = detected?.docLangTag ?: info.pageLanguage,
                        userLanguage = detected?.userLangTag ?: info.userLanguage,
                        translatedTo = translationState?.requestedTranslationPair?.toLanguage,
                        error = translationState?.error != null,
                    )
                }
            }
        })

        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                val record = HistoryPolicy.shouldRecord(
                    url = url,
                    topLevel = (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) != 0,
                    redirectSource = (
                        flags and (GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT)
                        ) != 0,
                    error = (flags and GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR) != 0,
                )
                if (record) ifCurrent(entry) { it.onVisited(entry.tabId, url) }
                return GeckoResult.fromValue(record)
            }

            override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray>? {
                val lookup = visitedLookup ?: return null
                val result = GeckoResult<BooleanArray>()
                scope.launch {
                    val visited = runCatching { lookup(urls.toList()) }.getOrDefault(emptySet())
                    result.complete(BooleanArray(urls.size) { urls[it] in visited })
                }
                return result
            }
        }
    }

    private fun onSessionGone(entry: Entry, crashed: Boolean) {
        if (!isCurrent(entry)) return
        entries.remove(entry.tabId)
        forgetTab(entry.tabId)
        generation.value++
        listener?.onSessionLost(entry.tabId, crashed)
    }

    private fun createContentSession(opener: Entry, uri: String): GeckoResult<GeckoSession>? {
        val l = listener ?: return null
        if (!isCurrent(opener)) return null
        val containerId = l.contentTabContainer(opener.tabId)
        if (containerId == null) {
            mainHandler.post { l.onOpenInNewTab(uri) }
            return null
        }
        val newTabId = UUID.randomUUID().toString()
        val newSession = if (containerId == PRIVATE_CONTEXT_ID) sessionFactory.createPrivate() else sessionFactory.create(containerId)
        val newEntry = Entry(newTabId, containerId, newSession).apply { initialLoadPending = false }
        attachDelegates(newEntry)
        entries[newTabId] = newEntry
        l.onContentOpenedTab(newTabId, opener.tabId, uri)
        if (!isCurrent(newEntry)) return null
        generation.value++
        // Gecko opens the returned session after this call; apply active state once it is open.
        mainHandler.post {
            lastState?.let(::reconcile)
            generation.value++
        }
        return GeckoResult.fromValue(newSession)
    }

    /**
     * Creates a tab requested by an extension (tabs.create) in the active Space's container.
     * Gecko opens the returned session and loads the URL itself.
     */
    private fun createExtensionTab(url: String?, active: Boolean): GeckoSession? {
        val l = listener ?: return null
        val containerId = l.extensionTabContainer() ?: return null
        val newTabId = UUID.randomUUID().toString()
        val newSession = sessionFactory.create(containerId)
        val newEntry = Entry(newTabId, containerId, newSession).apply { initialLoadPending = false }
        attachDelegates(newEntry)
        entries[newTabId] = newEntry
        l.onExtensionOpenedTab(newTabId, url.orEmpty(), active)
        if (!isCurrent(newEntry)) return null
        generation.value++
        mainHandler.post {
            lastState?.let(::reconcile)
            generation.value++
        }
        return newSession
    }

    private fun isExtensionPackage(uri: String): Boolean {
        if (!uri.startsWith("https://", ignoreCase = true)) return false
        val path = uri.substringBefore('#').substringBefore('?')
        return path.endsWith(".xpi", ignoreCase = true)
    }
}
