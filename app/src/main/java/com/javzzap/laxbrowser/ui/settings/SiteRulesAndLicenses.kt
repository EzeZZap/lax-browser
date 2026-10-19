package com.javzzap.laxbrowser.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.SiteRules
import com.javzzap.laxbrowser.domain.SiteSetting
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.platform.Licenses
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-site zoom levels and "always open in this Space" rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRulesScreen(
    state: BrowserState,
    onSetSpace: (host: String, space: Space?) -> Unit,
    onRemove: (host: String) -> Unit,
    onBack: () -> Unit,
) {
    val sites = state.data.siteSettings.sortedBy { it.host }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_rules_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.site_rules_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (sites.isEmpty()) {
                item { Text(stringResource(R.string.site_rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(sites, key = { it.host }) { site ->
                SiteRow(site, state, onSetSpace, onRemove)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SiteRow(site: SiteSetting, state: BrowserState, onSetSpace: (String, Space?) -> Unit, onRemove: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val space = state.space(site.spaceId)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(site.host, style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                stringResource(R.string.site_zoom_value, site.zoomPercent).takeIf { site.zoomPercent != SiteRules.DEFAULT_ZOOM },
                space?.let { stringResource(R.string.site_opens_in, it.name) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { menu = true }) {
                if (space != null) {
                    SpaceIcon(space)
                    Spacer(Modifier.width(6.dp))
                }
                Text(space?.name ?: stringResource(R.string.site_any_space))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.site_any_space)) }, onClick = { menu = false; onSetSpace(site.host, null) })
                state.sortedSpaces.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        leadingIcon = { SpaceIcon(option) },
                        onClick = { menu = false; onSetSpace(site.host, option) },
                    )
                }
            }
        }
        IconButton(onClick = { onRemove(site.host) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.site_rule_remove, site.host)) }
    }
}

/** Open-source components and their license texts, readable offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    var showing by remember { mutableStateOf<Licenses.License?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.licenses_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(Licenses.components) { component ->
                Column(Modifier.fillMaxWidth().clickable { showing = component.license }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(component.name, style = MaterialTheme.typography.bodyLarge)
                    Text(component.license.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        component.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onOpenUrl(component.url) },
                    )
                }
                HorizontalDivider()
            }
        }
    }
    showing?.let { license ->
        val context = LocalContext.current
        val text by produceState("", license) {
            value = withContext(Dispatchers.IO) { runCatching { context.assets.open(license.asset).bufferedReader().use { it.readText() } }.getOrDefault("") }
        }
        AlertDialog(
            onDismissRequest = { showing = null },
            title = { Text(license.title) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showing = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}
