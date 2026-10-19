package com.javzzap.laxbrowser.ui.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.Bookmark
import com.javzzap.laxbrowser.domain.BookmarkFolder
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import com.javzzap.laxbrowser.ui.common.LinkEditDialog
import com.javzzap.laxbrowser.ui.common.SiteIcon
import com.javzzap.laxbrowser.ui.common.TextInputDialog

interface BookmarksActions {
    fun open(bookmark: Bookmark)
    fun update(bookmark: Bookmark, title: String, url: String, folderId: String?): Boolean
    fun remove(bookmark: Bookmark)
    fun moveBookmark(bookmark: Bookmark, folderId: String?)
    fun createFolder(name: String, parentId: String?)
    fun renameFolder(folder: BookmarkFolder, name: String)
    fun moveFolder(folder: BookmarkFolder, parentId: String?)
    fun deleteFolder(folder: BookmarkFolder)
    fun folderContents(folder: BookmarkFolder): Pair<Int, Int>
}

private sealed interface BookmarksDialog {
    data class EditBookmark(val bookmark: Bookmark) : BookmarksDialog
    data class MoveBookmark(val bookmark: Bookmark) : BookmarksDialog
    data class RemoveBookmark(val bookmark: Bookmark) : BookmarksDialog
    data object NewFolder : BookmarksDialog
    data class RenameFolder(val folder: BookmarkFolder) : BookmarksDialog
    data class MoveFolder(val folder: BookmarkFolder) : BookmarksDialog
    data class DeleteFolder(val folder: BookmarkFolder) : BookmarksDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(state: BrowserState, actions: BookmarksActions, onBack: () -> Unit) {
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = state.bookmarkFolder(openFolderId)
    val folderId = folder?.id
    var dialog by remember { mutableStateOf<BookmarksDialog?>(null) }
    val goUp = { openFolderId = folder?.let { state.effectiveParent(it) } }
    BackHandler(enabled = folder != null, onBack = goUp)

    val folders = state.childFolders(folderId)
    val bookmarks = state.bookmarksIn(folderId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: stringResource(R.string.bookmarks), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (folder != null) goUp() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { dialog = BookmarksDialog.NewFolder }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            state.activeSpace?.let {
                item {
                    Text(
                        stringResource(R.string.bookmarks_hint_space, it.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (folders.isEmpty() && bookmarks.isEmpty()) {
                item {
                    Text(
                        stringResource(if (folder == null) R.string.bookmarks_empty else R.string.folder_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            items(folders, key = { "folder_${it.id}" }) { item ->
                FolderRow(item, onOpen = { openFolderId = item.id }, onDialog = { dialog = it })
            }
            items(bookmarks, key = { "bookmark_${it.id}" }) { bookmark ->
                BookmarkRow(bookmark, onOpen = { actions.open(bookmark) }, onDialog = { dialog = it })
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is BookmarksDialog.EditBookmark -> BookmarkEditDialog(
            state = state,
            bookmark = d.bookmark,
            onSave = { title, url, folder -> actions.update(d.bookmark, title, url, folder) },
            onDismiss = { dialog = null },
        )
        is BookmarksDialog.MoveBookmark -> FolderPickerDialog(
            state = state,
            title = stringResource(R.string.move_to_folder),
            selected = d.bookmark.folderId,
            excluded = emptySet(),
            onPick = { actions.moveBookmark(d.bookmark, it) },
            onDismiss = { dialog = null },
        )
        is BookmarksDialog.RemoveBookmark -> ConfirmDialog(
            title = stringResource(R.string.delete_bookmark_title),
            message = stringResource(R.string.delete_bookmark_message, d.bookmark.title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { actions.remove(d.bookmark) },
            onDismiss = { dialog = null },
        )
        BookmarksDialog.NewFolder -> TextInputDialog(
            title = stringResource(R.string.new_folder),
            label = stringResource(R.string.folder_name),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { actions.createFolder(it, folderId) },
            onDismiss = { dialog = null },
        )
        is BookmarksDialog.RenameFolder -> TextInputDialog(
            title = stringResource(R.string.rename_folder),
            label = stringResource(R.string.folder_name),
            initial = d.folder.name,
            confirmLabel = stringResource(R.string.rename),
            onConfirm = { actions.renameFolder(d.folder, it) },
            onDismiss = { dialog = null },
        )
        is BookmarksDialog.MoveFolder -> FolderPickerDialog(
            state = state,
            title = stringResource(R.string.move_to_folder),
            selected = d.folder.parentId,
            excluded = state.folderSubtree(d.folder.id),
            onPick = { actions.moveFolder(d.folder, it) },
            onDismiss = { dialog = null },
        )
        is BookmarksDialog.DeleteFolder -> {
            val (subfolders, contained) = actions.folderContents(d.folder)
            ConfirmDialog(
                title = stringResource(R.string.delete_folder_title, d.folder.name),
                message = stringResource(
                    R.string.delete_folder_message,
                    pluralStringResource(R.plurals.subfolders_count, subfolders, subfolders),
                    pluralStringResource(R.plurals.bookmarks_count, contained, contained),
                ),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { actions.deleteFolder(d.folder) },
                onDismiss = { dialog = null },
            )
        }
    }
}

@Composable
private fun FolderRow(folder: BookmarkFolder, onOpen: () -> Unit, onDialog: (BookmarksDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val description = stringResource(R.string.folder_cd, folder.name)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.open_folder), onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, folder.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onDialog(BookmarksDialog.RenameFolder(folder)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move)) }, onClick = { menu = false; onDialog(BookmarksDialog.MoveFolder(folder)) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_ellipsis), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDialog(BookmarksDialog.DeleteFolder(folder)) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onDialog: (BookmarksDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val description = stringResource(R.string.bookmark_cd, bookmark.title)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.open_bookmark), onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(bookmark.url, bookmark.title, bookmark.faviconUrl, size = 36.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                UrlInput.displayHost(bookmark.url),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, bookmark.title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onDialog(BookmarksDialog.EditBookmark(bookmark)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move)) }, onClick = { menu = false; onDialog(BookmarksDialog.MoveBookmark(bookmark)) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_ellipsis), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDialog(BookmarksDialog.RemoveBookmark(bookmark)) },
                )
            }
        }
    }
}

/** Edits a bookmark's title, address, and folder. */
@Composable
fun BookmarkEditDialog(
    state: BrowserState,
    bookmark: Bookmark,
    onSave: (title: String, url: String, folderId: String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    var folderId by remember { mutableStateOf(bookmark.folderId?.takeIf { state.bookmarkFolder(it) != null }) }
    var picking by remember { mutableStateOf(false) }
    LinkEditDialog(
        title = stringResource(R.string.edit_bookmark),
        initialTitle = bookmark.title,
        initialUrl = bookmark.url,
        confirmLabel = stringResource(R.string.save),
        onConfirm = { title, url -> onSave(title, url, folderId) },
        onDismiss = onDismiss,
        extra = {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.folder_label, state.bookmarkFolder(folderId)?.name ?: stringResource(R.string.bookmarks)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
    if (picking) {
        FolderPickerDialog(
            state = state,
            title = stringResource(R.string.choose_folder),
            selected = folderId,
            excluded = emptySet(),
            onPick = { folderId = it },
            onDismiss = { picking = false },
        )
    }
}

/** Lets the user pick a folder (or the root) from the folder tree, skipping [excluded] folders. */
@Composable
fun FolderPickerDialog(
    state: BrowserState,
    title: String,
    selected: String?,
    excluded: Set<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(selected) }
    val rows = remember(state.data.bookmarkFolders, excluded) {
        buildList {
            fun walk(parentId: String?, depth: Int) {
                state.childFolders(parentId).filter { it.id !in excluded }.forEach {
                    add(it to depth)
                    walk(it.id, depth + 1)
                }
            }
            walk(null, 1)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                FolderChoice(stringResource(R.string.bookmarks), depth = 0, selected = choice == null) { choice = null }
                rows.forEach { (folder, depth) ->
                    FolderChoice(folder.name, depth, selected = choice == folder.id) { choice = folder.id }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice); onDismiss() }) { Text(stringResource(R.string.move_here)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderChoice(name: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
