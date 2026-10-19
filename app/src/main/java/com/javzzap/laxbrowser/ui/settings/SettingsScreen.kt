package com.javzzap.laxbrowser.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmarks
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import com.javzzap.laxbrowser.domain.AutoUnload
import com.javzzap.laxbrowser.platform.CrashLog
import com.javzzap.laxbrowser.platform.DefaultBrowser
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.javzzap.laxbrowser.domain.AutoplayPolicy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserSettings
import com.javzzap.laxbrowser.domain.HistoryRange
import com.javzzap.laxbrowser.ui.history.ClearHistoryDialog
import com.javzzap.laxbrowser.domain.SearchEngines
import com.javzzap.laxbrowser.domain.ToolbarAction
import com.javzzap.laxbrowser.domain.LinkTarget
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.ToolbarPosition
import com.javzzap.laxbrowser.ui.browser.icon
import com.javzzap.laxbrowser.ui.browser.label
import com.javzzap.laxbrowser.ui.common.ColorDot
import androidx.compose.material.icons.filled.NewReleases
import com.javzzap.laxbrowser.ui.common.LaxWordmark
import androidx.compose.ui.graphics.toArgb
import android.os.Build
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.runtime.mutableIntStateOf
import com.javzzap.laxbrowser.engine.AppLinkMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.javzzap.laxbrowser.domain.TrackingProtection
import com.javzzap.laxbrowser.domain.WebsiteAppearance
import com.javzzap.laxbrowser.ui.AppLanguage
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import com.javzzap.laxbrowser.domain.LoginBackup
import com.javzzap.laxbrowser.platform.DeviceAuth
import kotlin.math.roundToInt

interface SettingsActions {
    /** Applies a settings change; returns false (and reports why) when it was rejected. */
    fun update(transform: (BrowserSettings) -> BrowserSettings): Boolean
    fun openSpaces()
    fun openWebApps()
    fun openBookmarks()
    fun openHistory()
    fun openExtensions()
    fun openDownloads()
    fun openSitePermissions()
    fun openSiteRules()
    fun openLogins()

    /** "Open links in apps": ask, always, or never; and per-app remembered answers. */
    var appLinkMode: AppLinkMode
    fun rememberedAppLinkCount(): Int
    fun clearAppLinkChoices()

    /** Opens the Ko-fi page in the user's default browser. */
    fun openSupportPage()
    fun openLicenses()
    fun openChangelog()
    fun exportBookmarks(uri: Uri)
    fun importBookmarks(uri: Uri)
    /** [loginsPassword] encrypts saved logins into the backup; null leaves them out. */
    fun exportBackup(uri: Uri, loginsPassword: CharArray?)
    fun restoreBackup(uri: Uri)
    val canCheckForUpdates: Boolean
    fun checkForUpdates()
    suspend fun crashLogs(): List<CrashLog.Entry>
    fun shareCrash(text: String)
    fun clearCrashLogs()
    fun clearHistory(range: HistoryRange)

    /** Clears the chosen data; a null [range] keeps history. */
    fun clearBrowsingData(range: HistoryRange?, cookies: Boolean, cache: Boolean, permissions: Boolean)
}

private enum class SettingsPage { MAIN, SEARCH, THEME, TOOLBAR, TABS, WEBSITES, PRIVACY, PRIVATE_BROWSING, LANGUAGE, DATA, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    actions: SettingsActions,
    onBack: () -> Unit,
    linkSpaces: List<Pair<Space, String?>> = emptyList(),
    newerVersion: String? = null,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val title = when (page) {
        SettingsPage.MAIN -> R.string.menu_settings
        SettingsPage.SEARCH -> R.string.settings_cat_search
        SettingsPage.THEME -> R.string.settings_cat_theme
        SettingsPage.TOOLBAR -> R.string.settings_cat_toolbar
        SettingsPage.TABS -> R.string.settings_cat_tabs
        SettingsPage.DATA -> R.string.settings_cat_data
        SettingsPage.WEBSITES -> R.string.settings_websites
        SettingsPage.PRIVACY -> R.string.settings_privacy
        SettingsPage.PRIVATE_BROWSING -> R.string.private_browsing
        SettingsPage.LANGUAGE -> R.string.settings_language
        SettingsPage.ABOUT -> R.string.settings_about
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (page) {
                SettingsPage.MAIN -> Categories(settings, version, newerVersion, actions) { page = it }
                SettingsPage.SEARCH -> SearchSection(settings, actions)
                SettingsPage.THEME -> ThemeSection(settings, actions)
                SettingsPage.TOOLBAR -> ToolbarSection(settings, actions)
                SettingsPage.TABS -> TabsSection(settings, actions, linkSpaces)
                SettingsPage.DATA -> DataSection(actions)
                SettingsPage.WEBSITES -> WebsitesSection(settings, actions)
                SettingsPage.PRIVACY -> PrivacySection(settings, actions)
                SettingsPage.PRIVATE_BROWSING -> PrivateBrowsingSection(settings, actions)
                SettingsPage.LANGUAGE -> LanguageSection()
                SettingsPage.ABOUT -> AboutSection(settings, version, newerVersion, actions)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Categories(settings: BrowserSettings, version: String, newerVersion: String?, actions: SettingsActions, onOpen: (SettingsPage) -> Unit) {
    Category(
        Icons.Default.Info,
        stringResource(R.string.settings_about),
        if (newerVersion != null) {
            stringResource(R.string.settings_about_update_available, version, newerVersion)
        } else {
            stringResource(R.string.settings_version, version)
        },
        highlight = newerVersion != null,
    ) {
        onOpen(SettingsPage.ABOUT)
    }
    Category(Icons.Default.LocalCafe, stringResource(R.string.support_coffee), stringResource(R.string.support_coffee_summary), onClick = actions::openSupportPage)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    DefaultBrowserCard()
    val engineName = SearchEngines.builtIn.firstOrNull { it.id == settings.searchEngineId }?.name
        ?: stringResource(R.string.settings_search_custom)
    Category(Icons.Default.Search, stringResource(R.string.settings_cat_search), stringResource(R.string.settings_cat_search_summary, engineName)) {
        onOpen(SettingsPage.SEARCH)
    }
    Category(Icons.Default.ColorLens, stringResource(R.string.settings_cat_theme), stringResource(R.string.settings_cat_theme_summary)) {
        onOpen(SettingsPage.THEME)
    }
    Category(Icons.Default.TouchApp, stringResource(R.string.settings_cat_toolbar), stringResource(R.string.settings_cat_toolbar_summary)) {
        onOpen(SettingsPage.TOOLBAR)
    }
    Category(Icons.Default.Tab, stringResource(R.string.settings_cat_tabs), autoUnloadLabel(settings.autoUnloadMinutes)) {
        onOpen(SettingsPage.TABS)
    }
    Category(Icons.Default.Palette, stringResource(R.string.settings_websites), stringResource(R.string.settings_cat_websites_summary)) {
        onOpen(SettingsPage.WEBSITES)
    }
    Category(Icons.Default.Security, stringResource(R.string.settings_privacy), stringResource(R.string.settings_cat_privacy_summary)) {
        onOpen(SettingsPage.PRIVACY)
    }
    Category(Icons.Default.VisibilityOff, stringResource(R.string.private_browsing), stringResource(R.string.settings_cat_private_summary)) {
        onOpen(SettingsPage.PRIVATE_BROWSING)
    }
    Category(Icons.Default.Language, stringResource(R.string.settings_language), languageLabel(AppLanguage.current())) {
        onOpen(SettingsPage.LANGUAGE)
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Category(Icons.Default.Extension, stringResource(R.string.menu_extensions), stringResource(R.string.settings_cat_extensions_summary), onClick = actions::openExtensions)
    Category(Icons.Default.Layers, stringResource(R.string.menu_spaces), stringResource(R.string.settings_cat_spaces_summary), onClick = actions::openSpaces)
    Category(Icons.Default.Bookmarks, stringResource(R.string.menu_bookmarks), stringResource(R.string.settings_cat_bookmarks_summary), onClick = actions::openBookmarks)
    Category(Icons.Default.History, stringResource(R.string.menu_history), stringResource(R.string.settings_cat_history_summary), onClick = actions::openHistory)
    Category(Icons.Default.Download, stringResource(R.string.menu_downloads), stringResource(R.string.settings_cat_downloads_summary), onClick = actions::openDownloads)
    Category(Icons.Default.Backup, stringResource(R.string.settings_cat_data), stringResource(R.string.settings_cat_data_summary)) {
        onOpen(SettingsPage.DATA)
    }
    Category(Icons.Default.Apps, stringResource(R.string.menu_web_apps), stringResource(R.string.settings_cat_web_apps_summary), onClick = actions::openWebApps)
}

/** A settings entry; [highlight] colors the summary when it needs attention (an available update). */
@Composable
private fun Category(icon: ImageVector, title: String, summary: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (highlight) ColorDot(MaterialTheme.colorScheme.primary.toArgb(), size = 10.dp)
    }
}

@Composable
private fun ThemeSection(settings: BrowserSettings, actions: SettingsActions) {
    Header(stringResource(R.string.settings_colors))
    SwitchRow(
        stringResource(R.string.settings_space_accent),
        stringResource(R.string.settings_space_accent_summary),
        settings.spaceAccent,
    ) { checked -> actions.update { it.copy(spaceAccent = checked) } }
    // Dynamic (wallpaper) colors exist from Android 12.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
            stringResource(R.string.settings_wallpaper_colors),
            stringResource(R.string.settings_wallpaper_colors_summary),
            settings.wallpaperColors,
        ) { checked -> actions.update { it.copy(wallpaperColors = checked) } }
    }
    Text(
        stringResource(R.string.settings_theme_dark_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToolbarSection(settings: BrowserSettings, actions: SettingsActions) {
    Header(stringResource(R.string.settings_toolbar_position))
    Column(Modifier.selectableGroup()) {
        RadioRow(stringResource(R.string.settings_toolbar_top), settings.toolbarPosition == ToolbarPosition.TOP) {
            actions.update { it.copy(toolbarPosition = ToolbarPosition.TOP) }
        }
        RadioRow(stringResource(R.string.settings_toolbar_bottom), settings.toolbarPosition == ToolbarPosition.BOTTOM) {
            actions.update { it.copy(toolbarPosition = ToolbarPosition.BOTTOM) }
        }
    }
    Header(stringResource(R.string.settings_toolbar_layout))
    var choosingAction by remember { mutableStateOf(false) }
    ActionRow(
        settings.toolbarAction.icon ?: Icons.Default.TouchApp,
        stringResource(R.string.settings_toolbar_button),
        stringResource(settings.toolbarAction.label),
    ) { choosingAction = true }
    SwitchRow(
        stringResource(R.string.settings_swap_toolbar_buttons),
        stringResource(R.string.settings_swap_toolbar_buttons_summary),
        settings.swapToolbarButtons,
    ) { checked -> actions.update { it.copy(swapToolbarButtons = checked) } }
    SwitchRow(
        stringResource(R.string.settings_sidebar_right),
        stringResource(R.string.settings_sidebar_right_summary),
        settings.sidebarOnRight,
    ) { checked -> actions.update { it.copy(sidebarOnRight = checked) } }
    if (choosingAction) {
        ToolbarActionDialog(
            selected = settings.toolbarAction,
            onSelect = { action ->
                actions.update { it.copy(toolbarAction = action) }
                choosingAction = false
            },
            onDismiss = { choosingAction = false },
        )
    }
    Header(stringResource(R.string.settings_gestures))
    SwitchRow(
        stringResource(R.string.settings_hide_toolbar),
        stringResource(R.string.settings_hide_toolbar_summary),
        settings.hideToolbarOnScroll,
    ) { checked -> actions.update { it.copy(hideToolbarOnScroll = checked) } }
    SwitchRow(
        stringResource(R.string.settings_pull_to_refresh),
        stringResource(R.string.settings_pull_to_refresh_summary),
        settings.pullToRefresh,
    ) { checked -> actions.update { it.copy(pullToRefresh = checked) } }
}

/** Picks what the toolbar button next to the address does. */
@Composable
private fun ToolbarActionDialog(selected: ToolbarAction, onSelect: (ToolbarAction) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_toolbar_button)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                ToolbarAction.entries.forEach { action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = action == selected, role = Role.RadioButton) { onSelect(action) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            action.icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(action.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun WebsitesSection(settings: BrowserSettings, actions: SettingsActions) {
    Header(stringResource(R.string.settings_appearance))
    Column(Modifier.selectableGroup()) {
        listOf(
            WebsiteAppearance.SYSTEM to R.string.settings_appearance_system,
            WebsiteAppearance.LIGHT to R.string.settings_appearance_light,
            WebsiteAppearance.DARK to R.string.settings_appearance_dark,
        ).forEach { (value, label) ->
            RadioRow(stringResource(label), settings.websiteAppearance == value) {
                actions.update { it.copy(websiteAppearance = value) }
            }
        }
    }
    TextSizeRow(settings.textScalePercent) { percent -> actions.update { it.copy(textScalePercent = percent) } }
    ActionRow(Icons.AutoMirrored.Filled.Rule, stringResource(R.string.site_rules_title), stringResource(R.string.site_rules_summary), onClick = actions::openSiteRules)
    AppLinksSettings(actions)
    Header(stringResource(R.string.settings_autoplay))
    Column(Modifier.selectableGroup()) {
        listOf(
            AutoplayPolicy.BLOCK_AUDIBLE to R.string.settings_autoplay_block_audible,
            AutoplayPolicy.BLOCK_ALL to R.string.settings_autoplay_block_all,
            AutoplayPolicy.ALLOW_ALL to R.string.settings_autoplay_allow,
        ).forEach { (value, label) ->
            RadioRow(stringResource(label), settings.autoplay == value) { actions.update { it.copy(autoplay = value) } }
        }
    }
    Text(
        stringResource(R.string.settings_autoplay_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** "Open links in apps" (like Firefox): ask first, always, or never, plus forgetting saved answers. */
@Composable
private fun AppLinksSettings(actions: SettingsActions) {
    var mode by remember { mutableStateOf(actions.appLinkMode) }
    var remembered by remember { mutableIntStateOf(actions.rememberedAppLinkCount()) }
    Header(stringResource(R.string.settings_app_links))
    Column(Modifier.selectableGroup()) {
        listOf(
            AppLinkMode.ASK to R.string.settings_app_links_ask,
            AppLinkMode.ALWAYS to R.string.settings_app_links_always,
            AppLinkMode.NEVER to R.string.settings_app_links_never,
        ).forEach { (value, label) ->
            RadioRow(stringResource(label), mode == value) {
                actions.appLinkMode = value
                mode = value
            }
        }
    }
    if (remembered > 0) {
        ActionRow(
            Icons.Default.Restore,
            stringResource(R.string.settings_app_links_forget),
            pluralStringResource(R.plurals.settings_app_links_remembered, remembered, remembered),
        ) {
            actions.clearAppLinkChoices()
            remembered = 0
        }
    }
}

@Composable
private fun PrivacySection(settings: BrowserSettings, actions: SettingsActions) {
    Header(stringResource(R.string.settings_tracking_protection))
    Column(Modifier.selectableGroup()) {
        listOf(
            Triple(TrackingProtection.STANDARD, R.string.settings_tp_standard, R.string.settings_tp_standard_summary),
            Triple(TrackingProtection.STRICT, R.string.settings_tp_strict, R.string.settings_tp_strict_summary),
            Triple(TrackingProtection.OFF, R.string.settings_tp_off, R.string.settings_tp_off_summary),
        ).forEach { (value, label, summary) ->
            RadioRow(stringResource(label), settings.trackingProtection == value, stringResource(summary)) {
                actions.update { it.copy(trackingProtection = value) }
            }
        }
    }
    SwitchRow(
        stringResource(R.string.settings_https_only),
        stringResource(R.string.settings_https_only_summary),
        settings.httpsOnly,
    ) { checked -> actions.update { it.copy(httpsOnly = checked) } }
    SwitchRow(
        stringResource(R.string.settings_gpc),
        stringResource(R.string.settings_gpc_summary),
        settings.globalPrivacyControl,
    ) { checked -> actions.update { it.copy(globalPrivacyControl = checked) } }
    ActionRow(Icons.Default.Key, stringResource(R.string.logins_title), stringResource(R.string.logins_summary), onClick = actions::openLogins)
    ActionRow(Icons.Default.Shield, stringResource(R.string.site_permissions_title), stringResource(R.string.site_permissions_summary), onClick = actions::openSitePermissions)

    var clearing by remember { mutableStateOf(false) }
    var clearingAll by remember { mutableStateOf(false) }
    Header(stringResource(R.string.settings_browsing_data))
    ActionRow(Icons.Default.DeleteSweep, stringResource(R.string.clear_history), stringResource(R.string.settings_clear_history_summary)) { clearing = true }
    ActionRow(Icons.Default.DeleteForever, stringResource(R.string.settings_clear_data), stringResource(R.string.settings_clear_data_summary)) { clearingAll = true }
    if (clearing) ClearHistoryDialog(onClear = actions::clearHistory, onDismiss = { clearing = false })
    if (clearingAll) ClearDataDialog(onClear = actions::clearBrowsingData, onDismiss = { clearingAll = false })
}

@Composable
private fun autoUnloadLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.auto_unload_off)
    minutes >= 60 -> pluralStringResource(R.plurals.auto_unload_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.auto_unload_minutes, minutes, minutes)
}

@Composable
private fun TabsSection(settings: BrowserSettings, actions: SettingsActions, linkSpaces: List<Pair<Space, String?>>) {
    Header(stringResource(R.string.settings_auto_unload))
    Text(
        stringResource(R.string.settings_auto_unload_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Column(Modifier.selectableGroup()) {
        AutoUnload.CHOICES.forEach { minutes ->
            RadioRow(autoUnloadLabel(minutes), settings.autoUnloadMinutes == minutes) {
                actions.update { it.copy(autoUnloadMinutes = minutes) }
            }
        }
    }
    Header(stringResource(R.string.settings_external_links))
    Text(
        stringResource(R.string.settings_external_links_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Column(Modifier.selectableGroup()) {
        val choose: (LinkTarget) -> Unit = { target -> actions.update { it.copy(externalLinks = target) } }
        RadioRow(stringResource(R.string.settings_links_active), settings.externalLinks == LinkTarget.ActiveSpace) { choose(LinkTarget.ActiveSpace) }
        RadioRow(stringResource(R.string.settings_links_ask), settings.externalLinks == LinkTarget.Ask) { choose(LinkTarget.Ask) }
        linkSpaces.forEach { (space, container) ->
            val target = LinkTarget.InSpace(space.id)
            RadioRow(
                stringResource(R.string.settings_links_space, space.name),
                settings.externalLinks == target,
                container?.let { stringResource(R.string.open_link_in_container, it) },
            ) { choose(target) }
        }
        RadioRow(stringResource(R.string.open_link_in_private), settings.externalLinks == LinkTarget.PrivateTab) { choose(LinkTarget.PrivateTab) }
    }
}

/** Shown until Lax is the default browser. */
@Composable
private fun DefaultBrowserCard() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DefaultBrowser.isDefault(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = DefaultBrowser.isDefault(context) }
    if (isDefault) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.default_browser_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.default_browser_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { runCatching { launcher.launch(DefaultBrowser.requestIntent(context)) } }) {
                Text(stringResource(R.string.default_browser_action))
            }
        }
    }
}

@Composable
private fun DataSection(actions: SettingsActions) {
    var restoring by remember { mutableStateOf<Uri?>(null) }
    var choosingBackup by remember { mutableStateOf(false) }
    var loginsPassword by remember { mutableStateOf<CharArray?>(null) }
    val exportBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri -> uri?.let(actions::exportBookmarks) }
    val importBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(actions::importBookmarks) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val password = loginsPassword
        loginsPassword = null
        if (uri != null) actions.exportBackup(uri, password) else password?.fill('\u0000')
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }
    val backupName = "lax-backup-${java.time.LocalDate.now()}.json"
    val activity = LocalActivity.current as? FragmentActivity
    val authTitle = stringResource(R.string.backup_logins_auth_title)

    Header(stringResource(R.string.menu_bookmarks))
    ActionRow(Icons.Default.FileDownload, stringResource(R.string.bookmarks_import), stringResource(R.string.bookmarks_import_summary)) {
        importBookmarks.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain", "application/octet-stream"))
    }
    ActionRow(Icons.Default.FileUpload, stringResource(R.string.bookmarks_export), stringResource(R.string.bookmarks_export_summary)) {
        exportBookmarks.launch("bookmarks.html")
    }
    Header(stringResource(R.string.backup_header))
    ActionRow(Icons.Default.Backup, stringResource(R.string.backup_export), stringResource(R.string.backup_export_summary)) {
        choosingBackup = true
    }
    if (choosingBackup) {
        BackupOptionsDialog(
            onSave = { password ->
                choosingBackup = false
                if (password == null) {
                    exportBackup.launch(backupName)
                } else {
                    // Putting every password in a file needs the screen lock, like showing one.
                    activity?.let {
                        DeviceAuth.authenticate(it, authTitle) {
                            loginsPassword = password
                            exportBackup.launch(backupName)
                        }
                    }
                }
            },
            onDismiss = { choosingBackup = false },
        )
    }
    ActionRow(Icons.Default.Restore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary)) {
        restoreBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    Text(
        stringResource(R.string.backup_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    restoring?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.restoreBackup(uri)
                    restoring = null
                }) { Text(stringResource(R.string.backup_restore_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Whether to include saved logins in a backup, and the password that encrypts them. */
@Composable
private fun BackupOptionsDialog(onSave: (loginsPassword: CharArray?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val canProtect = remember { DeviceAuth.isAvailable(context) }
    var includeLogins by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val tooShort = password.length < LoginBackup.MIN_PASSWORD_LENGTH
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    val valid = !includeLogins || (!tooShort && confirmation == password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.backup_export_summary), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = includeLogins, enabled = canProtect, role = Role.Checkbox, onValueChange = { includeLogins = it })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeLogins, onCheckedChange = null, enabled = canProtect)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.backup_include_logins), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (canProtect) R.string.backup_include_logins_summary else R.string.logins_need_lock),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (includeLogins) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        isError = password.isNotEmpty() && tooShort,
                        supportingText = {
                            Text(pluralStringResource(R.plurals.backup_password_hint, LoginBackup.MIN_PASSWORD_LENGTH, LoginBackup.MIN_PASSWORD_LENGTH))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(stringResource(R.string.backup_password_mismatch)) }) else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.backup_password_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(if (includeLogins) password.toCharArray() else null) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AboutSection(settings: BrowserSettings, version: String, newerVersion: String?, actions: SettingsActions) {
    var crashLogs by remember { mutableStateOf<List<CrashLog.Entry>?>(null) }
    var showingLogs by remember { mutableStateOf(false) }
    if (newerVersion != null && actions.canCheckForUpdates) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_available_title, newerVersion), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.about_update_current, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = actions::checkForUpdates) { Text(stringResource(R.string.about_update_action)) }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LaxWordmark(fontSize = 48.sp)
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.about_engine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = actions::openSupportPage) {
            Icon(Icons.Default.LocalCafe, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.support_coffee))
        }
    }
    if (actions.canCheckForUpdates) {
        Header(stringResource(R.string.updates_header))
        SwitchRow(
            stringResource(R.string.updates_auto),
            stringResource(R.string.updates_auto_summary),
            settings.checkForUpdates,
        ) { checked -> actions.update { it.copy(checkForUpdates = checked) } }
        ActionRow(Icons.Default.SystemUpdate, stringResource(R.string.updates_check_now), stringResource(R.string.updates_check_now_summary), actions::checkForUpdates)
    }
    Header(stringResource(R.string.about_more))
    ActionRow(Icons.Default.NewReleases, stringResource(R.string.changelog_title), stringResource(R.string.changelog_summary), onClick = actions::openChangelog)
    ActionRow(Icons.Default.Description, stringResource(R.string.licenses_title), stringResource(R.string.licenses_summary), onClick = actions::openLicenses)
    ActionRow(Icons.Default.BugReport, stringResource(R.string.crash_logs_title), stringResource(R.string.crash_logs_summary)) { showingLogs = true }
    if (showingLogs) {
        LaunchedEffect(Unit) { crashLogs = actions.crashLogs() }
        val logs = crashLogs
        AlertDialog(
            onDismissRequest = { showingLogs = false },
            title = { Text(stringResource(R.string.crash_logs_title)) },
            text = {
                when {
                    logs == null -> Unit
                    logs.isEmpty() -> Text(stringResource(R.string.crash_logs_empty))
                    else -> Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        logs.forEach { entry ->
                            Row(Modifier.fillMaxWidth().clickable { actions.shareCrash(entry.text) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAt)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingLogs = false }) { Text(stringResource(R.string.close)) } },
            dismissButton = {
                if (!logs.isNullOrEmpty()) {
                    TextButton(onClick = {
                        actions.clearCrashLogs()
                        showingLogs = false
                    }) { Text(stringResource(R.string.clear)) }
                }
            },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Chooses what to clear: history (with a time range), cookies and site data, cache, permissions. */
@Composable
private fun ClearDataDialog(onClear: (HistoryRange?, Boolean, Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf(true) }
    var range by remember { mutableStateOf(HistoryRange.ALL) }
    var cookies by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf(true) }
    var permissions by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_data)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CheckRow(stringResource(R.string.clear_data_history), null, history) { history = it }
                if (history) {
                    Column(Modifier.padding(start = 32.dp).selectableGroup()) {
                        listOf(
                            HistoryRange.LAST_HOUR to R.string.clear_history_last_hour,
                            HistoryRange.TODAY to R.string.clear_history_today,
                            HistoryRange.LAST_7_DAYS to R.string.clear_history_last_7_days,
                            HistoryRange.ALL to R.string.clear_history_all,
                        ).forEach { (value, label) -> RadioRow(stringResource(label), range == value) { range = value } }
                    }
                }
                CheckRow(stringResource(R.string.clear_data_cookies), stringResource(R.string.clear_data_cookies_summary), cookies) { cookies = it }
                CheckRow(stringResource(R.string.clear_data_cache), null, cache) { cache = it }
                CheckRow(stringResource(R.string.clear_data_permissions), null, permissions) { permissions = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = history || cookies || cache || permissions,
                onClick = {
                    onClear(range.takeIf { history }, cookies, cache, permissions)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CheckRow(label: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PrivateBrowsingSection(settings: BrowserSettings, actions: SettingsActions) {
    Text(
        stringResource(R.string.private_ntp_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    val context = LocalContext.current
    val canLock = remember { DeviceAuth.isAvailable(context) }
    if (canLock || settings.privateTabsLock) {
        SwitchRow(
            stringResource(R.string.settings_private_lock),
            stringResource(R.string.settings_private_lock_summary),
            settings.privateTabsLock,
        ) { checked -> actions.update { it.copy(privateTabsLock = checked) } }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_private_lock), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(stringResource(R.string.settings_private_lock_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SwitchRow(
        stringResource(R.string.settings_private_block_screenshots),
        stringResource(R.string.settings_private_block_screenshots_summary),
        settings.privateBlockScreenshots,
    ) { checked -> actions.update { it.copy(privateBlockScreenshots = checked) } }
    SwitchRow(
        stringResource(R.string.settings_private_hide_in_recents),
        stringResource(R.string.settings_private_hide_in_recents_summary),
        settings.privateHideInRecents,
    ) { checked -> actions.update { it.copy(privateHideInRecents = checked) } }
}

@Composable
private fun LanguageSection() {
    // AppCompat recreates the activity after a change; the saved page keeps the user here.
    val current = AppLanguage.current()
    Column(Modifier.selectableGroup()) {
        AppLanguage.entries.forEach { language ->
            RadioRow(languageLabel(language), current == language) {
                if (language != current) AppLanguage.apply(language)
            }
        }
    }
    Text(
        stringResource(R.string.settings_language_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.SPANISH -> R.string.language_spanish
    },
)

@Composable
private fun SearchSection(settings: BrowserSettings, actions: SettingsActions) {
    var custom by remember(settings.customSearchUrl) { mutableStateOf(settings.customSearchUrl.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val saveCustom: () -> Unit = {
        if (SearchEngines.isValidCustomTemplate(custom)) {
            error = false
            actions.update { it.copy(searchEngineId = SearchEngines.CUSTOM_ID, customSearchUrl = custom) }
        } else {
            error = true
        }
    }

    SwitchRow(
        stringResource(R.string.settings_search_suggestions),
        stringResource(R.string.settings_search_suggestions_summary),
        settings.searchSuggestions,
    ) { checked -> actions.update { it.copy(searchSuggestions = checked) } }
    Header(stringResource(R.string.settings_search_engine))
    Text(
        stringResource(R.string.settings_search_engine_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Column(Modifier.selectableGroup()) {
        SearchEngines.builtIn.forEach { engine ->
            RadioRow(engine.name, settings.searchEngineId == engine.id) {
                actions.update { it.copy(searchEngineId = engine.id) }
            }
        }
        RadioRow(stringResource(R.string.settings_search_custom), settings.searchEngineId == SearchEngines.CUSTOM_ID) {
            saveCustom()
        }
    }
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it; error = false },
        label = { Text(stringResource(R.string.settings_search_custom_label)) },
        placeholder = { Text("https://example.com/search?q=%s") },
        supportingText = {
            Text(stringResource(if (error) R.string.settings_search_custom_error else R.string.settings_search_custom_help))
        },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { saveCustom() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = saveCustom, enabled = custom.isNotBlank()) { Text(stringResource(R.string.settings_search_custom_use)) }
    }
}

@Composable
private fun TextSizeRow(percent: Int, onChange: (Int) -> Unit) {
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_text_size, value.roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange((value / 10f).roundToInt() * 10) },
            valueRange = 50f..200f,
            steps = 14,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, summary: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}