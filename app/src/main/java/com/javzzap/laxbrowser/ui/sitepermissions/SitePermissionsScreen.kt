package com.javzzap.laxbrowser.ui.sitepermissions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.engine.gecko.SitePermission
import com.javzzap.laxbrowser.engine.gecko.SitePermissionEntry
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.ConfirmDialog

/** Permissions Gecko stored per site and container; each can be allowed, blocked, or forgotten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePermissionsScreen(vm: BrowserViewModel, state: BrowserState, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<SitePermissionEntry>?>(null) }
    var clearing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) { entries = vm.sitePermissions() }
    // Groups by site and container, so the same site in two containers shows twice.
    val groups = entries.orEmpty().groupBy { it.host to it.contextId }.toList().sortedBy { it.first.first }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_permissions_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (entries != null && groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.site_permissions_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { (key, permissions) ->
                val (host, contextId) = key
                item(key = "$host|$contextId") {
                    val containerName = contextId?.let { state.container(it)?.name }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (containerName != null) stringResource(R.string.site_in_container, host, containerName) else host,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        IconButton(onClick = { clearing = permissions.first().uri }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.site_clear_data))
                        }
                    }
                }
                items(permissions, key = { "${it.host}|${it.contextId}|${it.permission}" }) { entry ->
                    PermissionRow(entry) { allowed ->
                        vm.setSitePermission(entry, allowed)
                        refresh++
                    }
                }
                item(key = "$host|$contextId|divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            }
        }
    }
    clearing?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.site_clear_data),
            message = stringResource(R.string.clear_data_cookies_summary),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = { vm.clearSiteData(uri) },
            onDismiss = { clearing = null },
        )
    }
}

@Composable
private fun PermissionRow(entry: SitePermissionEntry, onChange: (Boolean?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(sitePermissionName(entry.permission)), style = MaterialTheme.typography.bodyLarge)
        }
        Box {
            TextButton(onClick = { menu = true }) {
                Text(
                    stringResource(if (entry.allowed) R.string.site_perm_allowed else R.string.site_perm_blocked),
                    color = if (entry.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.allow)) }, onClick = { menu = false; onChange(true) })
                DropdownMenuItem(text = { Text(stringResource(R.string.block)) }, onClick = { menu = false; onChange(false) })
                DropdownMenuItem(text = { Text(stringResource(R.string.site_perm_forget)) }, onClick = { menu = false; onChange(null) })
            }
        }
    }
}

private fun sitePermissionName(permission: SitePermission): Int = when (permission) {
    SitePermission.LOCATION -> R.string.site_perm_location
    SitePermission.NOTIFICATIONS -> R.string.site_perm_notifications
    SitePermission.CAMERA -> R.string.site_perm_camera
    SitePermission.MICROPHONE -> R.string.site_perm_microphone
    SitePermission.DRM -> R.string.site_perm_drm
    SitePermission.AUTOPLAY_AUDIBLE -> R.string.site_perm_autoplay_audible
    SitePermission.AUTOPLAY_INAUDIBLE -> R.string.site_perm_autoplay_inaudible
    SitePermission.PERSISTENT_STORAGE -> R.string.site_perm_persistent_storage
    SitePermission.STORAGE_ACCESS -> R.string.site_perm_storage_access
    SitePermission.LOCAL_NETWORK -> R.string.site_perm_local_network
    SitePermission.LOCAL_DEVICE -> R.string.site_perm_local_device
}
