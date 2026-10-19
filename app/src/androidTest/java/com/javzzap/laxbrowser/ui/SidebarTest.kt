package com.javzzap.laxbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.IdGenerator
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.Reduction
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.ui.sidebar.Sidebar
import com.javzzap.laxbrowser.domain.TabFolder
import com.javzzap.laxbrowser.ui.sidebar.SidebarActions
import com.javzzap.laxbrowser.ui.sidebar.Suggestion
import com.javzzap.laxbrowser.ui.sidebar.SidebarTags
import com.javzzap.laxbrowser.ui.theme.LaxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SidebarTest {
    @get:Rule
    val compose = createComposeRule()

    private val ops = BrowserOperations(Clock.System, IdGenerator.Random)

    private fun Reduction.id() = (outcome as Outcome.Created).id

    private open class RecordingActions : SidebarActions {
        val submitted = mutableListOf<String>()
        override fun submitUrl(text: String) { submitted += text }
        override fun newTab() = Unit
        override fun selectTab(tab: Tab) = Unit
        override fun closeTab(tab: Tab) = Unit
        override fun unloadTab(tab: Tab) = Unit
        override fun pinTab(tab: Tab) = Unit
        override fun unpinTab(tab: Tab) = Unit
        override fun addToEssentials(tab: Tab) = Unit
        override fun removeEssential(tab: Tab) = Unit
        override fun moveTab(tab: Tab, delta: Int) = Unit
        override fun moveTabToSpace(tab: Tab) = Unit
        override fun bookmarkTab(tab: Tab) = Unit
        override fun switchSpace(space: Space) = Unit
        override fun newSpace() = Unit
        override fun manageSpaces() = Unit
        override fun setPrivateMode(enabled: Boolean) = Unit
        override fun closeAllPrivateTabs() = Unit
        override fun queryChanged(text: String) = Unit
        override fun openSuggestion(suggestion: Suggestion) = Unit
        override fun voiceSearch() = Unit
        override fun scanQrCode() = Unit
        override fun openInSplit(tab: Tab) = Unit
        override fun resetPinnedTab(tab: Tab) = Unit
        override fun replacePinnedUrl(tab: Tab) = Unit
        override fun editPinnedUrl(tab: Tab) = Unit
        override fun moveTabToFolder(tab: Tab) = Unit
        override fun newFolder() = Unit
        override fun renameFolder(folder: TabFolder) = Unit
        override fun deleteFolder(folder: TabFolder) = Unit
        override fun toggleFolder(folder: TabFolder) = Unit
        override fun moveFolder(folder: TabFolder, delta: Int) = Unit
        override fun switchSpaceBy(delta: Int) = Unit
    }

    /** Personal Space with one Essential, one pinned and one regular tab. */
    private fun sampleState(): BrowserState {
        var state = ops.ensureDefaults(BrowserState(isLoaded = true)).state
        val essential = ops.newTab(state, "https://mail.example").also { state = it.state }.id()
        state = ops.onTitleChange(state, essential, "Mail").state
        state = ops.addToEssentials(state, essential).state
        val pinned = ops.newTab(state, "https://docs.example").also { state = it.state }.id()
        state = ops.onTitleChange(state, pinned, "Docs").state
        state = ops.pinTab(state, pinned).state
        val regular = ops.newTab(state, "https://news.example").also { state = it.state }.id()
        state = ops.onTitleChange(state, regular, "News").state
        return state
    }

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun sectionsAppearInTheRequiredOrder() {
        val state = sampleState()
        compose.setContent { LaxTheme { Sidebar(state, RecordingActions(), focusUrlBar = false, onUrlBarFocused = {}) } }

        val order = listOf(SidebarTags.ESSENTIALS, SidebarTags.URL_BAR, SidebarTags.PINNED, SidebarTags.TABS, SidebarTags.SPACES).map(::top)
        assertEquals(order.sorted(), order)
        assertTrue(order.zipWithNext().all { (a, b) -> a < b })

        compose.onNodeWithContentDescription("Essential: Mail").assertIsDisplayed()
        compose.onNodeWithContentDescription("Docs, pinned").assertIsDisplayed()
        compose.onNodeWithContentDescription("News").assertIsDisplayed()
        val pinnedTop = compose.onNodeWithContentDescription("Docs, pinned").getUnclippedBoundsInRoot().top
        assertTrue("pinned tabs are below the URL bar", pinnedTop > top(SidebarTags.URL_BAR))
    }

    @Test
    fun essentialsStayVisibleInAnotherSpaceButPinnedTabsDoNot() {
        var state = sampleState()
        state = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).state
        compose.setContent { LaxTheme { Sidebar(state, RecordingActions(), focusUrlBar = false, onUrlBarFocused = {}) } }

        compose.onNodeWithContentDescription("Essential: Mail").assertIsDisplayed()
        compose.onNodeWithContentDescription("Docs, pinned").assertDoesNotExist()
        compose.onNodeWithContentDescription("News").assertDoesNotExist()
    }

    @Test
    fun urlBarSubmitsTheEnteredText() {
        val actions = RecordingActions()
        val state = sampleState()
        compose.setContent { LaxTheme { Sidebar(state, actions, focusUrlBar = false, onUrlBarFocused = {}) } }

        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("example.com")
        field.performImeAction()
        assertEquals(listOf("example.com"), actions.submitted)
    }
}
