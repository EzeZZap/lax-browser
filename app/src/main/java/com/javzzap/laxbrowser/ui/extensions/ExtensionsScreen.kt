package com.javzzap.laxbrowser.ui.extensions

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.engine.gecko.ExtensionInfo
import com.javzzap.laxbrowser.engine.gecko.ExtensionPermissionRequest
import com.javzzap.laxbrowser.engine.gecko.ExtensionPopup
import com.javzzap.laxbrowser.engine.gecko.await
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.Image

/** Installed Firefox extensions, with a shortcut to addons.mozilla.org. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(vm: BrowserViewModel, onOpened: () -> Unit, onBack: () -> Unit) {
    val extensions by vm.extensions.collectAsStateWithLifecycle()
    val installing by vm.installingExtensions.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<ExtensionInfo?>(null) }
    var showingPermissions by remember { mutableStateOf<ExtensionInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extensions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                StoreCard(onBrowse = {
                    vm.openExtensionStore()
                    onOpened()
                })
            }
            if (extensions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.extensions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            val (enabled, disabled) = extensions.partition { it.enabled }
            listOf(R.string.extensions_enabled to enabled, R.string.extensions_disabled to disabled).forEach { (header, section) ->
                if (section.isEmpty()) return@forEach
                item(key = "header_$header") {
                    Text(
                        stringResource(header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                extensionItems(section, vm, onOpened, onRemove = { removing = it }, onPermissions = { showingPermissions = it })
            }
            // Suggestions the user has not installed yet; the section disappears once all are added.
            val installedIds = extensions.map { it.id }.toSet()
            if (recommendedExtensions.any { it.id !in installedIds }) {
                item(key = "recommended") {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            stringResource(R.string.extensions_recommended),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                        )
                        RecommendedExtensionList(
                            installedIds = installedIds,
                            installingIds = installing,
                            onAdd = vm::installRecommended,
                            onDetails = {
                                vm.openExtensionListing(it.listingUrl)
                                onOpened()
                            },
                        )
                    }
                }
            }
        }
    }

    removing?.let { extension ->
        ConfirmDialog(
            title = stringResource(R.string.extension_remove_title),
            message = stringResource(R.string.extension_remove_message, extension.name),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = { vm.uninstallExtension(extension.id) },
            onDismiss = { removing = null },
        )
    }
    showingPermissions?.let { extension ->
        AlertDialog(
            onDismissRequest = { showingPermissions = null },
            icon = { ExtensionIcon(extension.icon, 40.dp) },
            title = { Text(extension.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    extension.creator?.let { Text(stringResource(R.string.extension_by, it), style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.extension_version, extension.version), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    PermissionList(extension.permissions, extension.origins, emptyList())
                }
            },
            confirmButton = { TextButton(onClick = { showingPermissions = null }) { Text(stringResource(R.string.done)) } },
        )
    }
}

private fun LazyListScope.extensionItems(
    extensions: List<ExtensionInfo>,
    vm: BrowserViewModel,
    onOpened: () -> Unit,
    onRemove: (ExtensionInfo) -> Unit,
    onPermissions: (ExtensionInfo) -> Unit,
) {
    items(extensions, key = { it.id }) { extension ->
        ExtensionRow(
            extension = extension,
            onToggle = { enabled -> vm.setExtensionEnabled(extension.id, enabled) },
            onPinChange = { pinned -> vm.setExtensionPinned(extension.id, pinned) },
            onSettings = {
                vm.openExtensionOptions(extension.id)
                onOpened()
            },
            onPrivateChange = { allowed -> vm.setExtensionAllowedInPrivate(extension.id, allowed) },
            onPermissions = { onPermissions(extension) },
            onListing = extension.amoListingUrl?.let { url ->
                {
                    vm.openExtensionListing(url)
                    onOpened()
                }
            },
            onRemove = { onRemove(extension) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun StoreCard(onBrowse: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.extensions_store_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.extensions_store_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBrowse) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.extensions_store_button))
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    extension: ExtensionInfo,
    onToggle: (Boolean) -> Unit,
    onPinChange: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onPermissions: () -> Unit,
    onListing: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = extension.enabled,
                enabled = !extension.disabledBySystem,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(extension.icon, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(extension.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                extension.disabledBySystem -> stringResource(R.string.extension_disabled_by_system)
                extension.description != null -> extension.description
                else -> stringResource(R.string.extension_version, extension.version)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (extension.disabledBySystem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (extension.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.extension_pin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (extension.allowedInPrivateBrowsing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.extension_runs_in_private),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Switch(checked = extension.enabled, onCheckedChange = null, enabled = !extension.disabledBySystem)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, extension.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (extension.optionsPageUrl != null && extension.enabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.extension_settings)) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { menu = false; onSettings() },
                    )
                }
                if (extension.hasAction || extension.optionsPageUrl != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.extension_pin)) },
                        leadingIcon = { Checkbox(checked = extension.pinned, onCheckedChange = null) },
                        onClick = { menu = false; onPinChange(!extension.pinned) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.extension_allow_private)) },
                    leadingIcon = { Checkbox(checked = extension.allowedInPrivateBrowsing, onCheckedChange = null) },
                    onClick = { menu = false; onPrivateChange(!extension.allowedInPrivateBrowsing) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.extension_permissions)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = { menu = false; onPermissions() },
                )
                onListing?.let { open ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.extension_view_listing)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = { menu = false; open() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

/** An extension or toolbar-button icon, rendered by Gecko at the needed size. */
@Composable
fun ExtensionIcon(image: Image?, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, image, px) {
        value = image?.let { runCatching { it.getBitmap(px).await() }.getOrNull() }
    }
    val shape = RoundedCornerShape(size / 5)
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded.asImageBitmap(), contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** Asks the user to approve an extension's install, update, or optional permissions. */
@Composable
fun ExtensionPermissionDialog(request: ExtensionPermissionRequest) {
    var allowPrivate by remember(request) { mutableStateOf(false) }
    var pin by remember(request) { mutableStateOf(true) }
    val install = request.kind == ExtensionPermissionRequest.Kind.INSTALL
    AlertDialog(
        // Only the buttons (or Back) answer, so a stray tap outside cannot cancel an install.
        onDismissRequest = { request.respond(false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { ExtensionIcon(request.icon, 48.dp) },
        title = {
            Text(
                when (request.kind) {
                    ExtensionPermissionRequest.Kind.INSTALL -> stringResource(R.string.extension_install_title, request.extensionName)
                    ExtensionPermissionRequest.Kind.UPDATE -> stringResource(R.string.extension_update_title, request.extensionName)
                    ExtensionPermissionRequest.Kind.OPTIONAL -> stringResource(R.string.extension_optional_title, request.extensionName)
                },
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PermissionList(request.permissions, request.origins, request.dataCollection)
                if (install) {
                    ToggleRow(stringResource(R.string.extension_pin), pin) { pin = it }
                    ToggleRow(stringResource(R.string.extension_allow_private), allowPrivate) { allowPrivate = it }
                    Text(
                        stringResource(R.string.extension_install_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { request.respond(true, allowPrivate, pin) }) {
                Text(stringResource(if (install) R.string.add else R.string.allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { request.respond(false) }) {
                Text(stringResource(if (install) R.string.cancel else R.string.deny))
            }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PermissionList(permissions: List<String>, origins: List<String>, dataCollection: List<String>) {
    val lines = permissionDescriptions(permissions, origins)
    if (lines.isEmpty()) {
        Text(stringResource(R.string.extension_no_permissions), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(stringResource(R.string.extension_permissions_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        lines.forEach { Bullet(it) }
    }
    val collected = dataCollection.map { dataCollectionDescription(it) }
    if (collected.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.extension_data_collection_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        collected.forEach { Bullet(it) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Human-readable permission warnings, following the ones Firefox shows. */
@Composable
private fun permissionDescriptions(permissions: List<String>, origins: List<String>): List<String> {
    val lines = mutableListOf<String>()
    val allSites = origins.any { it in ALL_SITES_PATTERNS } || permissions.any { it in ALL_SITES_PATTERNS }
    if (allSites) {
        lines += stringResource(R.string.ext_perm_all_urls)
    } else {
        origins.mapNotNull(::siteOf).distinct().forEach { lines += stringResource(R.string.ext_perm_site, it) }
    }
    permissions.mapNotNull { PERMISSION_STRINGS[it] }.distinct().forEach { lines += stringResource(it) }
    return lines
}

/** The host part of a match pattern, for example "*.example.com". */
private fun siteOf(pattern: String): String? {
    if (pattern in ALL_SITES_PATTERNS) return null
    val host = pattern.substringAfter("://", missingDelimiterValue = "").substringBefore('/')
    return host.takeIf { it.isNotBlank() }
}

@Composable
private fun dataCollectionDescription(type: String): String =
    DATA_COLLECTION_STRINGS[type]?.let { stringResource(it) } ?: type

private val ALL_SITES_PATTERNS = setOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*")

private val PERMISSION_STRINGS = mapOf(
    "bookmarks" to R.string.ext_perm_bookmarks,
    "browserSettings" to R.string.ext_perm_browser_settings,
    "browsingData" to R.string.ext_perm_browsing_data,
    "clipboardRead" to R.string.ext_perm_clipboard_read,
    "clipboardWrite" to R.string.ext_perm_clipboard_write,
    "declarativeNetRequest" to R.string.ext_perm_block_content,
    "declarativeNetRequestFeedback" to R.string.ext_perm_history,
    "downloads" to R.string.ext_perm_downloads,
    "downloads.open" to R.string.ext_perm_downloads_open,
    "find" to R.string.ext_perm_find,
    "geolocation" to R.string.ext_perm_geolocation,
    "history" to R.string.ext_perm_history,
    "management" to R.string.ext_perm_management,
    "nativeMessaging" to R.string.ext_perm_native_messaging,
    "notifications" to R.string.ext_perm_notifications,
    "privacy" to R.string.ext_perm_privacy,
    "proxy" to R.string.ext_perm_proxy,
    "sessions" to R.string.ext_perm_sessions,
    "tabs" to R.string.ext_perm_tabs,
    "tabHide" to R.string.ext_perm_tab_hide,
    "topSites" to R.string.ext_perm_history,
    "webNavigation" to R.string.ext_perm_web_navigation,
)

private val DATA_COLLECTION_STRINGS = mapOf(
    "authenticationInfo" to R.string.ext_data_authentication,
    "bookmarksInfo" to R.string.ext_data_bookmarks,
    "browsingActivity" to R.string.ext_data_browsing_activity,
    "financialAndPaymentInfo" to R.string.ext_data_financial,
    "healthInfo" to R.string.ext_data_health,
    "locationInfo" to R.string.ext_data_location,
    "personalCommunications" to R.string.ext_data_communications,
    "personallyIdentifyingInfo" to R.string.ext_data_personal,
    "searchTerms" to R.string.ext_data_search_terms,
    "technicalAndInteraction" to R.string.ext_data_technical,
    "websiteActivity" to R.string.ext_data_website_activity,
    "websiteContent" to R.string.ext_data_website_content,
)

/** Shows an extension's toolbar popup. The sheet does not drag, so the popup page can scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopupSheet(popup: ExtensionPopup, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
    ) {
        Text(
            popup.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(POPUP_HEIGHT),
            factory = { context ->
                GeckoView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { view ->
                if (view.session !== popup.session) {
                    if (view.session != null) view.releaseSession()
                    view.setSession(popup.session)
                }
            },
            onRelease = { it.releaseSession() },
        )
        Spacer(Modifier.height(16.dp))
    }
}

private val POPUP_HEIGHT = 440.dp
