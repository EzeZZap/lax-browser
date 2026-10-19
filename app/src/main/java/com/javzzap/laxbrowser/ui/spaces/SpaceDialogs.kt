package com.javzzap.laxbrowser.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.ui.common.ColorDot
import com.javzzap.laxbrowser.ui.common.ColorPicker

private const val NEW_CONTAINER = "__new__"

@Composable
private fun ContainerOptions(
    state: BrowserState,
    selected: String,
    newContainerLabel: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        ChoiceRow(newContainerLabel, null, selected == NEW_CONTAINER) { onSelect(NEW_CONTAINER) }
        state.data.containers.sortedBy { it.createdAt }.forEach { container ->
            ChoiceRow(container.name, container.color, selected == container.id) { onSelect(container.id) }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, color: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        color?.let { ColorDot(it); Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
}

@Composable
fun NewSpaceDialog(state: BrowserState, onCreate: (String, Int, ContainerChoice) -> Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(BrowserOperations.DEFAULT_COLORS[state.data.spaces.size % BrowserOperations.DEFAULT_COLORS.size]) }
    var container by remember { mutableStateOf(NEW_CONTAINER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_space)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(color) { color = it }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.container), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.container_sharing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ContainerOptions(state, container, stringResource(R.string.new_container_separate)) { container = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val choice = if (container == NEW_CONTAINER) ContainerChoice.New(name, color) else ContainerChoice.Existing(container)
                if (onCreate(name, color, choice)) onDismiss()
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ChangeContainerDialog(
    state: BrowserState,
    space: Space,
    onConfirm: (ContainerChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var container by remember { mutableStateOf(space.containerId) }
    val tabCount = state.data.tabs.count { it.spaceId == space.id }
    val oldName = state.container(space.containerId)?.name ?: stringResource(R.string.missing_container)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.container_for_space, space.name)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                ContainerOptions(state, container, stringResource(R.string.new_container)) { container = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.change_container_warning, pluralStringResource(R.plurals.tabs_count, tabCount, tabCount), oldName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = container != space.containerId, onClick = {
                onConfirm(if (container == NEW_CONTAINER) ContainerChoice.New(space.name, space.color) else ContainerChoice.Existing(container))
                onDismiss()
            }) { Text(stringResource(R.string.change)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MoveToSpaceDialog(state: BrowserState, tab: Tab, onMove: (Space) -> Unit, onDismiss: () -> Unit) {
    val targets = state.sortedSpaces.filter { it.id != tab.spaceId }
    var selected by remember { mutableStateOf(targets.firstOrNull()?.id) }
    val target = targets.firstOrNull { it.id == selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_tab_to_space)) },
        text = {
            Column(Modifier.selectableGroup()) {
                targets.forEach { space ->
                    ChoiceRow(space.name, space.color, selected == space.id) { selected = space.id }
                }
                if (target != null && target.containerId != tab.containerId) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.move_tab_container_warning, target.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { target?.let(onMove); onDismiss() }) { Text(stringResource(R.string.move_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
