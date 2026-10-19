package com.javzzap.laxbrowser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ContainerEntity::class,
        SpaceEntity::class,
        TabEntity::class,
        TabFolderEntity::class,
        TabSessionStateEntity::class,
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        ShortcutEntity::class,
        WebAppEntity::class,
        SettingsEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        ExtensionPrefEntity::class,
        SiteSettingEntity::class,
    ],
    version = BrowserDatabase.VERSION,
    exportSchema = true,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun extensionPrefDao(): ExtensionPrefDao

    companion object {
        const val VERSION = 11
        const val NAME = "browser.db"

        /** Every schema change must add a migration here and a test in `BrowserDatabaseTest`. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11,
        )

        fun create(context: Context, name: String? = NAME): BrowserDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, BrowserDatabase::class.java)
            } else {
                Room.databaseBuilder(context, BrowserDatabase::class.java, name)
            }
            return builder.addMigrations(*MIGRATIONS).build()
        }
    }
}
