package com.javzzap.laxbrowser.domain

/** A Space to create during onboarding, with its own new container. */
data class SpaceSetup(val name: String, val emoji: String?, val color: Int)

/** A site to add as an Essential during onboarding. */
data class EssentialPick(val url: String, val title: String)

/** Everything chosen in the first-run setup, applied together by [BrowserOperations.applyOnboarding]. */
data class OnboardingChoices(
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    val sidebarOnRight: Boolean = false,
    val searchEngineId: String = SearchEngines.DEFAULT_ID,
    val searchSuggestions: Boolean = true,
    val trackingProtection: TrackingProtection = TrackingProtection.STANDARD,
    val httpsOnly: Boolean = false,
    val autoUnloadMinutes: Int = AutoUnload.DEFAULT_MINUTES,
    val privateTabsLock: Boolean = false,
    val space: SpaceSetup? = null,
    val essentials: List<EssentialPick> = emptyList(),
) {
    companion object {
        /** Starts from the current settings, so skipping a page changes nothing. */
        fun from(settings: BrowserSettings) = OnboardingChoices(
            toolbarPosition = settings.toolbarPosition,
            sidebarOnRight = settings.sidebarOnRight,
            searchEngineId = settings.searchEngineId,
            searchSuggestions = settings.searchSuggestions,
            trackingProtection = settings.trackingProtection,
            httpsOnly = settings.httpsOnly,
            autoUnloadMinutes = settings.autoUnloadMinutes,
            privateTabsLock = settings.privateTabsLock,
        )
    }
}
