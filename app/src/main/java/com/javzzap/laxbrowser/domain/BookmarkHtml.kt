package com.javzzap.laxbrowser.domain

/**
 * Reads and writes the Netscape bookmark file format that Firefox, Chrome, and most browsers use
 * to import and export bookmarks. Imported files are untrusted: only folder names, titles, and
 * http(s) addresses are taken from them.
 */
object BookmarkHtml {
    sealed interface Node
    data class Folder(val name: String, val children: List<Node>) : Node
    data class Link(val url: String, val title: String) : Node

    private const val MAX_NODES = 50_000
    private const val MAX_TEXT = 500

    fun export(state: BrowserState, rootTitle: String): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<!-- This is an automatically generated file. It will be read and overwritten. -->")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>${escape(rootTitle)}</TITLE>")
        appendLine("<H1>${escape(rootTitle)}</H1>")
        appendLine("<DL><p>")
        writeFolder(state, null, 1)
        appendLine("</DL><p>")
    }

    private fun StringBuilder.writeFolder(state: BrowserState, folderId: String?, depth: Int) {
        val indent = "    ".repeat(depth)
        state.childFolders(folderId).forEach { folder ->
            appendLine("$indent<DT><H3 ADD_DATE=\"${folder.createdAt / 1000}\" LAST_MODIFIED=\"${folder.updatedAt / 1000}\">${escape(folder.name)}</H3>")
            appendLine("$indent<DL><p>")
            writeFolder(state, folder.id, depth + 1)
            appendLine("$indent</DL><p>")
        }
        state.bookmarksIn(folderId).forEach { bookmark ->
            appendLine("$indent<DT><A HREF=\"${escape(bookmark.url)}\" ADD_DATE=\"${bookmark.createdAt / 1000}\">${escape(bookmark.title)}</A>")
        }
    }

    /** Parses a bookmark file into its top-level nodes. Malformed parts are skipped. */
    fun parse(html: String): List<Node> {
        val token = Regex("(?is)<h3[^>]*>(.*?)</h3>|<a\\s([^>]*)>(.*?)</a>|<dl[^>]*>|</dl\\s*>")
        val hrefPattern = Regex("(?is)\\bhref\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
        data class Level(val name: String?, val children: MutableList<Node> = mutableListOf())

        val root = Level(null)
        val stack = ArrayDeque<Level>()
        var pendingFolder: String? = null
        var count = 0
        for (match in token.findAll(html)) {
            if (++count > MAX_NODES) break
            val value = match.value
            when {
                value.startsWith("<h3", ignoreCase = true) -> pendingFolder = clean(match.groupValues[1]).ifBlank { "…" }
                value.startsWith("<a", ignoreCase = true) -> {
                    val href = hrefPattern.find(match.groupValues[2])?.let { h -> h.groupValues.drop(2).firstOrNull { it.isNotEmpty() } }
                    val url = href?.let { UrlInput.normalizeWebUrl(unescape(it).trim()) }
                    if (url != null) (stack.lastOrNull() ?: root).children += Link(url, clean(match.groupValues[3]))
                }
                value.startsWith("</", ignoreCase = true) -> {
                    val finished = stack.removeLastOrNull() ?: continue
                    val parent = stack.lastOrNull() ?: root
                    parent.children += Folder(finished.name ?: "", finished.children)
                }
                else -> {
                    // <DL> opens the folder named by the preceding <H3>; the first <DL> is the root list.
                    val name = pendingFolder
                    pendingFolder = null
                    if (name != null) stack.addLast(Level(name)) else if (stack.isNotEmpty()) stack.addLast(Level(null))
                }
            }
        }
        // Unclosed folders still count.
        while (stack.isNotEmpty()) {
            val finished = stack.removeLast()
            (stack.lastOrNull() ?: root).children += Folder(finished.name ?: "", finished.children)
        }
        return flattenUnnamed(root.children)
    }

    /** Folders without a name (extra nesting) are merged into their parent. */
    private fun flattenUnnamed(nodes: List<Node>): List<Node> = nodes.flatMap { node ->
        if (node is Folder && node.name.isEmpty()) flattenUnnamed(node.children) else listOf(if (node is Folder) node.copy(children = flattenUnnamed(node.children)) else node)
    }

    private fun clean(text: String): String = unescape(text.replace(Regex("<[^>]*>"), "")).replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unescape(text: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);").replace(text) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> {
                val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
            }
        }
    }

    fun countLinks(nodes: List<Node>): Int = nodes.sumOf { if (it is Folder) countLinks(it.children) else 1 }
}
