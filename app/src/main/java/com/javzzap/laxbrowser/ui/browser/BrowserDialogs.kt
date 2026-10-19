package com.javzzap.laxbrowser.ui.browser

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import com.javzzap.laxbrowser.domain.LinkTarget
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.ClosedTab
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabFolder
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.engine.gecko.GlanceState
import com.javzzap.laxbrowser.engine.gecko.TranslationLanguages
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.SiteIcon
import org.mozilla.geckoview.GeckoView

/** Glance: a link preview floating over the page, in the opener's container. */
@Composable
fun GlanceOverlay(glance: GlanceState, vm: BrowserViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClickLabel = stringResource(R.string.glance_close)) { vm.engine.closeGlance() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                // Taps inside the card must not close it.
                .clickable(enabled = false) {},
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.engine.closeGlance() }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.glance_close)) }
                    if (glance.canGoBack) {
                        IconButton(onClick = { vm.engine.glanceBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(glance.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(UrlInput.displayHost(glance.url), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    TextButton(onClick = { vm.engine.promoteGlance() }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.glance_open_tab))
                    }
                }
                if (glance.progress in 1..99) LinearProgressIndicator(progress = { glance.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        GeckoView(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                    },
                    update = { view ->
                        if (view.session !== glance.session) {
                            if (view.session != null) view.releaseSession()
                            view.setSession(glance.session)
                        }
                    },
                    onRelease = { it.releaseSession() },
                )
            }
        }
    }
}

@Composable
fun FolderChooserDialog(
    folders: List<TabFolder>,
    current: String?,
    onChoose: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_choose_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                if (current != null) {
                    item { ChooserRow(Icons.Default.FolderOff, stringResource(R.string.folder_none)) { onChoose(null); onDismiss() } }
                }
                items(folders, key = { it.id }) { folder ->
                    ChooserRow(Icons.Default.Folder, folder.name, selected = folder.id == current) { onChoose(folder.id); onDismiss() }
                }
                item { ChooserRow(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder_ellipsis)) { onNewFolder() } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChooserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

/** Chooses which tab of the current list to show next to the active one. */
@Composable
fun SplitChooserDialog(state: BrowserState, onChoose: (Tab) -> Unit, onDismiss: () -> Unit) {
    val active = state.activeTab
    val candidates = if (state.privateMode) {
        state.sortedPrivateTabs
    } else {
        state.essentials + state.pinnedTabs(state.activeSpace?.id) + state.regularTabs(state.activeSpace?.id)
    }.filter { it.id != active?.id && it.kind == TabKind.WEB && !state.isBlocked(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_choose_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.split_no_tabs))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.id }) { tab -> TabChoiceRow(tab) { onChoose(tab); onDismiss() } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TabChoiceRow(tab: Tab, onClick: () -> Unit) {
    val title = tab.title.ifBlank { UrlInput.displayHost(tab.url) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SiteIcon(tab.url, title, tab.faviconUrl, size = 24.dp, ephemeral = tab.isPrivate)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(UrlInput.displayHost(tab.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Chooses the Space a site always opens in, or none. */
@Composable
fun SiteSpaceDialog(host: String, state: BrowserState, onChoose: (Space?) -> Unit, onDismiss: () -> Unit) {
    val current = state.data.siteSettings.firstOrNull { it.host == host }?.spaceId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_space_title, host)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(stringResource(R.string.site_space_explanation), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                item {
                    ChooserRow(Icons.Default.FolderOff, stringResource(R.string.site_any_space), selected = current == null) { onChoose(null); onDismiss() }
                }
                items(state.sortedSpaces, key = { it.id }) { space ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChoose(space); onDismiss() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpaceIcon(space, size = 16.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(space.name, color = if (space.id == current) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * "Open in YouTube?" for a tapped link an installed app can open. Staying keeps the page in Lax;
 * "Don't ask again" remembers either answer for that app (Settings → Websites can reset it).
 */
@Composable
fun AppLinkDialog(appName: String, isPrivate: Boolean, onAnswer: (openInApp: Boolean, remember: Boolean) -> Unit) {
    var dontAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(false, false) },
        icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        title = { Text(stringResource(R.string.app_link_title, appName)) },
        text = {
            Column {
                Text(stringResource(if (isPrivate) R.string.app_link_body_private else R.string.app_link_body, appName))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().toggleable(value = dontAskAgain, role = Role.Checkbox, onValueChange = { dontAskAgain = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontAskAgain, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.app_link_remember, appName), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, dontAskAgain) }) { Text(stringResource(R.string.app_link_open)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false, dontAskAgain) }) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * "Open in…": picks the Space (and with it the container, so the right logins) or a private tab
 * for a link from another app or a page.
 */
@Composable
fun OpenLinkInDialog(url: String, state: BrowserState, onChoose: (LinkTarget) -> Unit, onDismiss: () -> Unit) {
    val activeId = state.activeSpace?.id?.takeIf { !state.privateMode }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.open_link_in_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                item {
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(url, "", null, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(UrlInput.displayHost(url), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(state.sortedSpaces, key = { it.id }) { space ->
                    val container = state.container(space.containerId)?.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isSpaceBlocked(space)) { onChoose(LinkTarget.InSpace(space.id)) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpaceIcon(space, size = 16.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(space.name, color = if (space.id == activeId) MaterialTheme.colorScheme.primary else Color.Unspecified)
                            Text(
                                listOfNotNull(
                                    container?.let { stringResource(R.string.open_link_in_container, it) },
                                    stringResource(R.string.open_link_in_current).takeIf { space.id == activeId },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    ChooserRow(Icons.Default.VisibilityOff, stringResource(R.string.open_link_in_private), selected = false) { onChoose(LinkTarget.PrivateTab) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun RecentlyClosedDialog(closed: List<ClosedTab>, onReopen: (ClosedTab) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_recently_closed)) },
        text = {
            if (closed.isEmpty()) {
                Text(stringResource(R.string.recently_closed_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(closed, key = { it.tab.id }) { item -> TabChoiceRow(item.tab) { onReopen(item); onDismiss() } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Picks the languages for on-device page translation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateDialog(vm: BrowserViewModel, state: BrowserState, onDismiss: () -> Unit) {
    val tab = state.activeTab ?: return onDismiss()
    val translations by vm.engine.translations.collectAsStateWithLifecycle()
    val info = translations[tab.id]
    val languages by produceState<TranslationLanguages?>(null) { value = vm.translationLanguages() }
    val list = languages
    var from by remember(list, info?.pageLanguage) {
        mutableStateOf(list?.from?.firstOrNull { it.code == info?.pageLanguage } ?: list?.from?.firstOrNull())
    }
    val appLanguage = remember { java.util.Locale.getDefault().language }
    var to by remember(list) {
        mutableStateOf(list?.to?.firstOrNull { it.code == (info?.userLanguage ?: appLanguage) } ?: list?.to?.firstOrNull())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate_title)) },
        text = {
            when {
                list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                !list.isSupported -> Text(stringResource(R.string.translate_unsupported))
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguagePicker(stringResource(R.string.translate_from), list.from.map { it.code to it.name }, from?.code) { code -> from = list.from.first { it.code == code } }
                    LanguagePicker(stringResource(R.string.translate_to), list.to.map { it.code to it.name }, to?.code) { code -> to = list.to.first { it.code == code } }
                    if (info?.inProgress == true) Text(stringResource(R.string.translating), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val f = from
            val t = to
            TextButton(enabled = f != null && t != null && f.code != t.code, onClick = {
                vm.translate(f!!.code, t!!.code)
                onDismiss()
            }) { Text(stringResource(R.string.translate_action)) }
        },
        dismissButton = {
            Row {
                if (info?.translatedTo != null) TextButton(onClick = { vm.showOriginal(); onDismiss() }) { Text(stringResource(R.string.menu_show_original)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(code)
                    expanded = false
                })
            }
        }
    }
}
