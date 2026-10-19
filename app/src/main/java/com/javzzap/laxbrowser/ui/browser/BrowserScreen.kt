package com.javzzap.laxbrowser.ui.browser

import android.content.Context
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import com.javzzap.laxbrowser.ui.PipRatio
import android.content.res.Configuration
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.javzzap.laxbrowser.domain.TabFolder
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.javzzap.laxbrowser.domain.SiteRules
import com.javzzap.laxbrowser.platform.UpdateInfo
import com.javzzap.laxbrowser.ui.UiEvent
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import com.javzzap.laxbrowser.ui.onboarding.OnboardingScreen
import com.javzzap.laxbrowser.ui.settings.LicensesScreen
import com.javzzap.laxbrowser.ui.settings.SiteRulesScreen
import com.javzzap.laxbrowser.ui.logins.LoginsScreen
import com.javzzap.laxbrowser.ui.common.TextInputDialog
import com.javzzap.laxbrowser.ui.downloads.DownloadsScreen
import com.javzzap.laxbrowser.ui.prompts.WebPromptHost
import com.javzzap.laxbrowser.ui.sidebar.Suggestion
import com.javzzap.laxbrowser.ui.sitepermissions.SitePermissionsScreen
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.vector.ImageVector
import com.javzzap.laxbrowser.domain.ToolbarAction
import com.javzzap.laxbrowser.engine.AppLinkMode
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserSettings
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Container
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.ContextMenuTarget
import com.javzzap.laxbrowser.domain.Bookmark
import com.javzzap.laxbrowser.domain.BookmarkFolder
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.Shortcut
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.ToolbarPosition
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.pwa.WebAppActivity
import com.javzzap.laxbrowser.pwa.WebAppInstaller
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.FindState
import com.javzzap.laxbrowser.ui.bookmarks.BookmarkEditDialog
import com.javzzap.laxbrowser.ui.bookmarks.BookmarksActions
import com.javzzap.laxbrowser.ui.bookmarks.BookmarksScreen
import com.javzzap.laxbrowser.ui.common.ColorDot
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import com.javzzap.laxbrowser.ui.common.SiteIcon
import com.javzzap.laxbrowser.ui.gesture.SwipeDirection
import com.javzzap.laxbrowser.ui.gesture.ToolbarScroll
import com.javzzap.laxbrowser.ui.gesture.AddressSwipeDirection
import com.javzzap.laxbrowser.ui.gesture.addressSwipes
import com.javzzap.laxbrowser.domain.SwipeAction
import com.javzzap.laxbrowser.ui.KeyboardShortcut
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.javzzap.laxbrowser.ui.extensions.ExtensionPermissionDialog
import com.javzzap.laxbrowser.ui.extensions.ExtensionPopupSheet
import com.javzzap.laxbrowser.ui.extensions.ExtensionsScreen
import com.javzzap.laxbrowser.ui.history.HistoryScreen
import com.javzzap.laxbrowser.ui.theme.PrivateBrowsingTheme
import com.javzzap.laxbrowser.ui.theme.SpaceAccentTheme
import com.javzzap.laxbrowser.domain.HistoryRange
import com.javzzap.laxbrowser.ui.about.ChangelogScreen
import com.javzzap.laxbrowser.ui.about.WhatsNewDialog
import com.javzzap.laxbrowser.ui.ntp.NewTabPage
import com.javzzap.laxbrowser.ui.ntp.NewTabPageActions
import com.javzzap.laxbrowser.ui.settings.SettingsActions
import com.javzzap.laxbrowser.ui.settings.SettingsScreen
import com.javzzap.laxbrowser.ui.sidebar.Sidebar
import com.javzzap.laxbrowser.ui.sidebar.SidebarActions
import com.javzzap.laxbrowser.ui.spaces.MoveToSpaceDialog
import com.javzzap.laxbrowser.ui.spaces.NewSpaceDialog
import com.javzzap.laxbrowser.ui.spaces.SpacesActions
import com.javzzap.laxbrowser.ui.spaces.SpacesScreen
import com.javzzap.laxbrowser.ui.webapps.InstallDialog
import com.javzzap.laxbrowser.ui.webapps.WebAppsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { BROWSER, LOGINS, BOOKMARKS, HISTORY, SPACES, WEB_APPS, EXTENSIONS, DOWNLOADS, SITE_PERMISSIONS, SITE_RULES, LICENSES, CHANGELOG, SETTINGS }

private sealed interface BrowserDialog {
    data class ClosePinned(val tab: Tab) : BrowserDialog
    data class RemoveEssential(val tab: Tab) : BrowserDialog
    data class MoveToSpace(val tab: Tab) : BrowserDialog
    data class EditBookmark(val bookmarkId: String) : BrowserDialog
    data object NewSpace : BrowserDialog
    data object CloseAllPrivate : BrowserDialog
    data class EditPinnedUrl(val tab: Tab) : BrowserDialog
    data class MoveToFolder(val tab: Tab) : BrowserDialog
    data class NewFolder(val thenMove: Tab?) : BrowserDialog
    data class RenameFolder(val folder: TabFolder) : BrowserDialog
    data object SplitChooser : BrowserDialog
    data object Translate : BrowserDialog
    data object RecentlyClosed : BrowserDialog
    data class SiteSpace(val host: String) : BrowserDialog
}

/** Moves between app screens with a back stack, so going back returns to the previous screen. */
interface Navigator {
    fun open(screen: Screen)
    fun back()

    /** Returns to the browser, clearing the stack (for example after opening a bookmark). */
    fun home()
}

@Composable
fun BrowserApp(vm: BrowserViewModel, screen: Screen, navigator: Navigator) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.isLoaded) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (!state.data.settings.onboardingDone) {
        OnboardingScreen(vm, state, onDone = vm::finishOnboarding)
        // Extensions recommended during onboarding ask for their permissions here.
        val onboardingPermission by vm.extensionPermissionRequest.collectAsStateWithLifecycle()
        onboardingPermission?.let { ExtensionPermissionDialog(it) }
        return
    }
    BackHandler(enabled = screen != Screen.BROWSER) { navigator.back() }
    val context = LocalContext.current
    val controller = vm.controller
    val privateLocked by vm.privateTabsLocked.collectAsStateWithLifecycle()

    when (screen) {
        Screen.BROWSER -> PrivateBrowsingTheme(enabled = state.privateMode) {
            val accent = state.activeSpace?.color?.takeIf { !state.privateMode && state.data.settings.spaceAccent }
            Box(Modifier.fillMaxSize()) {
                SpaceAccentTheme(accent) { BrowserScreen(vm, state, navigator::open) }
                if (state.privateMode && privateLocked) {
                    PrivateTabsLock(onUnlocked = vm::unlockPrivateTabs, onLeave = { vm.setPrivateMode(false) })
                }
            }
        }
        Screen.LOGINS -> LoginsScreen(
            vm = vm,
            saveLogins = state.data.settings.saveLogins,
            onSaveLoginsChange = { enabled -> vm.report(controller.updateSettings { it.copy(saveLogins = enabled) }) },
            onBack = navigator::back,
        )
        Screen.SITE_RULES -> SiteRulesScreen(state, onSetSpace = vm::setSiteSpace, onRemove = vm::removeSiteSetting, onBack = navigator::back)
        Screen.LICENSES -> LicensesScreen(
            onOpenUrl = { url ->
                vm.openExtensionListing(url)
                navigator.home()
            },
            onBack = navigator::back,
        )
        Screen.CHANGELOG -> ChangelogScreen(onBack = navigator::back)
        Screen.BOOKMARKS -> BookmarksScreen(state, bookmarksActions(vm, navigator::home), navigator::back)
        Screen.HISTORY -> HistoryScreen(vm, onOpened = navigator::home, onBack = navigator::back)
        Screen.SPACES -> SpacesScreen(state, spacesActions(vm), onBack = navigator::back)
        Screen.WEB_APPS -> {
            val pinned by vm.pinnedWebAppIds.collectAsStateWithLifecycle()
            WebAppsScreen(
                state = state,
                pinnedIds = pinned,
                launcherSupported = vm.isLauncherSupported,
                loadIcon = vm::loadWebAppIcon,
                onRefresh = vm::refreshPinnedWebApps,
                onOpen = { app -> context.startActivity(webAppIntent(context, app)) },
                onRepin = vm::repinWebApp,
                onRemove = vm::uninstallWebApp,
                onBack = navigator::back,
            )
        }
        Screen.EXTENSIONS -> ExtensionsScreen(vm, onOpened = navigator::home, onBack = navigator::back)
        Screen.DOWNLOADS -> DownloadsScreen(vm, onBack = navigator::back)
        Screen.SITE_PERMISSIONS -> SitePermissionsScreen(vm, state, onBack = navigator::back)
        Screen.SETTINGS -> SettingsScreen(
            settings = state.data.settings,
            actions = object : SettingsActions {
                override fun update(transform: (BrowserSettings) -> BrowserSettings) = vm.report(controller.updateSettings(transform))
                override fun openSpaces() = navigator.open(Screen.SPACES)
                override fun openWebApps() = navigator.open(Screen.WEB_APPS)
                override fun openBookmarks() = navigator.open(Screen.BOOKMARKS)
                override fun openHistory() = navigator.open(Screen.HISTORY)
                override fun openExtensions() = navigator.open(Screen.EXTENSIONS)
                override fun openDownloads() = navigator.open(Screen.DOWNLOADS)
                override fun openSitePermissions() = navigator.open(Screen.SITE_PERMISSIONS)
                override fun openSiteRules() = navigator.open(Screen.SITE_RULES)
                override fun openLogins() = navigator.open(Screen.LOGINS)
                override var appLinkMode: AppLinkMode
                    get() = vm.engine.appLinks.mode
                    set(value) { vm.engine.appLinks.mode = value }
                override fun rememberedAppLinkCount() = vm.engine.appLinks.rememberedCount()
                override fun clearAppLinkChoices() = vm.engine.appLinks.clearChoices()
                override fun openSupportPage() {
                    // No package: Android opens the user's default browser (Lax itself when it is the default).
                    val intent = Intent(Intent.ACTION_VIEW, SUPPORT_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                    runCatching { context.startActivity(intent) }
                }
                override fun openLicenses() = navigator.open(Screen.LICENSES)
                override fun openChangelog() = navigator.open(Screen.CHANGELOG)
                override fun exportBookmarks(uri: Uri) = vm.exportBookmarks(uri)
                override fun importBookmarks(uri: Uri) = vm.importBookmarks(uri)
                override fun exportBackup(uri: Uri, loginsPassword: CharArray?) = vm.exportBackup(uri, loginsPassword)
                override fun restoreBackup(uri: Uri) = vm.restoreBackup(uri)
                override fun connectNextcloud(server: String) = vm.connectNextcloud(server)
                override fun connectNextcloudWithPassword(server: String, user: String, appPassword: String) =
                    vm.connectNextcloudWithPassword(server, user, appPassword)
                override fun cancelNextcloudLogin() = vm.cancelNextcloudLogin()
                override fun disconnectNextcloud() = vm.disconnectNextcloud()
                override fun setNextcloudAutoBackup(enabled: Boolean) = vm.setNextcloudAutoBackup(enabled)
                override fun setNextcloudLoginsPassword(password: String?) = vm.setNextcloudLoginsPassword(password)
                override fun backUpToNextcloud() = vm.backUpToNextcloud()
                override fun restoreFromNextcloud() = vm.restoreFromNextcloud()
                override val canCheckForUpdates: Boolean get() = vm.canCheckForUpdates
                override fun checkForUpdates() = vm.checkForUpdatesNow()
                override suspend fun crashLogs() = vm.crashLogs()
                override fun shareCrash(text: String) = vm.shareCrash(text)
                override fun clearCrashLogs() = vm.clearCrashLogs()
                override fun clearHistory(range: HistoryRange) = vm.clearHistory(range)
                override fun clearBrowsingData(range: HistoryRange?, cookies: Boolean, cache: Boolean, permissions: Boolean) =
                    vm.clearBrowsingData(range, cookies, cache, permissions)
            },
            onBack = navigator::back,
            linkSpaces = state.sortedSpaces.map { it to state.container(it.containerId)?.name },
            newerVersion = vm.newerVersion.collectAsStateWithLifecycle().value,
            nextcloud = vm.nextcloud.collectAsStateWithLifecycle().value,
        )
    }

    // Web pages and extensions can show dialogs from any screen; locked private tabs wait.
    WebPromptHost(
        vm.engine.prompts.prompts,
        vm.engine.permissions.requests,
        accepts = { id -> vm.state.value.tab(id)?.let { tab -> !(tab.isPrivate && privateLocked) } == true },
    )
    val permissionRequest by vm.extensionPermissionRequest.collectAsStateWithLifecycle()
    val popup by vm.extensionPopup.collectAsStateWithLifecycle()
    permissionRequest?.let { ExtensionPermissionDialog(it) }
    popup?.let { ExtensionPopupSheet(it, onDismiss = vm::dismissExtensionPopup) }
    val appLink by vm.engine.appLinkRequest.collectAsStateWithLifecycle()
    appLink?.let { request ->
        AppLinkDialog(request.app.label, request.isPrivate, onAnswer = vm.engine::answerAppLink)
    }
    val pendingLink by vm.pendingLink.collectAsStateWithLifecycle()
    pendingLink?.let { url ->
        OpenLinkInDialog(
            url = url,
            state = state,
            onChoose = { target ->
                vm.openPendingLinkIn(target)
                navigator.home()
            },
            onDismiss = vm::cancelPendingLink,
        )
    }
    val update by vm.availableUpdate.collectAsStateWithLifecycle()
    update?.let { UpdateDialog(it, onDownload = vm::installUpdate, onLater = { vm.dismissUpdate(false) }, onSkip = { vm.dismissUpdate(true) }) }
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()
    updateProgress?.let { progress ->
        UpdateProgressDialog(
            progress = progress,
            canInstall = vm::canInstallUpdates,
            onInstall = vm::continueUpdate,
            onOpenPage = vm::openUpdatePage,
            onClose = vm::closeUpdate,
        )
    }
    val whatsNew by vm.showWhatsNew.collectAsStateWithLifecycle()
    if (whatsNew) WhatsNewDialog(onSeeAll = { navigator.open(Screen.CHANGELOG) }, onDismiss = vm::dismissWhatsNew)
    val loginRestore by vm.pendingLoginRestore.collectAsStateWithLifecycle()
    loginRestore?.let { pending ->
        RestoreLoginsDialog(pending.count, onRestore = vm::restoreLogins, onSkip = vm::skipLoginRestore)
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_available_title, info.version)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.update_available_body))
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(info.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(if (info.apkUrl != null) R.string.update_install else R.string.update_open_page))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

private fun webAppIntent(context: Context, app: WebApp) =
    Intent(context, WebAppActivity::class.java).setAction(Intent.ACTION_VIEW).setData(WebAppInstaller.launchUri(app.id))

private fun shareUrl(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)))
}

private fun bookmarksActions(vm: BrowserViewModel, onOpened: () -> Unit) = object : BookmarksActions {
    private val c = vm.controller
    override fun open(bookmark: Bookmark) {
        if (vm.report(c.openBookmark(bookmark.id))) onOpened()
    }
    override fun update(bookmark: Bookmark, title: String, url: String, folderId: String?) =
        vm.report(c.updateBookmark(bookmark.id, title, url, folderId))
    override fun remove(bookmark: Bookmark) { vm.report(c.removeBookmark(bookmark.id), R.string.msg_bookmark_deleted) }
    override fun moveBookmark(bookmark: Bookmark, folderId: String?) { vm.report(c.moveBookmarkToFolder(bookmark.id, folderId)) }
    override fun createFolder(name: String, parentId: String?) { vm.report(c.createBookmarkFolder(name, parentId)) }
    override fun renameFolder(folder: BookmarkFolder, name: String) { vm.report(c.renameBookmarkFolder(folder.id, name)) }
    override fun moveFolder(folder: BookmarkFolder, parentId: String?) { vm.report(c.moveBookmarkFolder(folder.id, parentId)) }
    override fun deleteFolder(folder: BookmarkFolder) { vm.report(c.deleteBookmarkFolder(folder.id), R.string.msg_folder_deleted) }
    override fun folderContents(folder: BookmarkFolder) = c.folderContents(folder.id)
}

private fun spacesActions(vm: BrowserViewModel) = object : SpacesActions {
    private val c = vm.controller
    override fun createSpace(name: String, color: Int, choice: ContainerChoice) = vm.report(c.createSpace(name, color, choice))
    override fun renameSpace(space: Space, name: String) { vm.report(c.renameSpace(space.id, name)) }
    override fun setSpaceColor(space: Space, color: Int) { vm.report(c.setSpaceColor(space.id, color)) }
    override fun setSpaceEmoji(space: Space, emoji: String?) { vm.setSpaceEmoji(space, emoji) }
    override fun moveSpace(space: Space, delta: Int) { vm.report(c.moveSpace(space.id, delta)) }
    override fun deleteSpace(space: Space) { vm.report(c.deleteSpace(space.id), R.string.msg_space_deleted, space.name) }
    override fun changeSpaceContainer(space: Space, choice: ContainerChoice) {
        val containerId = when (choice) {
            is ContainerChoice.Existing -> choice.containerId
            is ContainerChoice.New -> (c.createContainer(choice.name, choice.color).outcome as? Outcome.Created)?.id ?: return
        }
        vm.report(c.setSpaceContainer(space.id, containerId), R.string.msg_space_container_changed, space.name)
    }
    override fun createContainer(name: String, color: Int) { vm.report(c.createContainer(name, color)) }
    override fun renameContainer(container: Container, name: String) { vm.report(c.renameContainer(container.id, name, null)) }
    override fun containerUsage(container: Container) = c.containerUsage(container.id)
    override fun deleteContainer(container: Container) {
        vm.report(c.deleteContainer(container.id), R.string.msg_container_deleted, container.name)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserScreen(vm: BrowserViewModel, state: BrowserState, onScreenChange: (Screen) -> Unit) {
    val controller = vm.controller
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var focusUrlBar by remember { mutableStateOf(false) }
    val installCandidate by vm.installCandidate.collectAsStateWithLifecycle()
    val contextMenu by vm.contextMenu.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val recentlyClosed by vm.recentlyClosed.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val fullScreenTabId by vm.engine.fullScreenTabId.collectAsStateWithLifecycle()
    val glance by vm.engine.glance.collectAsStateWithLifecycle()
    val mediaState by vm.engine.media.state.collectAsStateWithLifecycle()
    val pictureInPicture by vm.pictureInPicture.collectAsStateWithLifecycle()

    val settings = state.data.settings
    val activeTab = state.activeTab
    val runtime = activeTab?.let { state.runtime(it.id) }
    val webTab = activeTab?.takeIf { it.kind == TabKind.WEB && runtime?.loaded == true && !state.isBlocked(it) }
    val atTop = settings.toolbarPosition == ToolbarPosition.TOP
    val fullScreen = fullScreenTabId != null && fullScreenTabId == activeTab?.id

    // Voice search uses the system speech recognizer; QR codes are read with the camera.
    val voiceAvailable = remember { context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank() && vm.report(controller.navigate(controller.state.value.activeTab?.id, text))) {
            scope.launch { drawerState.close() }
        }
    }
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents?.trim()
        if (!text.isNullOrBlank()) {
            vm.report(controller.navigate(controller.state.value.activeTab?.id, text))
            scope.launch { drawerState.close() }
        }
    }

    val resources = LocalResources.current
    LaunchedEffect(resources) {
        vm.messages.collect { message ->
            val text = resources.getString(message.text, *message.args.toTypedArray())
            val action = message.actionLabel?.let { resources.getString(it) }
            val result = snackbar.showSnackbar(text, action, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }

    // Dynamic toolbar: follows the page scroll and settles fully shown or hidden.
    var toolbarAreaPx by remember { mutableIntStateOf(0) }
    val hidden = remember { Animatable(0f) }
    val toolbarScroll = remember { ToolbarScroll() }
    var scrollTick by remember { mutableIntStateOf(0) }
    val canHide = settings.hideToolbarOnScroll && webTab != null && find == null && state.splitTab == null && !fullScreen
    val scrollingTabId = webTab?.id
    DisposableEffect(vm.engine, scrollingTabId, canHide, toolbarAreaPx) {
        toolbarScroll.reset()
        vm.engine.scrollListener = { tabId, scrollY ->
            if (canHide && tabId == scrollingTabId) {
                val next = toolbarScroll.onScroll(scrollY, hidden.value, toolbarAreaPx.toFloat())
                scope.launch { hidden.snapTo(next) }
                scrollTick++
            }
        }
        onDispose { vm.engine.scrollListener = null }
    }
    LaunchedEffect(scrollTick) {
        if (scrollTick == 0) return@LaunchedEffect
        delay(SNAP_DELAY_MS)
        hidden.animateTo(ToolbarScroll.snapTarget(hidden.value, toolbarAreaPx.toFloat()))
    }
    LaunchedEffect(activeTab?.id, activeTab?.url, canHide, settings.toolbarPosition) {
        hidden.animateTo(0f)
    }

    // The system navigation bar hides together with a fully hidden toolbar. A swipe from the
    // screen edge still reveals it temporarily, and edge gestures keep working.
    val activity = LocalActivity.current
    val hideSystemNavigation by remember(canHide, toolbarAreaPx) {
        derivedStateOf { canHide && toolbarAreaPx > 0 && hidden.value >= toolbarAreaPx - 1f }
    }
    LaunchedEffect(activity, hideSystemNavigation, fullScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when {
            fullScreen -> controller.hide(WindowInsetsCompat.Type.systemBars())
            hideSystemNavigation -> {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
            else -> controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // A fullscreen landscape video turns the screen sideways, like other browsers.
    val fullscreenVideo = mediaState?.fullscreenVideo?.takeIf { fullScreen }
    DisposableEffect(activity, fullscreenVideo) {
        val landscape = fullscreenVideo != null && fullscreenVideo.first > fullscreenVideo.second
        if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Android 12+ moves a fullscreen video into picture-in-picture by itself when the user goes home.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val params = PictureInPictureParams.Builder().setAutoEnterEnabled(fullscreenVideo != null)
            fullscreenVideo?.let { (w, h) -> params.setAspectRatio(PipRatio.of(w, h)) }
            runCatching { activity?.setPictureInPictureParams(params.build()) }
        }
        onDispose { if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(activity, systemDark) {
        onDispose {
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    show(WindowInsetsCompat.Type.navigationBars())
                    isAppearanceLightStatusBars = !systemDark
                    isAppearanceLightNavigationBars = !systemDark
                }
            }
        }
    }

    // Private browsing: optional screenshot blocking and a blank recent-apps preview, plus light
    // system bar icons on the dark private theme. Android 12 and older can only hide the preview
    // with FLAG_SECURE, which also blocks screenshots.
    LaunchedEffect(activity, state.privateMode, settings.privateBlockScreenshots, settings.privateHideInRecents, systemDark) {
        val window = activity?.window ?: return@LaunchedEffect
        val private = state.privateMode
        val canHidePreviewOnly = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val secure = private && (settings.privateBlockScreenshots || (settings.privateHideInRecents && !canHidePreviewOnly))
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (canHidePreviewOnly) activity.setRecentsScreenshotEnabled(!(private && settings.privateHideInRecents))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !private && !systemDark
            isAppearanceLightNavigationBars = !private && !systemDark
        }
    }

    fun closeDrawer() = scope.launch { drawerState.close() }

    // A closed sidebar must not keep the address bar focused (with its suggestions) for next time.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isClosed }.drop(1).filter { it }.collect { focusManager.clearFocus() }
    }

    BackHandler(enabled = find != null) { vm.closeFind() }
    BackHandler(enabled = find == null && drawerState.isOpen) { closeDrawer() }
    BackHandler(enabled = find == null && !drawerState.isOpen && activeTab != null && runtime?.canGoBack == true) {
        activeTab?.let { controller.goBack(it.id) }
    }
    BackHandler(enabled = glance != null) { if (glance?.canGoBack == true) vm.engine.glanceBack() else vm.engine.closeGlance() }
    BackHandler(enabled = fullScreen) { vm.engine.exitFullScreen() }

    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current

    /** Hides the keyboard right away (not after the sidebar slides out), also when it stays docked. */
    fun leaveAddressBar() {
        keyboard?.hide()
        focusManager.clearFocus()
        closeDrawer()
    }
    val sidebarActions = remember(vm, haptics, keyboard, focusManager) {
        object : SidebarActions {
            override fun submitUrl(text: String) {
                // A rejected input keeps the keyboard so it can be corrected.
                if (vm.report(controller.navigate(controller.state.value.activeTab?.id, text))) leaveAddressBar()
            }
            override fun newTab() {
                if (vm.report(controller.openNewTabPage())) closeDrawer()
            }
            override fun selectTab(tab: Tab) {
                if (vm.report(controller.selectTab(tab.id))) closeDrawer()
            }
            override fun closeTab(tab: Tab) {
                if (tab.isPinned) dialog = BrowserDialog.ClosePinned(tab) else vm.closeTab(tab)
            }
            override fun unloadTab(tab: Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                vm.unloadTab(tab)
            }
            override fun pinTab(tab: Tab) {
                if (vm.report(controller.pinTab(tab.id))) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            }
            override fun unpinTab(tab: Tab) {
                if (vm.report(controller.unpinTab(tab.id))) haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
            }
            override fun addToEssentials(tab: Tab) {
                if (vm.report(controller.addToEssentials(tab.id), R.string.msg_added_to_essentials)) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            override fun removeEssential(tab: Tab) { dialog = BrowserDialog.RemoveEssential(tab) }
            override fun moveTab(tab: Tab, delta: Int) { vm.report(controller.moveTab(tab.id, delta)) }
            override fun moveTabToSpace(tab: Tab) { dialog = BrowserDialog.MoveToSpace(tab) }
            override fun bookmarkTab(tab: Tab) = vm.bookmarkTab(tab)
            override fun switchSpace(space: Space) {
                if (space.id != controller.state.value.activeSpace?.id && vm.report(controller.switchSpace(space.id))) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
            override fun newSpace() { dialog = BrowserDialog.NewSpace }
            override fun manageSpaces() {
                closeDrawer()
                onScreenChange(Screen.SPACES)
            }
            override fun setPrivateMode(enabled: Boolean) = vm.setPrivateMode(enabled)
            override fun closeAllPrivateTabs() {
                dialog = BrowserDialog.CloseAllPrivate
            }
            override fun queryChanged(text: String) = vm.updateQuery(text)
            override fun openSuggestion(suggestion: Suggestion) {
                if (vm.openSuggestion(suggestion)) leaveAddressBar()
            }
            override fun voiceSearch() {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                runCatching { voiceLauncher.launch(intent) }
            }
            override fun scanQrCode() {
                qrLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setOrientationLocked(false)
                        .setBeepEnabled(false)
                        .setPrompt(resources.getString(R.string.scan_qr_code)),
                )
            }
            override fun openInSplit(tab: Tab) {
                if (vm.openInSplit(tab)) closeDrawer()
            }
            override fun resetPinnedTab(tab: Tab) {
                if (vm.resetPinnedTab(tab)) closeDrawer()
            }
            override fun replacePinnedUrl(tab: Tab) { vm.setPinnedUrl(tab, null) }
            override fun editPinnedUrl(tab: Tab) { dialog = BrowserDialog.EditPinnedUrl(tab) }
            override fun moveTabToFolder(tab: Tab) {
                dialog = if (controller.state.value.tabFolders(tab.spaceId).isEmpty()) BrowserDialog.NewFolder(tab) else BrowserDialog.MoveToFolder(tab)
            }
            override fun newFolder() { dialog = BrowserDialog.NewFolder(null) }
            override fun renameFolder(folder: TabFolder) { dialog = BrowserDialog.RenameFolder(folder) }
            override fun deleteFolder(folder: TabFolder) { vm.deleteTabFolder(folder) }
            override fun toggleFolder(folder: TabFolder) { vm.toggleTabFolder(folder) }
            override fun moveFolder(folder: TabFolder, delta: Int) { vm.moveTabFolder(folder, delta) }
            override fun switchSpaceBy(delta: Int) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                vm.switchSpaceBy(delta)
            }
        }
    }

    // Fullscreen video and picture-in-picture show only the page.
    if (fullScreen || pictureInPicture) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            activeTab?.takeIf { it.kind == TabKind.WEB }?.let { tab ->
                WebContent(
                    engine = vm.engine,
                    tabId = tab.id,
                    canGoBack = false,
                    canGoForward = false,
                    pullToRefresh = false,
                    toolbarAtTop = true,
                    toolbarMaxPx = 0,
                    toolbarHiddenPx = { 0f },
                    onSwipeNavigate = {},
                    onRefresh = {},
                )
            }
        }
        return
    }

    // Landscape: the sidebar is a panel beside the page that the sidebar button shows and hides;
    // it starts hidden and stays open until toggled (tapping the page does not close it).
    // Wide portrait screens (tablets) keep it docked; phones in portrait use the sliding drawer.
    val configuration = LocalConfiguration.current
    val panelMode = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val docked = !panelMode && configuration.screenWidthDp >= DOCKED_SIDEBAR_MIN_WIDTH_DP
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val openSidebar: () -> Unit = {
        when {
            panelMode -> panelOpen = true
            !docked -> scope.launch { drawerState.open() }
        }
    }
    val toggleSidebar: () -> Unit = { if (panelMode) panelOpen = !panelOpen else openSidebar() }
    BackHandler(enabled = panelMode && panelOpen && find == null) { panelOpen = false }
    LaunchedEffect(Unit) {
        snapshotFlow { panelOpen }.drop(1).filter { !it }.collect { focusManager.clearFocus() }
    }
    val focusAddressBar: () -> Unit = {
        focusUrlBar = true
        openSidebar()
    }
    LaunchedEffect(vm, docked) {
        vm.uiEvents.collect { event ->
            when (event) {
                UiEvent.FOCUS_ADDRESS -> focusAddressBar()
            }
        }
    }
    val sidebar: @Composable () -> Unit = {
        Sidebar(
            state = state,
            actions = sidebarActions,
            focusUrlBar = focusUrlBar,
            onUrlBarFocused = { focusUrlBar = false },
            suggestions = suggestions,
            voiceAvailable = voiceAvailable,
        )
    }
    val pageArea: @Composable () -> Unit = {
        // The status bar keeps the toolbar color; the page runs behind the transparent navigation
        // bar. Content is clipped so a hidden toolbar slides completely out of view.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .clipToBounds(),
        ) {
            val density = LocalDensity.current
            val toolbarAreaDp = with(density) { toolbarAreaPx.toDp() }
            val navigationBarDp = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp() }
            // A Surface (not just a background) so text on these pages follows the dark theme.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Content(
                    vm = vm,
                    state = state,
                    tab = activeTab,
                    atTop = atTop,
                    toolbarAreaPx = toolbarAreaPx,
                    toolbarHiddenPx = { hidden.value },
                    staticPadding = if (atTop) {
                        PaddingValues(top = toolbarAreaDp, bottom = navigationBarDp)
                    } else {
                        PaddingValues(bottom = toolbarAreaDp)
                    },
                    onFocusAddressBar = focusAddressBar,
                )
            }
            FloatingToolbar(
                state = state,
                vm = vm,
                find = find,
                canReopen = recentlyClosed.isNotEmpty(),
                onOpenSidebar = toggleSidebar.takeUnless { docked },
                onAddressClick = focusAddressBar,
                onScreenChange = onScreenChange,
                onEditBookmark = { dialog = BrowserDialog.EditBookmark(it) },
                onMenuDialog = { which ->
                    dialog = when (which) {
                        MenuDialog.SPLIT -> BrowserDialog.SplitChooser
                        MenuDialog.TRANSLATE -> BrowserDialog.Translate
                        MenuDialog.RECENTLY_CLOSED -> BrowserDialog.RecentlyClosed
                        MenuDialog.SITE_SPACE -> activeTab?.let { tab -> SiteRules.hostKey(tab.url)?.let { BrowserDialog.SiteSpace(it) } }
                    }
                },
                modifier = Modifier
                    .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .then(if (atTop) Modifier else Modifier.imePadding())
                    .onSizeChanged { toolbarAreaPx = it.height }
                    .offset { IntOffset(0, (if (atTop) -hidden.value else hidden.value).toInt()) }
                    // Measured as part of the toolbar area, so hiding moves it past the navigation bar too.
                    // Uses the bar's height even while it is hidden, so the toolbar area stays stable.
                    .then(if (atTop) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)),
            )
            glance?.let { GlanceOverlay(it, vm) }
        }
    }

    val onRight = settings.sidebarOnRight
    val dockedSidebar: @Composable () -> Unit = {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .width(DRAWER_WIDTH)
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Vertical + if (onRight) WindowInsetsSides.Right else WindowInsetsSides.Left),
                )
                .testTag("sidebar"),
        ) { sidebar() }
    }
    Box(Modifier.fillMaxSize()) {
        if (docked || panelMode) {
            Row(Modifier.fillMaxSize()) {
                val side: @Composable () -> Unit = {
                    if (docked) {
                        dockedSidebar()
                    } else {
                        AnimatedVisibility(
                            visible = panelOpen,
                            enter = expandHorizontally(expandFrom = if (onRight) Alignment.Start else Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = if (onRight) Alignment.Start else Alignment.End) + fadeOut(),
                        ) { dockedSidebar() }
                    }
                }
                if (!onRight) side()
                Box(Modifier.weight(1f).fillMaxHeight()) { pageArea() }
                if (onRight) side()
            }
        } else {
            // The drawer always slides in from the layout's start edge, so a right-hand sidebar
            // runs it in right-to-left layout and restores the real direction for its contents.
            val direction = LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides if (onRight) LayoutDirection.Rtl else direction) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    // Opening is button-only so the screen edges stay free for system back and history swipes.
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        ModalDrawerSheet(Modifier.width(DRAWER_WIDTH).testTag("sidebar")) {
                            CompositionLocalProvider(LocalLayoutDirection provides direction) { sidebar() }
                        }
                    },
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        // The page eases away from the opening sidebar. Translation only: Gecko draws
                        // into a SurfaceView, which follows moves but not scaling.
                        val density = LocalDensity.current
                        val sheetPx = with(density) { DRAWER_WIDTH.toPx() }
                        val shiftPx = with(density) { PAGE_SHIFT.toPx() }
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val offset = drawerState.currentOffset
                                val progress = if (offset.isNaN()) 0f else (1f + offset / sheetPx).coerceIn(0f, 1f)
                                translationX = progress * shiftPx * if (onRight) -1f else 1f
                            },
                        ) { pageArea() }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            snackbar = { data -> SwipeableSnackbar(data) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (atTop) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    } else {
                        Modifier.offset { IntOffset(0, -(toolbarAreaPx - hidden.value).coerceAtLeast(0f).toInt()) }
                    },
                ),
        )
    }

    installCandidate?.let { InstallDialog(it, onConfirm = vm::confirmInstall, onDismiss = vm::cancelInstall) }
    contextMenu?.let { target ->
        ContextMenuDialog(
            target = target,
            onOpen = { url, background -> vm.openLink(url, background) },
            onCopy = vm::copyToClipboard,
            onShare = { shareUrl(context, it) },
            onGlance = vm::openGlance,
            onOpenIn = vm::askWhereToOpen,
            onSplit = { vm.openLinkInSplit(it) },
            onSave = vm::saveLink,
            onDismiss = controller::dismissContextMenu,
        )
    }

    when (val d = dialog) {
        null -> Unit
        is BrowserDialog.ClosePinned -> ConfirmDialog(
            title = stringResource(R.string.close_pinned_title),
            message = stringResource(R.string.close_pinned_message, d.tab.title.ifBlank { d.tab.url }),
            confirmLabel = stringResource(R.string.close_tab),
            onConfirm = { vm.closeTab(d.tab) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.RemoveEssential -> ConfirmDialog(
            title = stringResource(R.string.remove_essential_title),
            message = stringResource(R.string.remove_essential_message, d.tab.title.ifBlank { d.tab.url }),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = { vm.report(controller.removeEssential(d.tab.id)) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.MoveToSpace -> MoveToSpaceDialog(
            state = state,
            tab = d.tab,
            onMove = { space -> vm.report(controller.moveTabToSpace(d.tab.id, space.id), R.string.msg_moved_to_space, space.name) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.EditBookmark -> {
            val bookmark = state.data.bookmarks.firstOrNull { it.id == d.bookmarkId }
            if (bookmark != null) {
                BookmarkEditDialog(
                    state = state,
                    bookmark = bookmark,
                    onSave = { title, url, folderId -> vm.report(controller.updateBookmark(bookmark.id, title, url, folderId)) },
                    onDismiss = { dialog = null },
                )
            } else {
                LaunchedEffect(d) { dialog = null }
            }
        }
        BrowserDialog.NewSpace -> NewSpaceDialog(
            state = state,
            onCreate = { name, color, choice -> vm.report(controller.createSpace(name, color, choice)) },
            onDismiss = { dialog = null },
        )
        BrowserDialog.CloseAllPrivate -> ConfirmDialog(
            title = stringResource(R.string.close_all_private_title),
            message = pluralStringResource(R.plurals.close_all_private_message, state.privateTabs.size, state.privateTabs.size),
            confirmLabel = stringResource(R.string.close_all_private_tabs),
            onConfirm = vm::closeAllPrivateTabs,
            onDismiss = { dialog = null },
        )
        is BrowserDialog.EditPinnedUrl -> TextInputDialog(
            title = stringResource(R.string.edit_pinned_url_title),
            label = stringResource(R.string.link_address),
            initial = d.tab.pinnedUrl ?: d.tab.url,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { vm.setPinnedUrl(d.tab, it) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.MoveToFolder -> FolderChooserDialog(
            folders = state.tabFolders(d.tab.spaceId),
            current = d.tab.folderId.takeIf { d.tab.isPinned },
            onChoose = { folderId -> vm.moveTabToFolder(d.tab, folderId) },
            onNewFolder = { dialog = BrowserDialog.NewFolder(d.tab) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.NewFolder -> TextInputDialog(
            title = stringResource(R.string.new_tab_folder),
            label = stringResource(R.string.folder_name),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { vm.createTabFolder(it, d.thenMove) },
            onDismiss = { dialog = null },
        )
        is BrowserDialog.RenameFolder -> TextInputDialog(
            title = stringResource(R.string.rename_folder),
            label = stringResource(R.string.folder_name),
            initial = d.folder.name,
            confirmLabel = stringResource(R.string.rename),
            onConfirm = { vm.renameTabFolder(d.folder, it) },
            onDismiss = { dialog = null },
        )
        BrowserDialog.SplitChooser -> SplitChooserDialog(state, onChoose = { vm.openInSplit(it) }, onDismiss = { dialog = null })
        BrowserDialog.Translate -> TranslateDialog(vm, state, onDismiss = { dialog = null })
        is BrowserDialog.SiteSpace -> SiteSpaceDialog(
            host = d.host,
            state = state,
            onChoose = { space -> vm.setSiteSpace(d.host, space) },
            onDismiss = { dialog = null },
        )
        BrowserDialog.RecentlyClosed -> RecentlyClosedDialog(
            closed = recentlyClosed,
            onReopen = { vm.reopen(it) },
            onDismiss = { dialog = null },
        )
    }
}

/** A message at the bottom that can be swiped away to either side. */
@Composable
private fun SwipeableSnackbar(data: SnackbarData) {
    key(data) {
        val dismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
        }
        SwipeToDismissBox(state = dismissState, backgroundContent = {}) { Snackbar(data) }
    }
}

@Composable
private fun FloatingToolbar(
    state: BrowserState,
    vm: BrowserViewModel,
    find: FindState?,
    canReopen: Boolean,
    onOpenSidebar: (() -> Unit)?,
    onAddressClick: () -> Unit,
    onScreenChange: (Screen) -> Unit,
    onEditBookmark: (String) -> Unit,
    onMenuDialog: (MenuDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = state.activeTab
    val runtime = tab?.let { state.runtime(it.id) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().testTag("toolbar"),
    ) {
        Column {
            if (find != null) {
                FindBar(find, vm)
            } else {
                AddressRow(state, vm, canReopen, onOpenSidebar, onAddressClick, onScreenChange, onEditBookmark, onMenuDialog)
            }
            if (runtime?.isLoading == true && runtime.loaded) {
                LinearProgressIndicator(progress = { runtime.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
    }
}

@Composable
private fun AddressRow(
    state: BrowserState,
    vm: BrowserViewModel,
    canReopen: Boolean,
    onOpenSidebar: (() -> Unit)?,
    onAddressClick: () -> Unit,
    onScreenChange: (Screen) -> Unit,
    onEditBookmark: (String) -> Unit,
    onMenuDialog: (MenuDialog) -> Unit,
) {
    val context = LocalContext.current
    val tab = state.activeTab
    val runtime = tab?.let { state.runtime(it.id) }
    val webTab = tab?.takeIf { it.kind == TabKind.WEB }
    var menu by remember { mutableStateOf(false) }
    val space = state.activeSpace
    val addressDescription = stringResource(R.string.address_cd, webTab?.url ?: stringResource(R.string.address_new_tab))

    val settings = state.data.settings
    val sidebarButton: @Composable () -> Unit = {
        // Hidden when the sidebar is docked next to the page.
        if (onOpenSidebar != null) {
            IconButton(onClick = onOpenSidebar, modifier = Modifier.testTag("open_sidebar")) {
                Box {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_sidebar))
                    if (state.privateMode) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.private_browsing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).size(10.dp),
                        )
                    } else {
                        // Always the Space's color: an emoji would be too big to read at this size.
                        space?.let { ColorDot(it.color, Modifier.align(Alignment.TopEnd), size = 8.dp) }
                    }
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
    val menuButton: @Composable () -> Unit = {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("browser_menu")) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.browser_menu))
        }
    }

    // Swipes on the address run the actions chosen in Settings > Toolbar & gestures.
    val haptics = LocalHapticFeedback.current
    val swipeActions = mapOf(
        AddressSwipeDirection.LEFT to settings.swipeLeft,
        AddressSwipeDirection.RIGHT to settings.swipeRight,
        AddressSwipeDirection.UP to settings.swipeUp,
        AddressSwipeDirection.DOWN to settings.swipeDown,
    )
    val perform by rememberUpdatedState { action: SwipeAction ->
        when (action) {
            SwipeAction.NONE -> Unit
            SwipeAction.PREVIOUS_TAB -> vm.onShortcut(KeyboardShortcut.PREVIOUS_TAB)
            SwipeAction.NEXT_TAB -> vm.onShortcut(KeyboardShortcut.NEXT_TAB)
            SwipeAction.PREVIOUS_SPACE -> vm.onShortcut(KeyboardShortcut.PREVIOUS_SPACE)
            SwipeAction.NEXT_SPACE -> vm.onShortcut(KeyboardShortcut.NEXT_SPACE)
            SwipeAction.OPEN_SIDEBAR -> onOpenSidebar?.invoke()
            SwipeAction.OPEN_MENU -> menu = true
            SwipeAction.SPLIT_VIEW -> if (state.splitTab != null) vm.closeSplit() else if (tab != null) onMenuDialog(MenuDialog.SPLIT)
            SwipeAction.NEW_TAB -> vm.onShortcut(KeyboardShortcut.NEW_TAB)
            SwipeAction.CLOSE_TAB -> vm.onShortcut(KeyboardShortcut.CLOSE_TAB)
            SwipeAction.RELOAD -> vm.onShortcut(KeyboardShortcut.RELOAD)
            SwipeAction.EDIT_ADDRESS -> onAddressClick()
        }
    }
    val currentSwipes by rememberUpdatedState(swipeActions)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val onSwipe = remember {
        { direction: AddressSwipeDirection ->
            val action = currentSwipes.getValue(direction)
            if (action != SwipeAction.NONE) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                perform(action)
            }
        }
    }
    val onDrag = remember { { offset: Offset -> dragOffset = offset } }
    val nudge by animateOffsetAsState(dragOffset, label = "address swipe")
    val swipeLabels = swipeActions.values.filter { it != SwipeAction.NONE }.distinct().map { it to stringResource(it.label) }

    Row(Modifier.height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (settings.swapToolbarButtons) menuButton() else sidebarButton()
        Row(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    val limit = SWIPE_NUDGE.toPx()
                    translationX = (nudge.x * 0.25f).coerceIn(-limit, limit)
                    translationY = (nudge.y * 0.15f).coerceIn(-limit / 2, limit / 2)
                }
                .addressSwipes(enabled = swipeLabels.isNotEmpty(), onDrag = onDrag, onSwipe = onSwipe)
                .clickable(onClickLabel = stringResource(R.string.edit_address)) { onAddressClick() }
                .semantics(mergeDescendants = true) {
                    contentDescription = addressDescription
                    customActions = swipeLabels.map { (action, label) -> CustomAccessibilityAction(label) { perform(action); true } }
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (webTab != null) {
                SiteIcon(webTab.url, webTab.title, webTab.faviconUrl, size = 20.dp, ephemeral = webTab.isPrivate)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (webTab != null && runtime?.isSecure == true) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.secure_connection), modifier = Modifier.height(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = webTab?.let { UrlInput.displayHost(it.url) } ?: stringResource(R.string.url_bar_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (webTab != null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (webTab != null && webTab.title.isNotBlank()) {
                    Text(webTab.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ToolbarActionButton(settings.toolbarAction, state, vm, onScreenChange, onEditBookmark, onMenuDialog)
        if (settings.swapToolbarButtons) sidebarButton() else menuButton()
    }
    if (menu) {
        BrowserMenuSheet(
            state = state,
            vm = vm,
            canReopen = canReopen,
            onShare = { shareUrl(context, it) },
            onScreenChange = onScreenChange,
            onEditBookmark = onEditBookmark,
            onMenuDialog = onMenuDialog,
            onDismiss = { menu = false },
        )
    }
}

/**
 * The toolbar button chosen in settings. Page actions are disabled (not hidden) when they do not
 * apply, so the address keeps the same width; reload turns into stop while a page loads.
 */
@Composable
private fun ToolbarActionButton(
    action: ToolbarAction,
    state: BrowserState,
    vm: BrowserViewModel,
    onScreenChange: (Screen) -> Unit,
    onEditBookmark: (String) -> Unit,
    onMenuDialog: (MenuDialog) -> Unit,
) {
    val controller = vm.controller
    val context = LocalContext.current
    val translations by vm.engine.translations.collectAsStateWithLifecycle()
    val tab = state.activeTab
    val runtime = tab?.let { state.runtime(it.id) }
    val webTab = tab?.takeIf { it.kind == TabKind.WEB }
    val loaded = webTab != null && runtime?.loaded == true
    val isWeb = webTab != null && UrlInput.isWebUrl(webTab.url)

    @Composable
    fun button(icon: ImageVector, label: String, enabled: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag("toolbar_action")) {
            Icon(icon, contentDescription = label, tint = if (highlighted) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
    }

    when (action) {
        ToolbarAction.NONE -> Unit
        ToolbarAction.RELOAD -> if (webTab != null && runtime?.loaded == true && runtime.isLoading) {
            button(Icons.Default.Close, stringResource(R.string.stop_loading)) { controller.stop(webTab.id) }
        } else {
            button(Icons.Default.Refresh, stringResource(R.string.reload), enabled = webTab != null) { webTab?.let { vm.report(controller.reload(it.id)) } }
        }
        ToolbarAction.BACK -> button(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.menu_back), enabled = runtime?.canGoBack == true) {
            tab?.let { controller.goBack(it.id) }
        }
        ToolbarAction.FORWARD -> button(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.menu_forward), enabled = runtime?.canGoForward == true) {
            tab?.let { controller.goForward(it.id) }
        }
        ToolbarAction.HOME -> button(Icons.Default.Home, stringResource(R.string.toolbar_action_home)) { vm.report(controller.openNewTabPage()) }
        ToolbarAction.BOOKMARK -> {
            val bookmark = webTab?.let { controller.bookmarkFor(it.url) }
            button(
                if (bookmark != null) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(if (bookmark != null) R.string.menu_edit_bookmark else R.string.bookmark),
                enabled = webTab != null,
                highlighted = bookmark != null,
            ) { if (bookmark != null) onEditBookmark(bookmark.id) else vm.bookmarkCurrentPage(onEditBookmark) }
        }
        ToolbarAction.SHARE -> button(Icons.Default.Share, stringResource(R.string.menu_share), enabled = webTab != null) { webTab?.let { shareUrl(context, it.url) } }
        ToolbarAction.COPY_LINK -> button(Icons.Default.ContentCopy, stringResource(R.string.menu_copy_link), enabled = webTab != null) {
            webTab?.let { vm.copyToClipboard(it.url) }
        }
        ToolbarAction.FIND -> button(Icons.Default.FindInPage, stringResource(R.string.menu_find_in_page), enabled = loaded) { vm.openFind() }
        ToolbarAction.READER_VIEW -> button(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.menu_reader_view), enabled = loaded && isWeb) { vm.openReaderView() }
        ToolbarAction.TRANSLATE -> {
            val translated = webTab != null && translations[webTab.id]?.translatedTo != null
            button(
                Icons.Default.Translate,
                stringResource(if (translated) R.string.menu_show_original else R.string.menu_translate),
                enabled = loaded,
                highlighted = translated,
            ) { if (translated) vm.showOriginal() else onMenuDialog(MenuDialog.TRANSLATE) }
        }
        ToolbarAction.DESKTOP_SITE -> button(
            Icons.Default.DesktopWindows,
            stringResource(R.string.menu_desktop_site),
            enabled = webTab != null,
            highlighted = runtime?.desktopMode == true,
        ) { webTab?.let { vm.report(controller.toggleDesktopMode(it.id)) } }
        ToolbarAction.SPLIT_VIEW -> {
            val split = state.splitTab != null
            button(
                Icons.Default.VerticalSplit,
                stringResource(if (split) R.string.close_split_view else R.string.menu_split_view),
                enabled = tab != null,
                highlighted = split,
            ) { if (split) vm.closeSplit() else onMenuDialog(MenuDialog.SPLIT) }
        }
        ToolbarAction.PRIVATE_BROWSING -> button(
            if (state.privateMode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            stringResource(if (state.privateMode) R.string.exit_private_browsing else R.string.private_browsing),
        ) {
            when {
                state.privateMode -> vm.setPrivateMode(false)
                state.privateTabs.isEmpty() -> vm.newPrivateTab()
                else -> vm.setPrivateMode(true)
            }
        }
        ToolbarAction.BOOKMARKS -> button(Icons.Default.Bookmarks, stringResource(R.string.menu_bookmarks)) { onScreenChange(Screen.BOOKMARKS) }
        ToolbarAction.HISTORY -> button(Icons.Default.History, stringResource(R.string.menu_history)) { onScreenChange(Screen.HISTORY) }
        ToolbarAction.DOWNLOADS -> button(Icons.Default.Download, stringResource(R.string.menu_downloads)) { onScreenChange(Screen.DOWNLOADS) }
    }
}

@Composable
private fun FindBar(find: FindState, vm: BrowserViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = find.query,
            onValueChange = vm::find,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.find_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.findNext(backwards = false) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (find.query.isNotEmpty()) {
            Text(stringResource(R.string.find_count, find.current, find.total), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = { vm.findNext(backwards = true) }, enabled = find.total > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.find_previous))
        }
        IconButton(onClick = { vm.findNext(backwards = false) }, enabled = find.total > 0) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next))
        }
        IconButton(onClick = vm::closeFind) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.find_close)) }
    }
}

@Composable
private fun ContextMenuDialog(
    target: ContextMenuTarget,
    onOpen: (url: String, background: Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onGlance: (String) -> Unit,
    onOpenIn: (String) -> Unit,
    onSplit: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                target.title?.takeIf { it.isNotBlank() } ?: target.linkUrl ?: target.imageUrl.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        text = {
            Column {
                target.linkUrl?.let { link ->
                    Text(link, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    MenuAction(stringResource(R.string.ctx_glance)) { onGlance(link); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_open_new_tab)) { onOpen(link, false); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_open_background)) { onOpen(link, true); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_open_in)) { onOpenIn(link); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_open_split)) { onSplit(link); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_copy_link)) { onCopy(link); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_share_link)) { onShare(link); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_download_link)) { onSave(link); onDismiss() }
                }
                target.imageUrl?.let { image ->
                    if (target.linkUrl != null) HorizontalDivider()
                    MenuAction(stringResource(R.string.ctx_open_image)) { onOpen(image, false); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_save_image)) { onSave(image); onDismiss() }
                    MenuAction(stringResource(R.string.ctx_copy_image)) { onCopy(image); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun Content(
    vm: BrowserViewModel,
    state: BrowserState,
    tab: Tab?,
    atTop: Boolean,
    toolbarAreaPx: Int,
    toolbarHiddenPx: () -> Float,
    staticPadding: PaddingValues,
    onFocusAddressBar: () -> Unit,
) {
    val split = state.splitTab
    if (split != null && tab != null) {
        SplitContent(vm, state, primary = tab, secondary = split, padding = staticPadding, onFocusAddressBar = onFocusAddressBar)
        return
    }
    TabContent(vm, state, tab, atTop, toolbarAreaPx, toolbarHiddenPx, staticPadding, onFocusAddressBar)
}

/**
 * Split view: the active tab and [secondary] side by side in landscape, stacked in portrait. The
 * active half is outlined; tapping the other half moves focus there. The toolbar stays put.
 */
@Composable
private fun SplitContent(
    vm: BrowserViewModel,
    state: BrowserState,
    primary: Tab,
    secondary: Tab,
    padding: PaddingValues,
    onFocusAddressBar: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val focusLabel = stringResource(R.string.split_focus_cd)
    // Keep the two halves in a stable order while focus moves between them.
    val ordered = remember(primary.id, secondary.id) { listOf(primary.id, secondary.id).sorted() }
    val pane: @Composable (String, Modifier) -> Unit = { id, modifier ->
        val focused = id == primary.id
        Box(
            modifier
                .padding(2.dp)
                .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
        ) {
            TabContent(vm, state, state.tab(id), atTop = true, toolbarAreaPx = 0, toolbarHiddenPx = { 0f }, staticPadding = PaddingValues(), onFocusAddressBar)
            if (!focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClickLabel = focusLabel) { vm.swapSplit() }
                        .semantics { contentDescription = focusLabel },
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(padding)) {
        if (landscape) {
            Row(Modifier.fillMaxSize()) { ordered.forEach { pane(it, Modifier.weight(1f).fillMaxHeight()) } }
        } else {
            Column(Modifier.fillMaxSize()) { ordered.forEach { pane(it, Modifier.weight(1f).fillMaxWidth()) } }
        }
        FilledTonalIconButton(
            onClick = { vm.closeSplit() },
            modifier = Modifier.align(Alignment.Center).size(36.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_split_view), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TabContent(
    vm: BrowserViewModel,
    state: BrowserState,
    tab: Tab?,
    atTop: Boolean,
    toolbarAreaPx: Int,
    toolbarHiddenPx: () -> Float,
    staticPadding: PaddingValues,
    onFocusAddressBar: () -> Unit,
) {
    val controller = vm.controller
    when {
        tab == null || tab.kind == TabKind.NEW_TAB -> NewTabPage(
            space = state.activeSpace,
            shortcuts = state.data.shortcuts,
            actions = remember(vm, onFocusAddressBar) { newTabPageActions(vm, onFocusAddressBar) },
            contentPadding = staticPadding,
            privateMode = state.privateMode,
        )
        state.isBlocked(tab) -> Message(
            title = stringResource(R.string.container_missing_title),
            body = stringResource(R.string.container_missing_body),
            padding = staticPadding,
        )
        !state.runtime(tab.id).loaded -> {
            val crashed = state.runtime(tab.id).crashed
            Message(
                title = if (crashed) stringResource(R.string.tab_crashed_title) else tab.title.ifBlank { UrlInput.displayHost(tab.url) },
                body = stringResource(if (crashed) R.string.tab_crashed_body else R.string.tab_unloaded_body),
                padding = staticPadding,
                actionLabel = stringResource(R.string.reload),
                onAction = { vm.report(controller.reload(tab.id)) },
            )
        }
        else -> WebContent(
            engine = vm.engine,
            tabId = tab.id,
            canGoBack = state.runtime(tab.id).canGoBack,
            canGoForward = state.runtime(tab.id).canGoForward,
            pullToRefresh = state.data.settings.pullToRefresh,
            toolbarAtTop = atTop,
            toolbarMaxPx = toolbarAreaPx,
            toolbarHiddenPx = toolbarHiddenPx,
            onSwipeNavigate = { direction ->
                if (direction == SwipeDirection.BACK) controller.goBack(tab.id) else controller.goForward(tab.id)
            },
            onRefresh = { controller.reload(tab.id) },
        )
    }
}

private fun newTabPageActions(vm: BrowserViewModel, onFocusAddressBar: () -> Unit) = object : NewTabPageActions {
    private val c = vm.controller
    override fun focusAddressBar() = onFocusAddressBar()
    override fun openShortcut(shortcut: Shortcut) { vm.report(c.openShortcut(shortcut.id)) }
    override fun addShortcut(title: String, url: String) = vm.report(c.addShortcut(url, title))
    override fun updateShortcut(shortcut: Shortcut, title: String, url: String) = vm.report(c.updateShortcut(shortcut.id, title, url))
    override fun removeShortcut(shortcut: Shortcut) { vm.report(c.removeShortcut(shortcut.id), R.string.msg_shortcut_removed) }
    override fun moveShortcut(shortcut: Shortcut, delta: Int) { vm.report(c.moveShortcut(shortcut.id, delta)) }
}

@Composable
private fun Message(
    title: String,
    body: String,
    padding: PaddingValues,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private const val SNAP_DELAY_MS = 180L

/** Window width from which the sidebar is docked instead of sliding in (Material's "expanded" width). */
private const val DOCKED_SIDEBAR_MIN_WIDTH_DP = 840

private val DRAWER_WIDTH = 320.dp

/** "Buy me a coffee!" in Settings. */
private const val SUPPORT_URL = "https://ko-fi.com/ezezzap"
private val PAGE_SHIFT = 28.dp

/** How far the address follows a swipe before snapping back. */
private val SWIPE_NUDGE = 16.dp
