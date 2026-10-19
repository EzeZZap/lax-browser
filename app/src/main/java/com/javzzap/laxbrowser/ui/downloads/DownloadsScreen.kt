package com.javzzap.laxbrowser.ui.downloads

import android.content.Intent
import android.text.format.Formatter
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.Download
import com.javzzap.laxbrowser.domain.DownloadStatus
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.ui.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val downloads by remember(vm) { vm.downloads }.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_downloads)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(downloads, key = { it.id }) { download -> DownloadRow(download, vm) }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: Download, vm: BrowserViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val done = download.status == DownloadStatus.COMPLETED
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = done) { vm.openDownload(download) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (download.status == DownloadStatus.FAILED) Icons.Default.ErrorOutline else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (download.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(download.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
            val status = when (download.status) {
                DownloadStatus.IN_PROGRESS -> download.totalBytes?.let { "${size(download.downloadedBytes)} / ${size(it)}" } ?: size(download.downloadedBytes)
                DownloadStatus.COMPLETED -> "${size(download.downloadedBytes)} · ${UrlInput.displayHost(download.url)}"
                DownloadStatus.FAILED -> stringResource(R.string.download_failed)
                DownloadStatus.CANCELLED -> stringResource(R.string.download_cancelled)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (download.status == DownloadStatus.IN_PROGRESS) {
                val total = download.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        if (download.status == DownloadStatus.IN_PROGRESS) {
            IconButton(onClick = { vm.cancelDownload(download) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.download_cancel)) }
        } else {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, download.fileName)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (done) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menu = false; vm.openDownload(download) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_share)) }, onClick = {
                            menu = false
                            download.contentUri?.let { uri ->
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType(download.mimeType ?: "*/*")
                                    .putExtra(Intent.EXTRA_STREAM, uri.toUri())
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            }
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.download_remove)) }, onClick = { menu = false; vm.deleteDownload(download, deleteFile = false) })
                    if (done) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_delete_file), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; vm.deleteDownload(download, deleteFile = true) },
                        )
                    }
                }
            }
        }
    }
}
