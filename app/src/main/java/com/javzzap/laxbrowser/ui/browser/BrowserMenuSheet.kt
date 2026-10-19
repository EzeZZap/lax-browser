package com.javzzap.laxbrowser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.javzzap.laxbrowser.domain.SiteRules
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.engine.gecko.ExtensionAction
import com.javzzap.laxbrowser.pwa.Eligibility
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.extensions.ExtensionIcon
import kotlinx.coroutines.launch

/** Dialogs the menu opens on the browser screen. */
enum class MenuDialog { SPLIT, TRANSLATE, RECENTLY_CLOSED, SITE_SPACE }

/** The browser menu, shown as a bottom sheet like Firefox's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    state: BrowserState,
    vm: BrowserViewModel,
    canReopen: Boolean,
    onShare: (String) -> Unit,
    onScreenChange: (Screen) -> Unit,
    onEditBookmark: (String) -> Unit,
    onMenuDialog: (MenuDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    val activity = LocalActivity.current
    val translations by vm.engine.translations.collectAsStateWithLifecycle()
    val controller = vm.controller
    // Opens at half height so the page stays visible; dragging or scrolling up expands it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val tab = state.activeTab
    val runtime = tab?.let { state.runtime(it.id) }
    val webTab = tab?.takeIf { it.kind == TabKind.WEB }
    val loaded = webTab != null && runtime?.loaded == true
    val bookmark = webTab?.let { controller.bookmarkFor(it.url) }
    val extensionActions by vm.extensionActions.collectAsStateWithLifecycle()

    /** Hides the sheet, then runs [action], so screens and dialogs open after the sheet is gone. */
    fun run(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.nestedScroll(KeepFlingInSheetContent).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickAction(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.menu_back), runtime?.canGoBack == true) {
                    tab?.let { controller.goBack(it.id) }
                    onDismiss()
                }
                QuickAction(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.menu_forward), runtime?.canGoForward == true) {
                    tab?.let { controller.goForward(it.id) }
                    onDismiss()
                }
                if (runtime?.isLoading == true) {
                    QuickAction(Icons.Default.Close, stringResource(R.string.stop_loading), loaded) {
                        webTab?.let { controller.stop(it.id) }
                        onDismiss()
                    }
                } else {
                    QuickAction(Icons.Default.Refresh, stringResource(R.string.reload), webTab != null) {
                        webTab?.let { vm.report(controller.reload(it.id)) }
                        onDismiss()
                    }
                }
                QuickAction(
                    if (bookmark != null) Icons.Default.Star else Icons.Default.StarBorder,
                    stringResource(if (bookmark != null) R.string.menu_edit_bookmark else R.string.bookmark),
                    webTab != null,
                ) {
                    run { if (bookmark != null) onEditBookmark(bookmark.id) else vm.bookmarkCurrentPage(onEditBookmark) }
                }
                QuickAction(Icons.Default.Share, stringResource(R.string.menu_share), webTab != null) {
                    run { webTab?.let { onShare(it.url) } }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            MenuItem(Icons.Default.Add, stringResource(if (state.privateMode) R.string.new_private_tab else R.string.new_tab)) {
                run { vm.report(controller.openNewTabPage()) }
            }
            if (state.privateMode) {
                MenuItem(Icons.Default.Visibility, stringResource(R.string.exit_private_browsing)) { run { vm.setPrivateMode(false) } }
            } else {
                MenuItem(Icons.Default.VisibilityOff, stringResource(R.string.new_private_tab)) { run { vm.newPrivateTab() } }
            }
            if (canReopen) {
                MenuItem(Icons.Default.Restore, stringResource(R.string.menu_reopen_closed_tab)) { run { vm.reopen() } }
            }
            if (state.splitTab != null) {
                MenuItem(Icons.Default.VerticalSplit, stringResource(R.string.close_split_view)) { run { vm.closeSplit() } }
            } else if (tab != null) {
                MenuItem(Icons.Default.VerticalSplit, stringResource(R.string.menu_split_view)) { run { onMenuDialog(MenuDialog.SPLIT) } }
            }
            if (webTab != null) {
                MenuItem(Icons.Default.FindInPage, stringResource(R.string.menu_find_in_page), enabled = loaded) { run { vm.openFind() } }
                MenuItem(Icons.Default.ContentCopy, stringResource(R.string.menu_copy_link)) { run { vm.copyToClipboard(webTab.url) } }
                SwitchItem(Icons.Default.DesktopWindows, stringResource(R.string.menu_desktop_site), runtime?.desktopMode == true) {
                    vm.report(controller.toggleDesktopMode(webTab.id))
                }
                if (UrlInput.isWebUrl(webTab.url)) {
                    MenuItem(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.menu_reader_view), enabled = loaded) { run { vm.openReaderView() } }
                }
                if (translations[webTab.id]?.translatedTo != null) {
                    MenuItem(Icons.Default.Translate, stringResource(R.string.menu_show_original)) { run { vm.showOriginal() } }
                } else {
                    MenuItem(Icons.Default.Translate, stringResource(R.string.menu_translate), enabled = loaded) { run { onMenuDialog(MenuDialog.TRANSLATE) } }
                }
                MenuItem(Icons.Default.Print, stringResource(R.string.menu_print), enabled = loaded) { run { activity?.let(vm::print) } }
                if (UrlInput.isWebUrl(webTab.url)) ZoomItem(vm.zoomFor(webTab.url), onChange = vm::changeZoom)
                if (!webTab.isPrivate && UrlInput.isWebUrl(webTab.url) && state.data.spaces.size > 1) {
                    val ruled = SiteRules.spaceFor(state, webTab.url)
                    MenuItem(
                        Icons.Default.Layers,
                        ruled?.let { stringResource(R.string.site_opens_in, it.name) } ?: stringResource(R.string.menu_always_open_in_space),
                    ) { run { onMenuDialog(MenuDialog.SITE_SPACE) } }
                }
                if (UrlInput.isWebUrl(webTab.url)) {
                    val isShortcut = state.data.shortcuts.any { it.url == webTab.url }
                    MenuItem(
                        if (isShortcut) Icons.Default.DashboardCustomize else Icons.Default.AddToPhotos,
                        stringResource(if (isShortcut) R.string.menu_remove_ntp_shortcut else R.string.menu_add_ntp_shortcut),
                    ) { run { vm.toggleNewTabShortcut(webTab) } }
                }
                if (!webTab.isPrivate && UrlInput.isWebUrl(webTab.url)) {
                    MenuItem(Icons.Default.AddToHomeScreen, stringResource(R.string.menu_add_to_home)) { run { vm.addToHomeScreen() } }
                }
                if (vm.eligibility(state) is Eligibility.Eligible) {
                    MenuItem(Icons.Default.InstallMobile, stringResource(R.string.menu_install_app)) { run { vm.prepareInstall() } }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Toolbar buttons of enabled extensions; tapping may open the extension's popup.
            if (extensionActions.isNotEmpty()) {
                extensionActions.forEach { action ->
                    ExtensionActionItem(action) { run { vm.clickExtensionAction(action.extensionId) } }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            MenuItem(Icons.Default.Bookmarks, stringResource(R.string.menu_bookmarks)) { run { onScreenChange(Screen.BOOKMARKS) } }
            MenuItem(Icons.Default.History, stringResource(R.string.menu_history)) { run { onScreenChange(Screen.HISTORY) } }
            MenuItem(Icons.Default.Download, stringResource(R.string.menu_downloads)) { run { onScreenChange(Screen.DOWNLOADS) } }
            MenuItem(Icons.Default.Restore, stringResource(R.string.menu_recently_closed)) { run { onMenuDialog(MenuDialog.RECENTLY_CLOSED) } }
            MenuItem(Icons.Default.Shield, stringResource(R.string.menu_site_permissions)) { run { onScreenChange(Screen.SITE_PERMISSIONS) } }
            MenuItem(Icons.Default.Apps, stringResource(R.string.menu_web_apps)) { run { onScreenChange(Screen.WEB_APPS) } }
            MenuItem(Icons.Default.Extension, stringResource(R.string.menu_extensions)) { run { onScreenChange(Screen.EXTENSIONS) } }
            MenuItem(Icons.Default.Layers, stringResource(R.string.menu_spaces)) { run { onScreenChange(Screen.SPACES) } }
            MenuItem(Icons.Default.Settings, stringResource(R.string.menu_settings)) { run { onScreenChange(Screen.SETTINGS) } }
        }
    }
}

/**
 * Keeps a fling's leftover velocity (after the list hits its end) from reaching the sheet. The
 * sheet would start a settle animation with it, and while that runs the list counts as still
 * scrolling, so the next tap only stops the "scroll" instead of clicking a menu item. Dragging
 * still expands the sheet, since that happens before the list scrolls.
 */
private object KeepFlingInSheetContent : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp, max = 80.dp).alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExtensionActionItem(action: ExtensionAction, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = 24.dp)
            .alpha(if (action.enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(action.icon, 24.dp)
        Spacer(Modifier.width(20.dp))
        Text(action.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action.badgeText?.let { badge ->
            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(badge, maxLines = 1) }
        }
    }
}

/** Per-site zoom: minus, the current level (tap to reset), plus. */
@Composable
private fun ZoomItem(percent: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(stringResource(R.string.menu_zoom), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(-1) }, enabled = percent > SiteRules.MIN_ZOOM) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
        }
        TextButton(onClick = { onChange(0) }) { Text(stringResource(R.string.site_zoom_value, percent)) }
        IconButton(onClick = { onChange(1) }, enabled = percent < SiteRules.MAX_ZOOM) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
        }
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
