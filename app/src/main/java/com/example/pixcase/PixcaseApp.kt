package com.example.pixcase

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。
 *
 * @HiltAndroidApp 触发 Hilt 编译期生成 DI 容器;
 * Configuration.Provider 让 WorkManager 走 Hilt 的 WorkerFactory,
 * 从而能用 @AssistedInject 注入依赖到 Worker 里。
 *
 * onCreate 注册 MediaStore ContentObserver,任何图片增删改都会触发活跃 PagingSource 重新加载;
 * observer 与 Application 同生命周期,无需 unregister(进程死亡时系统自动清理)。
 */
@HiltAndroidApp
class PixcaseApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var mediaStoreDataSource: MediaStoreDataSource

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.INFO)
                .build()

    override fun onCreate() {
        super.onCreate()
        mediaStoreDataSource.observeImages()
    }
}
