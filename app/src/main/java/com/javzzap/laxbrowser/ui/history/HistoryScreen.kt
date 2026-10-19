package com.javzzap.laxbrowser.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.HistoryEntry
import com.javzzap.laxbrowser.domain.HistoryPolicy
import com.javzzap.laxbrowser.domain.HistoryRange
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.SiteIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: BrowserViewModel, onOpened: () -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    val entries by remember(query) { vm.history(query) }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember { DateFormat.getLongDateFormat(context) }
    val today = HistoryPolicy.startOfDay(System.currentTimeMillis())
    val yesterday = HistoryPolicy.startOfDay(System.currentTimeMillis(), daysBack = 1)
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)
    val groups = remember(entries, today) {
        entries.groupBy { entry ->
            when {
                entry.visitedAt >= today -> todayLabel
                entry.visitedAt >= yesterday -> yesterdayLabel
                else -> dateFormat.format(entry.visitedAt)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { clearing = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_history))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.history_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(24.dp),
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(if (query.isBlank()) R.string.history_empty else R.string.history_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            groups.forEach { (label, visits) ->
                item(key = "header_$label") {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(visits, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        time = timeFormat.format(entry.visitedAt),
                        onOpen = { if (vm.openHistoryEntry(entry, newTab = false)) onOpened() },
                        onOpenInNewTab = { if (vm.openHistoryEntry(entry, newTab = true)) onOpened() },
                        onCopy = { vm.copyToClipboard(entry.url) },
                        onDelete = { vm.deleteHistoryEntry(entry) },
                    )
                }
            }
        }
    }

    if (clearing) {
        ClearHistoryDialog(onClear = vm::clearHistory, onDismiss = { clearing = false })
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    time: String,
    onOpen: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.action_open), onClick = onOpen)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(entry.url, entry.title, entry.faviconUrl, size = 32.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title.ifBlank { UrlInput.displayHost(entry.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.history_host_time, UrlInput.displayHost(entry.url), time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, entry.title.ifBlank { entry.url }))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ctx_open_new_tab)) }, onClick = { menu = false; onOpenInNewTab() })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_copy_link)) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** Lets the user pick how much history to delete. */
@Composable
fun ClearHistoryDialog(onClear: (HistoryRange) -> Unit, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(HistoryRange.LAST_HOUR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_history_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                listOf(
                    HistoryRange.LAST_HOUR to R.string.clear_history_last_hour,
                    HistoryRange.TODAY to R.string.clear_history_today,
                    HistoryRange.LAST_7_DAYS to R.string.clear_history_last_7_days,
                    HistoryRange.ALL to R.string.clear_history_all,
                ).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = range == value, role = Role.RadioButton, onClick = { range = value })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = range == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
                Text(
                    stringResource(R.string.clear_history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onClear(range); onDismiss() }) {
                Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
