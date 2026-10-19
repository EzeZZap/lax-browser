package com.javzzap.laxbrowser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.engine.gecko.GeckoEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies with the real Gecko engine that containers (session context IDs) isolate cookies and
 * localStorage, keep data across sessions of the same container, and lose it when cleared.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ContainerIsolationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val engine: GeckoEngine = ApplicationProvider.getApplicationContext<Context>().appContainer.engine
    private lateinit var server: TestHttpServer
    private val containerA = "test-a-" + UUID.randomUUID()
    private val containerB = "test-b-" + UUID.randomUUID()

    @Before
    fun setUp() {
        server = TestHttpServer().start()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            engine.runtime.storageController.clearDataForSessionContext(containerA)
            engine.runtime.storageController.clearDataForSessionContext(containerB)
        }
        server.stop()
    }

    /** Opens a fresh session in [containerId], loads the storage page, and returns what it saw. */
    private fun visit(containerId: String, set: String? = null): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        var session: GeckoSession? = null
        instrumentation.runOnMainSync {
            session = engine.sessionFactory.create(containerId).apply {
                contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onTitleChange(session: GeckoSession, title: String?) {
                        if (title != null && title.startsWith("result:")) {
                            result = title.removePrefix("result:")
                            latch.countDown()
                        }
                    }
                }
                open(engine.runtime)
                setActive(true)
                loadUri(server.url("/storage" + (set?.let { "?set=$it" } ?: "?r=" + UUID.randomUUID())))
            }
        }
        val completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { session?.close() }
        assertTrue("page did not report storage in time", completed)
        return result!!
    }

    @Test
    fun storageIsIsolatedBetweenContainersAndKeptWithinOne() {
        assertEquals("ls=none;cookie=none", visit(containerA, set = "alpha"))
        assertEquals("a new session in the same container sees its data", "ls=alpha;cookie=alpha", visit(containerA))
        assertEquals("another container sees nothing", "ls=none;cookie=none", visit(containerB))

        visit(containerB, set = "beta")
        assertEquals("ls=alpha;cookie=alpha", visit(containerA))
        assertEquals("ls=beta;cookie=beta", visit(containerB))
    }

    @Test
    fun clearingAContainerRemovesOnlyItsData() {
        visit(containerA, set = "alpha")
        visit(containerB, set = "beta")
        instrumentation.runOnMainSync { engine.runtime.storageController.clearDataForSessionContext(containerA) }

        // clearDataForSessionContext has no completion callback; re-check a bounded number of times.
        var seen = visit(containerA)
        var attempts = 1
        while (seen != "ls=none;cookie=none" && attempts < MAX_CLEAR_CHECKS) {
            seen = visit(containerA)
            attempts++
        }
        assertEquals("ls=none;cookie=none", seen)
        assertEquals("ls=beta;cookie=beta", visit(containerB))
    }

    companion object {
        private const val TIMEOUT_SECONDS = 60L
        private const val MAX_CLEAR_CHECKS = 10
    }
}
