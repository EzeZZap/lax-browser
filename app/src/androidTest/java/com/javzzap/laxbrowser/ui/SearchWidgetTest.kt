package com.javzzap.laxbrowser.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.state.BrowserController
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen widget's two buttons open a new tab or a new private tab in the browser. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SearchWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var controller: BrowserController

    @Before
    fun finishOnboarding() {
        // The controller creates the Gecko runtime, which must happen on the main thread.
        instrumentation.runOnMainSync { controller = context.appContainer.controller }
        waitFor("saved state to load") { controller.state.value.isLoaded }
        instrumentation.runOnMainSync {
            controller.updateSettings { it.copy(onboardingDone = true) }
            controller.setPrivateMode(false)
        }
    }

    @After
    fun closePrivateTabs() {
        instrumentation.runOnMainSync {
            controller.closeAllPrivateTabs()
            controller.setPrivateMode(false)
        }
    }

    @Test
    fun searchOpensANewTab() = launchWidget(MainActivity.ACTION_WIDGET_SEARCH, private = false)

    @Test
    fun privateButtonOpensANewPrivateTab() = launchWidget(MainActivity.ACTION_WIDGET_PRIVATE, private = true)

    private fun launchWidget(action: String, private: Boolean) {
        val intent = Intent(context, MainActivity::class.java).setAction(action)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitFor("a new ${if (private) "private " else ""}tab") { opened(controller.state.value, private) }
        }
    }

    private fun opened(state: BrowserState, private: Boolean): Boolean {
        val tab = state.activeTab ?: return false
        return state.privateMode == private && tab.isPrivate == private && tab.kind == TabKind.NEW_TAB
    }

    /** Polls with a deadline; the app reacts to the intent asynchronously. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for $what" }
            SystemClock.sleep(POLL_MS)
        }
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
        private const val POLL_MS = 50L
    }
}
