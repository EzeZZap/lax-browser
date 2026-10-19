package com.javzzap.laxbrowser.engine

import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.ContextMenuTarget
import com.javzzap.laxbrowser.domain.EngineCommand
import com.javzzap.laxbrowser.domain.TabRuntime
import com.javzzap.laxbrowser.domain.WebAppManifest
import kotlinx.coroutines.flow.StateFlow

/** App-owned engine boundary. All methods are main-thread only. */
interface BrowserEngine {
    var listener: EngineListener?

    /** Increments whenever a session is opened or closed, so views can rebind. */
    val sessionGeneration: StateFlow<Int>

    /**
     * Makes engine sessions match [state]: tabs that are loaded get a session in their container,
     * sessions of missing/unloaded tabs are closed, sessions whose container no longer matches are
     * replaced, and only the active tab's session is active.
     */
    fun reconcile(state: BrowserState)

    fun execute(command: EngineCommand)

    /** Closes the session for [tabId] if one exists. Returns true when no session remains. */
    fun closeSession(tabId: String): Boolean

    fun hasSession(tabId: String): Boolean

    /** [httpsUrl] was typed without a scheme; if its secure connection fails, try it over http://. */
    fun allowHttpFallback(httpsUrl: String) = Unit
}

interface EngineListener {
    fun onLocationChange(tabId: String, url: String?)
    fun onTitleChange(tabId: String, title: String?)
    fun onRuntimeChange(tabId: String, update: (TabRuntime) -> TabRuntime)
    fun onManifest(tabId: String, manifest: WebAppManifest?)
    fun onSessionStateChange(tabId: String, state: String)
    fun onSessionLost(tabId: String, crashed: Boolean)
    fun onCloseRequest(tabId: String)

    /** Container for a window opened by [openerTabId]'s content, or null to refuse. */
    fun contentTabContainer(openerTabId: String): String?
    fun onContentOpenedTab(newTabId: String, openerTabId: String, url: String)

    /** A content-opened window that cannot use the opener's container opens as a normal tab. */
    fun onOpenInNewTab(url: String)

    /** The page in [tabId] at [pageUrl] uses the icon at [iconUrl]. */
    fun onFavicon(tabId: String, pageUrl: String, iconUrl: String)

    /** The user long-pressed a link or image. */
    fun onContextMenu(target: ContextMenuTarget)

    /** A top-level page finished a successful, non-redirecting visit in [tabId]. */
    fun onVisited(tabId: String, url: String)

    /** Container for a tab an extension opens, or null to refuse. */
    fun extensionTabContainer(): String?

    /** An extension opened a tab whose session the engine already created. */
    fun onExtensionOpenedTab(newTabId: String, url: String, select: Boolean)

    fun onExtensionSelectTab(tabId: String)

    /** An extension asks to close [tabId]. Returns true when the tab was closed. */
    fun onExtensionCloseTab(tabId: String): Boolean

    /** The zoom level (percent) of [url]'s site. */
    fun zoomFor(url: String): Int

    /** The Space a link clicked in [tabId] must open in because of a site rule, or null. */
    fun linkRuleSpace(tabId: String, url: String): String?

    fun openInSpace(spaceId: String, url: String)

    /** Container for a link preview opened from [openerTabId], or null to open a normal tab instead. */
    fun glanceContainer(openerTabId: String): String?

    /** A link preview became a tab with an existing session. Returns false when it was refused. */
    fun onGlancePromoted(newTabId: String, openerTabId: String, url: String): Boolean
}
