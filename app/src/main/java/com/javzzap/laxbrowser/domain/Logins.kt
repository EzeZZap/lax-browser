package com.javzzap.laxbrowser.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved website login. [origin] is the page's origin (`https://example.com`), [formActionOrigin]
 * where the form submits, [httpRealm] set for HTTP authentication instead of forms.
 */
data class SavedLogin(
    val guid: String,
    val origin: String,
    val formActionOrigin: String?,
    val httpRealm: String?,
    val username: String,
    val password: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val timesUsed: Int = 0,
) {
    /** The site's host, for display and grouping. */
    val host: String get() = UrlInput.hostOf(origin).removePrefix("www.")
}

/** Saved logins plus the sites the user said never to save for. */
data class LoginData(val logins: List<SavedLogin> = emptyList(), val neverSave: Set<String> = emptySet())

/** How many logins an import or restore added, and how many were already saved. */
data class LoginImportSummary(val imported: Int, val skipped: Int)

/** Pure login rules (matching, saving, encoding), unit-tested. Storage and encryption live elsewhere. */
object Logins {
    private const val FORMAT = 1

    /** Logins for [domain] and its subdomains (Gecko asks by base domain, like `example.com`). */
    fun forDomain(logins: List<SavedLogin>, domain: String): List<SavedLogin> {
        val wanted = domain.trim().lowercase().removePrefix("www.")
        if (wanted.isEmpty()) return emptyList()
        return logins.filter { login ->
            val host = UrlInput.hostOf(login.origin).lowercase()
            host == wanted || host.endsWith(".$wanted")
        }
    }

    /**
     * Adds or updates a login: the same [guid] updates it, otherwise the same origin and username
     * changes the password, otherwise a new login is added with [newGuid].
     */
    fun save(
        logins: List<SavedLogin>,
        guid: String?,
        origin: String,
        formActionOrigin: String?,
        httpRealm: String?,
        username: String,
        password: String,
        now: Long,
        newGuid: () -> String,
    ): List<SavedLogin> {
        val existing = logins.firstOrNull { guid != null && it.guid == guid }
            ?: logins.firstOrNull { it.origin == origin && it.username == username && it.httpRealm == httpRealm }
        if (existing != null) {
            val updated = existing.copy(
                origin = origin,
                formActionOrigin = formActionOrigin ?: existing.formActionOrigin,
                username = username,
                password = password,
                lastUsedAt = now,
            )
            return logins.map { if (it.guid == existing.guid) updated else it }
        }
        return logins + SavedLogin(newGuid(), origin, formActionOrigin, httpRealm, username, password, now, now)
    }

    /**
     * Adds imported logins. A login for the same site, username, and HTTP realm as one already
     * saved (or earlier in the same file) is skipped, so the saved password is never replaced.
     */
    fun merge(logins: List<SavedLogin>, incoming: List<ImportedLogin>, now: Long, newGuid: () -> String): Pair<List<SavedLogin>, LoginImportSummary> {
        val result = logins.toMutableList()
        val known = logins.mapTo(HashSet()) { Triple(it.origin, it.username, it.httpRealm) }
        var skipped = 0
        incoming.forEach { login ->
            if (!known.add(Triple(login.origin, login.username, login.httpRealm))) {
                skipped++
                return@forEach
            }
            val created = login.createdAt ?: now
            result += SavedLogin(newGuid(), login.origin, login.formActionOrigin, login.httpRealm, login.username, login.password, created, login.lastUsedAt ?: created)
        }
        return result to LoginImportSummary(result.size - logins.size, skipped)
    }

    /** Records that a login filled a form. */
    fun used(logins: List<SavedLogin>, guid: String?, now: Long): List<SavedLogin> =
        logins.map { if (it.guid == guid) it.copy(lastUsedAt = now, timesUsed = it.timesUsed + 1) else it }

    /** The origin used for "never save" (scheme and host), or null for non-web origins. */
    fun neverSaveKey(origin: String): String? = origin.trim().lowercase().takeIf(UrlInput::isWebUrl)?.let { url ->
        val scheme = url.substringBefore("://")
        "$scheme://${UrlInput.hostOf(url)}"
    }

    fun encode(data: LoginData): String = JSONObject().apply {
        put("format", FORMAT)
        put("logins", JSONArray(data.logins.map { login ->
            JSONObject()
                .put("guid", login.guid)
                .put("origin", login.origin)
                .putOpt("formActionOrigin", login.formActionOrigin)
                .putOpt("httpRealm", login.httpRealm)
                .put("username", login.username)
                .put("password", login.password)
                .put("createdAt", login.createdAt)
                .put("lastUsedAt", login.lastUsedAt)
                .put("timesUsed", login.timesUsed)
        }))
        put("neverSave", JSONArray(data.neverSave.sorted()))
    }.toString()

    /** Reads stored logins; malformed entries are skipped. Throws when the text is not login data. */
    fun decode(text: String): LoginData {
        val root = JSONObject(text)
        require(root.optInt("format", -1) in 1..FORMAT) { "Unknown login data format" }
        fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
        val logins = root.optJSONArray("logins")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                SavedLogin(
                    guid = o.str("guid") ?: return@mapNotNull null,
                    origin = o.str("origin") ?: return@mapNotNull null,
                    formActionOrigin = o.str("formActionOrigin"),
                    httpRealm = o.str("httpRealm"),
                    username = o.optString("username"),
                    password = o.str("password") ?: return@mapNotNull null,
                    createdAt = o.optLong("createdAt"),
                    lastUsedAt = o.optLong("lastUsedAt"),
                    timesUsed = o.optInt("timesUsed"),
                )
            }
        }.orEmpty().distinctBy { it.guid }
        val never = root.optJSONArray("neverSave")?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
        return LoginData(logins, never.toSet())
    }
}
