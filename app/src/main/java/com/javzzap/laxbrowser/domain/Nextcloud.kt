package com.javzzap.laxbrowser.domain

import org.json.JSONObject
import java.net.URLEncoder

/** A Nextcloud account Lax can back up to: the server, the login name, and an app password. */
data class NextcloudAccount(val server: String, val user: String, val appPassword: String)

/** Nextcloud backup settings, stored encrypted on the device. */
data class NextcloudConfig(
    val account: NextcloudAccount? = null,
    val autoBackup: Boolean = false,
    /** Encrypts saved logins in Nextcloud backups; null leaves logins out. */
    val loginsPassword: String? = null,
    val lastBackupAt: Long? = null,
)

/** Nextcloud's Login Flow v2: open [loginUrl] in the browser, then poll [pollEndpoint] with [token]. */
data class NextcloudLoginFlow(val loginUrl: String, val pollEndpoint: String, val token: String)

/** Pure Nextcloud rules (addresses, file locations, server replies), unit-tested. */
object Nextcloud {
    const val BACKUP_FILE = "Lax-backup.json"
    const val AUTO_BACKUP_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /**
     * The server's base address from what the user typed: `cloud.example.com`,
     * `https://example.com/nextcloud/`, or a pasted page address. Only HTTPS servers are accepted,
     * since the app password travels with every request.
     */
    fun normalizeServer(input: String): String? {
        var url = input.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        if (!url.contains("://")) url = "https://$url"
        if (!url.startsWith("https://", ignoreCase = true)) return null
        url = url.substringBefore('?').substringBefore('#')
        // Only the path is searched, so a host like login.example.com stays intact.
        val pathStart = url.indexOf('/', "https://".length).takeIf { it > 0 } ?: url.length
        val marker = listOf("/index.php", "/apps/", "/remote.php", "/login").map { url.indexOf(it, pathStart) }.filter { it >= 0 }.minOrNull()
        if (marker != null) url = url.substring(0, marker)
        url = url.trimEnd('/')
        return url.takeIf { UrlInput.isWebUrl(it) }
    }

    fun loginFlowUrl(server: String) = "$server/index.php/login/v2"

    fun backupUrl(account: NextcloudAccount) = "${account.server}/remote.php/dav/files/${encode(account.user)}/$BACKUP_FILE"

    /** Checks an app password typed by hand. */
    fun userUrl(server: String) = "$server/ocs/v2.php/cloud/user?format=json"

    fun parseLoginFlow(body: String): NextcloudLoginFlow? = runCatching {
        val json = JSONObject(body)
        val poll = json.getJSONObject("poll")
        NextcloudLoginFlow(
            loginUrl = json.getString("login").takeIf { it.startsWith("https://") } ?: return null,
            pollEndpoint = poll.getString("endpoint").takeIf { it.startsWith("https://") } ?: return null,
            token = poll.getString("token").takeIf { it.isNotBlank() } ?: return null,
        )
    }.getOrNull()

    /** The account Nextcloud returns once the user approved the login. */
    fun parseLoginResult(body: String): NextcloudAccount? = runCatching {
        val json = JSONObject(body)
        NextcloudAccount(
            server = normalizeServer(json.getString("server")) ?: return null,
            user = json.getString("loginName").takeIf { it.isNotBlank() } ?: return null,
            appPassword = json.getString("appPassword").takeIf { it.isNotBlank() } ?: return null,
        )
    }.getOrNull()

    fun isAutoBackupDue(config: NextcloudConfig, now: Long): Boolean =
        config.account != null && config.autoBackup && now - (config.lastBackupAt ?: 0L) >= AUTO_BACKUP_INTERVAL_MS

    fun encode(config: NextcloudConfig): String = JSONObject().apply {
        config.account?.let { put("server", it.server).put("user", it.user).put("appPassword", it.appPassword) }
        put("autoBackup", config.autoBackup)
        putOpt("loginsPassword", config.loginsPassword)
        putOpt("lastBackupAt", config.lastBackupAt)
    }.toString()

    fun decode(text: String): NextcloudConfig {
        val json = JSONObject(text)
        val account = if (json.has("server")) {
            NextcloudAccount(json.getString("server"), json.getString("user"), json.getString("appPassword"))
        } else {
            null
        }
        return NextcloudConfig(
            account = account,
            autoBackup = json.optBoolean("autoBackup"),
            loginsPassword = json.optString("loginsPassword").takeIf { json.has("loginsPassword") && it.isNotEmpty() },
            lastBackupAt = if (json.has("lastBackupAt")) json.getLong("lastBackupAt") else null,
        )
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
