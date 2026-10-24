package com.example.pixcase.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration_1_2 的回归用例(计划 § 五任务清单的硬前置基建首次落地):
 * 旧 `quick_sort_session.targetAlbumIds` 逗号分隔字符串必须逐行拆到
 * `quick_sort_session_target` 并保持顺序;其余列原样保留;空串不产生目标行;
 * schema 与 KSP 生成的 2.json 完全一致(多列/少列/索引缺失都会让 runMigrationsAndValidate 失败)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /** v1 的 quick_sort_session INSERT;占位符依序 = 8 个列值。 */
    private val legacyInsert =
        "INSERT INTO quick_sort_session (albumId, startedAt, lastActiveAt, targetAlbumIds, " +
            "totalCount, processedCount, currentIndex, isCompleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    private fun insertLegacySession(db: androidx.sqlite.db.SupportSQLiteDatabase, vararg values: Any) {
        db.execSQL(
            legacyInsert,
            arrayOf(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                values[6],
                values[7]
            )
        )
    }

    /**
     * createDatabase 不删除已存在的库文件,同文件名的库会在用例间串版本(上一个用例已迁到 2,
     * 下一个 createDatabase(…, 1) 触发 2→1 降级)——每个用例用独立库名,保证互不干扰。
     */
    private fun runMigrations(dbName: String) = helper.runMigrationsAndValidate(
        dbName,
        2,
        /* validateDroppedTables = */
        true,
        AppDatabaseMigrations.MIGRATION_1_2
    )

    @Test
    fun `migrate 1 to 2 splits target ids into rows preserving order`() {
        val dbName = "migration-order-test.db"
        helper.createDatabase(dbName, 1).use { db ->
            // 两条 legacy session:一条带 3 个目标(顺序 3,1,2),一条空串
            insertLegacySession(db, 7L, 100L, 200L, "3,1,2", 30, 5, 5, 0)
            insertLegacySession(db, 8L, 100L, 200L, "", 10, 0, 0, 0)
        }

        runMigrations(dbName).use { migrated ->
            migrated.query(
                "SELECT sessionAlbumId, position, targetAlbumId FROM quick_sort_session_target " +
                    "ORDER BY sessionAlbumId, position"
            ).use { cursor ->
                val rows = buildList {
                    while (cursor.moveToNext()) {
                        add(Triple(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2)))
                    }
                }
                // 只有 session 7 有目标,顺序 3 → 1 → 2 落 position 0 → 1 → 2
                assertEquals(
                    listOf(Triple(7L, 0, 3L), Triple(7L, 1, 1L), Triple(7L, 2, 2L)),
                    rows
                )
            }
            migrated.query(
                "SELECT totalCount, processedCount, currentIndex, isCompleted " +
                    "FROM quick_sort_session WHERE albumId = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(30, cursor.getInt(0))
                assertEquals(5, cursor.getInt(1))
                assertEquals(5, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
        }
    }

    @Test
    fun `migrate 1 to 2 tolerates messy id strings`() {
        val dbName = "migration-messy-test.db"
        helper.createDatabase(dbName, 1).use { db ->
            // 末尾逗号 + 空段 + 空白 + 非数字段:全部安全跳过,只留合法 id
            insertLegacySession(db, 9L, 0L, 0L, " 4 , ,x,5,", 0, 0, 0, 0)
        }

        runMigrations(dbName).use { migrated ->
            migrated.query(
                "SELECT position, targetAlbumId FROM quick_sort_session_target ORDER BY position"
            ).use { cursor ->
                val ids = buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(1))
                }
                assertEquals(listOf(4L, 5L), ids)
            }
        }
    }
}
