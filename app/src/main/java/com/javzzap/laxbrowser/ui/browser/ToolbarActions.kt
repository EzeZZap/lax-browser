package com.javzzap.laxbrowser.ui.browser

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.ToolbarAction

/** The icon shown for [ToolbarAction] in settings (the toolbar may show a state-specific one). */
val ToolbarAction.icon: ImageVector?
    get() = when (this) {
        ToolbarAction.RELOAD -> Icons.Default.Refresh
        ToolbarAction.BACK -> Icons.AutoMirrored.Filled.ArrowBack
        ToolbarAction.FORWARD -> Icons.AutoMirrored.Filled.ArrowForward
        ToolbarAction.HOME -> Icons.Default.Home
        ToolbarAction.BOOKMARK -> Icons.Default.StarBorder
        ToolbarAction.SHARE -> Icons.Default.Share
        ToolbarAction.COPY_LINK -> Icons.Default.ContentCopy
        ToolbarAction.FIND -> Icons.Default.FindInPage
        ToolbarAction.READER_VIEW -> Icons.AutoMirrored.Filled.MenuBook
        ToolbarAction.TRANSLATE -> Icons.Default.Translate
        ToolbarAction.DESKTOP_SITE -> Icons.Default.DesktopWindows
        ToolbarAction.SPLIT_VIEW -> Icons.Default.VerticalSplit
        ToolbarAction.PRIVATE_BROWSING -> Icons.Default.VisibilityOff
        ToolbarAction.BOOKMARKS -> Icons.Default.Bookmarks
        ToolbarAction.HISTORY -> Icons.Default.History
        ToolbarAction.DOWNLOADS -> Icons.Default.Download
        ToolbarAction.NONE -> null
    }

@get:StringRes
val ToolbarAction.label: Int
    get() = when (this) {
        ToolbarAction.RELOAD -> R.string.reload
        ToolbarAction.BACK -> R.string.menu_back
        ToolbarAction.FORWARD -> R.string.menu_forward
        ToolbarAction.HOME -> R.string.toolbar_action_home
        ToolbarAction.BOOKMARK -> R.string.toolbar_action_bookmark
        ToolbarAction.SHARE -> R.string.menu_share
        ToolbarAction.COPY_LINK -> R.string.menu_copy_link
        ToolbarAction.FIND -> R.string.menu_find_in_page
        ToolbarAction.READER_VIEW -> R.string.menu_reader_view
        ToolbarAction.TRANSLATE -> R.string.menu_translate
        ToolbarAction.DESKTOP_SITE -> R.string.menu_desktop_site
        ToolbarAction.SPLIT_VIEW -> R.string.menu_split_view
        ToolbarAction.PRIVATE_BROWSING -> R.string.private_browsing
        ToolbarAction.BOOKMARKS -> R.string.menu_bookmarks
        ToolbarAction.HISTORY -> R.string.menu_history
        ToolbarAction.DOWNLOADS -> R.string.menu_downloads
        ToolbarAction.NONE -> R.string.toolbar_action_none
    }
