package com.javzzap.laxbrowser.customtabs

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.domain.Download
import com.javzzap.laxbrowser.domain.DownloadNames
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.downloads.DownloadEvent
import com.javzzap.laxbrowser.downloads.DownloadRequest
import com.javzzap.laxbrowser.engine.ExternalLinkHandler
import com.javzzap.laxbrowser.ui.prompts.WebPromptHost
import com.javzzap.laxbrowser.ui.theme.LaxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.util.UUID

/**
 * A Custom Tab: a page opened by another app, shown in that app's task with a minimal toolbar.
 * It uses the active Space's container and is not added to the tab list; "Open in Lax" moves the
 * page into a normal tab.
 */
class CustomTabActivity : AppCompatActivity() {
    private val viewModel: CustomTabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf(UrlInput::isWebUrl)
        if (url == null) {
            finish()
            return
        }
        val toolbarColor = intent.getIntExtra(EXTRA_COLOR, 0).takeIf { it != 0 }
        viewModel.start(url)
        setContent {
            val browserState by appContainer.controller.state.collectAsStateWithLifecycle()
            LaxTheme(wallpaperColors = browserState.data.settings.wallpaperColors) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                BackHandler { if (ui.canGoBack) ui.session?.goBack() else finish() }
                CustomTabScreen(
                    ui, toolbarColor, onClose = ::finish, onShare = ::share, onOpenInBrowser = ::openInBrowser,
                    onOpenDownload = {
                        if (!viewModel.openDownload()) Toast.makeText(this, R.string.download_no_app, Toast.LENGTH_SHORT).show()
                    },
                )
                val engine = appContainer.engine
                WebPromptHost(engine.prompts.prompts, engine.permissions.requests, accepts = { it == viewModel.tabId })
            }
        }
    }

    private fun share() {
        val url = viewModel.ui.value.url
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, getString(R.string.share_link)))
    }

    private fun openInBrowser() {
        val url = viewModel.ui.value.url
        startActivity(
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_COLOR = "toolbar_color"

        /** Builds the intent from a caller's Custom Tabs intent, keeping only the toolbar color. */
        fun intent(context: Context, url: String, source: Intent): Intent {
            val color = runCatching {
                CustomTabsIntent.getColorSchemeParams(source, CustomTabsIntent.COLOR_SCHEME_LIGHT).toolbarColor
            }.getOrNull()
            return Intent(context, CustomTabActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .apply { color?.let { putExtra(EXTRA_COLOR, it or 0xFF000000.toInt()) } }
        }
    }
}

data class CustomTabUi(
    val session: GeckoSession? = null,
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val secure: Boolean = false,
    val canGoBack: Boolean = false,
    /** A file this tab is downloading (for example an APK linked from a chat app). */
    val download: CustomTabDownload? = null,
)

data class CustomTabDownload(
    val id: String,
    val fileName: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val finished: Download? = null,
    val failed: Boolean = false,
)

class CustomTabViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val mutableUi = MutableStateFlow(CustomTabUi())
    val ui: StateFlow<CustomTabUi> = mutableUi.asStateFlow()

    /** Identifies this tab's prompts and permission requests. */
    val tabId = "custom:" + UUID.randomUUID()
    private var started = false

    init {
        val downloader = container.downloader
        viewModelScope.launch {
            downloader.active.collect { active ->
                val download = mutableUi.value.download ?: return@collect
                val progress = active[download.id] ?: return@collect
                mutableUi.value = mutableUi.value.copy(download = download.copy(downloadedBytes = progress.downloadedBytes, totalBytes = progress.totalBytes))
            }
        }
        viewModelScope.launch {
            downloader.events.collect { event ->
                val download = mutableUi.value.download ?: return@collect
                when {
                    event is DownloadEvent.Completed && event.download.id == download.id ->
                        mutableUi.value = mutableUi.value.copy(download = download.copy(finished = event.download))
                    event is DownloadEvent.Failed && event.id == download.id ->
                        mutableUi.value = mutableUi.value.copy(download = download.copy(failed = true))
                }
            }
        }
    }

    /** Opens the finished download with another app (for an APK, the package installer). */
    fun openDownload(): Boolean = mutableUi.value.download?.finished?.let(container.downloader::open) ?: false

    fun start(url: String) {
        if (started) return
        started = true
        mutableUi.value = CustomTabUi(url = url, title = UrlInput.displayHost(url))
        viewModelScope.launch {
            val state = container.controller.state.first { it.isLoaded }
            val engine = container.engine
            val space = state.activeSpace
            val containerId = space?.containerId?.takeIf { state.container(it) != null }
            val session = if (containerId != null) engine.sessionFactory.create(containerId) else engine.sessionFactory.createPrivate()
            attach(session)
            session.open(engine.runtime)
            session.loadUri(url)
            mutableUi.value = mutableUi.value.copy(session = session)
        }
    }

    private fun attach(session: GeckoSession) {
        val engine = container.engine
        val links: ExternalLinkHandler = engine.externalLinks
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            private var errorPage: String? = null

            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (url != null && url != errorPage) mutableUi.value = mutableUi.value.copy(url = url)
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> =
                GeckoResult.fromValue(engine.errorPages.pageFor(uri, error).also { errorPage = it })

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                mutableUi.value = mutableUi.value.copy(canGoBack = canGoBack)
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (links.isWebScheme(request.uri)) return null
                if (request.hasUserGesture) {
                    val result = links.open(request.uri)
                    if (result is ExternalLinkHandler.Result.Fallback) session.loadUri(result.url)
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                session.loadUri(uri)
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                mutableUi.value = mutableUi.value.copy(progress = progress)
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                mutableUi.value = mutableUi.value.copy(secure = securityInfo.isSecure)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) mutableUi.value = mutableUi.value.copy(title = title)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val body = response.body ?: return
                val headers = response.headers.mapKeys { it.key.lowercase() }
                val disposition = headers["content-disposition"]
                val type = headers["content-type"]
                val length = headers["content-length"]?.toLongOrNull()
                // A link that is only a file (common from chat apps) leaves the page blank, so the
                // tab shows the download instead.
                val id = UUID.randomUUID().toString()
                mutableUi.value = mutableUi.value.copy(
                    download = CustomTabDownload(id, DownloadNames.fileName(disposition, response.uri, type), totalBytes = length),
                )
                container.downloader.start(DownloadRequest(response.uri, type, disposition, length, body, isPrivate = false), id)
            }
        }
        session.promptDelegate = engine.prompts.delegateFor { tabId }
        session.permissionDelegate = engine.permissions.delegateFor { tabId }
    }

    override fun onCleared() {
        container.engine.prompts.cancelFor(tabId)
        container.engine.permissions.cancelFor(tabId)
        val session = mutableUi.value.session ?: return
        val downloadId = mutableUi.value.download?.id
        if (downloadId == null || downloadId !in container.downloader.active.value) {
            if (session.isOpen) session.close()
            return
        }
        // Closing the session would cut off the file still streaming through it.
        container.mainScope.launch {
            container.downloader.active.first { downloadId !in it }
            if (session.isOpen) session.close()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun CustomTabScreen(
    ui: CustomTabUi,
    toolbarColor: Int?,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenDownload: () -> Unit,
) {
    val container = toolbarColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainer
    val content = if (toolbarColor != null && ColorUtils.calculateLuminance(toolbarColor) < 0.5) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = container, titleContentColor = content, navigationIconContentColor = content, actionIconContentColor = content),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) } },
                title = {
                    Column {
                        Text(ui.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        androidx.compose.foundation.layout.Row {
                            if (ui.secure) Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.secure_connection), modifier = Modifier.height(14.dp))
                            Text(UrlInput.displayHost(ui.url), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.menu_share)) }
                    IconButton(onClick = onOpenInBrowser) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_in_lax))
                    }
                },
            )
            if (ui.progress in 1..99) LinearProgressIndicator(progress = { ui.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
            val session = ui.session
            val download = ui.download
            if (download != null) {
                DownloadPanel(download, onOpen = onOpenDownload, onClose = onClose)
            } else if (session != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        GeckoView(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                    },
                    update = { view -> if (view.session !== session) view.setSession(session) },
                    onRelease = { it.releaseSession() },
                )
            }
        }
    }
}

/** The file this tab is downloading: progress, then Open (or the error). */
@androidx.compose.runtime.Composable
private fun DownloadPanel(download: CustomTabDownload, onOpen: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (download.failed) Icons.Default.ErrorOutline else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (download.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(download.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                val total = download.totalBytes
                when {
                    download.failed -> Text(stringResource(R.string.download_tab_failed), color = MaterialTheme.colorScheme.error)
                    download.finished != null -> Text(stringResource(R.string.download_tab_done))
                    else -> {
                        if (total != null && total > 0) {
                            LinearProgressIndicator(progress = { (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        val context = LocalContext.current
                        Text(
                            stringResource(
                                R.string.download_tab_progress,
                                Formatter.formatShortFileSize(context, download.downloadedBytes),
                                total?.let { Formatter.formatShortFileSize(context, it) } ?: "?",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.download_tab_background), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(stringResource(if (download.finished != null || download.failed) R.string.done else R.string.close)) }
                    if (download.finished != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onOpen) { Text(stringResource(R.string.download_tab_open)) }
                    }
                }
            }
        }
    }
}
