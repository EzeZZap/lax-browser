package com.javzzap.laxbrowser.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.EssentialPick
import com.javzzap.laxbrowser.domain.OnboardingChoices
import com.javzzap.laxbrowser.domain.SearchEngines
import com.javzzap.laxbrowser.domain.SpaceSetup
import com.javzzap.laxbrowser.domain.ToolbarPosition
import com.javzzap.laxbrowser.domain.TrackingProtection
import com.javzzap.laxbrowser.platform.DefaultBrowser
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.common.LaxWordmark
import com.javzzap.laxbrowser.ui.common.SiteIcon
import com.javzzap.laxbrowser.ui.extensions.RecommendedExtension
import com.javzzap.laxbrowser.ui.extensions.RecommendedExtensionList
import com.javzzap.laxbrowser.domain.ImportSummary
import com.javzzap.laxbrowser.domain.LoginImportSummary
import com.javzzap.laxbrowser.platform.DeviceAuth
import android.net.Uri
import com.javzzap.laxbrowser.ui.theme.BrandColors
import kotlinx.coroutines.launch

private data class IntroPage(val icon: ImageVector, val title: Int, val body: Int)

private enum class Step { SIDEBAR, SPACES, TABS, PRIVACY, LAYOUT, SPACE, ESSENTIALS, SEARCH, EXTENSIONS, FINISH }

private val introPages = mapOf(
    Step.SIDEBAR to IntroPage(Icons.AutoMirrored.Filled.ViewSidebar, R.string.onboarding_sidebar_title, R.string.onboarding_sidebar_body),
    Step.SPACES to IntroPage(Icons.Default.Layers, R.string.onboarding_spaces_title, R.string.onboarding_spaces_body),
    Step.TABS to IntroPage(Icons.Default.Bedtime, R.string.onboarding_tabs_title, R.string.onboarding_tabs_body),
    Step.PRIVACY to IntroPage(Icons.Default.Shield, R.string.onboarding_privacy_title, R.string.onboarding_privacy_body),
)

/**
 * First launch: a short introduction, then quick setup (layout, a second Space, Essentials,
 * search and privacy, extensions, default browser, bookmark import). Choices are applied together
 * when the user finishes or skips; extensions and imports happen right away.
 */
@Composable
fun OnboardingScreen(vm: BrowserViewModel, state: BrowserState, onDone: (OnboardingChoices) -> Unit) {
    val installed by vm.extensions.collectAsStateWithLifecycle()
    val installing by vm.installingExtensions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnboardingContent(
        homeSpaceName = state.activeSpace?.name.orEmpty(),
        initialChoices = remember { OnboardingChoices.from(state.data.settings) },
        installedExtensionIds = installed.map { it.id }.toSet(),
        installingExtensionIds = installing,
        onInstallExtension = vm::installRecommended,
        onImportBookmarks = vm::importBookmarks,
        onImportLogins = vm::importLogins,
        onDone = onDone,
        canLockPrivateTabs = remember { DeviceAuth.isAvailable(context) },
    )
}

/** The onboarding UI without the view model, so it can be previewed. */
@Composable
internal fun OnboardingContent(
    homeSpaceName: String,
    initialChoices: OnboardingChoices,
    installedExtensionIds: Set<String>,
    installingExtensionIds: Set<String>,
    onInstallExtension: (RecommendedExtension) -> Unit,
    onImportBookmarks: (Uri, (ImportSummary?) -> Unit) -> Unit,
    onDone: (OnboardingChoices) -> Unit,
    initialPage: Int = 0,
    onImportLogins: (Uri, (LoginImportSummary?) -> Unit) -> Unit = { _, _ -> },
    canLockPrivateTabs: Boolean = false,
) {
    var choices by remember { mutableStateOf(initialChoices) }
    val steps = Step.entries
    val pager = rememberPagerState(initialPage = initialPage) { steps.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == steps.lastIndex

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                LaxWordmark(fontSize = 28.sp)
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = { onDone(choices) }) { Text(stringResource(R.string.onboarding_skip)) }
            }
            LinearProgressIndicator(
                progress = { (pager.currentPage + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(vertical = 8.dp),
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                when (val step = steps[index]) {
                    Step.SIDEBAR, Step.SPACES, Step.TABS, Step.PRIVACY -> IntroStep(introPages.getValue(step))
                    Step.LAYOUT -> LayoutStep(choices) { choices = it }
                    Step.SPACE -> SpaceStep(homeSpaceName, choices) { choices = it }
                    Step.ESSENTIALS -> EssentialsStep(choices) { choices = it }
                    Step.SEARCH -> SearchStep(choices, canLockPrivateTabs) { choices = it }
                    Step.EXTENSIONS -> ExtensionsStep(installedExtensionIds, installingExtensionIds, onInstallExtension)
                    Step.FINISH -> FinishStep(choices, onImportBookmarks, onImportLogins)
                }
            }
            Row(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (last) {
                    Button(onClick = { onDone(choices) }) { Text(stringResource(R.string.onboarding_start)) }
                } else {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(page: IntroPage) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBadge(page.icon, 112.dp)
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@Composable
private fun BrandBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.29f)).background(Brush.linearGradient(BrandColors.gradientOnDark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = BrandColors.Ink, modifier = Modifier.size(size * 0.46f))
    }
}

/** A scrollable setup page with a small badge, a title, and an explanation. */
@Composable
private fun SetupStep(icon: ImageVector, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            BrandBadge(icon, 56.dp)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

// region Layout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutStep(choices: OnboardingChoices, onChange: (OnboardingChoices) -> Unit) {
    SetupStep(Icons.Default.Dashboard, stringResource(R.string.onboarding_layout_title), stringResource(R.string.onboarding_layout_body)) {
        SectionLabel(stringResource(R.string.onboarding_toolbar))
        OptionRow {
            listOf(ToolbarPosition.TOP to R.string.settings_toolbar_top, ToolbarPosition.BOTTOM to R.string.settings_toolbar_bottom).forEach { (position, label) ->
                OptionCard(stringResource(label), choices.toolbarPosition == position, it, onClick = { onChange(choices.copy(toolbarPosition = position)) }) {
                    PhonePreview(toolbarAtTop = position == ToolbarPosition.TOP)
                }
            }
        }
        SectionLabel(stringResource(R.string.onboarding_sidebar_side))
        OptionRow {
            listOf(false to R.string.onboarding_sidebar_left, true to R.string.onboarding_sidebar_right).forEach { (right, label) ->
                OptionCard(stringResource(label), choices.sidebarOnRight == right, it, onClick = { onChange(choices.copy(sidebarOnRight = right)) }) {
                    PhonePreview(sidebarOnRight = right)
                }
            }
        }
        SectionLabel(stringResource(R.string.onboarding_unload_title))
        val unloadChoices = listOf(
            15 to R.string.onboarding_unload_15,
            30 to R.string.onboarding_unload_30,
            60 to R.string.onboarding_unload_60,
            0 to R.string.onboarding_unload_never,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            unloadChoices.forEachIndexed { index, (minutes, label) ->
                SegmentedButton(
                    selected = choices.autoUnloadMinutes == minutes,
                    onClick = { onChange(choices.copy(autoUnloadMinutes = minutes)) },
                    shape = SegmentedButtonDefaults.itemShape(index, unloadChoices.size),
                    // No check mark: it would squeeze the labels in four narrow segments.
                    icon = {},
                    label = { Text(stringResource(label), maxLines = 1, softWrap = false) },
                )
            }
        }
        Note(stringResource(R.string.onboarding_unload_note))
    }
}

/** Side-by-side option cards of equal width and height; [content] gets each card's modifier. */
@Composable
private fun OptionRow(content: @Composable (Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * A selectable card: a picture above its label, or with [labelOnTop], a title above a
 * description. Content is centered so cards in an [OptionRow] line up.
 */
@Composable
private fun OptionCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    labelOnTop: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            val title: @Composable () -> Unit = {
                Text(label, style = if (labelOnTop) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
            if (labelOnTop) title()
            content()
            if (!labelOnTop) title()
        }
    }
}

/** A tiny phone sketch showing only the element being chosen: the toolbar or the sidebar. */
@Composable
private fun PhonePreview(toolbarAtTop: Boolean? = null, sidebarOnRight: Boolean? = null) {
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    Box(
        Modifier
            .size(width = 64.dp, height = 104.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(5.dp),
    ) {
        sidebarOnRight?.let { right ->
            Box(
                Modifier
                    .align(if (right) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(22.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill),
            )
        }
        toolbarAtTop?.let { top ->
            Box(
                Modifier
                    .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(fill),
            )
        }
    }
}

/**
 * Icon-and-label tiles in an even grid (the last row is padded so every tile has the same width).
 * Used for picking Essentials and the search engine.
 */
@Composable
private fun <T> TileGrid(items: List<T>, key: (T) -> Any, tile: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / TILE_MIN_WIDTH).toInt().coerceIn(3, 5)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item -> androidx.compose.runtime.key(key(item)) { tile(item, Modifier.weight(1f)) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A rectangular tile: icon on top, centered label below, with a check when selected. */
@Composable
private fun IconTile(
    label: String,
    selected: Boolean,
    role: Role,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(96.dp)
            .then(
                if (role == Role.Checkbox) {
                    Modifier.toggleable(value = selected, role = role, onValueChange = { onClick() })
                } else {
                    Modifier.selectable(selected = selected, role = role, onClick = onClick)
                },
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                icon()
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
                )
            }
        }
    }
}

private val TILE_MIN_WIDTH = 104.dp

// endregion

// region Space

@Composable
private fun SpaceStep(homeName: String, choices: OnboardingChoices, onChange: (OnboardingChoices) -> Unit) {
    SetupStep(Icons.Default.Layers, stringResource(R.string.onboarding_space_title), stringResource(R.string.onboarding_space_body, homeName)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceOption(
                emoji = null,
                color = MaterialTheme.colorScheme.surfaceVariant,
                title = stringResource(R.string.onboarding_space_none),
                description = stringResource(R.string.onboarding_space_none_desc, homeName),
                selected = choices.space == null,
            ) { onChange(choices.copy(space = null)) }
            spacePresets.forEach { preset ->
                val name = stringResource(preset.name)
                SpaceOption(
                    emoji = preset.emoji,
                    color = Color(preset.color).copy(alpha = 0.18f),
                    title = name,
                    description = stringResource(preset.description),
                    selected = choices.space?.name == name,
                ) { onChange(choices.copy(space = SpaceSetup(name, preset.emoji, preset.color))) }
            }
        }
        Note(stringResource(R.string.onboarding_space_later))
    }
}

@Composable
private fun SpaceOption(emoji: String?, color: Color, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(emoji ?: "–", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

// endregion

// region Essentials

@Composable
private fun EssentialsStep(choices: OnboardingChoices, onChange: (OnboardingChoices) -> Unit) {
    SetupStep(Icons.Default.Star, stringResource(R.string.onboarding_essentials_title), stringResource(R.string.onboarding_essentials_body)) {
        TileGrid(essentialPresets, key = { it.url }) { preset, modifier ->
            val selected = choices.essentials.any { it.url == preset.url }
            IconTile(
                label = preset.title,
                selected = selected,
                role = Role.Checkbox,
                modifier = modifier,
                onClick = {
                    val updated = if (selected) choices.essentials.filterNot { it.url == preset.url } else choices.essentials + EssentialPick(preset.url, preset.title)
                    onChange(choices.copy(essentials = updated))
                },
            ) { SiteIcon(preset.url, preset.title, null, size = 36.dp) }
        }
        if (choices.essentials.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.onboarding_essentials_count, choices.essentials.size, choices.essentials.size))
        }
        Note(stringResource(R.string.onboarding_essentials_later))
    }
}

// endregion

// region Search and privacy

@Composable
private fun SearchStep(choices: OnboardingChoices, canLockPrivateTabs: Boolean, onChange: (OnboardingChoices) -> Unit) {
    SetupStep(Icons.Default.Search, stringResource(R.string.onboarding_search_title), stringResource(R.string.onboarding_search_body)) {
        SectionLabel(stringResource(R.string.settings_search_engine))
        Box(Modifier.selectableGroup()) {
            TileGrid(SearchEngines.builtIn, key = { it.id }) { engine, modifier ->
                IconTile(
                    label = engine.name,
                    selected = choices.searchEngineId == engine.id,
                    role = Role.RadioButton,
                    modifier = modifier,
                    onClick = { onChange(choices.copy(searchEngineId = engine.id)) },
                ) { SiteIcon(engine.template, engine.name, null, size = 36.dp) }
            }
        }
        SwitchLine(
            stringResource(R.string.settings_search_suggestions),
            stringResource(R.string.settings_search_suggestions_summary),
            choices.searchSuggestions,
        ) { onChange(choices.copy(searchSuggestions = it)) }
        SectionLabel(stringResource(R.string.settings_tracking_protection))
        OptionRow { cardModifier ->
            listOf(
                Triple(TrackingProtection.STANDARD, R.string.settings_tp_standard, R.string.settings_tp_standard_summary),
                Triple(TrackingProtection.STRICT, R.string.settings_tp_strict, R.string.settings_tp_strict_summary),
            ).forEach { (level, label, summary) ->
                OptionCard(
                    stringResource(label),
                    choices.trackingProtection == level,
                    cardModifier,
                    labelOnTop = true,
                    onClick = { onChange(choices.copy(trackingProtection = level)) },
                ) {
                    Text(
                        stringResource(summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        SwitchLine(
            stringResource(R.string.settings_https_only),
            stringResource(R.string.settings_https_only_summary),
            choices.httpsOnly,
        ) { onChange(choices.copy(httpsOnly = it)) }
        if (canLockPrivateTabs) {
            SwitchLine(
                stringResource(R.string.settings_private_lock),
                stringResource(R.string.settings_private_lock_summary),
                choices.privateTabsLock,
            ) { onChange(choices.copy(privateTabsLock = it)) }
        }
    }
}

@Composable
private fun SwitchLine(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

// endregion

// region Extensions and finish

@Composable
private fun ExtensionsStep(installedIds: Set<String>, installingIds: Set<String>, onAdd: (RecommendedExtension) -> Unit) {
    SetupStep(Icons.Default.Extension, stringResource(R.string.onboarding_extensions_title), stringResource(R.string.onboarding_extensions_body)) {
        RecommendedExtensionList(
            installedIds = installedIds,
            installingIds = installingIds,
            onAdd = onAdd,
        )
        Note(stringResource(R.string.onboarding_extensions_more))
    }
}

@Composable
private fun FinishStep(
    choices: OnboardingChoices,
    onImportBookmarks: (Uri, (ImportSummary?) -> Unit) -> Unit,
    onImportLogins: (Uri, (LoginImportSummary?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    // Not available in previews, where there is no real package manager.
    fun checkDefault() = runCatching { DefaultBrowser.isDefault(context) }.getOrDefault(false)
    var isDefault by remember { mutableStateOf(checkDefault()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = checkDefault()
    }
    var importResult by remember { mutableStateOf<String?>(null) }
    var loginsResult by remember { mutableStateOf<String?>(null) }
    val noneFound = stringResource(R.string.onboarding_import_none)
    val noLogins = stringResource(R.string.msg_logins_import_empty)
    val resources = LocalResources.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportBookmarks(uri) { summary ->
                importResult = summary?.takeIf { it.imported > 0 }
                    ?.let { resources.getQuantityString(R.plurals.onboarding_imported, it.imported, it.imported) } ?: noneFound
            }
        }
    }
    val loginsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportLogins(uri) { summary ->
                loginsResult = summary?.let { resources.getQuantityString(R.plurals.logins_imported_count, it.imported, it.imported) } ?: noLogins
            }
        }
    }

    SetupStep(Icons.Default.CheckCircle, stringResource(R.string.onboarding_finish_title), stringResource(R.string.onboarding_finish_body)) {
        val summary = buildList {
            choices.space?.let { add(stringResource(R.string.onboarding_finish_space, it.name)) }
            if (choices.essentials.isNotEmpty()) add(pluralStringResource(R.plurals.onboarding_finish_essentials, choices.essentials.size, choices.essentials.size))
            add(
                stringResource(
                    R.string.onboarding_finish_layout,
                    stringResource(if (choices.toolbarPosition == ToolbarPosition.TOP) R.string.settings_toolbar_top else R.string.settings_toolbar_bottom).lowercase(),
                    stringResource(if (choices.sidebarOnRight) R.string.onboarding_sidebar_right else R.string.onboarding_sidebar_left).lowercase(),
                ),
            )
        }
        summary.forEach { line ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!isDefault) {
            FilledTonalButton(onClick = { roleLauncher.launch(DefaultBrowser.requestIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.default_browser_action))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_import))
        }
        Note(importResult ?: stringResource(R.string.onboarding_import_summary))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { loginsLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_import_logins))
        }
        Note(loginsResult ?: stringResource(R.string.onboarding_import_logins_summary))
    }
}

// endregion
