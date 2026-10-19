package com.javzzap.laxbrowser.ui.ntp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.ui.common.LaxWordmark
import com.javzzap.laxbrowser.ui.common.SpaceIcon
import com.javzzap.laxbrowser.ui.common.brandGradientColors
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.Shortcut
import com.javzzap.laxbrowser.ui.common.LinkEditDialog
import com.javzzap.laxbrowser.ui.common.SiteIcon

interface NewTabPageActions {
    /** Opens the sidebar with its address bar focused, which has search suggestions. */
    fun focusAddressBar()
    fun openShortcut(shortcut: Shortcut)
    fun addShortcut(title: String, url: String): Boolean
    fun updateShortcut(shortcut: Shortcut, title: String, url: String): Boolean
    fun removeShortcut(shortcut: Shortcut)
    fun moveShortcut(shortcut: Shortcut, delta: Int)
}

/**
 * The new tab page: the Lax wordmark over a soft wash of the Space's color, the Space's name, a
 * search field, and shortcuts.
 */
@Composable
fun NewTabPage(
    space: Space?,
    shortcuts: List<Shortcut>,
    actions: NewTabPageActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    privateMode: Boolean = false,
) {
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var adding by remember { mutableStateOf(false) }
    val wash by animateColorAsState(
        if (!privateMode && space != null) Color(space.color) else MaterialTheme.colorScheme.primary,
        tween(450),
        label = "ntp_wash",
    )

    // A Surface sets the content color, so text follows the light or dark theme.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to wash.copy(alpha = 0.22f), 0.6f to Color.Transparent))
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(24.dp)
                .testTag("new_tab_page"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            if (privateMode) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.private_browsing), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.private_ntp_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            } else {
                LaxWordmark()
                space?.let {
                    Spacer(Modifier.height(8.dp))
                    SpacePill(it)
                }
            }
            Spacer(Modifier.height(28.dp))
            SearchButton(onClick = actions::focusAddressBar)
            Spacer(Modifier.height(36.dp))
            Text(
                stringResource(R.string.shortcuts),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth().widthIn(max = 560.dp).testTag("ntp_shortcuts"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                shortcuts.sortedBy { it.position }.forEach { shortcut ->
                    ShortcutTile(shortcut, actions, onEdit = { editing = shortcut })
                }
                AddShortcutTile(onClick = { adding = true })
            }
            if (shortcuts.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.shortcuts_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                )
            }
        }
    }

    if (adding) {
        LinkEditDialog(
            title = stringResource(R.string.add_shortcut),
            initialTitle = "",
            initialUrl = "",
            confirmLabel = stringResource(R.string.add),
            onConfirm = actions::addShortcut,
            onDismiss = { adding = false },
        )
    }
    editing?.let { shortcut ->
        LinkEditDialog(
            title = stringResource(R.string.edit_shortcut),
            initialTitle = shortcut.title,
            initialUrl = shortcut.url,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { title, url -> actions.updateShortcut(shortcut, title, url) },
            onDismiss = { editing = null },
        )
    }
}

/** The Space this new tab belongs to, as a tinted pill. */
@Composable
private fun SpacePill(space: Space) {
    val description = stringResource(R.string.ntp_space, space.name)
    Surface(
        shape = CircleShape,
        color = Color(space.color).copy(alpha = 0.16f),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaceIcon(space, size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(space.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Looks like a search field; opens the sidebar's address bar, which has suggestions. */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    val label = stringResource(R.string.url_bar_hint)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, Brush.linearGradient(brandGradientColors())),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .height(56.dp)
            .testTag("ntp_search")
            .semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(shortcut: Shortcut, actions: NewTabPageActions, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val description = stringResource(R.string.shortcut_cd, shortcut.title)
    Box {
        Column(
            Modifier
                .size(width = 76.dp, height = 88.dp)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.open_shortcut),
                    onLongClickLabel = stringResource(R.string.shortcut_options),
                    onLongClick = { menu = true },
                    onClick = { actions.openShortcut(shortcut) },
                )
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { SiteIcon(shortcut.url, shortcut.title, shortcut.iconUrl, size = 32.dp) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                shortcut.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_left)) }, onClick = { menu = false; actions.moveShortcut(shortcut, -1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_right)) }, onClick = { menu = false; actions.moveShortcut(shortcut, 1) })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; actions.removeShortcut(shortcut) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddShortcutTile(onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 76.dp, height = 88.dp)
            .combinedClickable(onClickLabel = stringResource(R.string.add_shortcut), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = TileShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.add), style = MaterialTheme.typography.labelSmall)
    }
}

/** Rounded squares, echoing the icon's soft "L". */
private val TileShape = RoundedCornerShape(18.dp)
