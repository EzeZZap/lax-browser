package com.javzzap.laxbrowser.ui.gesture

import com.javzzap.laxbrowser.domain.Backup
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.BrowserSettings
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressSwipeTest {
    @Test
    fun `clear swipes pick their direction`() {
        assertEquals(AddressSwipeDirection.LEFT, AddressSwipe.direction(-120f, 10f, threshold = 100f))
        assertEquals(AddressSwipeDirection.RIGHT, AddressSwipe.direction(130f, -20f, threshold = 100f))
        assertEquals(AddressSwipeDirection.UP, AddressSwipe.direction(5f, -110f, threshold = 100f))
        assertEquals(AddressSwipeDirection.DOWN, AddressSwipe.direction(-15f, 140f, threshold = 100f))
    }

    @Test
    fun `short and diagonal drags do nothing`() {
        assertNull(AddressSwipe.direction(-60f, 0f, threshold = 100f))
        assertNull(AddressSwipe.direction(120f, 100f, threshold = 100f))
    }

    @Test
    fun `swipe defaults and choices survive a backup`() {
        val defaults = BrowserSettings()
        assertEquals(SwipeAction.PREVIOUS_TAB, defaults.swipeLeft)
        assertEquals(SwipeAction.NEXT_TAB, defaults.swipeRight)
        assertEquals(SwipeAction.NEXT_SPACE, defaults.swipeUp)
        assertEquals(SwipeAction.PREVIOUS_SPACE, defaults.swipeDown)

        val chosen = defaults.copy(swipeLeft = SwipeAction.OPEN_MENU, swipeUp = SwipeAction.SPLIT_VIEW, swipeDown = SwipeAction.NONE)
        val data = BrowserData(spaces = listOf(Space("s", "Home", "c", 0, 0, null)), settings = chosen)
        val restored = Backup.decode(Backup.encode(data, "1.0", 0)).settings
        assertEquals(SwipeAction.OPEN_MENU, restored.swipeLeft)
        assertEquals(SwipeAction.NEXT_TAB, restored.swipeRight)
        assertEquals(SwipeAction.SPLIT_VIEW, restored.swipeUp)
        assertEquals(SwipeAction.NONE, restored.swipeDown)
    }
}
