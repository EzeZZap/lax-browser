package com.javzzap.laxbrowser.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: bookmark folders and user preferences (search engine, toolbar, site settings). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookmark_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`parent_id` TEXT, `position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmark_folders_parent_id` ON `bookmark_folders` (`parent_id`)")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `folder_id` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_folder_id` ON `bookmarks` (`folder_id`)")
        listOf(
            "`search_engine_id` TEXT NOT NULL DEFAULT 'duckduckgo'",
            "`custom_search_url` TEXT",
            "`toolbar_position` TEXT NOT NULL DEFAULT 'TOP'",
            "`hide_toolbar_on_scroll` INTEGER NOT NULL DEFAULT 1",
            "`pull_to_refresh` INTEGER NOT NULL DEFAULT 1",
            "`website_appearance` TEXT NOT NULL DEFAULT 'SYSTEM'",
            "`text_scale_percent` INTEGER NOT NULL DEFAULT 100",
            "`https_only` INTEGER NOT NULL DEFAULT 0",
            "`tracking_protection` TEXT NOT NULL DEFAULT 'STANDARD'",
        ).forEach { column -> db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN $column") }
    }
}

/** Version 3: browsing history. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `favicon_url` TEXT, `visited_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visited_at` ON `history` (`visited_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
    }
}

/** Version 4: private browsing settings. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `private_block_screenshots` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `private_hide_in_recents` INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * Version 5: pinned-tab reset URLs, tab folders, Space emoji, downloads, extension preferences,
 * and search-suggestion, Global Privacy Control, and autoplay settings.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tabs` ADD COLUMN `pinned_url` TEXT")
        db.execSQL("ALTER TABLE `tabs` ADD COLUMN `folder_id` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_folder_id` ON `tabs` (`folder_id`)")
        // Existing pinned tabs and Essentials reset to the page they show now.
        db.execSQL("UPDATE `tabs` SET `pinned_url` = `url` WHERE (`is_pinned` = 1 OR `is_essential` = 1) AND `kind` = 'WEB'")
        db.execSQL("ALTER TABLE `spaces` ADD COLUMN `emoji` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tab_folders` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `collapsed` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tab_folders_space_id` ON `tab_folders` (`space_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `file_name` TEXT NOT NULL, " +
                "`mime_type` TEXT, `total_bytes` INTEGER, `downloaded_bytes` INTEGER NOT NULL DEFAULT 0, `status` TEXT NOT NULL, " +
                "`content_uri` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_created_at` ON `downloads` (`created_at`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `extension_prefs` (`id` TEXT NOT NULL, `pinned` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `search_suggestions` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `global_privacy_control` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `autoplay` TEXT NOT NULL DEFAULT 'BLOCK_AUDIBLE'")
    }
}

/** Version 6: automatic tab unloading, first-run onboarding, update checks, and per-site settings. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `auto_unload_minutes` INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `onboarding_done` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `check_for_updates` INTEGER NOT NULL DEFAULT 1")
        // People upgrading already know the browser, so they skip the first-run introduction.
        db.execSQL("UPDATE `browser_settings` SET `onboarding_done` = 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `site_settings` (`host` TEXT NOT NULL, `zoom_percent` INTEGER NOT NULL DEFAULT 100, " +
                "`space_id` TEXT, PRIMARY KEY(`host`))",
        )
    }
}

/** Version 7: the customizable toolbar button and the sidebar/menu button layout. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `toolbar_action` TEXT NOT NULL DEFAULT 'RELOAD'")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `swap_toolbar_buttons` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `sidebar_on_right` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 8: Lax's own palette (wallpaper colors become optional) and the Space color accent. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `wallpaper_colors` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `space_accent` INTEGER NOT NULL DEFAULT 1")
    }
}

/** Version 9: where links from other apps open (a Space, private browsing, or ask). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `external_links` TEXT NOT NULL DEFAULT 'active'")
    }
}

/** Version 10: saving and filling logins (the logins themselves live in an encrypted file). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `save_logins` INTEGER NOT NULL DEFAULT 1")
    }
}

/** Version 11: locking private tabs behind the screen lock. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `private_tabs_lock` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 12: what swipes on the toolbar's address do. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `swipe_left` TEXT NOT NULL DEFAULT 'PREVIOUS_TAB'")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `swipe_right` TEXT NOT NULL DEFAULT 'NEXT_TAB'")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `swipe_up` TEXT NOT NULL DEFAULT 'NEXT_SPACE'")
        db.execSQL("ALTER TABLE `browser_settings` ADD COLUMN `swipe_down` TEXT NOT NULL DEFAULT 'PREVIOUS_SPACE'")
    }
}
