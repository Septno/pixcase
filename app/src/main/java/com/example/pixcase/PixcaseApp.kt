package com.example.pixcase

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。
 *
 * @HiltAndroidApp 触发 Hilt 编译期生成 DI 容器;
 * Configuration.Provider 让 WorkManager 走 Hilt 的 WorkerFactory,
 * 从而能用 @AssistedInject 注入依赖到 Worker 里。
 */
@HiltAndroidApp
class PixcaseApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}