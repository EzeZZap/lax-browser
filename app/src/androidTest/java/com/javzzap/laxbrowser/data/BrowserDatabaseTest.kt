package com.javzzap.laxbrowser.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.DataDiff
import com.javzzap.laxbrowser.domain.IdGenerator
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.Reduction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ops = BrowserOperations(Clock.System, IdGenerator.Random)

    @get:Rule
    val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BrowserDatabase::class.java)

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(MIGRATION_DB)
    }

    private fun BrowserState.then(op: (BrowserState) -> Reduction): BrowserState = op(this).also { check(!it.isRejected) }.state
    private fun Reduction.id() = (outcome as Outcome.Created).id

    /** Validates the exported schema of every version and that all migrations reach the current one. */
    @Test
    fun schemaMigratesToCurrentVersion() {
        migrationHelper.createDatabase(MIGRATION_DB, 1).close()
        migrationHelper.runMigrationsAndValidate(MIGRATION_DB, BrowserDatabase.VERSION, true, *BrowserDatabase.MIGRATIONS).close()
    }

    @Test
    fun migration1To2KeepsDataAndAddsDefaults() {
        migrationHelper.createDatabase(MIGRATION_DB, 1).apply {
            execSQL(
                "INSERT INTO bookmarks (id, url, title, favicon_url, position, created_at, updated_at) " +
                    "VALUES ('b1', 'https://example.com', 'Example', NULL, 0, 1, 1)",
            )
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at) VALUES (0, 's1', 42)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 2, true, MIGRATION_1_2)
        db.query("SELECT title, folder_id FROM bookmarks WHERE id = 'b1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Example", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        db.query(
            "SELECT active_space_id, last_background_at, search_engine_id, toolbar_position, hide_toolbar_on_scroll, " +
                "pull_to_refresh, website_appearance, text_scale_percent, https_only, tracking_protection FROM browser_settings",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(42L, cursor.getLong(1))
            assertEquals("duckduckgo", cursor.getString(2))
            assertEquals("TOP", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals("SYSTEM", cursor.getString(6))
            assertEquals(100, cursor.getInt(7))
            assertEquals(0, cursor.getInt(8))
            assertEquals("STANDARD", cursor.getString(9))
        }
        db.query("SELECT COUNT(*) FROM bookmark_folders").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration2To3AddsAnEmptyHistoryTable() {
        migrationHelper.createDatabase(MIGRATION_DB, 2).apply {
            execSQL("INSERT INTO bookmark_folders (id, name, parent_id, position, created_at, updated_at) VALUES ('f1', 'Work', NULL, 0, 1, 1)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 3, true, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT name FROM bookmark_folders WHERE id = 'f1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Work", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun migration3To4TurnsOnPrivateBrowsingProtections() {
        migrationHelper.createDatabase(MIGRATION_DB, 3).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at) VALUES (0, 's1', 42)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 4, true, MIGRATION_3_4)
        db.query("SELECT active_space_id, private_block_screenshots, private_hide_in_recents FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    fun migration4To5SavesPinnedPagesAndAddsDefaults() {
        migrationHelper.createDatabase(MIGRATION_DB, 4).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at) VALUES (0, 's1', 42)")
            execSQL("INSERT INTO spaces (id, name, container_id, position, color, selected_tab_id) VALUES ('s1', 'Work', 'c1', 0, -1, NULL)")
            listOf(
                "('pinned', 's1', 'c1', 'https://pinned.example', 'P', NULL, 0, 'WEB', 1, 0, 1, 1)",
                "('essential', NULL, 'c1', 'https://essential.example', 'E', NULL, 0, 'WEB', 0, 1, 1, 1)",
                "('regular', 's1', 'c1', 'https://regular.example', 'R', NULL, 1, 'WEB', 0, 0, 1, 1)",
            ).forEach { values ->
                execSQL(
                    "INSERT INTO tabs (id, space_id, container_id, url, title, favicon_url, position, kind, is_pinned, is_essential, " +
                        "created_at, last_accessed_at) VALUES $values",
                )
            }
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 5, true, MIGRATION_4_5)
        db.query("SELECT id, pinned_url, folder_id FROM tabs ORDER BY id").use { cursor ->
            val rows = buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1) to cursor.isNull(2)) }
            assertEquals("https://essential.example" to true, rows["essential"])
            assertEquals("https://pinned.example" to true, rows["pinned"])
            assertEquals(null to true, rows["regular"])
        }
        db.query("SELECT emoji FROM spaces WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db.query("SELECT active_space_id, search_suggestions, global_privacy_control, autoplay FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals("BLOCK_AUDIBLE", cursor.getString(3))
        }
        listOf("tab_folders", "downloads", "extension_prefs").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        db.close()
    }

    @Test
    fun migration5To6SkipsOnboardingForUpgradesAndAddsSiteSettings() {
        migrationHelper.createDatabase(MIGRATION_DB, 5).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at) VALUES (0, 's1', 42)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 6, true, MIGRATION_5_6)
        db.query("SELECT active_space_id, auto_unload_minutes, onboarding_done, check_for_updates FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(30, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM site_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration6To7KeepsSettingsAndDefaultsTheToolbarLayout() {
        migrationHelper.createDatabase(MIGRATION_DB, 6).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at, auto_unload_minutes) VALUES (0, 's1', 42, 15)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 7, true, MIGRATION_6_7)
        db.query("SELECT active_space_id, auto_unload_minutes, toolbar_action, swap_toolbar_buttons, sidebar_on_right FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(15, cursor.getInt(1))
            assertEquals("RELOAD", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
        }
        db.close()
    }

    @Test
    fun migration7To8UsesLaxColorsAndTheSpaceAccent() {
        migrationHelper.createDatabase(MIGRATION_DB, 7).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at, toolbar_action) VALUES (0, 's1', 42, 'HOME')")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 8, true, MIGRATION_7_8)
        db.query("SELECT active_space_id, toolbar_action, wallpaper_colors, space_accent FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals("HOME", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
        }
        db.close()
    }

    @Test
    fun migration8To9OpensExternalLinksInTheActiveSpace() {
        migrationHelper.createDatabase(MIGRATION_DB, 8).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at, space_accent) VALUES (0, 's1', 42, 0)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 9, true, MIGRATION_8_9)
        db.query("SELECT active_space_id, space_accent, external_links FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("active", cursor.getString(2))
        }
        db.close()
    }

    @Test
    fun migration9To10OffersToSaveLogins() {
        migrationHelper.createDatabase(MIGRATION_DB, 9).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at, external_links) VALUES (0, 's1', 42, 'ask')")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 10, true, MIGRATION_9_10)
        db.query("SELECT active_space_id, external_links, save_logins FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals("ask", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    fun migration10To11LeavesPrivateTabsUnlocked() {
        migrationHelper.createDatabase(MIGRATION_DB, 10).apply {
            execSQL("INSERT INTO browser_settings (id, active_space_id, last_background_at, save_logins) VALUES (0, 's1', 42, 0)")
            close()
        }
        val db = migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 11, true, MIGRATION_10_11)
        db.query("SELECT active_space_id, save_logins, private_tabs_lock FROM browser_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    fun historyMergesQuickRevisitsSearchesAndClears() = runBlocking {
        BrowserDatabase.create(context, TEST_DB).use { db ->
            val history = RoomHistoryStore(db.historyDao())
            history.recordVisit("https://a.example/", "A", 1_000L)
            history.recordVisit("https://a.example/", "A", 30_000L)
            history.recordVisit("https://b.example/100%_off", "Sale", 200_000L)
            history.updateTitle("https://a.example/", "Page A")

            val all = history.observe("").first()
            assertEquals(listOf("https://b.example/100%_off", "https://a.example/"), all.map { it.url })
            assertEquals("Page A", all.last().title)
            assertEquals(listOf("https://b.example/100%_off"), history.observe("100%").first().map { it.url })
            assertEquals(setOf("https://a.example/"), history.visited(listOf("https://a.example/", "https://c.example/")))

            history.clearSince(100_000L)
            assertEquals(listOf("https://a.example/"), history.observe("").first().map { it.url })
            history.clearSince(0L)
            assertTrue(history.observe("").first().isEmpty())
        }
    }

    @Test
    fun browserDataSurvivesReopeningTheDatabase() = runBlocking {
        var state = ops.ensureDefaults(BrowserState(isLoaded = true)).state
        val pinned = ops.newTab(state, "https://pinned.example").also { state = it.state }.id()
        state = state.then { ops.pinTab(it, pinned) }.then { ops.unloadTab(it, pinned) }
        val essential = ops.newTab(state, "https://mail.example").also { state = it.state }.id()
        state = state
            .then { ops.addToEssentials(it, essential) }
            .then { ops.addBookmark(it, "https://bookmark.example", "Bookmark") }
            .then { ops.addShortcut(it, "https://shortcut.example", "Shortcut") }
            .then { ops.onBackground(it, 1234L) }
            .then { ops.updateSettings(it) { s -> s.copy(searchEngineId = "google", httpsOnly = true) } }
        val folder = ops.createBookmarkFolder(state, "Folder", null).also { state = it.state }.id()
        state = state.then { ops.addBookmark(it, "https://filed.example", "Filed", folderId = folder) }

        BrowserDatabase.create(context, TEST_DB).use { db ->
            val repository = RoomBrowserRepository(db.browserDao())
            repository.apply(DataDiff.between(BrowserData(), state.data))
            repository.saveSessionStates(mapOf(pinned to "{\"history\":[]}"), 0)
        }

        BrowserDatabase.create(context, TEST_DB).use { db ->
            val repository = RoomBrowserRepository(db.browserDao())
            val loaded = repository.load()
            assertEquals(state.data.containers.toSet(), loaded.containers.toSet())
            assertEquals(state.data.spaces.toSet(), loaded.spaces.toSet())
            assertEquals(state.data.tabs.toSet(), loaded.tabs.toSet())
            assertEquals(state.data.bookmarks.toSet(), loaded.bookmarks.toSet())
            assertEquals(state.data.bookmarkFolders, loaded.bookmarkFolders)
            assertEquals(state.data.shortcuts, loaded.shortcuts)
            assertEquals(state.data.settings, loaded.settings)
            assertTrue(loaded.tabs.first { it.id == pinned }.isPinned)
            assertEquals("{\"history\":[]}", repository.loadSessionState(pinned))
        }
    }

    @Test
    fun unreadableRowsAreSkippedWithoutBlockingStartup() = runBlocking {
        val state = ops.ensureDefaults(BrowserState(isLoaded = true)).state.then { ops.newTab(it, "https://good.example") }
        BrowserDatabase.create(context, TEST_DB).use { db ->
            RoomBrowserRepository(db.browserDao()).apply(DataDiff.between(BrowserData(), state.data))
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO tabs (id, space_id, container_id, url, title, favicon_url, position, kind, is_pinned, is_essential, created_at, last_accessed_at) " +
                    "VALUES ('bad', 'nowhere', 'c', 'https://bad.example', 'Bad', NULL, 0, 'SOMETHING_NEW', 0, 0, 0, 0)",
            )
            val loaded = RoomBrowserRepository(db.browserDao()).load()
            assertEquals(listOf("https://good.example"), loaded.tabs.map { it.url })
        }
    }

    @Test
    fun deletingASpaceRemovesItsTabsInOneTransaction() = runBlocking {
        var state = ops.ensureDefaults(BrowserState(isLoaded = true)).state
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        val tab = ops.newTab(state, "https://work.example").also { state = it.state }.id()

        BrowserDatabase.create(context, TEST_DB).use { db ->
            val repository = RoomBrowserRepository(db.browserDao())
            repository.apply(DataDiff.between(BrowserData(), state.data))
            repository.saveSessionStates(mapOf(tab to "{}"), 0)

            val deletion = ops.deleteSpace(state, work)
            repository.apply(DataDiff.between(state.data, deletion.state.data, deletion.discardSessionState))

            val loaded = repository.load()
            assertTrue(loaded.spaces.none { it.id == work })
            assertTrue(loaded.tabs.none { it.id == tab })
            assertTrue("the container and its data are kept", loaded.containers.any { it.name == "Work" })
            assertNull(repository.loadSessionState(tab))
        }
    }

    private inline fun <T> BrowserDatabase.use(block: (BrowserDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    companion object {
        private const val TEST_DB = "browser-test.db"
        private const val MIGRATION_DB = "browser-migration-test.db"
    }
}
