package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.BuildConfig
import com.javzzap.laxbrowser.platform.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {
    private val sample = """
        # Changelog

        ## Unreleased

        - Not shipped yet

        ## 0.2.0

        - Import **logins** from `CSV` files, and
          export them too
        - Password generator
        that also works offline

        ## 0.1.0

        Initial release.

        ### Tabs
        - Sidebar
        ### Privacy
        - Private tabs
    """.trimIndent()

    @Test
    fun `releases, groups, notes, and wrapped items are read`() {
        val releases = Changelog.parse(sample)
        assertEquals(listOf("0.2.0", "0.1.0"), releases.map { it.version })
        val latest = releases[0].sections.single()
        assertNull(latest.title)
        assertEquals(listOf("Import logins from CSV files, and export them too", "Password generator that also works offline"), latest.items)
        val first = releases[1].sections
        assertEquals(listOf(null, "Tabs", "Privacy"), first.map { it.title })
        assertEquals(listOf("Initial release."), first[0].notes)
        assertEquals(listOf("Private tabs"), first[2].items)
    }

    @Test
    fun `bundled changelogs list the same versions in both languages`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
        val english = Changelog.parse(File(root, "CHANGELOG.md").readText())
        val spanish = Changelog.parse(File(root, "CHANGELOG.es.md").readText())
        assertTrue(english.isNotEmpty())
        assertEquals(english.map { it.version }, spanish.map { it.version })
    }

    @Test
    fun `the version being built has release notes in both languages`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
        listOf("CHANGELOG.md", "CHANGELOG.es.md").forEach { name ->
            val versions = Changelog.parse(File(root, name).readText()).map { it.version }
            assertTrue("$name needs a \"## ${BuildConfig.VERSION_NAME}\" section", BuildConfig.VERSION_NAME in versions)
        }
    }

    @Test
    fun `private tabs lock only when the setting is on and private tabs are open`() {
        assertTrue(PrivateTabsLock.locksOnLeave(BrowserSettings(privateTabsLock = true), privateTabCount = 2))
        assertFalse(PrivateTabsLock.locksOnLeave(BrowserSettings(privateTabsLock = true), privateTabCount = 0))
        assertFalse(PrivateTabsLock.locksOnLeave(BrowserSettings(privateTabsLock = false), privateTabCount = 2))
    }

    @Test
    fun `what's new shows once after an update but not on a fresh install`() {
        assertFalse(WhatsNew.shouldShow(null, "0.3.0", onboardingDone = false, Versions::isNewer))
        assertTrue(WhatsNew.shouldShow(null, "0.3.0", onboardingDone = true, Versions::isNewer))
        assertTrue(WhatsNew.shouldShow("0.2.1", "0.3.0", onboardingDone = true, Versions::isNewer))
        assertFalse(WhatsNew.shouldShow("0.3.0", "0.3.0", onboardingDone = true, Versions::isNewer))
        assertFalse(WhatsNew.shouldShow("0.4.0", "0.3.0", onboardingDone = true, Versions::isNewer))
    }
}
