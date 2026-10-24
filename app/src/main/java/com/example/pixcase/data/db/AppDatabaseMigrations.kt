package com.example.pixcase.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移(计划 § 2.3):每个版本变更一个 Migration_x_y,严禁 fallbackToDestructiveMigration。
 */
internal object AppDatabaseMigrations {

    /**
     * 1 → 2(陷阱 5 解法):`quick_sort_session.targetAlbumIds`(逗号分隔字符串)拆为
     * `quick_sort_session_target` 独立表(position 承担 Dock 顺序),旧列随表重建移除。
     * 字符串拆行无原生 SQL 支持,在 Kotlin 侧逐行解析后回写。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_sort_session_target` " +
                    "(`sessionAlbumId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "`targetAlbumId` INTEGER NOT NULL, PRIMARY KEY(`sessionAlbumId`, `position`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quick_sort_session_target_targetAlbumId` " +
                    "ON `quick_sort_session_target` (`targetAlbumId`)"
            )
            db.query(
                "SELECT albumId, targetAlbumIds FROM quick_sort_session WHERE targetAlbumIds != ''"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val sessionAlbumId = cursor.getLong(0)
                    val targets = cursor.getString(1)
                        .split(',')
                        .mapNotNull { it.trim().toLongOrNull() }
                    targets.forEachIndexed { position, targetAlbumId ->
                        db.execSQL(
                            "INSERT INTO quick_sort_session_target (sessionAlbumId, position, targetAlbumId) " +
                                "VALUES ($sessionAlbumId, $position, $targetAlbumId)"
                        )
                    }
                }
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_sort_session_new` (`albumId` INTEGER NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `lastActiveAt` INTEGER NOT NULL, " +
                    "`totalCount` INTEGER NOT NULL, `processedCount` INTEGER NOT NULL, " +
                    "`currentIndex` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, PRIMARY KEY(`albumId`))"
            )
            db.execSQL(
                "INSERT INTO `quick_sort_session_new` " +
                    "(`albumId`, `startedAt`, `lastActiveAt`, `totalCount`, " +
                    "`processedCount`, `currentIndex`, `isCompleted`) " +
                    "SELECT `albumId`, `startedAt`, `lastActiveAt`, `totalCount`, `processedCount`, " +
                    "`currentIndex`, `isCompleted` FROM `quick_sort_session`"
            )
            db.execSQL("DROP TABLE `quick_sort_session`")
            db.execSQL("ALTER TABLE `quick_sort_session_new` RENAME TO `quick_sort_session`")
        }
    }
}
