package com.example.pixcase.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pixcase.data.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 孤儿清扫的定时壳:执行体在 [PhotoRepository.sweepOrphans](幂等),本类只负责 WorkManager
 * 的调度语义 —— 每天一次兜底,失败退避重试。库外删除(系统相册 / 文件管理器 / 连电脑)后
 * Room 覆盖层会留下指向不存在照片的孤儿行,删除路径内的即时清理覆盖不到它们,这里兜底。
 */
@HiltWorker
class MediaStoreSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val photoRepository: PhotoRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        photoRepository.sweepOrphans()
        Result.success()
    }.getOrElse {
        // 多为库外因素(权限被回收 / provider 忙):退避重试,超过系统策略上限由其放弃本轮
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "media-store-sync"

        /** 注册每日兜底清扫;KEEP:重复调用(每次进程启动)不重置已有周期。 */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MediaStoreSyncWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
