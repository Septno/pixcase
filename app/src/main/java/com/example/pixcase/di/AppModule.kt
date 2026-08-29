package com.example.pixcase.di

import android.content.Context
import androidx.room.Room
import com.example.pixcase.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用层 Hilt Module,提供 Room 数据库单例。
 *
 * DAO 由 Room 在 @Database 子类里通过 `database.xDao()` 提供,
 * 这里只暴露 Database,具体 DAO 在后续阶段按 Repository 维度拆分 Module。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // TODO: 阶段 0 暂不写 Migration;阶段 1 起每个 schema 变更必须配 Migration_x_y
            .build()
}
