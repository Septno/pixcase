package com.example.pixcase.di

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import com.example.pixcase.core.util.ApplicationScope
import com.example.pixcase.core.util.DefaultDispatcher
import com.example.pixcase.core.util.IoDispatcher
import com.example.pixcase.data.db.AppDatabase
import com.example.pixcase.data.db.AppDatabaseMigrations
import com.example.pixcase.data.db.dao.AlbumDao
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.QuickSortDecisionDao
import com.example.pixcase.data.db.dao.QuickSortSessionDao
import com.example.pixcase.data.db.dao.QuickSortSessionTargetDao
import com.example.pixcase.data.db.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用层 Hilt Module,聚合 Room 数据库、协程派发器、当前阶段用到的 DAO、ApplicationScope 单例。
 *
 * 计划 §3.3 规定单 Module + 实体类 @Inject constructor;DAO 不自动绑定到图,
 * 按使用方需要在此处显式 provide。当前阶段只 expose FavoriteDao / HiddenDao,
 * 后续按 Repository 依赖增补。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // 迁移按版本逐个挂载(计划 §2.3);严禁 fallbackToDestructiveMigration
            .addMigrations(AppDatabaseMigrations.MIGRATION_1_2)
            .build()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideHiddenDao(db: AppDatabase): HiddenDao = db.hiddenDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideAlbumPhotoDao(db: AppDatabase): AlbumPhotoDao = db.albumPhotoDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun providePhotoTagDao(db: AppDatabase): PhotoTagDao = db.photoTagDao()

    @Provides
    fun provideQuickSortSessionDao(db: AppDatabase): QuickSortSessionDao = db.quickSortSessionDao()

    @Provides
    fun provideQuickSortSessionTargetDao(db: AppDatabase): QuickSortSessionTargetDao = db.quickSortSessionTargetDao()

    @Provides
    fun provideQuickSortDecisionDao(db: AppDatabase): QuickSortDecisionDao = db.quickSortDecisionDao()

    @Provides
    @ImagesContentUri
    fun provideImagesContentUri(): Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
}
