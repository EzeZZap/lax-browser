package com.javzzap.laxbrowser.ui.common

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.Space

/** A Space's icon: its emoji when it has one, otherwise its color dot. */
@Composable
fun SpaceIcon(space: Space, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val emoji = space.emoji
    if (emoji != null) {
        Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = (size.value + 4).sp)
        }
    } else {
        ColorDot(space.color, modifier, size)
    }
}

/**
 * Picks any color: a saturation/brightness square, a hue bar, and a hex field. Android has no
 * standard color picker dialog, so Lax ships this one.
 */
@Composable
fun HsvColorPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val initial = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hue by remember(color) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(color) { mutableFloatStateOf(initial[1]) }
    var value by remember(color) { mutableFloatStateOf(initial[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    val description = stringResource(R.string.color_picker_cd)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    fun update(offset: Offset) {
                        saturation = (offset.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                    detectTapGestures { update(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                },
        ) {
            val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(saturation * size.width, (1f - value) * size.height)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color.Black, radius = 12.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures {
                        hue = (it.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
            val x = hue / 360f * size.width
            drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(width = 3.dp.toPx()))
        }
    }
}

/** Preset colors plus a custom color picker. */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    presets: List<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableIntStateOf(initial or OPAQUE) }
    var hex by remember { mutableStateOf(formatHex(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { index, preset ->
                            val label = stringResource(R.string.color_n, index + 1)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset))
                                    .border(if (preset or OPAQUE == current) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable {
                                        current = preset or OPAQUE
                                        hex = formatHex(current)
                                    }
                                    .semantics { contentDescription = label },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                HsvColorPicker(current, onChange = {
                    current = it
                    hex = formatHex(it)
                })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(current)))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text.take(7)
                            parseHex(hex)?.let { current = it }
                        },
                        label = { Text(stringResource(R.string.color_hex)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * AndroidX's emoji picker (with recents and skin tones) in a dialog, with a search field: while
 * there is a query, matching emoji replace the picker.
 */
@Composable
fun EmojiPickerDialog(onPick: (String) -> Unit, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val locale = LocalConfiguration.current.locales[0]
    val catalog by produceState<List<EmojiEntry>?>(null, locale) {
        value = withContext(Dispatchers.Default) { EmojiSearch.catalog(locale) }
    }
    val pick: (String) -> Unit = { emoji ->
        onPick(emoji)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_emoji)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.emoji_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search)) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                    if (query.isBlank()) {
                        AndroidView(
                            factory = { context ->
                                EmojiPickerView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    emojiGridColumns = 8
                                    setOnEmojiPickedListener { item -> pick(item.emoji) }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        EmojiResults(query, catalog, pick)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = onRemove?.let { remove ->
            { TextButton(onClick = { remove(); onDismiss() }) { Text(stringResource(R.string.use_color_dot)) } }
        },
    )
}

/** Search results as a grid. An emoji typed into the search field is offered first. */
@Composable
private fun EmojiResults(query: String, catalog: List<EmojiEntry>?, onPick: (String) -> Unit) {
    if (catalog == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val results = remember(query, catalog) {
        val typed = query.trim().takeIf(EmojiSearch::isTypedEmoji)
        listOfNotNull(typed) + EmojiSearch.search(catalog, query).filter { it != typed }
    }
    if (results.isEmpty()) {
        Text(
            stringResource(R.string.emoji_search_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 44.dp), modifier = Modifier.fillMaxSize()) {
        items(results, key = { it }) { emoji ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = emoji) { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 26.sp)
            }
        }
    }
}

private const val OPAQUE = 0xFF000000.toInt()

private fun formatHex(color: Int) = "#%06X".format(color and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val hex = text.trim().removePrefix("#").takeIf { it.length == 6 } ?: return null
    return hex.toLongOrNull(16)?.let { (it or 0xFF000000L).toInt() }
}

/** The color as an opaque ARGB int; used to pass Compose colors to the domain. */
fun Color.toOpaqueArgb(): Int = toArgb() or OPAQUE
