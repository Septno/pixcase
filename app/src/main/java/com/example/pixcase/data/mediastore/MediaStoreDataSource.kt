package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.PagingSource
import com.example.pixcase.core.util.IoDispatcher
import com.example.pixcase.data.model.MediaPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * MediaStore 数据源。对外暴露四类入口:
 *
 * - imagesPagingSource():返回新的 MediaStorePagingSource 实例,Paging 3 会基于此翻页;
 *   同时把源注册到 [activeSources] 集合,失效时自动从集合移除;
 * - observeImages():注册 ContentObserver 到 MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
 *   onChange 在主线程回调,触发 [invalidateAll] 让所有活跃的 PagingSource 重新加载;
 * - invalidateAll():对外暴露的强制失效入口(测试 / WorkManager 主动刷新场景);
 * - photoDetails():按 mediaId 补查单张的尺寸列,供查看器使用。
 *
 * 持有 ApplicationContext 注入的 ContentResolver 单例。翻页的 Cursor 操作由
 * MediaStorePagingSource.load() 在 Paging 调度线程上完成;[photoDetails] 则在本类内
 * 直接查询,由调用方切到 IO 派发器。
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /** 活跃的 MediaStorePagingSource 集合;失效时通过回调移除,ContentObserver 触发 invalidateAll 遍历。 */
    private val activeSources = CopyOnWriteArraySet<MediaStorePagingSource>()

    fun imagesPagingSource(): PagingSource<Int, MediaPhoto> {
        val source = MediaStorePagingSource(
            contentResolver = contentResolver,
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection = MediaStoreProjection.IMAGE_COLUMNS
        )
        activeSources.add(source)
        // 源被 Pager 触发失效时自动从集合移除,避免泄漏。
        source.registerInvalidatedCallback { activeSources.remove(source) }
        return source
    }

    /** 让所有活跃的 PagingSource 重新加载(由 ContentObserver 触发)。 */
    fun invalidateAll() {
        activeSources.forEach { it.invalidate() }
    }

    /**
     * 按 mediaId 补查查看器才需要的列(WIDTH / HEIGHT / SIZE)。
     *
     * 查单行,不传 sortOrder —— 既没有排序需求,也顺带避开 API 30 起 MediaProvider
     * 对 sortOrder 的校验。查不到返回 null(照片可能已被删除)。
     */
    suspend fun photoDetails(mediaId: Long): MediaPhoto? = withContext(ioDispatcher) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val cursor = contentResolver.query(
            uri,
            MediaStoreProjection.PHOTO_DETAIL_COLUMNS,
            /* selection = */
            null,
            /* selectionArgs = */
            null,
            /* sortOrder = */
            null
        ) ?: return@withContext null

        cursor.use { c -> if (c.moveToFirst()) c.toMediaPhotoWithDetailsOrNull(uri) else null }
    }

    /**
     * 注册 ContentObserver 到 MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
     * 返回 observer 实例,调用方持有以便在不需要时通过 [unregisterObserver] 解注册。
     * 一般在 Application.onCreate() 调用一次,进程生命周期内不必 unregister。
     */
    fun observeImages(): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        return object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                invalidateAll()
            }
        }.also { observer ->
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                /* notifyForDescendants = */
                true,
                observer
            )
        }
    }

    /** 解注册 observer。 */
    fun unregisterObserver(observer: ContentObserver) {
        contentResolver.unregisterContentObserver(observer)
    }

    /** 测试用:暴露 ContentResolver 给 PagingSource 测试用例。 */
    internal val resolver: ContentResolver get() = contentResolver

    /** 测试用:固定的 images Uri。 */
    internal val imagesUri: Uri get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
}
