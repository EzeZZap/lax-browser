package com.javzzap.laxbrowser.ui.logins

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.pluralStringResource
import com.javzzap.laxbrowser.domain.LoginImportSummary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.SavedLogin
import com.javzzap.laxbrowser.platform.DeviceAuth
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import com.javzzap.laxbrowser.ui.common.SiteIcon

/**
 * Saved logins: turn saving on or off, find, view, copy, and delete logins, and manage sites that
 * are never saved. Showing or copying a password asks for the screen lock first, and the screen
 * cannot be captured in screenshots or the recent apps preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginsScreen(vm: BrowserViewModel, saveLogins: Boolean, onSaveLoginsChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val data by vm.savedLogins.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val canProtect = remember { DeviceAuth.isAvailable(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var opened by remember { mutableStateOf<SavedLogin?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var confirmingExport by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Pair<Uri, LoginImportSummary>?>(null) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { file -> vm.importLogins(file) { summary -> if (summary != null) importResult = file to summary } }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportLogins) }

    LaunchedEffect(Unit) { vm.loadLogins() }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val logins = data?.logins.orEmpty()
        .filter { query.isBlank() || it.host.contains(query.trim(), ignoreCase = true) || it.username.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy({ it.host }, { it.username }))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logins_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { generating = true }) {
                        Icon(Icons.Default.Password, contentDescription = stringResource(R.string.password_generator_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logins_import)) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    importFile.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
                                },
                            )
                            // Exporting reveals every password, so it needs the screen lock like showing one does.
                            if (canProtect && !data?.logins.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logins_export)) },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmingExport = true
                                    },
                                )
                            }
                            if (!data?.logins.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logins_delete_all)) },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        deletingAll = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "toggle") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = saveLogins, role = Role.Switch, onValueChange = onSaveLoginsChange)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.logins_save_toggle), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.logins_save_toggle_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = saveLogins, onCheckedChange = null)
                }
            }
            if (!canProtect) {
                item(key = "no_lock") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.logins_need_lock), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.logins_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (data != null && logins.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (query.isBlank()) R.string.logins_empty else R.string.logins_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(logins, key = { it.guid }) { login ->
                Row(
                    Modifier.fillMaxWidth().clickable { opened = login }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteIcon(login.origin, login.host, null, size = 28.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(login.host, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            login.username.ifBlank { stringResource(R.string.login_no_username) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val never = data?.neverSave.orEmpty().sorted()
            if (never.isNotEmpty()) {
                item(key = "never_header") {
                    Text(
                        stringResource(R.string.logins_never_saved),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(never, key = { "never_$it" }) { site ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(site.substringAfter("://"), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.allowSavingFor(site) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.logins_allow_saving, site.substringAfter("://")))
                        }
                    }
                }
            }
        }
    }

    val authTitle = stringResource(R.string.logins_auth_title)
    opened?.let { login ->
        LoginDialog(
            login = login,
            canProtect = canProtect,
            authenticate = { onSuccess ->
                (activity as? FragmentActivity)?.let { DeviceAuth.authenticate(it, authTitle, onSuccess) }
            },
            onCopyUsername = { vm.copyUsername(login.username) },
            onCopyPassword = { vm.copySecret(login.password) },
            onDelete = {
                vm.deleteLogin(login)
                opened = null
            },
            onDismiss = { opened = null },
        )
    }
    if (deletingAll) {
        ConfirmDialog(
            title = stringResource(R.string.logins_delete_all),
            message = stringResource(R.string.logins_delete_all_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = vm::deleteAllLogins,
            onDismiss = { deletingAll = false },
        )
    }
    if (generating) PasswordGeneratorDialog(onCopy = vm::copySecret, onDismiss = { generating = false })
    if (confirmingExport) {
        val exportAuthTitle = stringResource(R.string.logins_export_auth_title)
        ConfirmDialog(
            title = stringResource(R.string.logins_export_title),
            message = stringResource(R.string.logins_export_message),
            confirmLabel = stringResource(R.string.logins_export_confirm),
            onConfirm = {
                (activity as? FragmentActivity)?.let { DeviceAuth.authenticate(it, exportAuthTitle) { exportFile.launch("lax-logins.csv") } }
            },
            onDismiss = { confirmingExport = false },
        )
    }
    importResult?.let { (file, summary) ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            title = { Text(stringResource(R.string.logins_imported_title)) },
            text = {
                Column {
                    Text(pluralStringResource(R.plurals.logins_imported_count, summary.imported, summary.imported))
                    if (summary.skipped > 0) Text(pluralStringResource(R.plurals.logins_already_saved_count, summary.skipped, summary.skipped))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.logins_imported_delete_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteImportedFile(file)
                    importResult = null
                }) { Text(stringResource(R.string.logins_imported_delete_file), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { importResult = null }) { Text(stringResource(R.string.logins_imported_keep_file)) } },
        )
    }
}

/** One login: username (copy), password hidden until the screen lock is confirmed, delete. */
@Composable
private fun LoginDialog(
    login: SavedLogin,
    canProtect: Boolean,
    authenticate: (onSuccess: () -> Unit) -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var revealed by remember(login.guid) { mutableStateOf(false) }
    var confirmingDelete by remember(login.guid) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { SiteIcon(login.origin, login.host, null, size = 32.dp) },
        title = { Text(login.host, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(stringResource(R.string.login_username), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(login.username.ifBlank { stringResource(R.string.login_no_username) }, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (login.username.isNotBlank()) {
                        IconButton(onClick = onCopyUsername) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.login_copy_username)) }
                    }
                }
                Text(stringResource(R.string.login_password), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (revealed) login.password else "••••••••",
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canProtect) {
                        if (!revealed) {
                            IconButton(onClick = { authenticate { revealed = true } }) {
                                Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.login_show_password))
                            }
                        }
                        IconButton(onClick = { if (revealed) onCopyPassword() else authenticate(onCopyPassword) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.login_copy_password))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = { confirmingDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
    )
    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.login_delete_title),
            message = stringResource(R.string.login_delete_message, login.host),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = onDelete,
            onDismiss = { confirmingDelete = false },
        )
    }
}
