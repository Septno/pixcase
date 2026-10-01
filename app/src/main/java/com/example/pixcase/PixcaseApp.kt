package com.example.pixcase

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStoreThumbnailFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okio.Path.Companion.toOkioPath

/**
 * 应用入口。
 *
 * @HiltAndroidApp 触发 Hilt 编译期生成 DI 容器;
 * Configuration.Provider 让 WorkManager 走 Hilt 的 WorkerFactory,
 * 从而能用 @AssistedInject 注入依赖到 Worker 里;
 * SingletonImageLoader.Factory 让 Coil 用这里配置的 ImageLoader 作为全局单例 ——
 * 照片墙的 AsyncImage 依赖它才能命中缩略图 Fetcher。
 *
 * onCreate 注册 MediaStore ContentObserver,任何图片增删改都会触发活跃 PagingSource 重新加载;
 * observer 与 Application 同生命周期,无需 unregister(进程死亡时系统自动清理)。
 */
@HiltAndroidApp
class PixcaseApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var mediaStoreDataSource: MediaStoreDataSource

    /** [newImageLoader] 建好的 Coil 单例。Coil 未提供「已建则取、未建则返回 null」的访问方式。 */
    private var imageLoaderInstance: ImageLoader? = null

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

    /**
     * 全局 ImageLoader:注册 MediaStore 缩略图 Fetcher,让照片墙走系统缩略图而不是解码原图。
     *
     * 磁盘缓存对当前缩略图路径不生效(Fetcher 直接返回已解码的 Bitmap,不产生可缓存的数据源),
     * 配在这里是为后续查看器加载原图准备。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(MediaStoreThumbnailFetcherFactory()) }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve(DISK_CACHE_DIR).toOkioPath())
                .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()
        .also { imageLoaderInstance = it }

    /**
     * 系统报告内存压力时调整图片内存缓存。
     *
     * 只处理 [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] 与 [ComponentCallbacks2.TRIM_MEMORY_BACKGROUND]:
     * RUNNING_LOW / RUNNING_CRITICAL / COMPLETE 自 API 34 起不再投递给应用,响应它们等于写死代码。
     * 用逐值匹配而非区间比较,因为 UI_HIDDEN(20) 夹在 RUNNING_CRITICAL(15) 与 BACKGROUND(40) 之间,
     * 区间判断会把「切后台」和「系统要内存」两件事混为一谈。
     *
     * 清缓存在本应用代价很低:缩略图由系统缓存供给,重建不需要解码原图。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 读字段而不是 SingletonImageLoader.get(this):后者在 Coil 还没初始化时会当场构建
        // ImageLoader,等于为了修剪一个空缓存付构建成本。
        val cache = imageLoaderInstance?.memoryCache ?: return
        when (level) {
            // 切后台:只砍一半,回前台滚动仍能命中一部分缩略图。
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                cache.trimToSize(cache.maxSize / TRIM_KEEP_DIVISOR)
            // 系统明确需要内存:全部释放。
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> cache.clear()
            // 其余级别(RUNNING_* / MODERATE / COMPLETE)自 API 34 起不再投递给应用。
            // 显式忽略而不是兜底 clear,避免系统只是轻量提示就把缓存清空。
            else -> Unit
        }
    }

    private companion object {
        const val DISK_CACHE_DIR = "image_cache"
        const val DISK_CACHE_MAX_BYTES = 100L * 1024 * 1024
        const val TRIM_KEEP_DIVISOR = 2
    }
}
