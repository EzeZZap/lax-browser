package com.javzzap.laxbrowser.domain

/**
 * Pure browser operations. Every function takes a state and returns a [Reduction]; nothing here
 * touches Android, the database, or Gecko, so all invariants are unit-testable.
 */
class BrowserOperations(
    private val clock: Clock,
    private val ids: IdGenerator,
    private val defaultSpaceName: String = DEFAULT_SPACE_NAME,
) {

    // region Startup and launch

    /** Guarantees at least one Space with a container and a valid active Space. */
    fun ensureDefaults(state: BrowserState): Reduction {
        var s = state
        if (s.data.spaces.isEmpty()) {
            val now = clock.now()
            val container = Container(ids.newId(), defaultSpaceName, DEFAULT_COLORS[0], now)
            val space = Space(ids.newId(), defaultSpaceName, container.id, 0, DEFAULT_COLORS[0], null)
            s = s.withData { it.copy(containers = it.containers + container, spaces = listOf(space)) }
        }
        if (s.activeSpace == null) {
            val first = s.sortedSpaces.first()
            s = s.withData { it.copy(settings = it.settings.copy(activeSpaceId = first.id)) }
        }
        s.activeTab?.let { s = s.markLoadedIfDisplayable(it.id) }
        return Reduction(s)
    }

    fun hasUsableState(state: BrowserState): Boolean =
        state.data.tabs.any { it.id !in state.quarantinedTabIds }

    /** Private browsing is left untouched: the time-based new-tab page only applies to Spaces. */
    fun onForeground(state: BrowserState, now: Long = clock.now()): Reduction {
        if (state.privateMode) return Reduction(state)
        val open = LaunchPolicy.shouldOpenNewTabPage(
            hasUsableState = hasUsableState(state),
            lastBackgroundAt = state.data.settings.lastBackgroundAt,
            now = now,
        )
        return if (open) openNewTabPage(state) else Reduction(state)
    }

    fun onBackground(state: BrowserState, now: Long = clock.now()): Reduction =
        Reduction(state.withData { it.copy(settings = it.settings.copy(lastBackgroundAt = now)) })

    /** Selects an untouched NTP in the active Space, or creates one. Never alters other tabs. */
    fun openNewTabPage(state: BrowserState): Reduction {
        val existing = if (state.privateMode) {
            state.sortedPrivateTabs.lastOrNull { it.kind == TabKind.NEW_TAB }
        } else {
            val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
            state.regularTabs(space.id).lastOrNull { it.kind == TabKind.NEW_TAB }
        }
        return if (existing != null) selectTab(state, existing.id) else newTab(state, url = null)
    }

    // endregion

    // region Private browsing

    /** Shows the private tab list (or the Spaces again). Private tabs stay open while hidden. */
    fun setPrivateMode(state: BrowserState, enabled: Boolean): Reduction {
        if (state.privateMode == enabled) return Reduction(state)
        var s = state.copy(privateMode = enabled)
        s.activeTab?.let { s = s.markLoadedIfDisplayable(it.id) }
        return Reduction(s)
    }

    /** Closes every private tab and returns to the Spaces. Gecko discards the private data. */
    fun closeAllPrivateTabs(state: BrowserState): Reduction {
        val ids = state.privateTabs.map { it.id }.toSet()
        return Reduction(state.copy(privateMode = false, privateTabs = emptyList(), privateSelectedTabId = null, runtime = state.runtime - ids))
    }

    // endregion

    // region Tabs

    /**
     * Opens a tab in the active Space. It inherits that Space and its container. A web tab opened
     * without being selected starts loading in the background.
     */
    fun newTab(state: BrowserState, url: String?, select: Boolean = true, applyRules: Boolean = true): Reduction {
        if (state.privateMode) return newPrivateTab(state, url, select)
        // "Always open in this Space" rules move the new tab to the site's Space.
        val ruled = url?.takeIf { applyRules }?.let { SiteRules.spaceFor(state, it) }
        if (ruled != null && ruled.id != state.activeSpace?.id) {
            return newTab(switchSpace(state, ruled.id).state, url, select, applyRules = false)
        }
        val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        if (state.isSpaceBlocked(space)) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
        val now = clock.now()
        val kind = if (url == null) TabKind.NEW_TAB else TabKind.WEB
        val tab = Tab(
            id = ids.newId(),
            spaceId = space.id,
            containerId = space.containerId,
            url = url.orEmpty(),
            title = url?.let(UrlInput::displayHost).orEmpty(),
            faviconUrl = null,
            position = nextPosition(state.regularTabs(space.id)),
            kind = kind,
            isPinned = false,
            isEssential = false,
            createdAt = now,
            lastAccessedAt = now,
        )
        var s = state.withData { it.copy(tabs = it.tabs + tab) }
        s = if (select) s.selectInSpace(space.id, tab.id) else s.markLoadedIfDisplayable(tab.id)
        return Reduction(s, outcome = Outcome.Created(tab.id))
    }

    private fun newPrivateTab(state: BrowserState, url: String?, select: Boolean): Reduction {
        val now = clock.now()
        val tab = Tab(
            id = ids.newId(),
            spaceId = null,
            containerId = PRIVATE_CONTEXT_ID,
            url = url.orEmpty(),
            title = url?.let(UrlInput::displayHost).orEmpty(),
            faviconUrl = null,
            position = nextPosition(state.privateTabs),
            kind = if (url == null) TabKind.NEW_TAB else TabKind.WEB,
            isPinned = false,
            isEssential = false,
            createdAt = now,
            lastAccessedAt = now,
            isPrivate = true,
        )
        var s = state.copy(privateTabs = state.privateTabs + tab)
        s = if (select) s.copy(privateSelectedTabId = tab.id).markLoadedIfDisplayable(tab.id) else s.markLoadedIfDisplayable(tab.id)
        return Reduction(s, outcome = Outcome.Created(tab.id))
    }

    /** Navigates to [input], searching with the user's search engine when it is not a URL. */
    fun navigate(state: BrowserState, tabId: String?, input: String): Reduction {
        val template = SearchEngines.templateFor(state.data.settings)
        val url = UrlInput.normalize(input, template) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        return loadUrl(state, tabId, url)
    }

    /** Opens a link from a page's context menu as a new tab in the active Space. */
    fun openLinkInNewTab(state: BrowserState, url: String, background: Boolean): Reduction {
        if (!UrlInput.isWebUrl(url)) return Reduction.reject(state, RejectReason.INVALID_URL)
        return newTab(state, url, select = !background)
    }

    /**
     * Reopens a closed tab with its original ID, Space, container, and position. Fails when the
     * Space or container is gone, or when the Space now uses another container.
     */
    fun restoreClosedTab(state: BrowserState, closed: Tab): Reduction {
        if (closed.isPrivate) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (state.data.tabs.any { it.id == closed.id }) return Reduction.reject(state, RejectReason.DUPLICATE)
        val space = state.space(closed.spaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (closed.isEssential || space.containerId != closed.containerId || state.isSpaceBlocked(space)) {
            return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        }
        var s = state.withData { it.copy(tabs = it.tabs + closed.copy(lastAccessedAt = clock.now())) }.copy(privateMode = false)
        if (state.data.settings.activeSpaceId != space.id) s = switchSpace(s, space.id).state
        s = s.setRuntime(closed.id) { it.copy(crashed = false) }.selectInSpace(space.id, closed.id)
        return Reduction(s, outcome = Outcome.Created(closed.id))
    }

    /** Switches the tab between the mobile and desktop versions of sites, then reloads it. */
    fun toggleDesktopMode(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val enabled = !state.runtime(tab.id).desktopMode
        val s = state.setRuntime(tab.id) { it.copy(desktopMode = enabled) }
        val commands = if (state.runtime(tab.id).loaded) listOf(EngineCommand.SetDesktopMode(tab.id, enabled)) else emptyList()
        return Reduction(s, commands = commands)
    }

    /**
     * Updates user preferences. A custom search engine must be a valid template and the text
     * scale is limited to 50–200 %.
     */
    /**
     * Applies the first-run setup in one step: settings, at most one extra Space (with its own
     * container; the user stays in the current Space), and Essentials. Essentials start unloaded
     * and use the current Space's container, like Essentials added from a tab. Sites that are
     * already Essentials and Spaces whose name exists are skipped, so applying twice is harmless.
     */
    fun applyOnboarding(state: BrowserState, choices: OnboardingChoices): Reduction {
        val engine = choices.searchEngineId.takeIf { id -> SearchEngines.builtIn.any { it.id == id } } ?: state.data.settings.searchEngineId
        var s = state.withData {
            it.copy(
                settings = it.settings.copy(
                    toolbarPosition = choices.toolbarPosition,
                    sidebarOnRight = choices.sidebarOnRight,
                    searchEngineId = engine,
                    searchSuggestions = choices.searchSuggestions,
                    trackingProtection = choices.trackingProtection,
                    httpsOnly = choices.httpsOnly,
                    autoUnloadMinutes = choices.autoUnloadMinutes.coerceIn(0, AutoUnload.MAX_MINUTES),
                    privateTabsLock = choices.privateTabsLock,
                    onboardingDone = true,
                ),
            )
        }
        val home = s.activeSpace
        choices.space?.takeIf { setup -> s.data.spaces.none { it.name.equals(setup.name.trim(), ignoreCase = true) } }?.let { setup ->
            val created = createSpace(s, setup.name, setup.color or OPAQUE, ContainerChoice.New(setup.name, setup.color or OPAQUE))
            val spaceId = (created.outcome as? Outcome.Created)?.id
            if (spaceId != null) {
                s = created.state
                setup.emoji?.let { emoji -> setSpaceEmoji(s, spaceId, emoji).takeIf { it.outcome !is Outcome.Rejected }?.let { s = it.state } }
                home?.let { h -> s = s.withData { it.copy(settings = it.settings.copy(activeSpaceId = h.id)) } }
            }
        }
        val containerId = home?.containerId
        if (containerId != null) {
            val now = clock.now()
            choices.essentials.forEach { pick ->
                val url = UrlInput.normalizeWebUrl(pick.url) ?: return@forEach
                if (s.essentials.any { it.url == url || it.pinnedUrl == url }) return@forEach
                val tab = Tab(
                    id = ids.newId(),
                    spaceId = null,
                    containerId = containerId,
                    url = url,
                    title = pick.title.trim().ifEmpty { UrlInput.displayHost(url) },
                    faviconUrl = null,
                    position = nextPosition(s.essentials),
                    kind = TabKind.WEB,
                    isPinned = false,
                    isEssential = true,
                    createdAt = now,
                    lastAccessedAt = now,
                    pinnedUrl = url,
                )
                s = s.withData { it.copy(tabs = it.tabs + tab) }
            }
        }
        return Reduction(s)
    }

    fun updateSettings(state: BrowserState, transform: (BrowserSettings) -> BrowserSettings): Reduction {
        val updated = transform(state.data.settings).let { it.copy(textScalePercent = it.textScalePercent.coerceIn(50, 200)) }
        if (updated.searchEngineId == SearchEngines.CUSTOM_ID && !SearchEngines.isValidCustomTemplate(updated.customSearchUrl)) {
            return Reduction.reject(state, RejectReason.INVALID_URL)
        }
        if (updated.searchEngineId != SearchEngines.CUSTOM_ID && SearchEngines.builtIn.none { it.id == updated.searchEngineId }) {
            return Reduction.reject(state, RejectReason.NOT_FOUND)
        }
        val normalized = updated.copy(
            customSearchUrl = updated.customSearchUrl?.trim()?.ifEmpty { null },
            autoUnloadMinutes = updated.autoUnloadMinutes.coerceIn(0, AutoUnload.MAX_MINUTES),
        )
        return Reduction(state.withData { it.copy(settings = normalized) })
    }

    /** Loads [url] in an existing tab, or in a new tab of the active Space when [tabId] is null. */
    fun loadUrl(state: BrowserState, tabId: String?, url: String, applyRules: Boolean = true): Reduction {
        val tab = tabId?.let(state::tab) ?: return newTab(state, url, applyRules = applyRules)
        if (state.isBlocked(tab)) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
        // A site ruled to another Space opens there; this tab stays as it is.
        if (applyRules && !tab.isPrivate && !tab.isEssential) {
            val ruled = SiteRules.spaceFor(state, url)
            if (ruled != null && ruled.id != tab.spaceId) return newTab(state, url)
        }
        val hasLiveSession = tab.kind == TabKind.WEB && state.runtime(tab.id).loaded
        var s = state.updateTab(tab.id) {
            it.copy(
                url = url,
                kind = TabKind.WEB,
                title = if (it.kind == TabKind.NEW_TAB || it.title.isBlank()) UrlInput.displayHost(url) else it.title,
                lastAccessedAt = clock.now(),
            )
        }
        s = s.setRuntime(tab.id) { it.copy(loaded = true, crashed = false, manifest = null) }
        if (tab.isPrivate) {
            if (s.privateMode) s = s.copy(privateSelectedTabId = tab.id)
        } else {
            s.activeSpace?.let { space ->
                if (tab.isEssential || tab.spaceId == space.id) s = s.selectInSpace(space.id, tab.id)
            }
        }
        return if (hasLiveSession) {
            Reduction(s, commands = listOf(EngineCommand.Load(tab.id, url)))
        } else {
            Reduction(s, discardSessionState = setOf(tab.id))
        }
    }

    /** Opens [url] in the active Space, reusing the selected NTP if there is one. */
    fun openUrlInActiveSpace(state: BrowserState, url: String): Reduction {
        val active = state.activeTab
        return if (active != null && active.kind == TabKind.NEW_TAB) loadUrl(state, active.id, url) else newTab(state, url)
    }

    /**
     * Selects a tab of the active Space or an Essential. Reloads it if it was unloaded. Selecting
     * the second tab of split view moves focus to it and keeps both tabs on screen.
     */
    fun selectTab(state: BrowserState, tabId: String): Reduction {
        if (state.splitTab?.id == tabId) return swapSplit(state)
        return selectOnly(state, tabId)
    }

    private fun selectOnly(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isPrivate != state.privateMode) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (tab.isPrivate) {
            val s = state.updateTab(tab.id) { it.copy(lastAccessedAt = clock.now()) }
                .setRuntime(tab.id) { it.copy(crashed = false) }
                .copy(privateSelectedTabId = tab.id)
                .markLoadedIfDisplayable(tab.id)
            return Reduction(s)
        }
        val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        if (!tab.isEssential && tab.spaceId != space.id) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        var s = state.updateTab(tab.id) { it.copy(lastAccessedAt = clock.now()) }
        s = s.setRuntime(tab.id) { it.copy(crashed = false) }
        s = s.selectInSpace(space.id, tab.id)
        return Reduction(s)
    }

    /**
     * Closes a tab. Ordinary and pinned tabs are removed; the caller must have closed the engine
     * session first. Essentials are never removed by close: they are unloaded instead.
     */
    fun closeTab(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isEssential) return unloadTab(state, tabId)
        return removeTabRecord(state, tab)
    }

    /** Releases the tab's session while keeping its record, URL, title, Space, and pin. */
    fun unloadTab(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val s = state.copy(splitTabs = state.splitTabs.filterValues { it != tab.id })
        return Reduction(s.setRuntime(tab.id) { it.copy(loaded = false, isLoading = false, progress = 0) })
    }

    fun pinTab(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isEssential || tab.isPrivate || tab.spaceId == null) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (tab.isPinned) return Reduction(state)
        val position = nextPosition(state.loosePinnedTabs(tab.spaceId))
        return Reduction(state.updateTab(tab.id) { it.copy(isPinned = true, position = position, pinnedUrl = pinnedUrlFor(it)) })
    }

    fun unpinTab(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (!tab.isPinned) return Reduction(state)
        val position = nextPosition(state.regularTabs(tab.spaceId))
        return Reduction(state.updateTab(tab.id) { it.copy(isPinned = false, position = position, pinnedUrl = null, folderId = null) })
    }

    /** The page a newly pinned tab or Essential resets to: the one it shows when pinned. */
    private fun pinnedUrlFor(tab: Tab): String? = tab.pinnedUrl ?: tab.url.takeIf { tab.kind == TabKind.WEB && UrlInput.isWebUrl(it) }

    /** Navigates a pinned tab or Essential back to its saved page (like Zen's "reset pinned tab"). */
    fun resetPinnedTab(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val target = tab.pinnedUrl?.takeIf { tab.isPinned || tab.isEssential } ?: return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (tab.url == target && state.runtime(tab.id).loaded) return selectTab(state, tab.id)
        return loadUrl(state, tab.id, target, applyRules = false)
    }

    /** Changes the saved page of a pinned tab or Essential; null saves the page it shows now. */
    fun setPinnedUrl(state: BrowserState, tabId: String, url: String?): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (!tab.isPinned && !tab.isEssential) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val target = if (url == null) {
            tab.url.takeIf { tab.kind == TabKind.WEB && UrlInput.isWebUrl(it) }
        } else {
            UrlInput.normalizeWebUrl(url)
        } ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        return Reduction(state.updateTab(tab.id) { it.copy(pinnedUrl = target) })
    }

    /** Turns a Space tab into a global Essential. It keeps its container and session. */
    fun addToEssentials(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isEssential) return Reduction(state)
        if (tab.kind != TabKind.WEB || tab.isPrivate) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val position = nextPosition(state.essentials)
        return Reduction(
            state.updateTab(tab.id) {
                it.copy(spaceId = null, isEssential = true, isPinned = false, position = position, pinnedUrl = pinnedUrlFor(it), folderId = null)
            },
        )
    }

    /** Deletes an Essential. The caller must have confirmed with the user and closed the session. */
    fun removeEssential(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (!tab.isEssential) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        return removeTabRecord(state, tab)
    }

    /** Moves a tab up or down inside its own list (Essentials, pinned, or regular). */
    fun moveTab(state: BrowserState, tabId: String, delta: Int): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val list = when {
            tab.isPrivate -> state.sortedPrivateTabs
            tab.isEssential -> state.essentials
            tab.isPinned && state.tabFolder(tab.folderId)?.spaceId == tab.spaceId -> state.tabsInFolder(tab.folderId!!)
            tab.isPinned -> state.loosePinnedTabs(tab.spaceId)
            else -> state.regularTabs(tab.spaceId)
        }
        val reordered = list.moved(tab, delta) ?: return Reduction(state)
        val positions = reordered.mapIndexed { index, t -> t.id to index }.toMap()
        val reposition = { t: Tab -> positions[t.id]?.let { t.copy(position = it) } ?: t }
        return Reduction(
            if (tab.isPrivate) state.copy(privateTabs = state.privateTabs.map(reposition))
            else state.withData { data -> data.copy(tabs = data.tabs.map(reposition)) },
        )
    }

    /**
     * Moves a Space tab to another Space. If the containers differ the tab is unloaded and its
     * history discarded so that it reloads in the target container; site data is not copied.
     */
    fun moveTabToSpace(state: BrowserState, tabId: String, targetSpaceId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val target = state.space(targetSpaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isEssential || tab.isPrivate || tab.spaceId == null) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (tab.spaceId == target.id) return Reduction(state)
        if (state.isSpaceBlocked(target)) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)

        val sourceSpaceId = tab.spaceId
        val containerChanged = tab.containerId != target.containerId
        val position = nextPosition(if (tab.isPinned) state.loosePinnedTabs(target.id) else state.regularTabs(target.id))

        var s = state.reselectIfSelected(sourceSpaceId, tab.id)
        // Folders belong to one Space, so a moved tab leaves its folder.
        s = s.updateTab(tab.id) { it.copy(spaceId = target.id, containerId = target.containerId, position = position, folderId = null) }
        if (containerChanged) s = s.setRuntime(tab.id) { it.copy(loaded = false, isLoading = false, progress = 0) }
        return Reduction(s, discardSessionState = if (containerChanged) setOf(tab.id) else emptySet())
    }

    fun goBack(state: BrowserState, tabId: String): Reduction =
        commandIfLoaded(state, tabId, state.runtime(tabId).canGoBack) { EngineCommand.GoBack(it) }

    fun goForward(state: BrowserState, tabId: String): Reduction =
        commandIfLoaded(state, tabId, state.runtime(tabId).canGoForward) { EngineCommand.GoForward(it) }

    fun reload(state: BrowserState, tabId: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.kind != TabKind.WEB) return Reduction(state)
        if (!state.runtime(tabId).loaded) return selectTab(state, tabId)
        return Reduction(state, commands = listOf(EngineCommand.Reload(tabId)))
    }

    fun stop(state: BrowserState, tabId: String): Reduction =
        commandIfLoaded(state, tabId, true) { EngineCommand.Stop(it) }

    private fun commandIfLoaded(
        state: BrowserState,
        tabId: String,
        allowed: Boolean,
        command: (String) -> EngineCommand,
    ): Reduction {
        if (state.tab(tabId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (!allowed || !state.runtime(tabId).loaded) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        return Reduction(state, commands = listOf(command(tabId)))
    }

    // endregion

    // region Engine events

    fun onLocationChange(state: BrowserState, tabId: String, url: String?): Reduction {
        val tab = state.tab(tabId) ?: return Reduction(state)
        if (url.isNullOrBlank() || url == tab.url) return Reduction(state)
        if (url == ABOUT_BLANK && tab.url.isNotEmpty()) return Reduction(state)
        val sameSite = UrlInput.hostOf(url) == UrlInput.hostOf(tab.url)
        val s = state.updateTab(tabId) { it.copy(url = url, kind = TabKind.WEB, faviconUrl = if (sameSite) it.faviconUrl else null) }
            .setRuntime(tabId) { it.copy(manifest = null) }
        return Reduction(s)
    }

    /**
     * Records the icon of the page currently shown in [tabId]. Bookmarks and shortcuts for the same
     * address pick it up too, so they get icons once their site has been visited.
     */
    fun onFavicon(state: BrowserState, tabId: String, pageUrl: String, iconUrl: String): Reduction {
        val tab = state.tab(tabId) ?: return Reduction(state)
        if (!UrlInput.isWebUrl(iconUrl) || UrlInput.hostOf(pageUrl) != UrlInput.hostOf(tab.url)) return Reduction(state)
        // Private browsing never updates saved bookmarks or shortcuts.
        if (tab.isPrivate) return Reduction(state.updateTab(tabId) { it.copy(faviconUrl = iconUrl) })
        val s = state.withData { d ->
            d.copy(
                tabs = d.tabs.map { if (it.id == tabId && it.faviconUrl != iconUrl) it.copy(faviconUrl = iconUrl) else it },
                bookmarks = d.bookmarks.map {
                    if (sameUrl(it.url, pageUrl) && it.faviconUrl != iconUrl) it.copy(faviconUrl = iconUrl) else it
                },
                shortcuts = d.shortcuts.map {
                    if (sameUrl(it.url, pageUrl) && it.iconUrl != iconUrl) it.copy(iconUrl = iconUrl) else it
                },
            )
        }
        return Reduction(s)
    }

    fun onTitleChange(state: BrowserState, tabId: String, title: String?): Reduction {
        val tab = state.tab(tabId) ?: return Reduction(state)
        val newTitle = title?.trim().orEmpty().ifEmpty { UrlInput.displayHost(tab.url) }
        if (newTitle == tab.title) return Reduction(state)
        return Reduction(state.updateTab(tabId) { it.copy(title = newTitle) })
    }

    fun onRuntimeChange(state: BrowserState, tabId: String, update: (TabRuntime) -> TabRuntime): Reduction {
        if (state.tab(tabId) == null) return Reduction(state)
        return Reduction(state.setRuntime(tabId, update))
    }

    /** The engine lost the session (crash or kill). The record is kept and can be reloaded. */
    fun onSessionLost(state: BrowserState, tabId: String, crashed: Boolean): Reduction {
        if (state.tab(tabId) == null) return Reduction(state)
        return Reduction(
            state.setRuntime(tabId) {
                it.copy(loaded = false, crashed = crashed, isLoading = false, progress = 0, canGoBack = false, canGoForward = false)
            },
        )
    }

    /**
     * Returns the container a content-opened window (window.open, target=_blank) must use, or
     * null when the opener cannot spawn one in its own container (Essentials, blocked tabs).
     */
    fun contentTabContainer(state: BrowserState, openerTabId: String): String? {
        val opener = state.tab(openerTabId) ?: return null
        if (opener.isPrivate) return PRIVATE_CONTEXT_ID
        if (opener.isEssential || opener.spaceId == null || state.isBlocked(opener)) return null
        val space = state.space(opener.spaceId) ?: return null
        return space.containerId.takeIf { it == opener.containerId }
    }

    /** Registers a tab whose session was created by web content in the opener's Space. */
    fun onContentOpenedTab(state: BrowserState, newTabId: String, openerTabId: String, url: String): Reduction {
        val containerId = contentTabContainer(state, openerTabId)
            ?: return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val opener = state.tab(openerTabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val now = clock.now()
        if (opener.isPrivate) {
            val tab = Tab(
                newTabId, null, PRIVATE_CONTEXT_ID, url, UrlInput.displayHost(url), null, nextPosition(state.privateTabs),
                TabKind.WEB, isPinned = false, isEssential = false, createdAt = now, lastAccessedAt = now, isPrivate = true,
            )
            var s = state.copy(privateTabs = state.privateTabs + tab).setRuntime(tab.id) { it.copy(loaded = true) }
            if (s.privateMode) s = s.copy(privateSelectedTabId = tab.id)
            return Reduction(s, outcome = Outcome.Created(tab.id))
        }
        val spaceId = opener.spaceId ?: return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val tab = Tab(
            id = newTabId,
            spaceId = spaceId,
            containerId = containerId,
            url = url,
            title = UrlInput.displayHost(url),
            faviconUrl = null,
            position = nextPosition(state.regularTabs(spaceId)),
            kind = TabKind.WEB,
            isPinned = false,
            isEssential = false,
            createdAt = now,
            lastAccessedAt = now,
        )
        var s = state.withData { it.copy(tabs = it.tabs + tab) }.setRuntime(tab.id) { it.copy(loaded = true) }
        if (spaceId == state.data.settings.activeSpaceId) s = s.selectInSpace(spaceId, tab.id)
        return Reduction(s, outcome = Outcome.Created(tab.id))
    }

    /**
     * Container of a link preview: the opener's own container (Essentials included), or private
     * browsing for private tabs. Null when the opener cannot load pages.
     */
    fun glanceContainer(state: BrowserState, openerTabId: String): String? {
        val opener = state.tab(openerTabId) ?: return null
        if (opener.isPrivate) return PRIVATE_CONTEXT_ID
        return opener.containerId.takeUnless { state.isBlocked(opener) }
    }

    /**
     * Registers a link preview as a tab. It opens next to its opener: in the opener's Space with
     * the same container, or in the active Space for Essentials whose container matches it.
     */
    fun onGlancePromoted(state: BrowserState, newTabId: String, openerTabId: String, url: String): Reduction {
        val opener = state.tab(openerTabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (contentTabContainer(state, openerTabId) != null) return onContentOpenedTab(state, newTabId, openerTabId, url)
        val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        if (!opener.isEssential || opener.containerId != space.containerId) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        return onExtensionOpenedTab(state, newTabId, url, select = true)
    }

    /**
     * Tabs opened by extensions go to the active Space and use its container. Extensions never
     * open private tabs, so while private browsing is on the tab opens in the background.
     */
    fun extensionTabContainer(state: BrowserState): String? {
        val space = state.activeSpace ?: return null
        return space.containerId.takeUnless { state.isSpaceBlocked(space) }
    }

    /** Registers a tab whose session was created for an extension in the active Space. */
    fun onExtensionOpenedTab(state: BrowserState, newTabId: String, url: String, select: Boolean): Reduction {
        val containerId = extensionTabContainer(state) ?: return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        if (state.tab(newTabId) != null) return Reduction.reject(state, RejectReason.DUPLICATE)
        val now = clock.now()
        val tab = Tab(
            id = newTabId,
            spaceId = space.id,
            containerId = containerId,
            url = url,
            title = UrlInput.displayHost(url),
            faviconUrl = null,
            position = nextPosition(state.regularTabs(space.id)),
            kind = TabKind.WEB,
            isPinned = false,
            isEssential = false,
            createdAt = now,
            lastAccessedAt = now,
        )
        var s = state.withData { it.copy(tabs = it.tabs + tab) }.setRuntime(tab.id) { it.copy(loaded = true) }
        if (select && !state.privateMode) s = s.selectInSpace(space.id, tab.id)
        return Reduction(s, outcome = Outcome.Created(tab.id))
    }

    // endregion

    // region Site settings

    /** Sets the zoom of [url]'s site; 100 % removes the setting unless it also has a Space rule. */
    fun setSiteZoom(state: BrowserState, url: String, percent: Int): Reduction {
        val host = SiteRules.hostKey(url) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        val zoom = percent.coerceIn(SiteRules.MIN_ZOOM, SiteRules.MAX_ZOOM)
        val existing = state.data.siteSettings.firstOrNull { it.host == host } ?: SiteSetting(host)
        return Reduction(state.putSiteSetting(existing.copy(zoomPercent = zoom)), commands = listOf(EngineCommand.RefreshZoom))
    }

    /** Makes [host] (and its subdomains) always open in [spaceId]; null removes the rule. */
    fun setSiteSpace(state: BrowserState, host: String, spaceId: String?): Reduction {
        val key = SiteRules.hostKey(if ("://" in host) host else "https://$host") ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        if (spaceId != null && state.space(spaceId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        val existing = state.data.siteSettings.firstOrNull { it.host == key } ?: SiteSetting(key)
        return Reduction(state.putSiteSetting(existing.copy(spaceId = spaceId)))
    }

    fun removeSiteSetting(state: BrowserState, host: String): Reduction {
        if (state.data.siteSettings.none { it.host == host }) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(
            state.withData { d -> d.copy(siteSettings = d.siteSettings.filter { it.host != host }) },
            commands = listOf(EngineCommand.RefreshZoom),
        )
    }

    /**
     * The Space a link clicked in [tabId] must open in, when a site rule sends [url] to another
     * Space. Private tabs and Essentials never follow rules.
     */
    fun linkRuleSpace(state: BrowserState, tabId: String, url: String): String? {
        val tab = state.tab(tabId) ?: return null
        if (tab.isPrivate || tab.isEssential || tab.spaceId == null) return null
        return SiteRules.spaceFor(state, url)?.id?.takeIf { it != tab.spaceId }
    }

    /** Opens [url] as a new tab of [spaceId], switching to it. */
    /**
     * Opens a link from another app (or chosen in the "Open in…" dialog) in a new tab of the
     * [target]: a Space (and so its container), private browsing, or the active Space. A missing
     * or unavailable Space falls back to the active one. [applyRules] lets "always open this site
     * in…" rules redirect the link; an explicit choice in the dialog passes false.
     */
    fun openLinkIn(state: BrowserState, url: String, target: LinkTarget, applyRules: Boolean): Reduction {
        if (!UrlInput.isWebUrl(url)) return Reduction.reject(state, RejectReason.INVALID_URL)
        return when (target) {
            LinkTarget.PrivateTab -> newTab(setPrivateMode(state, true).state, url)
            is LinkTarget.InSpace -> {
                val space = state.space(target.spaceId)?.takeIf { !state.isSpaceBlocked(it) }
                val inSpace = setPrivateMode(state, false).state.let { s -> space?.let { switchSpace(s, it.id).state } ?: s }
                newTab(inSpace, url, applyRules = applyRules)
            }
            LinkTarget.ActiveSpace, LinkTarget.Ask -> newTab(setPrivateMode(state, false).state, url, applyRules = applyRules)
        }
    }

    fun openInSpace(state: BrowserState, spaceId: String, url: String): Reduction {
        if (!UrlInput.isWebUrl(url)) return Reduction.reject(state, RejectReason.INVALID_URL)
        if (state.space(spaceId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        val switched = state.copy(privateMode = false).let { switchSpace(it, spaceId).state }
        return newTab(switched, url, applyRules = false)
    }

    private fun BrowserState.putSiteSetting(setting: SiteSetting): BrowserState = withData { d ->
        val others = d.siteSettings.filter { it.host != setting.host }
        val keep = setting.zoomPercent != SiteRules.DEFAULT_ZOOM || setting.spaceId != null
        d.copy(siteSettings = if (keep) others + setting else others)
    }

    // endregion

    // region Tab folders

    /** Creates a folder for pinned tabs in the active Space. */
    fun createTabFolder(state: BrowserState, name: String): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (state.privateMode) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val space = state.activeSpace ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        val folder = TabFolder(ids.newId(), space.id, trimmed, nextPosition(state.tabFolders(space.id)), collapsed = false, createdAt = clock.now())
        return Reduction(state.withData { it.copy(tabFolders = it.tabFolders + folder) }, outcome = Outcome.Created(folder.id))
    }

    fun renameTabFolder(state: BrowserState, folderId: String, name: String): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (state.tabFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.updateTabFolder(folderId) { it.copy(name = trimmed) })
    }

    fun setTabFolderCollapsed(state: BrowserState, folderId: String, collapsed: Boolean): Reduction {
        if (state.tabFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.updateTabFolder(folderId) { it.copy(collapsed = collapsed) })
    }

    fun moveTabFolder(state: BrowserState, folderId: String, delta: Int): Reduction {
        val folder = state.tabFolder(folderId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val reordered = state.tabFolders(folder.spaceId).moved(folder, delta) ?: return Reduction(state)
        val positions = reordered.mapIndexed { index, f -> f.id to index }.toMap()
        return Reduction(state.withData { d -> d.copy(tabFolders = d.tabFolders.map { f -> positions[f.id]?.let { f.copy(position = it) } ?: f }) })
    }

    /** Removes a folder. Its tabs stay pinned in the Space; nothing is closed. */
    fun deleteTabFolder(state: BrowserState, folderId: String): Reduction {
        val folder = state.tabFolder(folderId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        var position = nextPosition(state.loosePinnedTabs(folder.spaceId))
        val members = state.tabsInFolder(folder.id).associate { it.id to position++ }
        return Reduction(
            state.withData { d ->
                d.copy(
                    tabFolders = d.tabFolders.filter { it.id != folder.id },
                    tabs = d.tabs.map { t -> members[t.id]?.let { t.copy(folderId = null, position = it) } ?: t },
                )
            },
        )
    }

    /**
     * Puts a tab of the folder's Space into the folder, pinning it if needed (folders hold pinned
     * tabs, like Zen). A null [folderId] takes a pinned tab out of its folder.
     */
    fun moveTabToFolder(state: BrowserState, tabId: String, folderId: String?): Reduction {
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (tab.isEssential || tab.isPrivate || tab.spaceId == null) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (folderId == null) {
            if (tab.folderId == null) return Reduction(state)
            val position = nextPosition(state.loosePinnedTabs(tab.spaceId))
            return Reduction(state.updateTab(tab.id) { it.copy(folderId = null, position = position) })
        }
        val folder = state.tabFolder(folderId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (folder.spaceId != tab.spaceId) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        if (tab.folderId == folderId && tab.isPinned) return Reduction(state)
        val position = nextPosition(state.tabsInFolder(folderId))
        return Reduction(
            state.updateTab(tab.id) { it.copy(isPinned = true, folderId = folderId, position = position, pinnedUrl = pinnedUrlFor(it)) },
        )
    }

    // endregion

    // region Split view

    /** Shows [tabId] next to the active tab. Both tabs belong to the current tab list. */
    fun openSplit(state: BrowserState, tabId: String): Reduction {
        val active = state.activeTab ?: return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        val tab = state.tab(tabId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val key = state.contextKey ?: return Reduction.reject(state, RejectReason.NO_ACTIVE_SPACE)
        if (tab.id == active.id || tab.kind != TabKind.WEB || tab.isPrivate != state.privateMode) {
            return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        }
        if (state.isBlocked(tab)) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
        if (!state.privateMode && !tab.isEssential && tab.spaceId != state.activeSpace?.id) {
            return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        }
        val s = state.copy(splitTabs = state.splitTabs + (key to tab.id)).setRuntime(tab.id) { it.copy(loaded = true, crashed = false) }
        return Reduction(s)
    }

    /** Opens [url] in a new tab shown next to the active one. */
    fun openUrlInSplit(state: BrowserState, url: String): Reduction {
        if (!UrlInput.isWebUrl(url)) return Reduction.reject(state, RejectReason.INVALID_URL)
        val created = newTab(state, url, select = false, applyRules = false)
        val id = (created.outcome as? Outcome.Created)?.id ?: return created
        val split = openSplit(created.state, id)
        return if (split.isRejected) created else split.copy(outcome = Outcome.Created(id))
    }

    /** Leaves split view. Both tabs stay open; the active tab fills the screen. */
    fun closeSplit(state: BrowserState): Reduction {
        val key = state.contextKey ?: return Reduction(state)
        return Reduction(state.copy(splitTabs = state.splitTabs - key))
    }

    /** Moves focus to the other half of split view. */
    fun swapSplit(state: BrowserState): Reduction {
        val split = state.splitTab ?: return Reduction(state)
        val active = state.activeTab ?: return Reduction(state)
        val key = state.contextKey ?: return Reduction(state)
        val selected = selectOnly(state, split.id)
        if (selected.isRejected) return selected
        return Reduction(selected.state.copy(splitTabs = selected.state.splitTabs + (key to active.id)))
    }

    // endregion

    // region Spaces

    fun createSpace(state: BrowserState, name: String, color: Int, containerChoice: ContainerChoice): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        var s = state
        val containerId = when (containerChoice) {
            is ContainerChoice.Existing -> {
                if (s.container(containerChoice.containerId) == null) {
                    return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
                }
                containerChoice.containerId
            }
            is ContainerChoice.New -> {
                val containerName = containerChoice.name.trim().ifEmpty { trimmed }
                val container = Container(ids.newId(), containerName, containerChoice.color, clock.now())
                s = s.withData { it.copy(containers = it.containers + container) }
                container.id
            }
        }
        val space = Space(ids.newId(), trimmed, containerId, nextPosition(s.data.spaces), color, null)
        s = s.withData { it.copy(spaces = it.spaces + space, settings = it.settings.copy(activeSpaceId = space.id)) }
        return Reduction(s, outcome = Outcome.Created(space.id))
    }

    fun renameSpace(state: BrowserState, spaceId: String, name: String): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (state.space(spaceId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.updateSpace(spaceId) { it.copy(name = trimmed) })
    }

    /** Sets a Space's color, a preset or any custom color (fully opaque). */
    fun setSpaceColor(state: BrowserState, spaceId: String, color: Int): Reduction {
        if (state.space(spaceId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.updateSpace(spaceId) { it.copy(color = color or OPAQUE) })
    }

    /** Uses [emoji] as the Space's icon instead of its color dot; null goes back to the dot. */
    fun setSpaceEmoji(state: BrowserState, spaceId: String, emoji: String?): Reduction {
        if (state.space(spaceId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        val value = emoji?.trim()?.takeIf { it.isNotEmpty() }
        if (value != null && (value.length > MAX_EMOJI_LENGTH || value.any { it.isLetterOrDigit() })) {
            return Reduction.reject(state, RejectReason.INVALID_NAME)
        }
        return Reduction(state.updateSpace(spaceId) { it.copy(emoji = value) })
    }

    /** The Space after (delta = 1) or before (delta = -1) the active one, wrapping around. */
    fun switchSpaceBy(state: BrowserState, delta: Int): Reduction {
        val ordered = state.sortedSpaces
        if (ordered.size < 2 || state.privateMode) return Reduction(state)
        val index = ordered.indexOfFirst { it.id == state.activeSpace?.id }.coerceAtLeast(0)
        val next = ordered[Math.floorMod(index + delta, ordered.size)]
        return switchSpace(state, next.id)
    }

    fun moveSpace(state: BrowserState, spaceId: String, delta: Int): Reduction {
        val space = state.space(spaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val reordered = state.sortedSpaces.moved(space, delta) ?: return Reduction(state)
        val positions = reordered.mapIndexed { index, sp -> sp.id to index }.toMap()
        return Reduction(state.withData { d -> d.copy(spaces = d.spaces.map { it.copy(position = positions.getValue(it.id)) }) })
    }

    /** Changes the visible tab list and selected session. Tabs of the previous Space are untouched. */
    fun switchSpace(state: BrowserState, spaceId: String): Reduction {
        val space = state.space(spaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        var s = state.withData { it.copy(settings = it.settings.copy(activeSpaceId = space.id)) }
        space.selectedTabId?.let { s = s.markLoadedIfDisplayable(it) }
        return Reduction(s)
    }

    /** Deletes a Space and its tabs. Its container and site data are kept. Requires confirmation. */
    fun deleteSpace(state: BrowserState, spaceId: String): Reduction {
        val space = state.space(spaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (state.data.spaces.size <= 1) return Reduction.reject(state, RejectReason.LAST_SPACE)
        val removedTabIds = state.data.tabs.filter { it.spaceId == space.id }.map { it.id }.toSet()
        var s = state.copy(
            data = state.data.copy(
                spaces = state.data.spaces.filter { it.id != space.id },
                tabs = state.data.tabs.filter { it.id !in removedTabIds },
                tabFolders = state.data.tabFolders.filter { it.spaceId != space.id },
                // Rules pointing to the deleted Space are dropped; zoom levels are kept.
                siteSettings = state.data.siteSettings.mapNotNull { setting ->
                    if (setting.spaceId != space.id) setting else setting.copy(spaceId = null).takeIf { it.zoomPercent != SiteRules.DEFAULT_ZOOM }
                },
            ),
            runtime = state.runtime - removedTabIds,
            splitTabs = state.splitTabs - space.id,
        )
        if (s.data.settings.externalLinks == LinkTarget.InSpace(space.id)) {
            s = s.withData { it.copy(settings = it.settings.copy(externalLinks = LinkTarget.ActiveSpace)) }
        }
        if (state.data.settings.activeSpaceId == space.id) {
            val ordered = state.sortedSpaces
            val index = ordered.indexOfFirst { it.id == space.id }
            val next = ordered.getOrNull(index + 1) ?: ordered.getOrNull(index - 1)
            if (next != null) s = switchSpace(s, next.id).state
        }
        return Reduction(s, discardSessionState = removedTabIds)
    }

    /**
     * Assigns a different container to a Space. Every tab of the Space moves to the new container,
     * loses its saved history, and reloads; the engine replaces live sessions.
     */
    fun setSpaceContainer(state: BrowserState, spaceId: String, containerId: String): Reduction {
        val space = state.space(spaceId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (state.container(containerId) == null) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
        if (space.containerId == containerId) return Reduction(state)
        val affected = state.data.tabs.filter { it.spaceId == space.id }.map { it.id }.toSet()
        var s = state.updateSpace(space.id) { it.copy(containerId = containerId) }
        s = s.withData { d -> d.copy(tabs = d.tabs.map { if (it.id in affected) it.copy(containerId = containerId) else it }) }
        s = s.copy(runtime = s.runtime.mapValues { (id, r) -> if (id in affected) r.copy(loaded = false, isLoading = false, progress = 0) else r })
        if (s.data.settings.activeSpaceId == space.id) space.selectedTabId?.let { s = s.markLoadedIfDisplayable(it) }
        return Reduction(s, discardSessionState = affected)
    }

    // endregion

    // region Containers

    fun createContainer(state: BrowserState, name: String, color: Int): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        val container = Container(ids.newId(), trimmed, color, clock.now())
        return Reduction(state.withData { it.copy(containers = it.containers + container) }, outcome = Outcome.Created(container.id))
    }

    fun renameContainer(state: BrowserState, containerId: String, name: String, color: Int? = null): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (state.container(containerId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(
            state.withData { d ->
                d.copy(containers = d.containers.map { if (it.id == containerId) it.copy(name = trimmed, color = color ?: it.color) else it })
            },
        )
    }

    fun containerUsage(state: BrowserState, containerId: String) = ContainerUsage(
        spaces = state.data.spaces.filter { it.containerId == containerId },
        tabs = state.data.tabs.filter { it.containerId == containerId },
        webApps = state.data.webApps.filter { it.containerId == containerId },
    )

    /** Deletes an unused container and clears its site data. Requires confirmation. */
    fun deleteContainer(state: BrowserState, containerId: String): Reduction {
        if (state.container(containerId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (!containerUsage(state, containerId).isUnused) return Reduction.reject(state, RejectReason.CONTAINER_IN_USE)
        return Reduction(
            state.withData { d -> d.copy(containers = d.containers.filter { it.id != containerId }) },
            commands = listOf(EngineCommand.ClearContainerData(containerId)),
        )
    }

    // endregion

    // region Bookmarks

    /**
     * Bookmarks an address. Addresses are unique across all folders: bookmarking an existing one
     * returns [Outcome.AlreadyExists] instead of creating a duplicate.
     */
    fun addBookmark(state: BrowserState, url: String, title: String?, folderId: String? = null, faviconUrl: String? = null): Reduction {
        val normalized = UrlInput.normalizeWebUrl(url) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        if (folderId != null && state.bookmarkFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        state.data.bookmarks.firstOrNull { sameUrl(it.url, normalized) }?.let {
            return Reduction(state, outcome = Outcome.AlreadyExists(it.id))
        }
        val now = clock.now()
        val bookmark = Bookmark(
            id = ids.newId(),
            url = normalized,
            title = title?.trim().orEmpty().ifEmpty { UrlInput.displayHost(normalized) },
            faviconUrl = faviconUrl?.takeIf { UrlInput.isWebUrl(it) },
            position = nextPosition(state.bookmarksIn(folderId)),
            createdAt = now,
            updatedAt = now,
            folderId = folderId,
        )
        return Reduction(state.withData { it.copy(bookmarks = it.bookmarks + bookmark) }, outcome = Outcome.Created(bookmark.id))
    }

    fun updateBookmark(
        state: BrowserState,
        bookmarkId: String,
        title: String,
        url: String,
        folderId: String? = state.data.bookmarks.firstOrNull { it.id == bookmarkId }?.folderId,
    ): Reduction {
        val existing = state.data.bookmarks.firstOrNull { it.id == bookmarkId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val normalized = UrlInput.normalizeWebUrl(url) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        if (folderId != null && state.bookmarkFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (state.data.bookmarks.any { it.id != bookmarkId && sameUrl(it.url, normalized) }) {
            return Reduction.reject(state, RejectReason.DUPLICATE)
        }
        val movedFolder = folderId != existing.folderId
        val updated = existing.copy(
            url = normalized,
            title = title.trim().ifEmpty { UrlInput.displayHost(normalized) },
            faviconUrl = if (sameUrl(existing.url, normalized)) existing.faviconUrl else null,
            folderId = folderId,
            position = if (movedFolder) nextPosition(state.bookmarksIn(folderId)) else existing.position,
            updatedAt = clock.now(),
        )
        return Reduction(state.withData { d -> d.copy(bookmarks = d.bookmarks.map { if (it.id == bookmarkId) updated else it }) })
    }

    fun moveBookmarkToFolder(state: BrowserState, bookmarkId: String, folderId: String?): Reduction {
        val bookmark = state.data.bookmarks.firstOrNull { it.id == bookmarkId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        return updateBookmark(state, bookmarkId, bookmark.title, bookmark.url, folderId)
    }

    fun createBookmarkFolder(state: BrowserState, name: String, parentId: String?): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (parentId != null && state.bookmarkFolder(parentId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        val now = clock.now()
        val folder = BookmarkFolder(ids.newId(), trimmed, parentId, nextPosition(state.childFolders(parentId)), now, now)
        return Reduction(state.withData { it.copy(bookmarkFolders = it.bookmarkFolders + folder) }, outcome = Outcome.Created(folder.id))
    }

    fun renameBookmarkFolder(state: BrowserState, folderId: String, name: String): Reduction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Reduction.reject(state, RejectReason.INVALID_NAME)
        if (state.bookmarkFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(
            state.withData { d ->
                d.copy(bookmarkFolders = d.bookmarkFolders.map { if (it.id == folderId) it.copy(name = trimmed, updatedAt = clock.now()) else it })
            },
        )
    }

    /** Moves a folder under [parentId]. A folder cannot be moved into itself or its own subfolders. */
    fun moveBookmarkFolder(state: BrowserState, folderId: String, parentId: String?): Reduction {
        val folder = state.bookmarkFolder(folderId) ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        if (parentId != null) {
            if (state.bookmarkFolder(parentId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
            if (parentId in state.folderSubtree(folderId)) return Reduction.reject(state, RejectReason.NOT_ALLOWED)
        }
        if (folder.parentId == parentId) return Reduction(state)
        val position = nextPosition(state.childFolders(parentId))
        return Reduction(
            state.withData { d ->
                d.copy(
                    bookmarkFolders = d.bookmarkFolders.map {
                        if (it.id == folderId) it.copy(parentId = parentId, position = position, updatedAt = clock.now()) else it
                    },
                )
            },
        )
    }

    /** Deletes a folder with all its subfolders and bookmarks. Requires confirmation. */
    fun deleteBookmarkFolder(state: BrowserState, folderId: String): Reduction {
        if (state.bookmarkFolder(folderId) == null) return Reduction.reject(state, RejectReason.NOT_FOUND)
        val subtree = state.folderSubtree(folderId)
        return Reduction(
            state.withData { d ->
                d.copy(
                    bookmarkFolders = d.bookmarkFolders.filter { it.id !in subtree },
                    bookmarks = d.bookmarks.filter { it.folderId !in subtree },
                )
            },
        )
    }

    /** Number of folders and bookmarks that deleting [folderId] would remove, including itself. */
    fun folderContents(state: BrowserState, folderId: String): Pair<Int, Int> {
        val subtree = state.folderSubtree(folderId)
        return (subtree.size - 1) to state.data.bookmarks.count { it.folderId in subtree }
    }

    fun removeBookmark(state: BrowserState, bookmarkId: String): Reduction {
        if (state.data.bookmarks.none { it.id == bookmarkId }) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.withData { d -> d.copy(bookmarks = d.bookmarks.filter { it.id != bookmarkId }) })
    }

    /**
     * Adds imported bookmarks inside a new root folder named [folderName], keeping the file's
     * folder structure. Addresses that are already bookmarked are skipped.
     */
    fun importBookmarks(state: BrowserState, nodes: List<BookmarkHtml.Node>, folderName: String): Pair<Reduction, ImportSummary> {
        var s = state
        var imported = 0
        var skipped = 0
        fun addAll(items: List<BookmarkHtml.Node>, parentId: String) {
            items.forEach { node ->
                when (node) {
                    is BookmarkHtml.Folder -> {
                        val created = createBookmarkFolder(s, node.name, parentId)
                        val id = (created.outcome as? Outcome.Created)?.id ?: return@forEach
                        s = created.state
                        addAll(node.children, id)
                    }
                    is BookmarkHtml.Link -> {
                        val added = addBookmark(s, node.url, node.title, parentId)
                        if (added.outcome is Outcome.Created) {
                            s = added.state
                            imported++
                        } else {
                            skipped++
                        }
                    }
                }
            }
        }
        val root = createBookmarkFolder(s, folderName, null)
        val rootId = (root.outcome as? Outcome.Created)?.id ?: return root to ImportSummary(0, 0)
        s = root.state
        addAll(nodes, rootId)
        return Reduction(s, outcome = Outcome.Created(rootId)) to ImportSummary(imported, skipped)
    }

    fun bookmarkFor(state: BrowserState, url: String): Bookmark? =
        state.data.bookmarks.firstOrNull { sameUrl(it.url, url) }

    fun openBookmark(state: BrowserState, bookmarkId: String): Reduction {
        val bookmark = state.data.bookmarks.firstOrNull { it.id == bookmarkId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        return openUrlInActiveSpace(state, bookmark.url)
    }

    // endregion

    // region Shortcuts

    fun addShortcut(state: BrowserState, url: String, title: String?, iconUrl: String? = null): Reduction {
        val normalized = UrlInput.normalizeWebUrl(url) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        val shortcut = Shortcut(
            id = ids.newId(),
            url = normalized,
            title = title?.trim().orEmpty().ifEmpty { UrlInput.displayHost(normalized) },
            iconUrl = iconUrl?.takeIf(UrlInput::isWebUrl),
            position = nextPosition(state.data.shortcuts),
        )
        return Reduction(state.withData { it.copy(shortcuts = it.shortcuts + shortcut) }, outcome = Outcome.Created(shortcut.id))
    }

    fun updateShortcut(state: BrowserState, shortcutId: String, title: String, url: String): Reduction {
        val existing = state.data.shortcuts.firstOrNull { it.id == shortcutId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val normalized = UrlInput.normalizeWebUrl(url) ?: return Reduction.reject(state, RejectReason.INVALID_URL)
        val updated = existing.copy(url = normalized, title = title.trim().ifEmpty { UrlInput.displayHost(normalized) })
        return Reduction(state.withData { d -> d.copy(shortcuts = d.shortcuts.map { if (it.id == shortcutId) updated else it }) })
    }

    fun removeShortcut(state: BrowserState, shortcutId: String): Reduction {
        if (state.data.shortcuts.none { it.id == shortcutId }) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.withData { d -> d.copy(shortcuts = d.shortcuts.filter { it.id != shortcutId }) })
    }

    fun moveShortcut(state: BrowserState, shortcutId: String, delta: Int): Reduction {
        val shortcut = state.data.shortcuts.firstOrNull { it.id == shortcutId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        val reordered = state.data.shortcuts.sortedBy { it.position }.moved(shortcut, delta) ?: return Reduction(state)
        val positions = reordered.mapIndexed { index, sc -> sc.id to index }.toMap()
        return Reduction(state.withData { d -> d.copy(shortcuts = d.shortcuts.map { it.copy(position = positions.getValue(it.id)) }) })
    }

    fun openShortcut(state: BrowserState, shortcutId: String): Reduction {
        val shortcut = state.data.shortcuts.firstOrNull { it.id == shortcutId }
            ?: return Reduction.reject(state, RejectReason.NOT_FOUND)
        return openUrlInActiveSpace(state, shortcut.url)
    }

    // endregion

    // region Web apps

    fun upsertWebApp(state: BrowserState, app: WebApp): Reduction {
        if (state.container(app.containerId) == null) return Reduction.reject(state, RejectReason.CONTAINER_MISSING)
        val exists = state.data.webApps.any { it.id == app.id }
        return Reduction(
            state.withData { d ->
                d.copy(webApps = if (exists) d.webApps.map { if (it.id == app.id) app else it } else d.webApps + app)
            },
            outcome = if (exists) Outcome.Ok else Outcome.Created(app.id),
        )
    }

    fun removeWebApp(state: BrowserState, appId: String): Reduction {
        if (state.data.webApps.none { it.id == appId }) return Reduction.reject(state, RejectReason.NOT_FOUND)
        return Reduction(state.withData { d -> d.copy(webApps = d.webApps.filter { it.id != appId }) })
    }

    // endregion

    // region Helpers

    private fun removeTabRecord(original: BrowserState, tab: Tab): Reduction {
        // Closing one half of split view leaves the other half on screen.
        val partner = original.splitTab?.takeIf { original.activeTab?.id == tab.id }
        var state = original.copy(splitTabs = original.splitTabs.filterValues { it != tab.id })
        if (partner != null) {
            state = selectOnly(state, partner.id).state
            state = state.copy(splitTabs = state.splitTabs - (original.contextKey ?: ""))
        }
        if (tab.isPrivate) {
            val ordered = state.sortedPrivateTabs
            val index = ordered.indexOfFirst { it.id == tab.id }
            val remaining = ordered.filter { it.id != tab.id }
            val selected = if (state.privateSelectedTabId != tab.id) {
                state.privateSelectedTabId
            } else {
                remaining.getOrNull(minOf(index, remaining.size - 1))?.id
            }
            val s = state.copy(privateTabs = remaining, privateSelectedTabId = selected, runtime = state.runtime - tab.id)
            return Reduction(selected?.let { s.markLoadedIfDisplayable(it) } ?: s)
        }
        var s = state
        for (space in state.data.spaces) {
            if (space.selectedTabId == tab.id) s = s.reselectIfSelected(space.id, tab.id)
        }
        s = s.copy(
            data = s.data.copy(tabs = s.data.tabs.filter { it.id != tab.id }),
            runtime = s.runtime - tab.id,
        )
        return Reduction(s, discardSessionState = setOf(tab.id))
    }

    /** If [tabId] is selected in [spaceId], selects its neighbour in display order instead. */
    private fun BrowserState.reselectIfSelected(spaceId: String, tabId: String): BrowserState {
        val space = space(spaceId) ?: return this
        if (space.selectedTabId != tabId) return this
        val ordered = pinnedTabs(spaceId) + regularTabs(spaceId)
        val index = ordered.indexOfFirst { it.id == tabId }
        val remaining = ordered.filter { it.id != tabId }
        val next = when {
            remaining.isEmpty() -> null
            index < 0 -> remaining.last()
            else -> remaining[minOf(index, remaining.size - 1)]
        }
        return selectInSpace(spaceId, next?.id)
    }

    private fun BrowserState.selectInSpace(spaceId: String, tabId: String?): BrowserState {
        var s = updateSpace(spaceId) { it.copy(selectedTabId = tabId) }
        if (tabId != null && spaceId == s.data.settings.activeSpaceId) s = s.markLoadedIfDisplayable(tabId)
        return s
    }

    private fun BrowserState.markLoadedIfDisplayable(tabId: String): BrowserState {
        val tab = tab(tabId) ?: return this
        if (tab.kind != TabKind.WEB || isBlocked(tab)) return this
        return setRuntime(tabId) { it.copy(loaded = true) }
    }

    private fun sameUrl(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')

    // endregion

    companion object {
        const val DEFAULT_SPACE_NAME = "Personal"
        const val ABOUT_BLANK = "about:blank"
        private const val OPAQUE = 0xFF000000.toInt()

        /** Long enough for multi-codepoint emoji (skin tones, flags, ZWJ sequences). */
        private const val MAX_EMOJI_LENGTH = 16

        val DEFAULT_COLORS = listOf(
            0xFF6750A4.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt(),
            0xFFF4511E.toInt(),
            0xFFD81B60.toInt(),
            0xFF00897B.toInt(),
            0xFFFDD835.toInt(),
            0xFF8D6E63.toInt(),
        )
    }
}

internal fun BrowserState.withData(transform: (BrowserData) -> BrowserData): BrowserState = copy(data = transform(data))

internal fun BrowserState.updateTab(id: String, transform: (Tab) -> Tab): BrowserState =
    if (privateTabs.any { it.id == id }) {
        copy(privateTabs = privateTabs.map { if (it.id == id) transform(it) else it })
    } else {
        withData { d -> d.copy(tabs = d.tabs.map { if (it.id == id) transform(it) else it }) }
    }

internal fun BrowserState.updateSpace(id: String, transform: (Space) -> Space): BrowserState =
    withData { d -> d.copy(spaces = d.spaces.map { if (it.id == id) transform(it) else it }) }

internal fun BrowserState.updateTabFolder(id: String, transform: (TabFolder) -> TabFolder): BrowserState =
    withData { d -> d.copy(tabFolders = d.tabFolders.map { if (it.id == id) transform(it) else it }) }

internal fun BrowserState.setRuntime(id: String, transform: (TabRuntime) -> TabRuntime): BrowserState =
    copy(runtime = runtime + (id to transform(runtime(id))))

private fun nextPosition(items: List<Any>): Int = (items.maxOfOrNull { positionOf(it) } ?: -1) + 1

private fun positionOf(item: Any): Int = when (item) {
    is Tab -> item.position
    is Space -> item.position
    is Bookmark -> item.position
    is BookmarkFolder -> item.position
    is Shortcut -> item.position
    is TabFolder -> item.position
    else -> 0
}

/** Returns the list with [item] moved by [delta] places, or null when it cannot move. */
private fun <T> List<T>.moved(item: T, delta: Int): List<T>? {
    val from = indexOf(item)
    if (from < 0) return null
    val to = (from + delta).coerceIn(0, size - 1)
    if (to == from) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}
