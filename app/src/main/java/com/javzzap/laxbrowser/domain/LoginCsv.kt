package com.javzzap.laxbrowser.domain

/** A login read from another browser's export, before it is merged into the saved logins. */
data class ImportedLogin(
    val origin: String,
    val formActionOrigin: String?,
    val httpRealm: String?,
    val username: String,
    val password: String,
    val createdAt: Long? = null,
    val lastUsedAt: Long? = null,
)

/** Which app wrote a password file, detected from its header row. */
enum class LoginCsvFormat { FIREFOX, CHROMIUM, BITWARDEN, GENERIC }

/** [skipped] counts rows without a password or a website address (for example app logins). */
data class LoginCsvResult(val format: LoginCsvFormat?, val logins: List<ImportedLogin>, val skipped: Int)

/**
 * Password files exported by Firefox, Chrome and other Chromium browsers (Edge, Brave, Vivaldi,
 * Opera, Samsung Internet), Bitwarden, and other managers with similar columns. Lax exports in
 * Firefox's format, which all of them can import. The text is untrusted and never logged.
 */
object LoginCsv {
    private const val MAX_ROWS = 50_000

    private val urlColumns = listOf("url", "login_uri", "website", "origin", "hostname", "web site", "uri")
    private val usernameColumns = listOf("username", "login_username", "user", "email", "user name", "login")
    private val passwordColumns = listOf("password", "login_password")

    private val exportHeader = listOf("url", "username", "password", "httpRealm", "formActionOrigin", "guid", "timeCreated", "timeLastUsed", "timePasswordChanged")

    fun parse(text: String): LoginCsvResult {
        val rows = rows(text.removePrefix("\uFEFF"))
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return LoginCsvResult(null, emptyList(), 0)
        fun column(names: List<String>) = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }
        val url = column(urlColumns)
        val password = column(passwordColumns)
        if (url == null || password == null) return LoginCsvResult(null, emptyList(), 0)
        val username = column(usernameColumns)
        val realm = column(listOf("httprealm"))
        val formAction = column(listOf("formactionorigin"))
        val created = column(listOf("timecreated"))
        val lastUsed = column(listOf("timelastused"))
        val type = column(listOf("type"))
        val format = when {
            "guid" in header && realm != null -> LoginCsvFormat.FIREFOX
            "login_uri" in header -> LoginCsvFormat.BITWARDEN
            header.take(4) == listOf("name", "url", "username", "password") -> LoginCsvFormat.CHROMIUM
            else -> LoginCsvFormat.GENERIC
        }

        var skipped = 0
        val logins = rows.drop(1).take(MAX_ROWS).mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            // Usernames and passwords keep their spaces; the other cells are trimmed.
            fun raw(index: Int?) = index?.let(row::getOrNull).orEmpty()
            fun cell(index: Int?) = raw(index).trim()
            if (type != null && cell(type).isNotEmpty() && !cell(type).equals("login", ignoreCase = true)) {
                skipped++
                return@mapNotNull null
            }
            // Bitwarden puts several addresses in one cell; the first one is the main site.
            val origin = originOf(cell(url).split(',').first())
            val secret = raw(password)
            if (origin == null || secret.isEmpty()) {
                skipped++
                return@mapNotNull null
            }
            ImportedLogin(
                origin = origin,
                formActionOrigin = originOf(cell(formAction)),
                httpRealm = cell(realm).takeIf { it.isNotEmpty() },
                username = raw(username),
                password = secret,
                createdAt = cell(created).toLongOrNull()?.takeIf { it > 0 },
                lastUsedAt = cell(lastUsed).toLongOrNull()?.takeIf { it > 0 },
            )
        }
        return LoginCsvResult(format, logins, skipped)
    }

    /** Firefox's export format; usernames and passwords are kept exactly as saved. */
    fun export(logins: List<SavedLogin>): String = buildString {
        appendRow(exportHeader)
        logins.sortedWith(compareBy({ it.host }, { it.username })).forEach { login ->
            appendRow(
                listOf(
                    login.origin,
                    login.username,
                    login.password,
                    login.httpRealm.orEmpty(),
                    login.formActionOrigin.orEmpty(),
                    "{${login.guid}}",
                    login.createdAt.toString(),
                    login.lastUsedAt.toString(),
                    login.createdAt.toString(),
                ),
            )
        }
    }

    /** The page origin (`https://host[:port]`) of a saved address, or null for non-web entries. */
    internal fun originOf(address: String): String? {
        var url = address.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        if (!url.contains("://")) url = "https://$url"
        val scheme = url.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        if (authority.isEmpty()) return null
        return "$scheme://${authority.lowercase()}".takeIf(UrlInput::isWebUrl)
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        append("\r\n")
    }

    /** RFC 4180 rows: quoted cells may contain commas, quotes (doubled), and line breaks. */
    internal fun rows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == ',' -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = ArrayList()
                    if (rows.size > MAX_ROWS) return rows
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }
}
