package com.javzzap.laxbrowser.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.BuildConfig
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.Changelog
import com.javzzap.laxbrowser.domain.ChangelogRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled changelog in the app's language; null while it loads, empty if it cannot be read. */
@Composable
private fun rememberChangelog(): List<ChangelogRelease>? {
    val resources = LocalResources.current
    val releases by produceState<List<ChangelogRelease>?>(null, resources) {
        value = withContext(Dispatchers.IO) {
            runCatching { resources.openRawResource(R.raw.changelog).bufferedReader().use { Changelog.parse(it.readText()) } }.getOrDefault(emptyList())
        }
    }
    return releases
}

/** Shown once after an update: the changes in the installed version. */
@Composable
fun WhatsNewDialog(onSeeAll: () -> Unit, onDismiss: () -> Unit) {
    val releases = rememberChangelog() ?: return
    val release = releases.firstOrNull { it.version == BuildConfig.VERSION_NAME }
    if (release == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NewReleases, contentDescription = null) },
        title = { Text(stringResource(R.string.whats_new_title, release.version)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { ReleaseNotes(release) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok)) } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onSeeAll()
            }) { Text(stringResource(R.string.whats_new_all)) }
        },
    )
}

/** Every release, newest first (Settings > About > What's new). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val releases = rememberChangelog()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (releases?.isEmpty() == true) {
                item { Text(stringResource(R.string.changelog_unavailable), modifier = Modifier.padding(16.dp)) }
            }
            items(releases.orEmpty(), key = { it.version }) { release ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.changelog_version, release.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (release.version == BuildConfig.VERSION_NAME) {
                        Text(stringResource(R.string.changelog_installed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ReleaseNotes(release)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReleaseNotes(release: ChangelogRelease) {
    release.sections.forEach { section ->
        section.title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
        }
        section.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        section.items.forEach { item ->
            Row(Modifier.padding(top = 6.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
