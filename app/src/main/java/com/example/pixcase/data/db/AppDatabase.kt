package com.example.pixcase.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.pixcase.data.db.dao.AlbumDao
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.ImageCategoryCrossDao
import com.example.pixcase.data.db.dao.ImageCategoryDao
import com.example.pixcase.data.db.dao.ImageMarkDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.QuickSortDecisionDao
import com.example.pixcase.data.db.dao.QuickSortSessionDao
import com.example.pixcase.data.db.dao.SearchHistoryDao
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.AlbumPhotoCrossRef
import com.example.pixcase.data.db.entity.FavoriteEntity
import com.example.pixcase.data.db.entity.HiddenEntity
import com.example.pixcase.data.db.entity.ImageCategoryCrossRef
import com.example.pixcase.data.db.entity.ImageCategoryEntity
import com.example.pixcase.data.db.entity.ImageMarkEntity
import com.example.pixcase.data.db.entity.PhotoTagCrossRef
import com.example.pixcase.data.db.entity.QuickSortDecisionEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import com.example.pixcase.data.db.entity.TagEntity

/**
 * Room 数据库,聚合本 App 全部自有元数据。
 *
 * 阶段 0:所有表占位建立,迁移策略 (`Migration_x_y`) 在每次 schema 变更时按计划 § 2.3 补充。
 * 严禁 `fallbackToDestructiveMigration()` ——会清空用户元数据。
 */
@Suppress("TooManyFunctions") // 12 个 dao 访问器是必要的(对应 12 张 Entity 表),后续按 Repository 维度拆分 Module 时再考虑下沉
@Database(
    entities = [
        AlbumEntity::class,
        AlbumPhotoCrossRef::class,
        TagEntity::class,
        PhotoTagCrossRef::class,
        FavoriteEntity::class,
        HiddenEntity::class,
        SearchHistoryEntity::class,
        QuickSortSessionEntity::class,
        QuickSortDecisionEntity::class,
        ImageCategoryEntity::class,
        ImageCategoryCrossRef::class,
        ImageMarkEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    abstract fun albumPhotoDao(): AlbumPhotoDao

    abstract fun tagDao(): TagDao

    abstract fun photoTagDao(): PhotoTagDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun hiddenDao(): HiddenDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun quickSortSessionDao(): QuickSortSessionDao

    abstract fun quickSortDecisionDao(): QuickSortDecisionDao

    abstract fun imageCategoryDao(): ImageCategoryDao

    abstract fun imageCategoryCrossDao(): ImageCategoryCrossDao

    abstract fun imageMarkDao(): ImageMarkDao

    companion object {
        const val DATABASE_NAME = "pixcase.db"
    }
}
