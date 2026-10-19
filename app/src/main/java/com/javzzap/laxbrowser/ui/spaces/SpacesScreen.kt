package com.javzzap.laxbrowser.ui.spaces

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Container
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.ContainerUsage
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.ui.common.ColorDot
import com.javzzap.laxbrowser.ui.common.ColorPickerDialog
import com.javzzap.laxbrowser.ui.common.EmojiPickerDialog
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import com.javzzap.laxbrowser.ui.common.ColorPicker
import com.javzzap.laxbrowser.ui.common.ConfirmDialog
import com.javzzap.laxbrowser.ui.common.TextInputDialog

interface SpacesActions {
    fun createSpace(name: String, color: Int, choice: ContainerChoice): Boolean
    fun renameSpace(space: Space, name: String)
    fun setSpaceColor(space: Space, color: Int)
    fun setSpaceEmoji(space: Space, emoji: String?)
    fun moveSpace(space: Space, delta: Int)
    fun deleteSpace(space: Space)
    fun changeSpaceContainer(space: Space, choice: ContainerChoice)
    fun createContainer(name: String, color: Int)
    fun renameContainer(container: Container, name: String)
    fun containerUsage(container: Container): ContainerUsage
    fun deleteContainer(container: Container)
}

private sealed interface SpacesDialog {
    data object NewSpace : SpacesDialog
    data class RenameSpace(val space: Space) : SpacesDialog
    data class SpaceColor(val space: Space) : SpacesDialog
    data class SpaceEmoji(val space: Space) : SpacesDialog
    data class DeleteSpace(val space: Space) : SpacesDialog
    data class ChangeContainer(val space: Space) : SpacesDialog
    data object NewContainer : SpacesDialog
    data class RenameContainer(val container: Container) : SpacesDialog
    data class DeleteContainer(val container: Container, val usage: ContainerUsage) : SpacesDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(state: BrowserState, actions: SpacesActions, onBack: () -> Unit) {
    var dialog by remember { mutableStateOf<SpacesDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_spaces)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Header(stringResource(R.string.spaces)) }
            items(state.sortedSpaces, key = { it.id }) { space ->
                SpaceItem(state, space, actions, onDialog = { dialog = it })
            }
            item {
                TextButton(onClick = { dialog = SpacesDialog.NewSpace }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_space))
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header(stringResource(R.string.containers)) }
            item {
                Text(
                    stringResource(R.string.containers_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.data.containers.sortedBy { it.createdAt }, key = { it.id }) { container ->
                ContainerItem(state, container, actions, onDialog = { dialog = it })
            }
            item {
                TextButton(onClick = { dialog = SpacesDialog.NewContainer }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_container))
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        SpacesDialog.NewSpace -> NewSpaceDialog(state, actions::createSpace) { dialog = null }
        is SpacesDialog.RenameSpace -> TextInputDialog(
            stringResource(R.string.rename_space),
            stringResource(R.string.name),
            d.space.name,
            stringResource(R.string.rename),
            { actions.renameSpace(d.space, it) },
        ) { dialog = null }
        is SpacesDialog.SpaceColor -> ColorPickerDialog(
            title = stringResource(R.string.space_color),
            initial = d.space.color,
            presets = BrowserOperations.DEFAULT_COLORS,
            onPick = { actions.setSpaceColor(d.space, it) },
            onDismiss = { dialog = null },
        )
        is SpacesDialog.SpaceEmoji -> EmojiPickerDialog(
            onPick = { actions.setSpaceEmoji(d.space, it) },
            onRemove = d.space.emoji?.let { { actions.setSpaceEmoji(d.space, null) } },
            onDismiss = { dialog = null },
        )
        is SpacesDialog.ChangeContainer -> ChangeContainerDialog(state, d.space, { actions.changeSpaceContainer(d.space, it) }) { dialog = null }
        is SpacesDialog.DeleteSpace -> {
            val tabs = state.data.tabs.filter { it.spaceId == d.space.id }
            val pinned = tabs.count { it.isPinned }
            val containerName = state.container(d.space.containerId)?.name ?: stringResource(R.string.missing_container)
            ConfirmDialog(
                title = stringResource(R.string.delete_space_title, d.space.name),
                message = stringResource(
                    R.string.delete_space_message,
                    pluralStringResource(R.plurals.tabs_count, tabs.size, tabs.size),
                    pluralStringResource(R.plurals.pinned_count, pinned, pinned),
                    containerName,
                ),
                confirmLabel = stringResource(R.string.delete_space),
                onConfirm = { actions.deleteSpace(d.space) },
                onDismiss = { dialog = null },
            )
        }
        SpacesDialog.NewContainer -> TextInputDialog(
            stringResource(R.string.new_container),
            stringResource(R.string.name),
            "",
            stringResource(R.string.create),
            {
                actions.createContainer(it, BrowserOperations.DEFAULT_COLORS[state.data.containers.size % BrowserOperations.DEFAULT_COLORS.size])
            },
        ) { dialog = null }
        is SpacesDialog.RenameContainer -> TextInputDialog(
            stringResource(R.string.rename_container),
            stringResource(R.string.name),
            d.container.name,
            stringResource(R.string.rename),
            { actions.renameContainer(d.container, it) },
        ) { dialog = null }
        is SpacesDialog.DeleteContainer -> if (d.usage.isUnused) {
            ConfirmDialog(
                title = stringResource(R.string.delete_container_title, d.container.name),
                message = stringResource(R.string.delete_container_message),
                confirmLabel = stringResource(R.string.delete_container_confirm),
                onConfirm = { actions.deleteContainer(d.container) },
                onDismiss = { dialog = null },
            )
        } else {
            val essentials = d.usage.tabs.count { it.isEssential }
            val others = d.usage.tabs.count { !it.isEssential }
            val parts = buildList {
                if (d.usage.spaces.isNotEmpty()) add(stringResource(R.string.usage_spaces, d.usage.spaces.joinToString { it.name }))
                if (essentials > 0) add(pluralStringResource(R.plurals.usage_essentials, essentials, essentials))
                if (others > 0) add(pluralStringResource(R.plurals.tabs_count, others, others))
                if (d.usage.webApps.isNotEmpty()) add(stringResource(R.string.usage_web_apps, d.usage.webApps.joinToString { it.name }))
            }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.container_in_use_title, d.container.name)) },
                text = { Text(stringResource(R.string.container_in_use_message) + "\n\n" + parts.joinToString("\n")) },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
    )
}

@Composable
private fun SpaceItem(state: BrowserState, space: Space, actions: SpacesActions, onDialog: (SpacesDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val container = state.container(space.containerId)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SpaceIcon(space, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (space.id == state.activeSpace?.id) stringResource(R.string.space_current, space.name) else space.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (container != null) stringResource(R.string.container_label, container.name) else stringResource(R.string.container_missing_assign),
                style = MaterialTheme.typography.bodySmall,
                color = if (container == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { actions.moveSpace(space, -1) }) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_item_up, space.name))
        }
        IconButton(onClick = { actions.moveSpace(space, 1) }) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_item_down, space.name))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, space.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onDialog(SpacesDialog.RenameSpace(space)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.change_color)) }, onClick = { menu = false; onDialog(SpacesDialog.SpaceColor(space)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.space_icon_emoji)) }, onClick = { menu = false; onDialog(SpacesDialog.SpaceEmoji(space)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.change_container)) }, onClick = { menu = false; onDialog(SpacesDialog.ChangeContainer(space)) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_ellipsis), color = MaterialTheme.colorScheme.error) },
                    enabled = state.data.spaces.size > 1,
                    onClick = { menu = false; onDialog(SpacesDialog.DeleteSpace(space)) },
                )
            }
        }
    }
}

@Composable
private fun ContainerItem(state: BrowserState, container: Container, actions: SpacesActions, onDialog: (SpacesDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val spaces = state.data.spaces.filter { it.containerId == container.id }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(container.color, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(container.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (spaces.isEmpty()) stringResource(R.string.container_unused) else stringResource(R.string.container_used_by, spaces.joinToString { it.name }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options_for, container.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onDialog(SpacesDialog.RenameContainer(container)) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_ellipsis), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDialog(SpacesDialog.DeleteContainer(container, actions.containerUsage(container))) },
                )
            }
        }
    }
}

@Composable
private fun ColorDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var color by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.space_color)) },
        text = { ColorPicker(color) { color = it } },
        confirmButton = { TextButton(onClick = { onConfirm(color); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
