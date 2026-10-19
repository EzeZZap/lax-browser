package com.javzzap.laxbrowser.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.ui.common.ColorDot
import com.javzzap.laxbrowser.ui.theme.LaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chooses the Space a search widget opens tabs in, shown when the widget is added (before
 * Android 12) or reconfigured. Only reads the saved Spaces; it does not start the browser engine.
 */
class SearchWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out before choosing keeps the widget (it then follows the open Space).
        setResult(RESULT_OK, result)
        enableEdgeToEdge()
        val repository = appContainer.repository
        setContent {
            LaxTheme {
                val data by produceState<BrowserData?>(null) {
                    value = withContext(Dispatchers.IO) { runCatching { repository.load() }.getOrNull() }
                }
                SpaceChooser(
                    data = data,
                    selectedId = SearchWidgetSettings.space(this, widgetId)?.id,
                    onChoose = { space ->
                        SearchWidgetSettings.setSpace(this, widgetId, space?.let { SearchWidgetSettings.WidgetSpace(it.id, it.name) })
                        SearchWidgetProvider.refresh(this, widgetId)
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onBack = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceChooser(data: BrowserData?, selectedId: String?, onChoose: (Space?) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup()) {
            item(key = "current") {
                ChoiceRow(selected = selectedId == null, onClick = { onChoose(null) }, leading = {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }) {
                    Text(stringResource(R.string.widget_config_current), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.widget_config_current_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val spaces = data?.spaces.orEmpty().sortedBy { it.position }
            items(spaces, key = { it.id }) { space ->
                val container = data?.containers?.firstOrNull { it.id == space.containerId }?.name
                ChoiceRow(selected = space.id == selectedId, onClick = { onChoose(space) }, leading = {
                    if (space.emoji != null) Text(space.emoji, style = MaterialTheme.typography.titleLarge) else ColorDot(space.color, size = 16.dp)
                }) {
                    Text(space.name, style = MaterialTheme.typography.bodyLarge)
                    container?.let {
                        Text(
                            stringResource(R.string.widget_config_container, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { content() }
        RadioButton(selected = selected, onClick = null)
    }
}
