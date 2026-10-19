package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudTest {
    @Test
    fun `server addresses are cleaned up and must use https`() {
        assertEquals("https://cloud.example.com", Nextcloud.normalizeServer("cloud.example.com"))
        assertEquals("https://example.com/nextcloud", Nextcloud.normalizeServer(" https://example.com/nextcloud/ "))
        assertEquals("https://cloud.example.com", Nextcloud.normalizeServer("https://cloud.example.com/index.php/apps/files/?dir=/"))
        assertEquals("https://cloud.example.com", Nextcloud.normalizeServer("https://cloud.example.com/login?redirect_url=x"))
        assertEquals("https://login.example.com", Nextcloud.normalizeServer("https://login.example.com/apps/dashboard/"))
        assertNull(Nextcloud.normalizeServer("http://cloud.example.com"))
        assertNull(Nextcloud.normalizeServer("not a server"))
        assertNull(Nextcloud.normalizeServer(""))
    }

    @Test
    fun `the backup lives in the user's files`() {
        val account = NextcloudAccount("https://cloud.example.com", "ana maría", "secret")
        assertEquals("https://cloud.example.com/remote.php/dav/files/ana%20mar%C3%ADa/Lax-backup.json", Nextcloud.backupUrl(account))
        assertEquals("https://cloud.example.com/index.php/login/v2", Nextcloud.loginFlowUrl("https://cloud.example.com"))
    }

    @Test
    fun `login flow replies are read and checked`() {
        val flow = Nextcloud.parseLoginFlow(
            """{"poll":{"token":"abc","endpoint":"https://cloud.example.com/login/v2/poll"},"login":"https://cloud.example.com/login/v2/flow/xyz"}""",
        )
        assertEquals(NextcloudLoginFlow("https://cloud.example.com/login/v2/flow/xyz", "https://cloud.example.com/login/v2/poll", "abc"), flow)
        assertNull(Nextcloud.parseLoginFlow("""{"poll":{"token":"abc","endpoint":"http://evil.example/poll"},"login":"https://x"}"""))
        assertNull(Nextcloud.parseLoginFlow("<html>"))

        val account = Nextcloud.parseLoginResult("""{"server":"https://cloud.example.com","loginName":"ana","appPassword":"app-pass"}""")
        assertEquals(NextcloudAccount("https://cloud.example.com", "ana", "app-pass"), account)
        assertNull(Nextcloud.parseLoginResult("""{"server":"http://cloud.example.com","loginName":"ana","appPassword":"x"}"""))
    }

    @Test
    fun `settings round-trip and automatic backups run once a day`() {
        val config = NextcloudConfig(NextcloudAccount("https://c.example", "ana", "p"), autoBackup = true, loginsPassword = "backup-pass", lastBackupAt = 1_000)
        assertEquals(config, Nextcloud.decode(Nextcloud.encode(config)))
        assertEquals(NextcloudConfig(), Nextcloud.decode(Nextcloud.encode(NextcloudConfig())))

        val day = Nextcloud.AUTO_BACKUP_INTERVAL_MS
        assertFalse(Nextcloud.isAutoBackupDue(config, now = 1_000 + day - 1))
        assertTrue(Nextcloud.isAutoBackupDue(config, now = 1_000 + day))
        assertFalse(Nextcloud.isAutoBackupDue(config.copy(autoBackup = false), now = 10 * day))
        assertFalse(Nextcloud.isAutoBackupDue(config.copy(account = null), now = 10 * day))
    }
}
