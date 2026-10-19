package com.javzzap.laxbrowser.state

import com.javzzap.laxbrowser.data.PersistenceQueue
import com.javzzap.laxbrowser.data.SessionStateStore
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.LaunchPolicy
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.TabLifecycle
import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.FakeEngine
import com.javzzap.laxbrowser.testing.FakeHistoryStore
import com.javzzap.laxbrowser.testing.InMemoryRepository
import com.javzzap.laxbrowser.testing.SequentialIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserControllerTest {
    private val clock = FakeClock()
    private val repository = InMemoryRepository()
    private var idPrefix = 0

    private class Harness(val controller: BrowserController, val engine: FakeEngine)

    private val launched = mutableListOf<BrowserController>()

    /** Runs pending work, including the background persistence queues, until everything is written. */
    private suspend fun TestScope.settle() {
        this.advanceUntilIdle()
        launched.forEach { it.awaitPersistence() }
    }

    /** Builds a controller as a fresh process would, on top of the shared [repository]. */
    private suspend fun TestScope.launchBrowser(): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = PersistenceQueue(backgroundScope, dispatcher)
        val engine = FakeEngine()
        val sessionStates = SessionStateStore(repository, queue, backgroundScope, clock)
        val controller = BrowserController(
            repository = repository,
            engine = engine,
            sessionStates = sessionStates,
            queue = queue,
            clock = clock,
            ops = BrowserOperations(clock, SequentialIds("run${idPrefix++}")),
            scope = this,
            ioDispatcher = dispatcher,
        )
        launched += controller
        controller.start()
        settle()
        return Harness(controller, engine)
    }

    @Test
    fun `first launch creates defaults and opens the new tab page`() = runTest {
        val browser = launchBrowser()
        browser.controller.onForeground()
        settle()
        val state = browser.controller.state.value
        assertEquals(1, state.data.spaces.size)
        assertEquals(TabKind.NEW_TAB, state.activeTab!!.kind)
        assertEquals(1, repository.data.spaces.size)
        assertEquals(1, repository.data.tabs.size)
    }

    @Test
    fun `foreground before load is applied once state is ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = PersistenceQueue(backgroundScope, dispatcher)
        val controller = BrowserController(
            repository, FakeEngine(), SessionStateStore(repository, queue, backgroundScope, clock), queue, clock,
            BrowserOperations(clock, SequentialIds()), this, dispatcher,
        )
        controller.start()
        controller.onForeground()
        settle()
        assertEquals(TabKind.NEW_TAB, controller.state.value.activeTab!!.kind)
    }

    @Test
    fun `pinned and unloaded tabs, bookmarks and shortcuts survive a restart`() = runTest {
        val first = launchBrowser().controller
        val tabId = (first.newTab("https://example.com").outcome as Outcome.Created).id
        first.pinTab(tabId)
        first.newTab("https://other.example")
        first.unloadTab(tabId)
        first.addBookmark("https://bookmark.example", "B")
        first.addShortcut("https://shortcut.example", "S")
        settle()

        val second = launchBrowser().controller
        val state = second.state.value
        val tab = state.tab(tabId)!!
        assertTrue(tab.isPinned)
        assertEquals("https://example.com", tab.url)
        assertEquals(TabLifecycle.UNLOADED, state.lifecycle(tab))
        assertEquals(listOf("https://bookmark.example"), state.data.bookmarks.map { it.url })
        assertEquals(listOf("https://shortcut.example"), state.data.shortcuts.map { it.url })
    }

    @Test
    fun `restart within an hour does not add a new tab page, after an hour it does`() = runTest {
        val first = launchBrowser().controller
        first.newTab("https://example.com")
        first.onBackground()
        settle()

        clock.advance(LaunchPolicy.NEW_TAB_PAGE_THRESHOLD_MS - 1)
        val second = launchBrowser().controller
        second.onForeground()
        settle()
        assertEquals(1, second.state.value.data.tabs.size)
        assertEquals(TabKind.WEB, second.state.value.activeTab!!.kind)
        second.onBackground()
        settle()

        clock.advance(LaunchPolicy.NEW_TAB_PAGE_THRESHOLD_MS)
        val third = launchBrowser().controller
        third.onForeground()
        settle()
        assertEquals(2, third.state.value.data.tabs.size)
        assertEquals(TabKind.NEW_TAB, third.state.value.activeTab!!.kind)
    }

    @Test
    fun `selected tab gets a session in its space's container, others are not loaded`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val a = (c.newTab("https://a.example").outcome as Outcome.Created).id
        val b = (c.newTab("https://b.example").outcome as Outcome.Created).id
        settle()
        val container = c.state.value.activeSpace!!.containerId
        assertEquals(container, browser.engine.sessions[b])
        assertEquals(b, browser.engine.activeTabId)

        val restarted = launchBrowser()
        assertTrue("only the selected tab is loaded after restart", restarted.engine.sessions.keys == setOf(b))
        assertFalse(restarted.controller.state.value.runtime(a).loaded)
    }

    @Test
    fun `closing a tab closes its session before removing the record`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val id = (c.newTab("https://a.example").outcome as Outcome.Created).id
        c.closeTab(id)
        settle()
        assertTrue(id in browser.engine.closed)
        assertNull(c.state.value.tab(id))
        assertTrue(repository.data.tabs.none { it.id == id })
    }

    @Test
    fun `closed tabs can be reopened, essentials are not offered`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val id = (c.newTab("https://a.example").outcome as Outcome.Created).id
        c.onSessionStateChange(id, "{\"history\":[]}")
        c.closeTab(id)
        settle()
        assertEquals(listOf(id), c.recentlyClosed.value.map { it.tab.id })
        assertEquals("{\"history\":[]}", c.recentlyClosed.value.single().sessionState)
        assertTrue(repository.data.tabs.none { it.id == id })

        c.reopenClosedTab()
        settle()
        assertNotNull(c.state.value.tab(id))
        assertTrue(c.recentlyClosed.value.isEmpty())
        assertTrue(browser.engine.sessions.containsKey(id))
        assertTrue(repository.data.tabs.any { it.id == id })

        val essential = (c.newTab("https://mail.example").outcome as Outcome.Created).id
        c.addToEssentials(essential)
        c.closeTab(essential)
        assertTrue(c.recentlyClosed.value.isEmpty())
        assertNotNull(c.state.value.tab(essential))
    }

    @Test
    fun `visits are recorded with their final title and can be cleared`() = runTest {
        val history = FakeHistoryStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = PersistenceQueue(backgroundScope, dispatcher)
        val c = BrowserController(
            repository, FakeEngine(), SessionStateStore(repository, queue, backgroundScope, clock), queue, clock,
            BrowserOperations(clock, SequentialIds()), this, dispatcher, history,
        )
        c.start()
        settle()
        val tab = (c.newTab("https://a.example/").outcome as Outcome.Created).id

        c.onVisited(tab, "https://a.example/")
        c.onTitleChange(tab, "Page A")
        c.onVisited(tab, "https://a.example/")
        settle()
        assertEquals(1, history.entries.size)
        assertEquals("Page A", history.entries.single().title)

        clock.advance(120_000L)
        c.onVisited(tab, "https://a.example/")
        settle()
        assertEquals("a visit after the duplicate window is a new entry", 2, history.entries.size)

        c.clearHistory(0L)
        settle()
        assertEquals(0L, history.clearedSince)
        assertTrue(history.entries.isEmpty())
    }

    @Test
    fun `private tabs get private sessions but are never persisted, recorded or reopenable`() = runTest {
        val history = FakeHistoryStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = PersistenceQueue(backgroundScope, dispatcher)
        val engine = FakeEngine()
        val c = BrowserController(
            repository, engine, SessionStateStore(repository, queue, backgroundScope, clock), queue, clock,
            BrowserOperations(clock, SequentialIds()), this, dispatcher, history,
        )
        c.start()
        settle()
        c.setPrivateMode(true)
        val tab = (c.newTab("https://secret.example/").outcome as Outcome.Created).id
        assertTrue(engine.sessions.containsKey(tab))

        c.onVisited(tab, "https://secret.example/")
        c.onTitleChange(tab, "Secret")
        c.onSessionStateChange(tab, "{\"history\":[]}")
        c.closeTab(tab)
        this.advanceUntilIdle()
        c.awaitPersistence()

        assertTrue(history.entries.isEmpty())
        assertTrue(repository.data.tabs.none { it.id == tab })
        assertTrue(repository.sessionStates.isEmpty())
        assertTrue(c.recentlyClosed.value.isEmpty())
        assertFalse(engine.sessions.containsKey(tab))
    }

    @Test
    fun `switching spaces keeps sessions of the previous space open`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val personal = c.state.value.activeSpace!!
        val a = (c.newTab("https://a.example").outcome as Outcome.Created).id
        val work = (c.createSpace("Work", 0, com.javzzap.laxbrowser.domain.ContainerChoice.New("Work", 0)).outcome as Outcome.Created).id
        val b = (c.newTab("https://b.example").outcome as Outcome.Created).id
        assertTrue(browser.engine.sessions.containsKey(a))
        assertEquals(b, browser.engine.activeTabId)

        c.switchSpace(personal.id)
        assertEquals(a, browser.engine.activeTabId)
        assertTrue(browser.engine.sessions.containsKey(b))
        assertEquals(c.state.value.space(work)!!.containerId, browser.engine.sessions[b])
    }

    @Test
    fun `changing a space's container replaces live sessions with the new context`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val space = c.state.value.activeSpace!!
        val tab = (c.newTab("https://a.example").outcome as Outcome.Created).id
        val newContainer = (c.createContainer("Other", 0).outcome as Outcome.Created).id
        c.setSpaceContainer(space.id, newContainer)
        assertEquals(newContainer, browser.engine.sessions[tab])
        assertTrue(browser.engine.opened.contains(tab to space.containerId))
        assertTrue(browser.engine.opened.contains(tab to newContainer))
    }

    @Test
    fun `deleting a space is persisted atomically with its tabs`() = runTest {
        val browser = launchBrowser()
        val c = browser.controller
        val work = (c.createSpace("Work", 0, com.javzzap.laxbrowser.domain.ContainerChoice.New("Work", 0)).outcome as Outcome.Created).id
        c.newTab("https://a.example")
        c.newTab("https://b.example")
        settle()
        val writesBefore = repository.applyCount

        c.deleteSpace(work)
        settle()
        assertEquals(writesBefore + 1, repository.applyCount)
        assertTrue(repository.data.tabs.none { it.spaceId == work })
        assertNull(repository.data.spaces.firstOrNull { it.id == work })
        assertNotNull(repository.data.containers.firstOrNull { it.name == "Work" })
    }
}
