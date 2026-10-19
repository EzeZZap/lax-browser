package com.javzzap.laxbrowser.engine.gecko

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * The only place that constructs a [GeckoSession]. Every regular session gets an explicit container
 * context ID before it is opened, so no tab can fall back to Gecko's default storage context.
 * Private sessions are the one explicit exception and use Gecko's private browsing instead.
 */
class GeckoSessionFactory {
    fun create(
        containerId: String,
        displayMode: Int = GeckoSessionSettings.DISPLAY_MODE_BROWSER,
        desktopMode: Boolean = false,
    ): GeckoSession {
        require(containerId.isNotBlank()) { "A container ID is required for every session" }
        val settings = GeckoSessionSettings.Builder()
            .contextId(containerId)
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .displayMode(displayMode)
            .userAgentMode(userAgentMode(desktopMode))
            .viewportMode(viewportMode(desktopMode))
            .build()
        return GeckoSession(settings)
    }

    /**
     * A private-browsing session: Gecko keeps its cookies and storage in memory, writes no disk
     * cache, and discards everything when the last private session closes. Private sessions never
     * use a container.
     */
    fun createPrivate(desktopMode: Boolean = false): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            .useTrackingProtection(true)
            .userAgentMode(userAgentMode(desktopMode))
            .viewportMode(viewportMode(desktopMode))
            .build()
        return GeckoSession(settings)
    }

    /**
     * A session for an extension's toolbar popup. It only shows the extension's own page, which
     * must share storage with the rest of the extension, so it has no container. Never use it for
     * web content.
     */
    fun createExtensionPopup(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .build()
        return GeckoSession(settings)
    }

    companion object {
        fun userAgentMode(desktop: Boolean) =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE

        fun viewportMode(desktop: Boolean) =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
}
