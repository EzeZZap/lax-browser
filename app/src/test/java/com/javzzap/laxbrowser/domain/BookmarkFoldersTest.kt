package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkFoldersTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `folders nest and bookmarks can be filed into them`() {
        var state = initialState(ops)
        val work = ops.createBookmarkFolder(state, "Work", null).also { state = it.state }.id()
        val docs = ops.createBookmarkFolder(state, "Docs", work).also { state = it.state }.id()
        val bookmark = ops.addBookmark(state, "https://docs.example", "Docs", folderId = docs).also { state = it.state }.id()

        assertEquals(listOf(work), state.childFolders(null).map { it.id })
        assertEquals(listOf(docs), state.childFolders(work).map { it.id })
        assertEquals(listOf(bookmark), state.bookmarksIn(docs).map { it.id })
        assertTrue(state.bookmarksIn(null).isEmpty())

        state = state.then { ops.moveBookmarkToFolder(it, bookmark, null) }
        assertEquals(listOf(bookmark), state.bookmarksIn(null).map { it.id })
    }

    @Test
    fun `a folder cannot be moved into itself or its subfolders`() {
        var state = initialState(ops)
        val a = ops.createBookmarkFolder(state, "A", null).also { state = it.state }.id()
        val b = ops.createBookmarkFolder(state, "B", a).also { state = it.state }.id()
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.moveBookmarkFolder(state, a, b).outcome)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.moveBookmarkFolder(state, a, a).outcome)

        val c = ops.createBookmarkFolder(state, "C", null).also { state = it.state }.id()
        state = state.then { ops.moveBookmarkFolder(it, b, c) }
        assertEquals(listOf(b), state.childFolders(c).map { it.id })
    }

    @Test
    fun `deleting a folder removes everything inside it only`() {
        var state = initialState(ops)
        val a = ops.createBookmarkFolder(state, "A", null).also { state = it.state }.id()
        val b = ops.createBookmarkFolder(state, "B", a).also { state = it.state }.id()
        state = state
            .then { ops.addBookmark(it, "https://one.example", null, folderId = a) }
            .then { ops.addBookmark(it, "https://two.example", null, folderId = b) }
            .then { ops.addBookmark(it, "https://kept.example", null) }

        assertEquals(1 to 2, ops.folderContents(state, a))
        state = state.then { ops.deleteBookmarkFolder(it, a) }
        assertTrue(state.data.bookmarkFolders.isEmpty())
        assertEquals(listOf("https://kept.example"), state.data.bookmarks.map { it.url })
    }

    @Test
    fun `addresses stay unique across folders`() {
        var state = initialState(ops)
        val a = ops.createBookmarkFolder(state, "A", null).also { state = it.state }.id()
        val existing = ops.addBookmark(state, "https://example.com", null).also { state = it.state }.id()
        assertEquals(Outcome.AlreadyExists(existing), ops.addBookmark(state, "https://example.com/", null, folderId = a).outcome)
    }

    @Test
    fun `corrupt folder data is displayed at the root instead of disappearing`() {
        val orphan = BookmarkFolder("orphan", "Orphan", "missing", 0, 0, 0)
        val loopA = BookmarkFolder("a", "A", "b", 1, 0, 0)
        val loopB = BookmarkFolder("b", "B", "a", 2, 0, 0)
        val stray = Bookmark("x", "https://x.example", "X", null, 0, 0, 0, folderId = "missing")
        val state = BrowserState(isLoaded = true, data = BrowserData(bookmarkFolders = listOf(orphan, loopA, loopB), bookmarks = listOf(stray)))
        assertEquals(setOf("orphan", "a", "b"), state.childFolders(null).map { it.id }.toSet())
        assertNull(state.effectiveParent(loopA))
        assertEquals(listOf("x"), state.bookmarksIn(null).map { it.id })
    }

    @Test
    fun `folder names are required`() {
        val state = initialState(ops)
        assertEquals(Outcome.Rejected(RejectReason.INVALID_NAME), ops.createBookmarkFolder(state, "  ", null).outcome)
        assertEquals(Outcome.Rejected(RejectReason.NOT_FOUND), ops.createBookmarkFolder(state, "X", "missing").outcome)
    }
}
