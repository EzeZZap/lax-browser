package com.javzzap.laxbrowser.ui.webapps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.ui.InstallCandidate
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import com.javzzap.laxbrowser.ui.common.SiteIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppsScreen(
    state: BrowserState,
    pinnedIds: Set<String>,
    launcherSupported: Boolean,
    loadIcon: suspend (WebApp) -> Bitmap?,
    onRefresh: () -> Unit,
    onOpen: (WebApp) -> Unit,
    onRepin: (WebApp) -> Unit,
    onRemove: (WebApp) -> Unit,
    onBack: () -> Unit,
) {
    var removing by remember { mutableStateOf<WebApp?>(null) }
    LaunchedEffect(Unit) { onRefresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_web_apps)) },
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
                Text(
                    stringResource(R.string.web_apps_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (!launcherSupported) {
                    Text(
                        stringResource(R.string.web_apps_launcher_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.data.webApps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.web_apps_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.data.webApps.sortedBy { it.name.lowercase() }, key = { it.id }) { app ->
                var menu by remember { mutableStateOf(false) }
                val icon by produceState<Bitmap?>(null, app.id, app.iconFile) { value = loadIcon(app) }
                val pinned = app.id in pinnedIds
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(icon, app)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val containerName = state.container(app.containerId)?.name ?: stringResource(R.string.missing_container)
                        Text(
                            stringResource(R.string.web_app_origin_container, UrlInput.originOf(app.startUrl) ?: app.startUrl, containerName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(if (pinned) R.string.web_app_on_home_screen else R.string.web_app_not_on_home_screen),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, app.name))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menu = false; onOpen(app) })
                            if (!pinned && launcherSupported) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.web_app_repin)) }, onClick = { menu = false; onRepin(app) })
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_ellipsis), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; removing = app },
                            )
                        }
                    }
                }
            }
        }
    }

    removing?.let { app ->
        ConfirmDialog(
            title = stringResource(R.string.remove_web_app_title, app.name),
            message = stringResource(R.string.remove_web_app_message),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = { onRemove(app) },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun AppIcon(icon: Bitmap?, app: WebApp) {
    if (icon != null) {
        Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
    } else {
        SiteIcon(app.startUrl, app.name, null, size = 40.dp)
    }
}

@Composable
fun InstallDialog(candidate: InstallCandidate, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = candidate.manifest.displayName ?: candidate.origin
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                candidate.icon.asImageBitmap(),
                contentDescription = stringResource(R.string.app_icon),
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
        },
        title = { Text(stringResource(R.string.install_title, name)) },
        text = {
            Column {
                Text(candidate.origin, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.install_explanation, candidate.containerName), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
