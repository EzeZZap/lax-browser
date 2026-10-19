package com.javzzap.laxbrowser.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.UrlInput

/** Loads favicons by URL. Null in previews and tests, where letter avatars are shown. */
fun interface FaviconLoader {
    /** [ephemeral] icons (private tabs) must not be written to disk. */
    suspend fun load(url: String, ephemeral: Boolean): Bitmap?
}

val LocalFaviconLoader = staticCompositionLocalOf<FaviconLoader?> { null }

/**
 * A site's favicon. Until it loads (or when there is none), well-known sites show their bundled
 * icon and others a letter avatar.
 */
@Composable
fun SiteIcon(
    url: String,
    title: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    dimmed: Boolean = false,
    ephemeral: Boolean = false,
) {
    val loader = LocalFaviconLoader.current
    val bitmap by produceState<Bitmap?>(null, iconUrl, loader, ephemeral) {
        value = if (iconUrl != null && loader != null) loader.load(iconUrl, ephemeral) else null
    }
    val icon = bitmap
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .alpha(if (dimmed) 0.45f else 1f),
        )
    } else {
        val bundled = remember(url) { BundledIcons.forUrl(url) }
        if (bundled != null) {
            Image(
                painter = painterResource(bundled),
                contentDescription = null,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(size / 4))
                    .alpha(if (dimmed) 0.45f else 1f),
            )
        } else {
            LetterAvatar(url, title, modifier, size, dimmed)
        }
    }
}

@Composable
private fun LetterAvatar(url: String, title: String, modifier: Modifier, size: Dp, dimmed: Boolean) {
    val host = UrlInput.displayHost(url)
    val label = (title.ifBlank { host }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val base = avatarColor(host.ifEmpty { title })
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dimmed) base.copy(alpha = 0.4f) else base),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
    }
}

private fun avatarColor(key: String): Color {
    val palette = BrowserOperations.DEFAULT_COLORS
    return Color(palette[(key.hashCode() and 0x7fffffff) % palette.size])
}

@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color(color)))
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrowserOperations.DEFAULT_COLORS.forEachIndexed { index, color ->
            val isSelected = color == selected
            val description = stringResource(R.string.color_n, index + 1)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (isSelected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(color) }
                    .semantics {
                        contentDescription = description
                        this.selected = isSelected
                    },
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Title + URL editor used for bookmarks and NTP shortcuts. [extra] adds fields such as a folder picker. */
@Composable
fun LinkEditDialog(
    title: String,
    initialTitle: String,
    initialUrl: String,
    confirmLabel: String,
    onConfirm: (title: String, url: String) -> Boolean,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.link_title)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text(stringResource(R.string.link_address)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (UrlInput.normalizeWebUrl(url) == null) {
                    error = R.string.error_invalid_address
                } else if (onConfirm(name, url)) {
                    onDismiss()
                } else {
                    error = R.string.error_not_saved
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
