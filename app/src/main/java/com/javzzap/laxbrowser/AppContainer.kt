package com.javzzap.laxbrowser

import android.content.Context
import com.javzzap.laxbrowser.data.BrowserDatabase
import com.javzzap.laxbrowser.data.DownloadStore
import com.javzzap.laxbrowser.data.ExtensionPrefEntity
import com.javzzap.laxbrowser.data.FileRecentlyClosedStore
import com.javzzap.laxbrowser.data.LoginStore
import com.javzzap.laxbrowser.data.PersistenceQueue
import com.javzzap.laxbrowser.data.RoomHistoryStore
import com.javzzap.laxbrowser.data.RoomBrowserRepository
import com.javzzap.laxbrowser.data.SessionStateStore
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.IdGenerator
import com.javzzap.laxbrowser.engine.AppLinks
import com.javzzap.laxbrowser.engine.ExternalLinkHandler
import com.javzzap.laxbrowser.downloads.Downloader
import com.javzzap.laxbrowser.engine.gecko.GeckoEngine
import com.javzzap.laxbrowser.engine.gecko.SearchSuggestions
import com.javzzap.laxbrowser.media.MediaPlaybackService
import com.javzzap.laxbrowser.platform.UpdateChecker
import com.javzzap.laxbrowser.platform.UpdateInstaller
import com.javzzap.laxbrowser.platform.NextcloudClient
import com.javzzap.laxbrowser.platform.NextcloudStore
import com.javzzap.laxbrowser.pwa.WebAppInstaller
import com.javzzap.laxbrowser.state.BrowserController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-scoped object graph. Everything is lazy: Gecko child processes also create the
 * Application, and they must not open the database or a second runtime.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.System
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { BrowserDatabase.create(appContext) }
    val repository by lazy { RoomBrowserRepository(database.browserDao()) }
    private val queue by lazy { PersistenceQueue(ioScope, Dispatchers.IO) }
    private val sessionStates by lazy { SessionStateStore(repository, queue, mainScope, clock) }

    val history by lazy { RoomHistoryStore(database.historyDao()) }

    val downloader by lazy { Downloader(appContext, DownloadStore(database.downloadDao()), mainScope).also { it.recover() } }

    val logins by lazy { LoginStore(appContext, mainScope) }

    val engine by lazy {
        GeckoEngine(appContext, sessionStates, mainScope, ExternalLinkHandler(appContext), logins, AppLinks(appContext)).also { engine ->
            engine.visitedLookup = { urls -> withContext(Dispatchers.IO) { history.visited(urls) } }
            engine.downloadListener = { request -> downloader.start(request) }
            engine.prompts.clearUploadCache()
            connectExtensionPrefs(engine)
            // Playing media keeps a foreground service with system media controls.
            mainScope.launch {
                engine.media.state.map { it?.playing == true }.distinctUntilChanged().collect { playing ->
                    if (playing) MediaPlaybackService.start(appContext)
                }
            }
        }
    }

    private fun connectExtensionPrefs(engine: GeckoEngine) {
        val dao = database.extensionPrefDao()
        mainScope.launch {
            val pinned = withContext(Dispatchers.IO) { runCatching { dao.pinnedIds() }.getOrDefault(emptyList()) }
            engine.extensions.restorePinned(pinned.toSet())
        }
        engine.extensions.onPinnedChanged = { id, pinned ->
            ioScope.launch { runCatching { if (pinned) dao.upsert(ExtensionPrefEntity(id, true)) else dao.delete(id) } }
        }
    }

    val searchSuggestions by lazy { SearchSuggestions { engine.runtime } }

    val controller by lazy {
        BrowserController(
            repository = repository,
            engine = engine,
            sessionStates = sessionStates,
            queue = queue,
            clock = clock,
            ops = BrowserOperations(clock, IdGenerator.Random, appContext.getString(R.string.default_space_name)),
            scope = mainScope,
            ioDispatcher = Dispatchers.IO,
            history = history,
            closedTabStore = FileRecentlyClosedStore(appContext),
        ).also { controller ->
            // The tab playing media is never unloaded automatically.
            controller.protectedTabIds = { setOfNotNull(engine.media.state.value?.tabId) }
            controller.start()
            controller.startAutoUnload()
        }
    }

    val updateChecker by lazy {
        val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull().orEmpty()
        UpdateChecker(appContext, BuildConfig.UPDATE_REPO, version)
    }

    val updateInstaller by lazy { UpdateInstaller(appContext, mainScope) }

    val nextcloudStore by lazy { NextcloudStore(appContext) }
    val nextcloudClient by lazy { NextcloudClient() }

    val webAppInstaller by lazy { WebAppInstaller(appContext, clock) }
}
