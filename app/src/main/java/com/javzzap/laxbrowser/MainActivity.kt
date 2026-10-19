package com.javzzap.laxbrowser

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import com.javzzap.laxbrowser.ui.KeyboardShortcuts
import com.javzzap.laxbrowser.engine.gecko.WebNotifications
import com.javzzap.laxbrowser.ui.PipRatio
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.ui.BrowserViewModel
import com.javzzap.laxbrowser.ui.browser.BrowserApp
import com.javzzap.laxbrowser.ui.browser.Navigator
import com.javzzap.laxbrowser.ui.browser.Screen
import com.javzzap.laxbrowser.ui.common.FaviconLoader
import com.javzzap.laxbrowser.ui.common.LocalFaviconLoader
import com.javzzap.laxbrowser.ui.theme.LaxTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash stays until saved tabs are loaded (briefly capped), instead of showing an empty screen.
        val shownAt = System.currentTimeMillis()
        splash.setKeepOnScreenCondition { !viewModel.state.value.isLoaded && System.currentTimeMillis() - shownAt < SPLASH_MAX_MS }
        enableEdgeToEdge()
        // Changing the app language recreates the activity, so this keeps Gecko in sync.
        appContainer.engine.setRequestedLocales(resources.configuration.locales.toLanguageTags().split(','))
        val favicons = appContainer.engine.favicons
        val faviconLoader = FaviconLoader { url, ephemeral -> favicons.load(url, ephemeral) }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaxTheme(wallpaperColors = state.data.settings.wallpaperColors) {
                CompositionLocalProvider(LocalFaviconLoader provides faviconLoader) {
                    var stack by rememberSaveable(stateSaver = screenStackSaver) { mutableStateOf(listOf(Screen.BROWSER)) }
                    val navigator = remember {
                        object : Navigator {
                            override fun open(screen: Screen) {
                                if (stack.last() != screen) stack = stack + screen
                            }

                            override fun back() {
                                if (stack.size > 1) stack = stack.dropLast(1)
                            }

                            override fun home() {
                                stack = listOf(Screen.BROWSER)
                            }
                        }
                    }
                    LaunchedEffect(Unit) { viewModel.showBrowser.collect { stack = listOf(Screen.BROWSER) } }
                    BrowserApp(viewModel, stack.last(), navigator)
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.skipNextForeground) {
            viewModel.skipNextForeground = false
        } else {
            viewModel.controller.onForeground()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            viewModel.skipNextForeground = true
        } else {
            viewModel.controller.onBackground()
            viewModel.onAppBackground()
        }
    }

    /** Hardware keyboard shortcuts, checked before the page sees the key. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            KeyboardShortcuts.match(event)?.let { if (viewModel.onShortcut(it)) return true }
        }
        return super.dispatchKeyEvent(event)
    }

    /** A fullscreen video keeps playing in a picture-in-picture window when the user leaves the app. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val engine = appContainer.engine
        val video = engine.media.state.value?.fullscreenVideo ?: return
        if (engine.fullScreenTabId.value == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(PipRatio.of(video.first, video.second)).build()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPictureInPicture(isInPictureInPictureMode)
    }

    /** External intents are untrusted: only http(s) VIEW URLs are accepted, and only as a new tab. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf(UrlInput::isWebUrl)?.let(viewModel::openExternalUrl)
            ACTION_OPEN_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { viewModel.openSharedText(it, ask = intent.getBooleanExtra(EXTRA_ASK_TARGET, false)) }
            WebNotifications.ACTION_CLICK -> intent.getStringExtra(WebNotifications.EXTRA_TAG)?.let(appContainer.engine.notifications::onClicked)
            ACTION_WIDGET_SEARCH -> viewModel.searchFromWidget(private = false, spaceId = intent.getStringExtra(EXTRA_SPACE_ID))
            ACTION_WIDGET_PRIVATE -> viewModel.searchFromWidget(private = true)
        }
    }

    companion object {
        /** Shared or selected text forwarded by [IntentReceiverActivity]. */
        const val ACTION_OPEN_TEXT = "com.javzzap.laxbrowser.OPEN_TEXT"
        const val EXTRA_TEXT = "text"

        /** Ask which Space (or private browsing) opens the shared link. */
        const val EXTRA_ASK_TARGET = "ask_target"

        /** From the home screen search widget: a new tab, or a new private tab, ready for typing. */
        const val ACTION_WIDGET_SEARCH = "com.javzzap.laxbrowser.WIDGET_SEARCH"
        const val ACTION_WIDGET_PRIVATE = "com.javzzap.laxbrowser.WIDGET_PRIVATE"

        /** The Space a search widget is set to; missing means the Space that is open. */
        const val EXTRA_SPACE_ID = "space_id"
        private const val SPLASH_MAX_MS = 1_500L

        private val screenStackSaver = listSaver<List<Screen>, String>(
            save = { stack -> stack.map { it.name } },
            restore = { names -> names.mapNotNull { name -> Screen.entries.firstOrNull { it.name == name } }.ifEmpty { listOf(Screen.BROWSER) } },
        )
    }
}
