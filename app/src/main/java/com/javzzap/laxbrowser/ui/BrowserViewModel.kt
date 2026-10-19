package com.javzzap.laxbrowser.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.ClosedTab
import com.javzzap.laxbrowser.domain.ContextMenuTarget
import com.javzzap.laxbrowser.domain.HistoryEntry
import com.javzzap.laxbrowser.domain.HistoryPolicy
import com.javzzap.laxbrowser.domain.HistoryRange
import kotlinx.coroutines.flow.Flow
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.Reduction
import com.javzzap.laxbrowser.domain.RejectReason
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.domain.WebAppManifest
import com.javzzap.laxbrowser.engine.gecko.ExtensionAction
import com.javzzap.laxbrowser.engine.gecko.ExtensionEvent
import com.javzzap.laxbrowser.engine.gecko.ExtensionInfo
import com.javzzap.laxbrowser.engine.gecko.ExtensionPermissionRequest
import com.javzzap.laxbrowser.engine.gecko.ExtensionPopup
import com.javzzap.laxbrowser.engine.gecko.FindResult
import com.javzzap.laxbrowser.pwa.Eligibility
import com.javzzap.laxbrowser.pwa.Ineligibility
import com.javzzap.laxbrowser.pwa.WebAppEligibility
import android.content.Intent
import android.net.Uri
import com.javzzap.laxbrowser.domain.Backup
import com.javzzap.laxbrowser.domain.BookmarkHtml
import com.javzzap.laxbrowser.domain.ImportSummary
import com.javzzap.laxbrowser.domain.LinkTarget
import com.javzzap.laxbrowser.domain.ImportedLogin
import com.javzzap.laxbrowser.domain.LoginBackup
import com.javzzap.laxbrowser.domain.LoginCsv
import com.javzzap.laxbrowser.domain.LoginData
import com.javzzap.laxbrowser.domain.LoginImportSummary
import com.javzzap.laxbrowser.domain.SavedLogin
import com.javzzap.laxbrowser.platform.UpdateProgress
import com.javzzap.laxbrowser.platform.Versions
import com.javzzap.laxbrowser.domain.WhatsNew
import com.javzzap.laxbrowser.domain.PrivateTabsLock
import com.javzzap.laxbrowser.BuildConfig
import androidx.core.content.edit
import android.provider.DocumentsContract
import org.json.JSONObject
import com.javzzap.laxbrowser.domain.OnboardingChoices
import com.javzzap.laxbrowser.ui.extensions.RecommendedExtension
import com.javzzap.laxbrowser.domain.SiteRules
import com.javzzap.laxbrowser.platform.CrashLog
import com.javzzap.laxbrowser.platform.UpdateInfo
import com.javzzap.laxbrowser.domain.Download
import com.javzzap.laxbrowser.domain.SearchEngines
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.TabFolder
import com.javzzap.laxbrowser.downloads.DownloadEvent
import com.javzzap.laxbrowser.engine.gecko.SitePermissionEntry
import com.javzzap.laxbrowser.ui.sidebar.Suggestion
import com.javzzap.laxbrowser.ui.sidebar.SuggestionMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.mozilla.geckoview.StorageController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A snackbar message. Text is resolved in the UI so it follows the current locale. */
data class UiMessage(
    @param:StringRes val text: Int,
    val args: List<Any> = emptyList(),
    @param:StringRes val actionLabel: Int? = null,
    val action: (() -> Unit)? = null,
)

data class InstallCandidate(
    val manifest: WebAppManifest,
    val icon: Bitmap,
    val origin: String,
    val containerId: String,
    val containerName: String,
)

data class FindState(val query: String = "", val current: Int = 0, val total: Int = 0)

enum class UiEvent { FOCUS_ADDRESS }

enum class KeyboardShortcut {
    NEW_TAB, NEW_PRIVATE_TAB, CLOSE_TAB, REOPEN_TAB, FOCUS_ADDRESS, FIND, BOOKMARK, RELOAD,
    BACK, FORWARD, NEXT_TAB, PREVIOUS_TAB, NEXT_SPACE, PREVIOUS_SPACE, ZOOM_IN, ZOOM_OUT, ZOOM_RESET,
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    val controller = container.controller
    val engine = container.engine
    private val installer = container.webAppInstaller

    val state: StateFlow<BrowserState> = controller.state
    val recentlyClosed: StateFlow<List<ClosedTab>> = controller.recentlyClosed
    val contextMenu: StateFlow<ContextMenuTarget?> = controller.contextMenu

    private val mutableMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = mutableMessages.asSharedFlow()

    private val mutableInstall = MutableStateFlow<InstallCandidate?>(null)
    val installCandidate: StateFlow<InstallCandidate?> = mutableInstall.asStateFlow()

    private val mutablePinnedIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedWebAppIds: StateFlow<Set<String>> = mutablePinnedIds.asStateFlow()

    private val mutableFind = MutableStateFlow<FindState?>(null)

    /** Non-null while the find-in-page bar is open. */
    val find: StateFlow<FindState?> = mutableFind.asStateFlow()

    /** Set while the activity is recreated for a configuration change, so it is not a "launch". */
    var skipNextForeground = false

    private val mutablePictureInPicture = MutableStateFlow(false)

    /** True while the browser shows only the video in a picture-in-picture window. */
    val pictureInPicture: StateFlow<Boolean> = mutablePictureInPicture.asStateFlow()

    fun setPictureInPicture(enabled: Boolean) {
        mutablePictureInPicture.value = enabled
    }

    val isLauncherSupported: Boolean get() = installer.isLauncherSupported()

    fun message(@StringRes text: Int, vararg args: Any, @StringRes actionLabel: Int? = null, action: (() -> Unit)? = null) {
        mutableMessages.tryEmit(UiMessage(text, args.toList(), actionLabel, action))
    }

    /** Shows a message for rejected operations. Returns true when the operation succeeded. */
    fun report(reduction: Reduction, @StringRes success: Int? = null, vararg args: Any): Boolean {
        val outcome = reduction.outcome
        if (outcome is Outcome.Rejected) {
            message(rejectionText(outcome.reason))
            return false
        }
        success?.let { message(it, *args) }
        return true
    }

    /** Opens an http(s) URL handed to the app by another app, once state is loaded. */
    fun openExternalUrl(url: String, ask: Boolean = false) {
        if (!UrlInput.isWebUrl(url)) return
        viewModelScope.launch {
            state.first { it.isLoaded }
            routeLink(url, ask)
        }
    }

    private val mutablePendingLink = MutableStateFlow<String?>(null)

    /** A link waiting for the user to choose a Space (or private browsing) in the "Open in…" dialog. */
    val pendingLink: StateFlow<String?> = mutablePendingLink.asStateFlow()

    /**
     * Opens a link from another app where the setting says, or asks first. The browser comes to
     * the front, so a link opened from a Lax screen (for example Settings, when Lax is the default
     * browser) does not load out of sight.
     */
    private fun routeLink(url: String, ask: Boolean) {
        val target = state.value.data.settings.externalLinks
        if (ask || target == LinkTarget.Ask) {
            mutablePendingLink.value = url
        } else if (report(controller.openLinkIn(url, target, applyRules = true))) {
            mutableShowBrowser.tryEmit(Unit)
        }
    }

    /** Shows the "Open in…" dialog for a link on a page. */
    fun askWhereToOpen(url: String) {
        if (UrlInput.isWebUrl(url)) mutablePendingLink.value = url
    }

    fun openPendingLinkIn(target: LinkTarget) {
        val url = mutablePendingLink.value ?: return
        mutablePendingLink.value = null
        report(controller.openLinkIn(url, target, applyRules = false))
    }

    fun cancelPendingLink() {
        mutablePendingLink.value = null
    }

    // region Private browsing

    fun setPrivateMode(enabled: Boolean) {
        report(controller.setPrivateMode(enabled))
    }

    fun newPrivateTab() {
        controller.setPrivateMode(true)
        report(controller.openNewTabPage())
    }

    fun closeAllPrivateTabs() {
        report(controller.closeAllPrivateTabs(), R.string.msg_private_tabs_closed)
    }

    // endregion

    // region Tabs

    /** Closes a tab and offers to reopen it. */
    fun closeTab(tab: Tab) {
        if (!report(controller.closeTab(tab.id))) return
        if (tab.isEssential) return
        val closed = controller.recentlyClosed.value.firstOrNull { it.tab.id == tab.id } ?: return
        message(R.string.msg_tab_closed, actionLabel = R.string.action_reopen) { reopen(closed) }
    }

    fun reopen(closed: ClosedTab? = null) {
        val reduction = controller.reopenClosedTab(closed ?: controller.recentlyClosed.value.firstOrNull())
        if (reduction.isRejected) message(R.string.msg_cannot_reopen)
    }

    fun unloadTab(tab: Tab) {
        report(controller.unloadTab(tab.id), R.string.msg_tab_unloaded)
    }

    fun openLink(url: String, background: Boolean) {
        val reduction = controller.openLinkInNewTab(url, background)
        if (!report(reduction) || !background) return
        val id = (reduction.outcome as? Outcome.Created)?.id ?: return
        message(R.string.msg_opened_background, actionLabel = R.string.action_switch) { controller.selectTab(id) }
    }

    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        message(R.string.msg_link_copied)
    }

    // endregion

    // region Find in page

    fun openFind() {
        if (state.value.activeTab?.kind == TabKind.WEB) mutableFind.value = FindState()
    }

    fun find(query: String) {
        val tab = state.value.activeTab ?: return
        mutableFind.value = FindState(query)
        if (query.isEmpty()) {
            engine.clearFind(tab.id)
            return
        }
        engine.find(tab.id, query, backwards = false, onResult = ::onFindResult)
    }

    fun findNext(backwards: Boolean) {
        val tab = state.value.activeTab ?: return
        val current = mutableFind.value ?: return
        if (current.query.isEmpty()) return
        engine.find(tab.id, null, backwards, ::onFindResult)
    }

    fun closeFind() {
        state.value.activeTab?.let { engine.clearFind(it.id) }
        mutableFind.value = null
    }

    private fun onFindResult(result: FindResult) {
        val current = mutableFind.value ?: return
        if (result.query != current.query) return
        mutableFind.value = current.copy(current = result.current, total = result.total.coerceAtLeast(0))
    }

    // endregion

    // region Bookmarks

    // region Saved logins

    /** Saved logins and "never save" sites; null until loaded (see [loadLogins]). */
    val savedLogins: StateFlow<LoginData?> get() = container.logins.data

    fun loadLogins() {
        viewModelScope.launch { runCatching { container.logins.load() } }
    }

    fun deleteLogin(login: SavedLogin) {
        viewModelScope.launch { runCatching { container.logins.delete(login.guid) }.onSuccess { message(R.string.msg_login_deleted) } }
    }

    fun deleteAllLogins() {
        viewModelScope.launch { runCatching { container.logins.deleteAll() }.onSuccess { message(R.string.msg_logins_deleted) } }
    }

    fun allowSavingFor(site: String) {
        viewModelScope.launch { runCatching { container.logins.allowSaving(site) } }
    }

    /** Copies a password; marked sensitive so Android 13+ hides it from clipboard previews. */
    fun copySecret(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        // ClipDescription.EXTRA_IS_SENSITIVE (API 33); older versions ignore the extra.
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        message(R.string.msg_password_copied)
    }

    fun copyUsername(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
        message(R.string.msg_username_copied)
    }

    /**
     * Imports a password file exported by another browser or password manager. [onResult] gets
     * the summary, or null when the file had no logins Lax can use.
     */
    fun importLogins(uri: Uri, onResult: ((LoginImportSummary?) -> Unit)? = null) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) { readText(uri)?.let(LoginCsv::parse) }
            if (parsed == null || parsed.logins.isEmpty()) {
                message(R.string.msg_logins_import_empty)
                onResult?.invoke(null)
                return@launch
            }
            val summary = runCatching { container.logins.import(parsed.logins) }.getOrNull()
            if (summary == null) message(R.string.msg_file_failed)
            onResult?.invoke(summary)
        }
    }

    /** Deletes an imported password file, when the app that provided it allows it. */
    fun deleteImportedFile(uri: Uri) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }.getOrDefault(false)
            }
            message(if (deleted) R.string.msg_import_file_deleted else R.string.msg_import_file_not_deleted)
        }
    }

    /** Writes all saved logins to [uri] as an unencrypted file. The caller confirmed and checked the screen lock. */
    fun exportLogins(uri: Uri) {
        viewModelScope.launch {
            val csv = runCatching { LoginCsv.export(container.logins.all()) }.getOrNull()
            val ok = csv != null && withContext(Dispatchers.IO) { writeText(uri, csv) }
            message(if (ok) R.string.msg_logins_exported else R.string.msg_file_failed)
        }
    }

    // endregion

    // region Private tabs lock

    private val mutablePrivateLocked = MutableStateFlow(false)

    /** True after leaving the app with private tabs open while the lock is on, until the user unlocks. */
    val privateTabsLocked: StateFlow<Boolean> = mutablePrivateLocked.asStateFlow()

    /** Called when the app leaves the foreground. */
    fun onAppBackground() {
        val current = state.value
        if (PrivateTabsLock.locksOnLeave(current.data.settings, current.privateTabs.size)) mutablePrivateLocked.value = true
    }

    fun unlockPrivateTabs() {
        mutablePrivateLocked.value = false
    }

    // endregion

    // region Home screen widget

    private val mutableShowBrowser = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Asks the activity to return to the browser from any other screen. */
    val showBrowser: SharedFlow<Unit> = mutableShowBrowser.asSharedFlow()

    /**
     * The search widget: a new tab (or private tab) with the address bar ready for typing, in the
     * widget's Space when it has one that still exists, otherwise in the open Space.
     */
    fun searchFromWidget(private: Boolean, spaceId: String? = null) {
        viewModelScope.launch {
            state.first { it.isLoaded }
            if (!state.value.data.settings.onboardingDone) return@launch
            controller.setPrivateMode(private)
            if (!private && spaceId != null && state.value.space(spaceId) != null && state.value.activeSpace?.id != spaceId) {
                controller.switchSpace(spaceId)
            }
            if (!report(controller.openNewTabPage())) return@launch
            mutableShowBrowser.tryEmit(Unit)
            // The browser screen may still be composing; events are not replayed.
            mutableUiEvents.subscriptionCount.first { it > 0 }
            sendUiEvent(UiEvent.FOCUS_ADDRESS)
        }
    }

    // endregion

    /** Adds the page to the new tab page's shortcuts, or removes it when it is already one. */
    fun toggleNewTabShortcut(tab: Tab) {
        if (tab.kind != TabKind.WEB) return
        val existing = state.value.data.shortcuts.firstOrNull { it.url == tab.url }
        if (existing != null) {
            report(controller.removeShortcut(existing.id), R.string.msg_shortcut_removed)
        } else {
            report(controller.addShortcut(tab.url, tab.title, tab.faviconUrl.takeUnless { tab.isPrivate }), R.string.msg_shortcut_added)
        }
    }

    fun bookmarkCurrentPage(onEdit: (String) -> Unit) {
        val tab = state.value.activeTab ?: return
        bookmarkTab(tab, onEdit)
    }

    fun bookmarkTab(tab: Tab, onEdit: ((String) -> Unit)? = null) {
        if (tab.kind != TabKind.WEB) return
        val reduction = controller.addBookmark(tab.url, tab.title, faviconUrl = tab.faviconUrl)
        when (val outcome = reduction.outcome) {
            is Outcome.Created ->
                if (onEdit != null) message(R.string.msg_bookmark_added, actionLabel = R.string.action_edit) { onEdit(outcome.id) }
                else message(R.string.msg_bookmark_added)
            is Outcome.AlreadyExists ->
                if (onEdit != null) message(R.string.msg_already_bookmarked, actionLabel = R.string.action_edit) { onEdit(outcome.id) }
                else message(R.string.msg_already_bookmarked)
            is Outcome.Rejected -> message(rejectionText(outcome.reason))
            Outcome.Ok -> Unit
        }
    }

    // endregion

    // region History

    fun history(query: String): Flow<List<HistoryEntry>> = controller.history.observe(query)

    fun clearHistory(range: HistoryRange) {
        controller.clearHistory(HistoryPolicy.since(range, System.currentTimeMillis()))
        message(R.string.msg_history_cleared)
    }

    fun deleteHistoryEntry(entry: HistoryEntry) {
        controller.deleteHistoryEntry(entry.id)
    }

    /** Opens a history entry in the active Space, reusing the new-tab page or as a new tab. */
    fun openHistoryEntry(entry: HistoryEntry, newTab: Boolean): Boolean =
        report(if (newTab) controller.newTab(entry.url) else controller.openUrl(entry.url))

    // endregion

    // region Web apps

    fun eligibility(state: BrowserState): Eligibility? {
        val tab = state.activeTab ?: return null
        if (tab.kind != TabKind.WEB || tab.isPrivate) return null
        val runtime = state.runtime(tab.id)
        return WebAppEligibility.check(runtime.manifest, tab.url, runtime.isSecure)
    }

    fun prepareInstall() {
        val current = state.value
        val tab = current.activeTab ?: return
        when (val eligibility = eligibility(current)) {
            null -> return
            is Eligibility.Ineligible -> message(ineligibilityText(eligibility.reason))
            is Eligibility.Eligible -> {
                if (!installer.isLauncherSupported()) {
                    message(R.string.msg_launcher_unsupported)
                    return
                }
                val containerName = current.container(tab.containerId)?.name
                if (containerName == null) {
                    message(rejectionText(RejectReason.CONTAINER_MISSING))
                    return
                }
                viewModelScope.launch {
                    val icon = installer.fetchIcon(eligibility.icon)
                    if (icon == null) {
                        message(R.string.msg_icon_failed)
                        return@launch
                    }
                    mutableInstall.value = InstallCandidate(
                        manifest = eligibility.manifest,
                        icon = icon,
                        origin = UrlInput.originOf(eligibility.manifest.startUrl) ?: eligibility.manifest.startUrl,
                        containerId = tab.containerId,
                        containerName = containerName,
                    )
                }
            }
        }
    }

    fun cancelInstall() {
        mutableInstall.value = null
    }

    fun confirmInstall() {
        val candidate = mutableInstall.value ?: return
        mutableInstall.value = null
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val iconFile = installer.saveIcon(id, candidate.icon)
            val app = installer.newWebApp(id, candidate.manifest, candidate.containerId, iconFile)
            if (!report(controller.upsertWebApp(app))) {
                installer.uninstall(app)
                return@launch
            }
            if (installer.requestPin(app, candidate.icon)) {
                message(R.string.msg_install_confirm, app.name)
            } else {
                controller.removeWebApp(app.id)
                installer.uninstall(app)
                message(R.string.msg_launcher_declined)
            }
        }
    }

    fun refreshPinnedWebApps() {
        viewModelScope.launch {
            mutablePinnedIds.value = withContext(Dispatchers.IO) { installer.pinnedShortcutIds() }
        }
    }

    fun repinWebApp(app: WebApp) {
        viewModelScope.launch {
            val icon = installer.loadIcon(app)
            if (!installer.requestPin(app, icon)) message(R.string.msg_launcher_declined)
        }
    }

    fun uninstallWebApp(app: WebApp) {
        viewModelScope.launch {
            installer.uninstall(app)
            controller.removeWebApp(app.id)
            refreshPinnedWebApps()
            message(R.string.msg_web_app_removed, app.name)
        }
    }

    suspend fun loadWebAppIcon(app: WebApp): Bitmap? = installer.loadIcon(app)

    // endregion

    // region Extensions

    private val extensionManager = engine.extensions
    val extensions: StateFlow<List<ExtensionInfo>> = extensionManager.extensions
    val extensionActions: StateFlow<List<ExtensionAction>> = extensionManager.actions
    val extensionPermissionRequest: StateFlow<ExtensionPermissionRequest?> = extensionManager.permissionRequest
    val extensionPopup: StateFlow<ExtensionPopup?> = extensionManager.popup

    init {
        viewModelScope.launch {
            extensionManager.events.collect { event ->
                when (event) {
                    ExtensionEvent.Downloading -> message(R.string.msg_extension_downloading)
                    is ExtensionEvent.Installed -> message(R.string.msg_extension_installed, event.name)
                    is ExtensionEvent.Removed -> message(R.string.msg_extension_removed, event.name)
                    is ExtensionEvent.InstallFailed -> message(
                        when {
                            event.blocked -> R.string.msg_extension_blocked
                            event.incompatible -> R.string.msg_extension_incompatible
                            event.unsigned -> R.string.msg_extension_unsigned
                            else -> R.string.msg_extension_install_failed
                        },
                    )
                }
            }
        }
    }

    fun setExtensionEnabled(id: String, enabled: Boolean) = extensionManager.setEnabled(id, enabled)

    fun setExtensionAllowedInPrivate(id: String, allowed: Boolean) = extensionManager.setAllowedInPrivateBrowsing(id, allowed)

    fun uninstallExtension(id: String) = extensionManager.uninstall(id)

    /** Extension pages open as normal tabs; private tabs only run extensions allowed there. */
    fun openExtensionOptions(id: String) {
        controller.setPrivateMode(false)
        extensionManager.openOptions(id)
    }

    fun openExtensionStore() {
        controller.setPrivateMode(false)
        report(controller.newTab(EXTENSION_STORE_URL))
    }

    fun openExtensionListing(url: String) {
        controller.setPrivateMode(false)
        report(controller.newTab(url))
    }

    private val mutableInstallingExtensions = MutableStateFlow<Set<String>>(emptySet())

    /** IDs of recommended extensions being downloaded or waiting for the permission prompt. */
    val installingExtensions: StateFlow<Set<String>> = mutableInstallingExtensions.asStateFlow()

    /**
     * Installs a recommended extension from addons.mozilla.org; Gecko shows the permission prompt.
     * It stops showing as installing when the install ends, including when the prompt is cancelled.
     */
    fun installRecommended(extension: RecommendedExtension) {
        if (extension.id in mutableInstallingExtensions.value || extensions.value.any { it.id == extension.id }) return
        mutableInstallingExtensions.value += extension.id
        extensionManager.install(extension.installUrl) { mutableInstallingExtensions.value -= extension.id }
    }

    fun clickExtensionAction(id: String) = extensionManager.clickAction(id)

    fun dismissExtensionPopup() = extensionManager.dismissPopup()

    fun setExtensionPinned(id: String, pinned: Boolean) = extensionManager.setPinned(id, pinned)

    // endregion

    // region Address bar suggestions

    private val mutableSuggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = mutableSuggestions.asStateFlow()
    private var suggestionJob: Job? = null

    /**
     * Updates suggestions for what is typed: bookmarks, history, and the search engine's
     * suggestions (never in private browsing). Matching open tabs are listed by the sidebar,
     * which lets the user pick the Space to search.
     */
    fun updateQuery(text: String) {
        suggestionJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) {
            mutableSuggestions.value = emptyList()
            return
        }
        val current = state.value
        val template = SearchEngines.templateFor(current.data.settings)
        val local = buildList {
            UrlInput.normalize(query, template)?.takeIf { UrlInput.toUrlOrNull(query) != null }?.let { add(Suggestion.Address(it)) }
            add(Suggestion.Search(query, fromEngine = false))
            if (!current.privateMode) addAll(SuggestionMatcher.bookmarks(current, query))
        }
        mutableSuggestions.value = local
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DELAY_MS)
            val openTabs = SuggestionMatcher.openTabs(current, query, limit = Int.MAX_VALUE).map { it.tab.url }
            val seen = (local.mapNotNull { (it as? Suggestion.Bookmark)?.url } + openTabs).toSet()
            val history = if (current.privateMode) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) { runCatching { controller.history.search(query, HISTORY_SUGGESTIONS) }.getOrDefault(emptyList()) }
                    .filter { it.url !in seen }
                    .map { Suggestion.History(it.url, it.title, it.faviconUrl) }
            }
            mutableSuggestions.value = local + history
            val suggestTemplate = SearchEngines.suggestTemplateFor(current.data.settings)
            if (!current.privateMode && current.data.settings.searchSuggestions && suggestTemplate != null) {
                val remote = container.searchSuggestions.fetch(suggestTemplate, query).map { Suggestion.Search(it, fromEngine = true) }
                mutableSuggestions.value = local + history + remote
            }
        }
    }

    /** Opens a suggestion: switches to an open tab (and its Space), or loads the address or search. */
    fun openSuggestion(suggestion: Suggestion): Boolean {
        val active = state.value.activeTab?.id
        val reduction = when (suggestion) {
            is Suggestion.OpenTab -> {
                val tab = suggestion.tab
                if (!tab.isPrivate && tab.spaceId != null && tab.spaceId != state.value.activeSpace?.id) controller.switchSpace(tab.spaceId)
                controller.selectTab(tab.id)
            }
            is Suggestion.Search -> controller.navigate(active, suggestion.query)
            is Suggestion.Address -> controller.loadUrl(active, suggestion.url)
            is Suggestion.Bookmark -> controller.loadUrl(active, suggestion.url)
            is Suggestion.History -> controller.loadUrl(active, suggestion.url)
        }
        mutableSuggestions.value = emptyList()
        return report(reduction)
    }

    // endregion

    // region Downloads

    val downloads: Flow<List<Download>> get() = container.downloader.observe()

    init {
        viewModelScope.launch {
            container.downloader.events.collect { event ->
                when (event) {
                    is DownloadEvent.Started -> message(R.string.msg_download_started, event.fileName)
                    is DownloadEvent.Failed -> message(R.string.msg_download_failed, event.fileName)
                    is DownloadEvent.Completed -> message(R.string.msg_download_complete, event.download.fileName, actionLabel = R.string.action_open) {
                        if (!container.downloader.open(event.download)) message(R.string.msg_no_app_to_open)
                    }
                }
            }
        }
    }

    fun openDownload(download: Download) {
        if (!container.downloader.open(download)) message(R.string.msg_no_app_to_open)
    }

    fun cancelDownload(download: Download) = container.downloader.cancel(download.id)

    fun deleteDownload(download: Download, deleteFile: Boolean) = container.downloader.delete(download, deleteFile)

    /** Saves a link or image from a page, without sending the page's cookies. */
    fun saveLink(url: String) {
        val isPrivate = state.value.activeTab?.isPrivate == true
        engine.saveUrl(url, isPrivate) { message(R.string.msg_download_failed, UrlInput.displayHost(url)) }
    }

    // endregion

    // region Site permissions and browsing data

    suspend fun sitePermissions(): List<SitePermissionEntry> = runCatching { engine.permissions.list() }.getOrDefault(emptyList())

    fun setSitePermission(entry: SitePermissionEntry, allowed: Boolean?) = engine.permissions.set(entry, allowed)

    /** Clears the chosen kinds of browsing data in every container. */
    fun clearBrowsingData(range: HistoryRange?, cookies: Boolean, cache: Boolean, permissions: Boolean) {
        range?.let { controller.clearHistory(HistoryPolicy.since(it, System.currentTimeMillis())) }
        var flags = 0L
        if (cookies) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS
        if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (permissions) flags = flags or StorageController.ClearFlags.SITE_SETTINGS
        if (flags != 0L) engine.runtime.storageController.clearData(flags)
        message(R.string.msg_browsing_data_cleared)
    }

    /** Clears cookies and storage of the current page's site in every container. */
    fun clearSiteData(url: String) {
        val host = UrlInput.hostOf(url) ?: return
        engine.runtime.storageController.clearDataFromBaseDomain(
            host,
            StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS,
        )
        message(R.string.msg_site_data_cleared, UrlInput.displayHost(url))
    }

    // endregion

    // region Page tools

    fun openReaderView() {
        val tab = state.value.activeTab?.takeIf { it.kind == TabKind.WEB && UrlInput.isWebUrl(it.url) } ?: return
        report(controller.loadUrl(tab.id, READER_PREFIX + java.net.URLEncoder.encode(tab.url, "UTF-8")))
    }

    suspend fun translationLanguages() = engine.translationLanguages()

    fun translate(from: String, to: String) {
        val tab = state.value.activeTab ?: return
        engine.translate(tab.id, from, to) { message(R.string.msg_translation_failed) }
    }

    fun showOriginal() {
        state.value.activeTab?.let { engine.restoreOriginal(it.id) }
    }

    /** Renders the page as a PDF and hands it to Android printing (which can also save a PDF). */
    fun print(activity: android.app.Activity) {
        val tab = state.value.activeTab ?: return
        viewModelScope.launch {
            val stream = engine.saveAsPdf(tab.id)
            if (stream == null) {
                message(R.string.msg_print_failed)
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(getApplication<Application>().cacheDir, "print").apply { mkdirs() }
                    java.io.File(dir, "page.pdf").also { out -> stream.use { input -> out.outputStream().use { input.copyTo(it) } } }
                }.getOrNull()
            }
            if (file == null) {
                message(R.string.msg_print_failed)
                return@launch
            }
            PdfPrinter.print(activity, file, tab.title.ifBlank { UrlInput.displayHost(tab.url) })
        }
    }

    /** Adds a home screen shortcut that opens the page in a new tab. */
    fun addToHomeScreen() {
        val tab = state.value.activeTab?.takeIf { it.kind == TabKind.WEB && UrlInput.isWebUrl(it.url) && !it.isPrivate } ?: return
        viewModelScope.launch {
            val icon = tab.faviconUrl?.let { runCatching { engine.favicons.load(it, false) }.getOrNull() }
            if (!HomeScreenShortcuts.add(getApplication(), tab.url, tab.title.ifBlank { UrlInput.displayHost(tab.url) }, icon)) {
                message(R.string.msg_launcher_unsupported)
            }
        }
    }

    // endregion

    // region Split view, Glance, pinned pages, folders, Space appearance

    fun openInSplit(tab: Tab) = report(controller.openSplit(tab.id))

    fun openLinkInSplit(url: String) = report(controller.openUrlInSplit(url))

    fun closeSplit() = report(controller.closeSplit())

    fun swapSplit() = report(controller.swapSplit())

    fun openGlance(url: String) {
        val opener = state.value.activeTab ?: return
        engine.openGlance(opener.id, url)
    }

    fun resetPinnedTab(tab: Tab) = report(controller.resetPinnedTab(tab.id))

    fun setPinnedUrl(tab: Tab, url: String?) =
        report(controller.setPinnedUrl(tab.id, url), R.string.msg_pinned_page_saved)

    fun createTabFolder(name: String, thenMove: Tab? = null) {
        val reduction = controller.createTabFolder(name)
        if (!report(reduction)) return
        val folderId = (reduction.outcome as? Outcome.Created)?.id ?: return
        thenMove?.let { moveTabToFolder(it, folderId) }
    }

    fun renameTabFolder(folder: TabFolder, name: String) = report(controller.renameTabFolder(folder.id, name))

    fun deleteTabFolder(folder: TabFolder) = report(controller.deleteTabFolder(folder.id))

    fun toggleTabFolder(folder: TabFolder) = report(controller.setTabFolderCollapsed(folder.id, !folder.collapsed))

    fun moveTabFolder(folder: TabFolder, delta: Int) = report(controller.moveTabFolder(folder.id, delta))

    fun moveTabToFolder(tab: Tab, folderId: String?) = report(controller.moveTabToFolder(tab.id, folderId))

    fun setSpaceEmoji(space: Space, emoji: String?) = report(controller.setSpaceEmoji(space.id, emoji))

    fun switchSpaceBy(delta: Int) = report(controller.switchSpaceBy(delta))

    // endregion

    // region Site settings

    fun zoomFor(url: String): Int = SiteRules.zoomFor(state.value, url)

    /** Steps the current site's zoom up or down; 0 resets it to 100 %. */
    fun changeZoom(delta: Int) {
        val tab = state.value.activeTab?.takeIf { it.kind == TabKind.WEB } ?: return
        val current = zoomFor(tab.url)
        val next = if (delta == 0) SiteRules.DEFAULT_ZOOM else SiteRules.step(current, delta)
        report(controller.setSiteZoom(tab.url, next))
    }

    fun setSiteSpace(host: String, space: Space?) =
        report(controller.setSiteSpace(host, space?.id), if (space != null) R.string.msg_site_rule_saved else null, *listOfNotNull(space?.name).toTypedArray())

    fun removeSiteSetting(host: String) = report(controller.removeSiteSetting(host))

    // endregion

    // region Bookmark files and backups

    /** Writes all bookmarks to [uri] in the standard bookmark file format. */
    fun exportBookmarks(uri: Uri) {
        val html = BookmarkHtml.export(state.value, getApplication<Application>().getString(R.string.bookmarks))
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { writeText(uri, html) }
            message(if (ok) R.string.msg_bookmarks_exported else R.string.msg_file_failed)
        }
    }

    /** Imports a bookmark file from another browser into a new folder. */
    /** Imports a bookmark file; [onResult] gets the summary, or null when nothing was imported. */
    fun importBookmarks(uri: Uri, onResult: ((ImportSummary?) -> Unit)? = null) {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) { readText(uri)?.let(BookmarkHtml::parse) }
            if (nodes == null || BookmarkHtml.countLinks(nodes) == 0) {
                message(R.string.msg_bookmarks_import_empty)
                onResult?.invoke(null)
                return@launch
            }
            val summary = controller.importBookmarks(nodes, getApplication<Application>().getString(R.string.imported_bookmarks_folder))
            if (summary == null) message(R.string.msg_file_failed) else message(R.string.msg_bookmarks_imported, summary.imported, summary.skipped)
            onResult?.invoke(summary)
        }
    }

    /**
     * Saves Spaces, tabs, bookmarks, shortcuts, site settings, and preferences to [uri]; with
     * [loginsPassword], also the saved logins encrypted with it. The password array is cleared.
     */
    fun exportBackup(uri: Uri, loginsPassword: CharArray? = null) {
        val version = runCatching {
            getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName
        }.getOrNull().orEmpty()
        val data = state.value.data
        viewModelScope.launch {
            val logins = if (loginsPassword != null) {
                val saved = runCatching { container.logins.load() }.getOrNull()
                val section = saved?.let { withContext(Dispatchers.Default) { runCatching { LoginBackup.encrypt(it, loginsPassword) }.getOrNull() } }
                loginsPassword.fill('\u0000')
                if (section == null) {
                    message(R.string.msg_file_failed)
                    return@launch
                }
                section
            } else {
                null
            }
            val json = Backup.encode(data, version, System.currentTimeMillis(), logins)
            val ok = withContext(Dispatchers.IO) { writeText(uri, json) }
            message(if (ok) R.string.msg_backup_saved else R.string.msg_file_failed)
        }
    }

    /** Encrypted logins from a restored backup, waiting for the user's password. */
    data class PendingLoginRestore(val section: JSONObject, val count: Int)

    private val mutableLoginRestore = MutableStateFlow<PendingLoginRestore?>(null)
    val pendingLoginRestore: StateFlow<PendingLoginRestore?> = mutableLoginRestore.asStateFlow()

    /** Replaces browser data with a backup. The caller has confirmed the replacement. */
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readText(uri) }
            val data = text?.let { withContext(Dispatchers.Default) { runCatching { Backup.decode(it) }.getOrNull() } }
            if (text == null || data == null) {
                message(R.string.msg_backup_invalid)
                return@launch
            }
            controller.replaceData(data)
            message(R.string.msg_backup_restored)
            Backup.encryptedLogins(text)?.let { mutableLoginRestore.value = PendingLoginRestore(it, LoginBackup.count(it)) }
        }
    }

    /**
     * Decrypts the logins of a restored backup and adds them to the saved logins. A wrong
     * password calls [onWrongPassword] and keeps the request open. The password array is cleared.
     */
    fun restoreLogins(password: CharArray, onWrongPassword: () -> Unit) {
        val pending = mutableLoginRestore.value ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { LoginBackup.decrypt(pending.section, password) } }
            password.fill('\u0000')
            val restored = result.getOrElse { error ->
                if (error is LoginBackup.WrongPasswordException) {
                    onWrongPassword()
                } else {
                    mutableLoginRestore.value = null
                    message(R.string.msg_logins_restore_damaged)
                }
                return@launch
            }
            mutableLoginRestore.value = null
            val logins = restored.logins.map { ImportedLogin(it.origin, it.formActionOrigin, it.httpRealm, it.username, it.password, it.createdAt, it.lastUsedAt) }
            runCatching { container.logins.import(logins, restored.neverSave) }.fold(
                onSuccess = { message(R.string.msg_logins_restored, it.imported) },
                onFailure = { message(R.string.msg_file_failed) },
            )
        }
    }

    fun skipLoginRestore() {
        mutableLoginRestore.value = null
    }

    private fun writeText(uri: Uri, text: String): Boolean = runCatching {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("No stream")
    }.isSuccess

    private fun readText(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytesLimited(MAX_IMPORT_BYTES) ?: return@use null
            String(bytes, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    // endregion

    // region Updates and crash logs

    private val mutableUpdate = MutableStateFlow<UpdateInfo?>(null)

    /** A newer release to offer, shown as a dialog. */
    val availableUpdate: StateFlow<UpdateInfo?> = mutableUpdate.asStateFlow()

    val canCheckForUpdates: Boolean get() = container.updateChecker.isAvailable

    private val mutableNewerVersion = MutableStateFlow<String?>(null)

    /** A newer release found by an earlier check, even after its dialog was dismissed; shown in Settings > About. */
    val newerVersion: StateFlow<String?> = mutableNewerVersion.asStateFlow()

    private suspend fun refreshNewerVersion() {
        mutableNewerVersion.value = withContext(Dispatchers.IO) { container.updateChecker.knownNewerVersion() }
    }

    init {
        viewModelScope.launch {
            refreshNewerVersion()
            val loaded = state.first { it.isLoaded }
            val checker = container.updateChecker
            val now = System.currentTimeMillis()
            if (loaded.data.settings.checkForUpdates && withContext(Dispatchers.IO) { checker.isDue(now) }) {
                checker.check(now).getOrNull()?.let { mutableUpdate.value = it }
                refreshNewerVersion()
            }
        }
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { CrashLog.consumeNewCrash(getApplication()) }) {
                // Messages are not replayed, so wait until the browser screen is listening.
                mutableMessages.subscriptionCount.first { it > 0 }
                message(R.string.msg_crashed_last_time, actionLabel = R.string.action_share) { shareLatestCrash() }
            }
        }
    }

    /** Checks GitHub now, from Settings; reports when Lax is up to date. */
    fun checkForUpdatesNow() {
        viewModelScope.launch {
            // A toast, not a snackbar: this runs from Settings, where the browser's snackbars are not shown.
            container.updateChecker.check(System.currentTimeMillis(), includeDismissed = true).fold(
                onSuccess = { info -> if (info != null) mutableUpdate.value = info else toast(R.string.msg_up_to_date) },
                onFailure = { toast(R.string.msg_update_check_failed) },
            )
            refreshNewerVersion()
        }
    }

    private fun toast(@StringRes text: Int) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
    }

    fun dismissUpdate(remember: Boolean) {
        mutableUpdate.value?.let { if (remember) container.updateChecker.dismiss(it.version) }
        mutableUpdate.value = null
    }

    /** The in-app update in progress (GitHub builds), shown as a dialog. */
    val updateProgress: StateFlow<UpdateProgress?> get() = container.updateInstaller.progress

    private var updatePage: String? = null

    /** Downloads and installs this device's APK of the release, or opens the release page when there is none. */
    fun installUpdate() {
        val info = mutableUpdate.value ?: return
        mutableUpdate.value = null
        updatePage = info.pageUrl
        if (info.apkUrl != null) container.updateInstaller.start(info) else openExtensionListing(info.pageUrl)
    }

    /** Continues an update after the user allowed installs or closed the system dialog. */
    fun continueUpdate() = container.updateInstaller.install()

    fun canInstallUpdates(): Boolean = container.updateInstaller.canInstallApps()

    fun closeUpdate() = container.updateInstaller.dismiss()

    /** After a failed in-app update, the release page offers the APKs for a manual install. */
    fun openUpdatePage() {
        val page = updatePage ?: return
        closeUpdate()
        openExtensionListing(page)
    }

    suspend fun crashLogs(): List<CrashLog.Entry> = withContext(Dispatchers.IO) { CrashLog.entries(getApplication()) }

    fun clearCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) { CrashLog.clear(getApplication()) }
    }

    /** Opens the share sheet with a crash log. */
    fun shareCrash(text: String) {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, app.getString(R.string.crash_report_subject))
        app.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shareLatestCrash() {
        viewModelScope.launch { crashLogs().firstOrNull()?.let { shareCrash(it.text) } }
    }

    // endregion

    // region What's new

    private val mutableShowWhatsNew = MutableStateFlow(false)

    /** True on the first launch after an update, until the "What's new" dialog is closed. */
    val showWhatsNew: StateFlow<Boolean> = mutableShowWhatsNew.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = state.first { it.isLoaded }
            val prefs = getApplication<Application>().getSharedPreferences(WHATS_NEW_PREFS, Context.MODE_PRIVATE)
            val lastSeen = withContext(Dispatchers.IO) { prefs.getString(KEY_LAST_VERSION, null) }
            val current = BuildConfig.VERSION_NAME
            if (WhatsNew.shouldShow(lastSeen, current, loaded.data.settings.onboardingDone, Versions::isNewer)) mutableShowWhatsNew.value = true
            if (lastSeen != current) withContext(Dispatchers.IO) { prefs.edit { putString(KEY_LAST_VERSION, current) } }
        }
    }

    fun dismissWhatsNew() {
        mutableShowWhatsNew.value = false
    }

    // endregion

    // region Onboarding and keyboard shortcuts

    fun finishOnboarding(choices: OnboardingChoices) {
        report(controller.applyOnboarding(choices))
    }

    private val mutableUiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)

    /** Requests the browser screen handles itself (focusing the address bar). */
    val uiEvents: SharedFlow<UiEvent> = mutableUiEvents.asSharedFlow()

    fun sendUiEvent(event: UiEvent) {
        mutableUiEvents.tryEmit(event)
    }

    /** Hardware keyboard shortcuts (tablets, Chromebooks, keyboards). Returns true when handled. */
    fun onShortcut(shortcut: KeyboardShortcut): Boolean {
        val current = state.value
        val active = current.activeTab
        when (shortcut) {
            KeyboardShortcut.NEW_TAB -> report(controller.openNewTabPage())
            KeyboardShortcut.NEW_PRIVATE_TAB -> newPrivateTab()
            KeyboardShortcut.CLOSE_TAB -> active?.let { if (it.isPinned || it.isEssential) unloadTab(it) else closeTab(it) }
            KeyboardShortcut.REOPEN_TAB -> reopen()
            KeyboardShortcut.FOCUS_ADDRESS -> sendUiEvent(UiEvent.FOCUS_ADDRESS)
            KeyboardShortcut.FIND -> openFind()
            KeyboardShortcut.BOOKMARK -> active?.let { bookmarkTab(it) }
            KeyboardShortcut.RELOAD -> active?.let { report(controller.reload(it.id)) }
            KeyboardShortcut.BACK -> active?.let { controller.goBack(it.id) }
            KeyboardShortcut.FORWARD -> active?.let { controller.goForward(it.id) }
            KeyboardShortcut.NEXT_TAB, KeyboardShortcut.PREVIOUS_TAB -> {
                val list = if (current.privateMode) current.sortedPrivateTabs else current.essentials +
                    current.pinnedTabs(current.activeSpace?.id) + current.regularTabs(current.activeSpace?.id)
                if (list.size < 2) return true
                val index = list.indexOfFirst { it.id == active?.id }
                val step = if (shortcut == KeyboardShortcut.NEXT_TAB) 1 else -1
                controller.selectTab(list[Math.floorMod(index + step, list.size)].id)
            }
            KeyboardShortcut.NEXT_SPACE -> switchSpaceBy(1)
            KeyboardShortcut.PREVIOUS_SPACE -> switchSpaceBy(-1)
            KeyboardShortcut.ZOOM_IN -> changeZoom(1)
            KeyboardShortcut.ZOOM_OUT -> changeZoom(-1)
            KeyboardShortcut.ZOOM_RESET -> changeZoom(0)
        }
        return true
    }

    // endregion

    /** Text shared from another app: an address opens directly, anything else is searched. */
    fun openSharedText(text: String, ask: Boolean = false) {
        val candidate = SharedText.extract(text) ?: return
        viewModelScope.launch {
            state.first { it.isLoaded }
            val template = SearchEngines.templateFor(state.value.data.settings)
            val url = UrlInput.normalize(candidate, template)?.takeIf(UrlInput::isWebUrl) ?: return@launch
            routeLink(url, ask)
        }
    }

    companion object {
        /** Mozilla's add-on store, filtered to extensions that work on Android. */
        const val EXTENSION_STORE_URL = "https://addons.mozilla.org/android/"
        private const val READER_PREFIX = "about:reader?url="
        private const val SUGGESTION_DELAY_MS = 150L
        private const val HISTORY_SUGGESTIONS = 6
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
        private const val WHATS_NEW_PREFS = "whats_new"
        private const val KEY_LAST_VERSION = "last_version"

        @StringRes
        fun rejectionText(reason: RejectReason): Int = when (reason) {
            RejectReason.NOT_FOUND -> R.string.reject_not_found
            RejectReason.INVALID_URL -> R.string.reject_invalid_url
            RejectReason.INVALID_NAME -> R.string.reject_invalid_name
            RejectReason.LAST_SPACE -> R.string.reject_last_space
            RejectReason.CONTAINER_IN_USE -> R.string.reject_container_in_use
            RejectReason.CONTAINER_MISSING -> R.string.reject_container_missing
            RejectReason.DUPLICATE -> R.string.reject_duplicate
            RejectReason.NOT_ALLOWED -> R.string.reject_not_allowed
            RejectReason.NO_ACTIVE_SPACE -> R.string.reject_no_active_space
        }

        @StringRes
        fun ineligibilityText(reason: Ineligibility): Int = when (reason) {
            Ineligibility.NO_MANIFEST -> R.string.pwa_no_manifest
            Ineligibility.INSECURE -> R.string.pwa_insecure
            Ineligibility.NO_NAME -> R.string.pwa_no_name
            Ineligibility.DISPLAY_MODE -> R.string.pwa_display_mode
            Ineligibility.NO_ICON -> R.string.pwa_no_icon
            Ineligibility.CROSS_ORIGIN -> R.string.pwa_cross_origin
            Ineligibility.PREFERS_NATIVE_APP -> R.string.pwa_prefers_native
        }
    }
}
