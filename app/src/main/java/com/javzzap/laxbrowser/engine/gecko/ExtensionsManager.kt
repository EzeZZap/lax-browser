package com.javzzap.laxbrowser.engine.gecko

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** An installed, user-visible Firefox extension. Built-in helper extensions are never listed. */
data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val creator: String?,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val optionsPageUrl: String?,
    val amoListingUrl: String?,
    val permissions: List<String>,
    val origins: List<String>,
    /** Disabled by the add-on blocklist or because it is unsigned or incompatible. */
    val disabledBySystem: Boolean,
    val icon: Image,
    /** Shown in the browser menu. */
    val pinned: Boolean,
    val hasAction: Boolean,
)

/**
 * A pinned extension's entry in the browser menu: its toolbar button ("browser action"), or its
 * settings page when it has no button.
 */
data class ExtensionAction(
    val extensionId: String,
    val title: String,
    val enabled: Boolean,
    val badgeText: String?,
    val icon: Image?,
)

/** Permissions an extension asks for, waiting for the user's decision. */
class ExtensionPermissionRequest(
    val kind: Kind,
    val extensionName: String,
    val icon: Image?,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollection: List<String>,
    private val onRespond: (granted: Boolean, allowInPrivate: Boolean, pin: Boolean) -> Unit,
) {
    enum class Kind { INSTALL, UPDATE, OPTIONAL }

    private var answered = false

    fun respond(granted: Boolean, allowInPrivate: Boolean = false, pin: Boolean = false) {
        if (answered) return
        answered = true
        onRespond(granted, allowInPrivate, pin)
    }
}

/** A browser-action popup. Its session is open while the popup is shown. */
data class ExtensionPopup(val extensionId: String, val title: String, val session: GeckoSession)

sealed interface ExtensionEvent {
    data object Downloading : ExtensionEvent
    data class Installed(val name: String) : ExtensionEvent
    data class InstallFailed(val name: String?, val blocked: Boolean, val incompatible: Boolean, val unsigned: Boolean) : ExtensionEvent
    data class Removed(val name: String) : ExtensionEvent
}

/**
 * Installs and manages Firefox extensions through GeckoView's [WebExtensionController], and
 * connects extension requests (new tabs, popups, tab updates) to the browser through [Host].
 * Gecko enforces add-on signing and the blocklist. All methods run on the main thread.
 */
class ExtensionsManager(private val host: Host) {

    interface Host {
        val runtime: GeckoRuntime

        /** Every open tab session, so per-tab delegates can be registered. */
        fun sessions(): List<GeckoSession>

        /** Creates a browser tab for an extension and returns its not-yet-opened session. */
        fun openExtensionTab(url: String?, active: Boolean): GeckoSession?

        fun openInNewTab(url: String)
        fun selectTab(session: GeckoSession)

        /** Closes the tab if it may be closed (not pinned or Essential). */
        fun closeTab(session: GeckoSession): Boolean

        /** Creates an open session for an extension popup. */
        fun createPopupSession(): GeckoSession
    }

    private val installed = LinkedHashMap<String, WebExtension>()
    private val defaultActions = LinkedHashMap<String, WebExtension.Action>()
    private var started = false
    private val pinnedIds = LinkedHashSet<String>()

    /** Pin choices made in install prompts, applied once the install finishes. */
    private val pendingPins = HashMap<String, Boolean>()

    /** Persists pin changes; set by the app. */
    var onPinnedChanged: ((id: String, pinned: Boolean) -> Unit)? = null

    /** Restores saved pins at startup. */
    fun restorePinned(ids: Set<String>) {
        pinnedIds += ids
        publish()
    }

    /** Shows or hides the extension in the browser menu. */
    fun setPinned(id: String, pinned: Boolean) {
        val changed = if (pinned) pinnedIds.add(id) else pinnedIds.remove(id)
        if (changed) {
            onPinnedChanged?.invoke(id, pinned)
            publish()
        }
    }

    private val mutableExtensions = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    val extensions: StateFlow<List<ExtensionInfo>> = mutableExtensions.asStateFlow()

    private val mutableActions = MutableStateFlow<List<ExtensionAction>>(emptyList())
    val actions: StateFlow<List<ExtensionAction>> = mutableActions.asStateFlow()

    /** Permission prompts in the order they arrived; the first one is shown. */
    private val requestQueue = ArrayDeque<ExtensionPermissionRequest>()
    private val mutablePermissionRequest = MutableStateFlow<ExtensionPermissionRequest?>(null)
    val permissionRequest: StateFlow<ExtensionPermissionRequest?> = mutablePermissionRequest.asStateFlow()

    private val mutablePopup = MutableStateFlow<ExtensionPopup?>(null)
    val popup: StateFlow<ExtensionPopup?> = mutablePopup.asStateFlow()

    private val mutableEvents = MutableSharedFlow<ExtensionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ExtensionEvent> = mutableEvents.asSharedFlow()

    /** Registers delegates and loads installed extensions. Called once the runtime exists. */
    fun start(runtime: GeckoRuntime) {
        if (started) return
        started = true
        val controller = runtime.webExtensionController
        controller.setPromptDelegate(promptDelegate)
        controller.setAddonManagerDelegate(addonManagerDelegate)
        controller.list().accept(
            { list -> list.orEmpty().forEach(::register) },
            { error -> Log.w(TAG, "Could not list extensions", error) },
        )
    }

    /**
     * Starts the install of the extension package at [url]. Gecko verifies its signature.
     * [onFinished] runs once with whether it was installed, also when the user cancels.
     */
    fun install(url: String, onFinished: ((installed: Boolean) -> Unit)? = null) {
        if (!url.startsWith("https://")) {
            mutableEvents.tryEmit(ExtensionEvent.InstallFailed(null, blocked = false, incompatible = false, unsigned = false))
            onFinished?.invoke(false)
            return
        }
        mutableEvents.tryEmit(ExtensionEvent.Downloading)
        host.runtime.webExtensionController.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(
            { extension ->
                if (extension != null) {
                    pendingPins.remove(extension.id)?.let { setPinned(extension.id, it) }
                    register(extension)
                    mutableEvents.tryEmit(ExtensionEvent.Installed(displayName(extension)))
                }
                onFinished?.invoke(extension != null)
            },
            { error ->
                onInstallError(error)
                onFinished?.invoke(false)
            },
        )
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val extension = installed[id] ?: return
        val controller = host.runtime.webExtensionController
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept({ updated -> updated?.let(::register) }, { error -> Log.w(TAG, "Could not change extension state", error) })
    }

    fun setAllowedInPrivateBrowsing(id: String, allowed: Boolean) {
        val extension = installed[id] ?: return
        host.runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, allowed).accept(
            { updated -> updated?.let(::register) },
            { error -> Log.w(TAG, "Could not change private browsing access", error) },
        )
    }

    fun uninstall(id: String) {
        val extension = installed[id] ?: return
        host.runtime.webExtensionController.uninstall(extension).accept(
            {
                forget(id)
                mutableEvents.tryEmit(ExtensionEvent.Removed(displayName(extension)))
            },
            { error -> Log.w(TAG, "Could not remove extension", error) },
        )
    }

    /** Clicks the extension's toolbar button (Gecko asks for a popup when it has one), or opens its settings. */
    fun clickAction(id: String) {
        val action = defaultActions[id]
        if (action != null) action.click() else openOptions(id)
    }

    fun dismissPopup() {
        val popup = mutablePopup.value ?: return
        mutablePopup.value = null
        if (popup.session.isOpen) popup.session.close()
    }

    /** Opens the extension's settings page in a new tab. */
    fun openOptions(id: String) {
        installed[id]?.metaData?.optionsPageUrl?.let(host::openInNewTab)
    }

    /** Lets every extension see [session] as a tab it can update or close. */
    fun attach(session: GeckoSession) {
        installed.values.forEach { attach(session, it) }
    }

    private fun attach(session: GeckoSession, extension: WebExtension) {
        session.webExtensionController.setTabDelegate(extension, sessionTabDelegate)
    }

    private fun register(extension: WebExtension) {
        if (extension.isBuiltIn) return
        val isNew = extension.id !in installed
        installed[extension.id] = extension
        if (isNew) {
            extension.setActionDelegate(actionDelegate)
            extension.setTabDelegate(tabDelegate)
            host.sessions().forEach { attach(it, extension) }
        }
        publish()
    }

    private fun forget(id: String) {
        installed.remove(id)
        defaultActions.remove(id)
        setPinned(id, false)
        if (mutablePopup.value?.extensionId == id) dismissPopup()
        publish()
    }

    private fun publish() {
        mutableExtensions.value = installed.values.map(::info).sortedBy { it.name.lowercase() }
        mutableActions.value = installed.values.filter { it.id in pinnedIds && it.metaData.enabled }.mapNotNull { extension ->
            val action = defaultActions[extension.id]
            if (action == null && extension.metaData.optionsPageUrl.isNullOrBlank()) return@mapNotNull null
            ExtensionAction(
                extensionId = extension.id,
                title = action?.title?.takeIf { it.isNotBlank() } ?: displayName(extension),
                enabled = action?.enabled != false,
                badgeText = action?.badgeText?.takeIf { it.isNotBlank() },
                icon = action?.icon ?: extension.metaData.icon,
            )
        }.sortedBy { it.title.lowercase() }
    }

    private fun info(extension: WebExtension): ExtensionInfo {
        val meta = extension.metaData
        return ExtensionInfo(
            id = extension.id,
            name = displayName(extension),
            version = meta.version,
            description = meta.description?.takeIf { it.isNotBlank() },
            creator = meta.creatorName?.takeIf { it.isNotBlank() },
            enabled = meta.enabled,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            optionsPageUrl = meta.optionsPageUrl?.takeIf { it.isNotBlank() },
            amoListingUrl = meta.amoListingUrl?.takeIf { it.startsWith("https://") },
            permissions = meta.requiredPermissions.toList(),
            origins = meta.requiredOrigins.toList(),
            disabledBySystem = !meta.enabled && (meta.disabledFlags and WebExtension.DisabledFlags.USER) == 0,
            icon = meta.icon,
            pinned = extension.id in pinnedIds,
            hasAction = extension.id in defaultActions,
        )
    }

    private fun onInstallError(error: Throwable?) {
        val install = error as? WebExtension.InstallException
        if (install?.code == WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED) return
        val code = install?.code
        mutableEvents.tryEmit(
            ExtensionEvent.InstallFailed(
                name = install?.extensionName,
                blocked = code == WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ||
                    code == WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED,
                incompatible = code == WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ||
                    code == WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE,
                unsigned = code == WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED,
            ),
        )
    }

    private fun request(
        kind: ExtensionPermissionRequest.Kind,
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollection: Array<String>,
        onRespond: (granted: Boolean, allowInPrivate: Boolean, pin: Boolean) -> Unit,
    ) {
        // Requests wait in line, so installing several add-ons shows one prompt after another.
        lateinit var created: ExtensionPermissionRequest
        created = ExtensionPermissionRequest(
            kind = kind,
            extensionName = displayName(extension),
            icon = extension.metaData.icon,
            permissions = permissions.toList(),
            origins = origins.toList(),
            dataCollection = dataCollection.filterNot { it == NO_DATA_COLLECTION },
        ) { granted, allowInPrivate, pin ->
            requestQueue.remove(created)
            mutablePermissionRequest.value = requestQueue.firstOrNull()
            onRespond(granted, allowInPrivate, pin)
        }
        requestQueue.addLast(created)
        if (mutablePermissionRequest.value == null) mutablePermissionRequest.value = created
    }

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            request(ExtensionPermissionRequest.Kind.INSTALL, extension, permissions, origins, dataCollectionPermissions) { granted, private, pin ->
                if (granted) pendingPins[extension.id] = pin
                result.complete(WebExtension.PermissionPromptResponse(granted, granted && private, false))
            }
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
            newDataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny> = allowOrDeny(ExtensionPermissionRequest.Kind.UPDATE, extension, newPermissions, newOrigins, newDataCollectionPermissions)

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny> = allowOrDeny(ExtensionPermissionRequest.Kind.OPTIONAL, extension, permissions, origins, dataCollectionPermissions)
    }

    private fun allowOrDeny(
        kind: ExtensionPermissionRequest.Kind,
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollection: Array<String>,
    ): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        request(kind, extension, permissions, origins, dataCollection) { granted, _, _ ->
            result.complete(if (granted) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }
        return result
    }

    private val addonManagerDelegate = object : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) = register(extension)
        override fun onReady(extension: WebExtension) = register(extension)
        override fun onEnabled(extension: WebExtension) = register(extension)
        override fun onDisabled(extension: WebExtension) = register(extension)
        override fun onUninstalled(extension: WebExtension) = forget(extension.id)
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            // Only the default action is shown; per-tab overrides would need a per-tab toolbar.
            if (session != null) return
            defaultActions[extension.id] = action
            publish()
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
            if (mutablePopup.value?.extensionId == extension.id) {
                dismissPopup()
                return null
            }
            return showPopup(extension, action)
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            showPopup(extension, action)
    }

    private fun showPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
        dismissPopup()
        val session = host.createPopupSession()
        mutablePopup.value = ExtensionPopup(extension.id, action.title?.takeIf { it.isNotBlank() } ?: displayName(extension), session)
        return GeckoResult.fromValue(session)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            val session = host.openExtensionTab(createDetails.url, createDetails.active != false) ?: return null
            return GeckoResult.fromValue(session)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            source.metaData.optionsPageUrl?.takeIf { it.isNotBlank() }?.let(host::openInNewTab)
        }
    }

    private val sessionTabDelegate = object : WebExtension.SessionTabDelegate {
        override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> =
            GeckoResult.fromValue(if (host.closeTab(session)) AllowOrDeny.ALLOW else AllowOrDeny.DENY)

        override fun onUpdateTab(
            extension: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails,
        ): GeckoResult<AllowOrDeny> {
            if (details.active == true) host.selectTab(session)
            return GeckoResult.allow()
        }
    }

    private fun displayName(extension: WebExtension): String =
        extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id

    companion object {
        private const val TAG = "ExtensionsManager"

        /** Data-collection value that means "collects nothing"; not worth asking about. */
        private const val NO_DATA_COLLECTION = "none"
    }
}
