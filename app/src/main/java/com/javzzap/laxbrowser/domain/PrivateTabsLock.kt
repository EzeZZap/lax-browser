package com.javzzap.laxbrowser.domain

/** When private tabs are covered by the lock screen. */
object PrivateTabsLock {
    /** Leaving the app locks private tabs only when the setting is on and some are open. */
    fun locksOnLeave(settings: BrowserSettings, privateTabCount: Int): Boolean = settings.privateTabsLock && privateTabCount > 0
}
