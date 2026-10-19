package com.javzzap.laxbrowser.ui.sidebar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabFolder
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.TabLifecycle
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.ui.common.SectionHeader
import com.javzzap.laxbrowser.ui.common.SiteIcon
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import com.javzzap.laxbrowser.ui.common.SwipeToAct
import kotlin.math.abs

/** User intents raised by the sidebar. The screen decides about confirmations. */
interface SidebarActions {
    fun submitUrl(text: String)
    fun queryChanged(text: String)
    fun openSuggestion(suggestion: Suggestion)
    fun voiceSearch()
    fun scanQrCode()
    fun newTab()
    fun selectTab(tab: Tab)
    fun closeTab(tab: Tab)
    fun unloadTab(tab: Tab)
    fun pinTab(tab: Tab)
    fun unpinTab(tab: Tab)
    fun addToEssentials(tab: Tab)
    fun removeEssential(tab: Tab)
    fun moveTab(tab: Tab, delta: Int)
    fun moveTabToSpace(tab: Tab)
    fun bookmarkTab(tab: Tab)
    fun openInSplit(tab: Tab)
    fun resetPinnedTab(tab: Tab)
    fun replacePinnedUrl(tab: Tab)
    fun editPinnedUrl(tab: Tab)
    fun moveTabToFolder(tab: Tab)
    fun newFolder()
    fun renameFolder(folder: TabFolder)
    fun deleteFolder(folder: TabFolder)
    fun toggleFolder(folder: TabFolder)
    fun moveFolder(folder: TabFolder, delta: Int)
    fun switchSpace(space: Space)

    /** Switches to the next (1) or previous (-1) Space, from a swipe. */
    fun switchSpaceBy(delta: Int)
    fun newSpace()
    fun manageSpaces()
    fun setPrivateMode(enabled: Boolean)
    fun closeAllPrivateTabs()
}

object SidebarTags {
    const val ESSENTIALS = "sidebar_essentials"
    const val URL_BAR = "sidebar_url_bar"
    const val PINNED = "sidebar_pinned"
    const val TABS = "sidebar_tabs"
    const val SPACES = "sidebar_spaces"
    const val SUGGESTIONS = "sidebar_suggestions"
    const val TAB_RESULTS = "sidebar_tab_results"
}

/**
 * Taps in the sidebar outside the address bar and its suggestions take focus away from the
 * address bar, which clears what was typed and brings the tab list back. Areas marked with
 * [keepsSearchFocus] see each touch first (initial pass); the sidebar decides after its children
 * (final pass), so it never steals a tap that a suggestion is about to handle.
 */
private class SearchFocusTaps {
    var keep = false
}

private fun Modifier.keepsSearchFocus(taps: SearchFocusTaps): Modifier = pointerInput(taps) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        taps.keep = true
    }
}

private fun Modifier.clearsSearchFocus(taps: SearchFocusTaps, focusManager: FocusManager): Modifier = pointerInput(taps, focusManager) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (!taps.keep) focusManager.clearFocus()
        taps.keep = false
    }
}

@Composable
fun Sidebar(
    state: BrowserState,
    actions: SidebarActions,
    focusUrlBar: Boolean,
    onUrlBarFocused: () -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<Suggestion> = emptyList(),
    voiceAvailable: Boolean = false,
) {
    val taps = remember { SearchFocusTaps() }
    val focusManager = LocalFocusManager.current
    val keep = Modifier.keepsSearchFocus(taps)
    var query by remember { mutableStateOf("") }
    // The Space whose open tabs the address bar searches; null searches every Space.
    var tabScope by remember(state.activeSpace?.id) { mutableStateOf(state.activeSpace?.id) }
    val onQuery: (String) -> Unit = {
        if (it.isBlank()) tabScope = state.activeSpace?.id
        query = it
        actions.queryChanged(it)
    }
    if (state.privateMode) {
        PrivateSidebar(state, actions, focusUrlBar, onUrlBarFocused, query, onQuery, suggestions, voiceAvailable, taps, modifier.clearsSearchFocus(taps, focusManager))
        return
    }
    val space = state.activeSpace
    // Space theme: the sidebar is tinted with the active Space's color.
    val tint by animateColorAsState(space?.let { Color(it.color) } ?: Color.Transparent, tween(450), label = "space_tint")

    Column(
        modifier
            .fillMaxSize()
            .clearsSearchFocus(taps, focusManager)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.04f)))),
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "essentials") {
                Column(Modifier.testTag(SidebarTags.ESSENTIALS)) {
                    SectionHeader(stringResource(R.string.essentials), Modifier.semantics { heading() })
                    Essentials(state, actions)
                }
            }
            item(key = "url_bar") {
                UrlBar(
                    currentUrl = state.activeTab?.takeIf { it.kind == TabKind.WEB }?.url.orEmpty(),
                    onSubmit = actions::submitUrl,
                    onQueryChange = onQuery,
                    requestFocus = focusUrlBar,
                    onFocused = onUrlBarFocused,
                    voiceAvailable = voiceAvailable,
                    onVoice = actions::voiceSearch,
                    onScan = actions::scanQrCode,
                    modifier = keep.testTag(SidebarTags.URL_BAR),
                )
            }
            if (query.isNotBlank()) {
                suggestionItems(state, query, tabScope, { tabScope = it }, suggestions, actions, keep)
            } else {
                item(key = "space_header") { SpaceHeader(state, actions) }
                spaceTabItems(state, actions)
            }
        }
        HorizontalDivider()
        SpaceBar(state, actions, Modifier.testTag(SidebarTags.SPACES))
    }
}

private fun LazyListScope.spaceTabItems(state: BrowserState, actions: SidebarActions) {
    val space = state.activeSpace
    val folders = state.tabFolders(space?.id)
    val loose = state.loosePinnedTabs(space?.id)
    val regular = state.regularTabs(space?.id)
    val spaceCount = state.data.spaces.size
    item(key = "pinned_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                stringResource(if (loose.isEmpty() && folders.isEmpty()) R.string.pinned_empty else R.string.pinned),
                Modifier.weight(1f).testTag(SidebarTags.PINNED).semantics { heading() },
            )
            IconButton(onClick = actions::newFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_tab_folder))
            }
        }
    }
    folders.forEach { folder ->
        val members = state.tabsInFolder(folder.id)
        item(key = "folder_${folder.id}") { FolderRow(folder, members.size, actions) }
        if (!folder.collapsed) {
            items(members, key = { "folder_tab_${it.id}" }) { tab ->
                TabRow(tab, state, actions, spaceCount, Modifier.padding(start = 20.dp))
            }
        }
    }
    items(loose, key = { "pinned_${it.id}" }) { tab -> TabRow(tab, state, actions, spaceCount) }
    item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) }
    item(key = "tabs_header") {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(SidebarTags.TABS)
                .clickable(onClickLabel = stringResource(R.string.new_tab)) { actions.newTab() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.new_tab), style = MaterialTheme.typography.bodyLarge)
        }
    }
    items(regular, key = { "tab_${it.id}" }) { tab -> TabRow(tab, state, actions, spaceCount) }
}

/**
 * The active Space's name. Swipe it left or right, or use the arrows, to change Space. Only the
 * name slides (out one side, in from the other, in the direction of the switch); the arrows stay.
 */
@Composable
private fun SpaceHeader(state: BrowserState, actions: SidebarActions) {
    val space = state.activeSpace ?: return
    val spaces = state.sortedSpaces
    val canSwipe = spaces.size > 1
    val previous = stringResource(R.string.previous_space)
    val next = stringResource(R.string.next_space)
    // Direction of the last arrow tap or swipe; 0 (a switch from elsewhere) falls back to list order.
    var direction by remember { mutableIntStateOf(0) }
    val switchBy: (Int) -> Unit = { delta ->
        direction = delta
        actions.switchSpaceBy(delta)
    }
    LaunchedEffect(space.id) { direction = 0 }

    Row(
        Modifier
            .fillMaxWidth()
            .spaceSwipe(canSwipe, switchBy)
            .semantics {
                if (canSwipe) {
                    customActions = listOf(
                        CustomAccessibilityAction(previous) { switchBy(-1); true },
                        CustomAccessibilityAction(next) { switchBy(1); true },
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canSwipe) {
            IconButton(onClick = { switchBy(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = previous) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        AnimatedContent(
            targetState = space,
            transitionSpec = {
                val forward = if (direction != 0) {
                    direction > 0
                } else {
                    spaces.indexOfFirst { it.id == targetState.id } >= spaces.indexOfFirst { it.id == initialState.id }
                }
                val sign = if (forward) 1 else -1
                (slideInHorizontally(tween(SPACE_SLIDE_MS)) { width -> sign * width } + fadeIn(tween(SPACE_SLIDE_MS))) togetherWith
                    (slideOutHorizontally(tween(SPACE_SLIDE_MS)) { width -> -sign * width } + fadeOut(tween(SPACE_SLIDE_MS / 2))) using
                    SizeTransform(clip = true)
            },
            contentKey = { it.id },
            modifier = Modifier.weight(1f).clipToBounds(),
            label = "space_header",
        ) { shown ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SpaceIcon(shown, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    shown.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        }
        if (canSwipe) {
            IconButton(onClick = { switchBy(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = next) }
        }
    }
}

private const val SPACE_SLIDE_MS = 260

/**
 * Horizontal swipes switch Space: left goes to the next one, right to the previous one. Used on
 * the Space header and the Space bar only, so swiping tab rows still unloads or closes them.
 */
@Composable
private fun Modifier.spaceSwipe(enabled: Boolean, onSwipe: (Int) -> Unit): Modifier {
    if (!enabled) return this
    val threshold = with(LocalDensity.current) { SPACE_SWIPE_THRESHOLD.toPx() }
    var travelled by remember { mutableFloatStateOf(0f) }
    return pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (abs(travelled) > threshold) onSwipe(if (travelled < 0) 1 else -1)
                travelled = 0f
            },
            onDragCancel = { travelled = 0f },
        ) { change, dragAmount ->
            change.consume()
            travelled += dragAmount
        }
    }
}

@Composable
private fun FolderRow(folder: TabFolder, count: Int, actions: SidebarActions) {
    var menu by remember { mutableStateOf(false) }
    val expandLabel = stringResource(if (folder.collapsed) R.string.expand_folder else R.string.collapse_folder)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clickable(onClickLabel = expandLabel) { actions.toggleFolder(folder) }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (folder.collapsed && count > 0) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.tabs_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(if (folder.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = null)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, folder.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; actions.renameFolder(folder) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; actions.moveFolder(folder, -1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; actions.moveFolder(folder, 1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.ungroup_folder)) }, onClick = { menu = false; actions.deleteFolder(folder) })
            }
        }
    }
}

/**
 * What the address bar finds: the address or search itself, open tabs in the chosen Space (or
 * every Space), then bookmarks, history, and the search engine's suggestions.
 */
private fun LazyListScope.suggestionItems(
    state: BrowserState,
    query: String,
    tabScope: String?,
    onTabScope: (String?) -> Unit,
    suggestions: List<Suggestion>,
    actions: SidebarActions,
    keep: Modifier,
) {
    val (primary, rest) = suggestions.partition { it is Suggestion.Address || (it is Suggestion.Search && !it.fromEngine) }
    val tabs = SuggestionMatcher.openTabs(state, query, tabScope)
    val showScopes = !state.privateMode && state.data.spaces.size > 1
    item(key = "suggestions_header") {
        Box(Modifier.testTag(SidebarTags.SUGGESTIONS))
    }
    items(primary, key = { suggestionKey(it) }) { suggestion -> SuggestionRow(suggestion, actions, keep) }
    if (showScopes || tabs.isNotEmpty()) {
        item(key = "tab_results_header") { TabResultsHeader(state, tabScope, onTabScope, showScopes, keep) }
    }
    if (tabs.isEmpty() && showScopes) {
        item(key = "tab_results_empty") {
            val spaceName = tabScope?.let { state.space(it)?.name }
            Text(
                spaceName?.let { stringResource(R.string.tab_search_none_in_space, it) } ?: stringResource(R.string.tab_search_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = keep.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
    items(tabs, key = { suggestionKey(it) }) { suggestion -> SuggestionRow(suggestion, actions, keep) }
    items(rest, key = { suggestionKey(it) }) { suggestion -> SuggestionRow(suggestion, actions, keep) }
}

/** "Open tabs", with chips that choose which Space is searched. */
@Composable
private fun TabResultsHeader(state: BrowserState, tabScope: String?, onTabScope: (String?) -> Unit, showScopes: Boolean, modifier: Modifier) {
    Column(modifier.testTag(SidebarTags.TAB_RESULTS)) {
        SectionHeader(stringResource(R.string.tab_search_title), Modifier.semantics { heading() })
        if (showScopes) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.sortedSpaces, key = { it.id }) { space ->
                    FilterChip(
                        selected = tabScope == space.id,
                        onClick = { onTabScope(space.id) },
                        label = { Text(space.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { SpaceIcon(space, size = 12.dp) },
                    )
                }
                item(key = "all_spaces") {
                    FilterChip(
                        selected = tabScope == null,
                        onClick = { onTabScope(null) },
                        label = { Text(stringResource(R.string.tab_search_all_spaces)) },
                    )
                }
            }
        }
    }
}

private fun suggestionKey(suggestion: Suggestion): String = when (suggestion) {
    is Suggestion.Search -> "search_${suggestion.fromEngine}_${suggestion.query}"
    is Suggestion.Address -> "address_${suggestion.url}"
    is Suggestion.OpenTab -> "open_${suggestion.tab.id}"
    is Suggestion.Bookmark -> "bookmark_${suggestion.url}"
    is Suggestion.History -> "history_${suggestion.url}"
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, actions: SidebarActions, modifier: Modifier) {
    val (title, subtitle) = when (suggestion) {
        is Suggestion.Search -> suggestion.query to null
        is Suggestion.Address -> UrlInput.displayHost(suggestion.url) to suggestion.url
        is Suggestion.OpenTab -> suggestion.tab.title.ifBlank { UrlInput.displayHost(suggestion.tab.url) } to
            (suggestion.spaceName?.let { stringResource(R.string.suggestion_tab_in_space, it) } ?: stringResource(R.string.switch_to_tab))
        is Suggestion.Bookmark -> suggestion.title.ifBlank { UrlInput.displayHost(suggestion.url) } to UrlInput.displayHost(suggestion.url)
        is Suggestion.History -> suggestion.title.ifBlank { UrlInput.displayHost(suggestion.url) } to UrlInput.displayHost(suggestion.url)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clickable { actions.openSuggestion(suggestion) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            is Suggestion.Search -> Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            is Suggestion.Address -> Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            is Suggestion.OpenTab -> SiteIcon(suggestion.tab.url, title, suggestion.tab.faviconUrl, size = 24.dp, ephemeral = suggestion.tab.isPrivate)
            is Suggestion.Bookmark -> SiteIcon(suggestion.url, title, suggestion.faviconUrl, size = 24.dp)
            is Suggestion.History -> SiteIcon(suggestion.url, title, suggestion.faviconUrl, size = 24.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val badge = when (suggestion) {
            is Suggestion.OpenTab -> Icons.Default.Tab
            is Suggestion.Bookmark -> Icons.Default.Bookmark
            is Suggestion.History -> Icons.Default.History
            is Suggestion.Address -> Icons.AutoMirrored.Filled.OpenInNew
            is Suggestion.Search -> null
        }
        badge?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Private browsing shows only private tabs: no Essentials, pinned tabs, or Spaces. */
@Composable
private fun PrivateSidebar(
    state: BrowserState,
    actions: SidebarActions,
    focusUrlBar: Boolean,
    onUrlBarFocused: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    suggestions: List<Suggestion>,
    voiceAvailable: Boolean,
    taps: SearchFocusTaps,
    modifier: Modifier,
) {
    val keep = Modifier.keepsSearchFocus(taps)
    val tabs = state.sortedPrivateTabs
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "private_header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.private_browsing),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "url_bar") {
                UrlBar(
                    currentUrl = state.activeTab?.takeIf { it.kind == TabKind.WEB }?.url.orEmpty(),
                    onSubmit = actions::submitUrl,
                    onQueryChange = onQuery,
                    requestFocus = focusUrlBar,
                    onFocused = onUrlBarFocused,
                    voiceAvailable = voiceAvailable,
                    onVoice = actions::voiceSearch,
                    onScan = actions::scanQrCode,
                    modifier = keep.testTag(SidebarTags.URL_BAR),
                )
            }
            if (query.isNotBlank()) {
                suggestionItems(state, query, tabScope = null, onTabScope = {}, suggestions, actions, keep)
            } else {
                item(key = "new_private_tab") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag(SidebarTags.TABS)
                            .clickable(onClickLabel = stringResource(R.string.new_private_tab)) { actions.newTab() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.new_private_tab), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(tabs, key = { "private_${it.id}" }) { tab -> TabRow(tab, state, actions, spaceCount = 0) }
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { actions.setPrivateMode(false) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.exit_private_browsing), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = actions::closeAllPrivateTabs, enabled = tabs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.close_all_private_tabs), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UrlBar(
    currentUrl: String,
    onSubmit: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    voiceAvailable: Boolean,
    onVoice: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(currentUrl) { mutableStateOf(TextFieldValue(currentUrl)) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocused()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            val changed = it.text != value.text
            value = it
            if (changed) onQueryChange(if (it.text == currentUrl) "" else it.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused && !focused) value = value.copy(selection = TextRange(0, value.text.length))
                if (!it.isFocused && focused) {
                    value = TextFieldValue(currentUrl)
                    onQueryChange("")
                }
                focused = it.isFocused
            },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.url_bar_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (focused && value.text.isNotEmpty()) {
                IconButton(onClick = {
                    value = TextFieldValue("")
                    onQueryChange("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_address))
                }
            } else {
                Row {
                    if (voiceAvailable) {
                        IconButton(onClick = onVoice) { Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_search)) }
                    }
                    IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_qr_code)) }
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { if (value.text.isNotBlank()) onSubmit(value.text) }),
        shape = RoundedCornerShape(24.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Essentials(state: BrowserState, actions: SidebarActions) {
    val essentials = state.essentials
    if (essentials.isEmpty()) {
        Text(
            stringResource(R.string.essentials_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    FlowRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        essentials.forEach { tab ->
            var menu by remember { mutableStateOf(false) }
            val selected = tab.id in state.visibleTabIds
            val unloaded = state.lifecycle(tab) == TabLifecycle.UNLOADED
            val name = tab.title.ifBlank { tab.url }
            val description = stringResource(R.string.essential_cd, name) +
                if (unloaded) stringResource(R.string.cd_separator) + stringResource(R.string.cd_unloaded) else ""
            Box {
                SwipeToAct(
                    label = stringResource(R.string.swipe_unload),
                    icon = Icons.Default.Bedtime,
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    enabled = !unloaded,
                    onSwiped = { actions.unloadTab(tab) },
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(64.dp)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .combinedClickable(
                                onClickLabel = stringResource(R.string.action_open),
                                onLongClickLabel = stringResource(R.string.essential_options),
                                onLongClick = { menu = true },
                                onClick = { actions.selectTab(tab) },
                            )
                            .semantics {
                                contentDescription = description
                                this.selected = selected
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SiteIcon(tab.url, tab.title, tab.faviconUrl, size = 32.dp, dimmed = unloaded)
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.unload_keep_essential)) },
                        enabled = !unloaded,
                        onClick = { menu = false; actions.unloadTab(tab) },
                    )
                    PinnedUrlItems(tab, state, actions) { menu = false }
                    SplitItem(tab, state, actions) { menu = false }
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_left)) }, onClick = { menu = false; actions.moveTab(tab, -1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_right)) }, onClick = { menu = false; actions.moveTab(tab, 1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bookmark)) }, onClick = { menu = false; actions.bookmarkTab(tab) })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_essentials), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; actions.removeEssential(tab) },
                    )
                }
            }
        }
    }
}

/** Reset / replace / edit the saved page of a pinned tab or Essential. */
@Composable
private fun PinnedUrlItems(tab: Tab, state: BrowserState, actions: SidebarActions, dismiss: () -> Unit) {
    val pinnedUrl = tab.pinnedUrl ?: return
    val onSavedPage = tab.url == pinnedUrl && tab.kind == TabKind.WEB
    if (!onSavedPage || state.lifecycle(tab) == TabLifecycle.UNLOADED) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reset_pinned_tab)) },
            onClick = { dismiss(); actions.resetPinnedTab(tab) },
        )
    }
    if (!onSavedPage && tab.kind == TabKind.WEB && UrlInput.isWebUrl(tab.url)) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.replace_pinned_url)) },
            onClick = { dismiss(); actions.replacePinnedUrl(tab) },
        )
    }
    DropdownMenuItem(text = { Text(stringResource(R.string.edit_pinned_url)) }, onClick = { dismiss(); actions.editPinnedUrl(tab) })
}

@Composable
private fun SplitItem(tab: Tab, state: BrowserState, actions: SidebarActions, dismiss: () -> Unit) {
    if (tab.kind != TabKind.WEB || tab.id in state.visibleTabIds || state.isBlocked(tab) || state.activeTab == null) return
    DropdownMenuItem(text = { Text(stringResource(R.string.open_in_split_view)) }, onClick = { dismiss(); actions.openInSplit(tab) })
}

@Composable
private fun TabRow(tab: Tab, state: BrowserState, actions: SidebarActions, spaceCount: Int, modifier: Modifier = Modifier) {
    val lifecycle = state.lifecycle(tab)
    val unloaded = lifecycle == TabLifecycle.UNLOADED && tab.kind == TabKind.WEB
    if (tab.isPinned) {
        SwipeToAct(
            label = stringResource(R.string.swipe_unload),
            icon = Icons.Default.Bedtime,
            background = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            enabled = !unloaded,
            onSwiped = { actions.unloadTab(tab) },
            modifier = modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        ) { TabRowContent(tab, state, actions, spaceCount) }
    } else {
        SwipeToAct(
            label = stringResource(R.string.swipe_close),
            icon = Icons.Default.Close,
            background = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onSwiped = { actions.closeTab(tab) },
            modifier = modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        ) { TabRowContent(tab, state, actions, spaceCount) }
    }
}

@Composable
private fun TabRowContent(tab: Tab, state: BrowserState, actions: SidebarActions, spaceCount: Int) {
    val lifecycle = state.lifecycle(tab)
    val selected = tab.id == state.activeTab?.id
    val inSplit = tab.id == state.splitTab?.id
    val unloaded = lifecycle == TabLifecycle.UNLOADED && tab.kind == TabKind.WEB
    val blocked = state.isBlocked(tab)
    val title = when {
        tab.kind == TabKind.NEW_TAB -> stringResource(R.string.new_tab)
        tab.title.isNotBlank() -> tab.title
        else -> UrlInput.displayHost(tab.url)
    }
    val separator = stringResource(R.string.cd_separator)
    val description = buildList {
        add(title)
        if (tab.isPinned) add(stringResource(R.string.cd_pinned))
        if (inSplit) add(stringResource(R.string.cd_in_split_view))
        if (unloaded) add(stringResource(R.string.cd_unloaded))
        if (blocked) add(stringResource(R.string.cd_container_missing))
    }.joinToString(separator)
    var menu by remember { mutableStateOf(false) }
    // Unloading "puts the tab to sleep": it fades and shrinks a little instead of snapping.
    val sleep by animateFloatAsState(if (unloaded || blocked) 1f else 0f, tween(600), label = "tab_sleep")
    val background by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            inSplit -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        tween(250),
        label = "tab_background",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.switch_to_tab)) { actions.selectTab(tab) }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.graphicsLayer {
                val scale = 1f - 0.1f * sleep
                scaleX = scale
                scaleY = scale
            },
        ) {
            SiteIcon(tab.url, title, tab.faviconUrl, dimmed = unloaded || blocked, ephemeral = tab.isPrivate)
            if (tab.isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp).align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).graphicsLayer { alpha = 1f - 0.4f * sleep }) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val subtitle = when {
                blocked -> stringResource(R.string.container_missing)
                unloaded -> stringResource(R.string.unloaded_host, UrlInput.displayHost(tab.url))
                inSplit -> stringResource(R.string.in_split_view)
                tab.kind == TabKind.WEB -> UrlInput.displayHost(tab.url)
                else -> null
            }
            subtitle?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (unloaded) FontStyle.Italic else FontStyle.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!tab.isPinned) {
            IconButton(onClick = { actions.closeTab(tab) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_tab_cd, title))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (tab.isPrivate) {
                    if (tab.kind == TabKind.WEB) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.bookmark)) }, onClick = { menu = false; actions.bookmarkTab(tab) })
                    }
                } else if (tab.isPinned) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.unpin)) }, onClick = { menu = false; actions.unpinTab(tab) })
                    PinnedUrlItems(tab, state, actions) { menu = false }
                } else {
                    DropdownMenuItem(text = { Text(stringResource(R.string.pin_to_space)) }, onClick = { menu = false; actions.pinTab(tab) })
                }
                SplitItem(tab, state, actions) { menu = false }
                if (!tab.isPrivate && tab.spaceId != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { menu = false; actions.moveTabToFolder(tab) })
                }
                if (tab.kind == TabKind.WEB && !tab.isPrivate) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.unload_keep_tab)) },
                        enabled = lifecycle != TabLifecycle.UNLOADED,
                        onClick = { menu = false; actions.unloadTab(tab) },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_to_essentials)) }, onClick = { menu = false; actions.addToEssentials(tab) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bookmark)) }, onClick = { menu = false; actions.bookmarkTab(tab) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; actions.moveTab(tab, -1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; actions.moveTab(tab, 1) })
                if (spaceCount > 1) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_space)) }, onClick = { menu = false; actions.moveTabToSpace(tab) })
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (tab.isPinned) R.string.close_pinned_tab else R.string.close_tab),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menu = false; actions.closeTab(tab) },
                )
            }
        }
    }
}

@Composable
private fun SpaceBar(state: BrowserState, actions: SidebarActions, modifier: Modifier = Modifier) {
    val active = state.activeSpace
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp).spaceSwipe(state.data.spaces.size > 1, actions::switchSpaceBy)) {
        active?.let {
            val containerName = state.container(it.containerId)?.name ?: stringResource(R.string.missing_container)
            Text(
                stringResource(R.string.space_container_label, it.name, containerName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(state.sortedSpaces, key = { it.id }) { space ->
                    val selected = space.id == active?.id
                    val description = stringResource(R.string.space_cd, space.name)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier
                            .clickable(onClickLabel = stringResource(R.string.switch_to_space)) { actions.switchSpace(space) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = description
                                this.selected = selected
                            },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpaceIcon(space)
                            Spacer(Modifier.width(6.dp))
                            Text(space.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            IconButton(onClick = { actions.setPrivateMode(true) }) {
                Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.private_browsing))
            }
            IconButton(onClick = actions::newSpace) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_space)) }
            IconButton(onClick = actions::manageSpaces) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.manage_spaces_cd))
            }
        }
    }
}

private val SPACE_SWIPE_THRESHOLD = 56.dp
