package com.javzzap.laxbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.ui.sidebar.SidebarTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Waits for saved state, then skips the first-run setup so the browser screen shows. */
    private fun awaitLoaded() {
        val controller = compose.activity.appContainer.controller
        compose.waitUntil(LOAD_TIMEOUT_MS) { controller.state.value.isLoaded }
        if (!controller.state.value.data.settings.onboardingDone) {
            compose.runOnUiThread { controller.updateSettings { it.copy(onboardingDone = true) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun sidebarOpensFromTheButtonAndClosesWithBack() {
        awaitLoaded()
        compose.onNodeWithTag(SidebarTags.URL_BAR).assertIsNotDisplayed()
        compose.onNodeWithTag("open_sidebar").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SidebarTags.URL_BAR).assertIsDisplayed()
        compose.onNodeWithTag(SidebarTags.ESSENTIALS).assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(SidebarTags.URL_BAR).assertIsNotDisplayed()
    }

    @Test
    fun activityRecreationKeepsBrowserState() {
        awaitLoaded()
        val controller = compose.activity.appContainer.controller
        val before = controller.state.value

        compose.activityRule.scenario.recreate()
        awaitLoaded()

        val after = controller.state.value
        assertEquals("recreation must not add tabs or a new tab page", before.data.tabs, after.data.tabs)
        assertEquals(before.activeTab?.id, after.activeTab?.id)
        assertEquals(before.data.settings.activeSpaceId, after.data.settings.activeSpaceId)
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L
    }
}
