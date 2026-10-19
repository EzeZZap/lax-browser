package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private val choices = OnboardingChoices(
        toolbarPosition = ToolbarPosition.BOTTOM,
        sidebarOnRight = true,
        searchEngineId = "brave",
        searchSuggestions = false,
        trackingProtection = TrackingProtection.STRICT,
        httpsOnly = true,
        autoUnloadMinutes = 15,
        privateTabsLock = true,
        space = SpaceSetup("Work", "💼", 0xFF1E88E5.toInt()),
        essentials = listOf(EssentialPick("https://mail.google.com/", "Gmail"), EssentialPick("https://www.youtube.com/", "YouTube")),
    )

    @Test
    fun `onboarding applies settings and finishes`() {
        val state = ops.applyOnboarding(initialState(ops), choices).state
        val s = state.data.settings
        assertTrue(s.onboardingDone)
        assertEquals(ToolbarPosition.BOTTOM, s.toolbarPosition)
        assertTrue(s.sidebarOnRight)
        assertEquals("brave", s.searchEngineId)
        assertFalse(s.searchSuggestions)
        assertEquals(TrackingProtection.STRICT, s.trackingProtection)
        assertTrue(s.httpsOnly)
        assertEquals(15, s.autoUnloadMinutes)
        assertTrue(s.privateTabsLock)
    }

    @Test
    fun `the preset Space gets its own container and the user stays in the first Space`() {
        val start = initialState(ops)
        val home = start.activeSpace!!
        val state = ops.applyOnboarding(start, choices).state
        val work = state.data.spaces.single { it.name == "Work" }
        assertEquals("💼", work.emoji)
        assertNotEquals(home.containerId, work.containerId)
        assertTrue(state.data.containers.any { it.id == work.containerId })
        assertEquals(home.id, state.activeSpace!!.id)
    }

    @Test
    fun `preset Essentials are global, unloaded, and not duplicated`() {
        val start = initialState(ops)
        var state = ops.applyOnboarding(start, choices).state
        state = ops.applyOnboarding(state, choices).state
        assertEquals(listOf("https://mail.google.com/", "https://www.youtube.com/"), state.essentials.map { it.url })
        assertTrue(state.essentials.all { it.spaceId == null && it.containerId == start.activeSpace!!.containerId })
        assertTrue(state.essentials.all { state.lifecycle(it) == TabLifecycle.UNLOADED })
        assertEquals("the Space is created once", 1, state.data.spaces.count { it.name == "Work" })
    }

    @Test
    fun `skipping keeps the current settings`() {
        val start = initialState(ops)
        val state = ops.applyOnboarding(start, OnboardingChoices.from(start.data.settings)).state
        assertEquals(start.data.settings.copy(onboardingDone = true), state.data.settings)
        assertEquals(start.data.spaces, state.data.spaces)
        assertTrue(state.essentials.isEmpty())
    }
}
