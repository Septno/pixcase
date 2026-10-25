package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.PagingSource
import com.example.pixcase.core.util.IoDispatcher
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.di.ImagesContentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 文件名搜索的结果上限:不分页的有界集合。 */
private const val DISPLAY_SEARCH_LIMIT = 200

/**
 * MediaStore 数据源。对外暴露五类入口:
 *
 * - imagesPagingSource():返回新的 MediaStorePagingSource 实例,Paging 3 会基于此翻页;
 *   同时把源注册到 [activeSources] 集合,失效时自动从集合移除;selection 非空时做条件分页
 *   (智能相册);
 * - observeImages():注册 ContentObserver 到 MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
 *   onChange 在主线程回调,触发 [invalidateAll] 让所有活跃的 PagingSource 重新加载;
 * - invalidateAll():对外暴露的强制失效入口(测试 / WorkManager 主动刷新场景);
 * - observeSummary():条件筛选的概览流(条数 + 最近一张),自带 ContentObserver,
 *   供智能相册卡片展示计数与封面;
 * - photoDetails():按 mediaId 补查单张的尺寸列,供查看器使用。
 *
 * 持有 ApplicationContext 注入的 ContentResolver 单例。翻页的 Cursor 操作由
 * MediaStorePagingSource.load() 在 Paging 调度线程上完成;[photoDetails] 与
 * [observeSummary] 则在本类内直接查询,由调用方切到 IO 派发器。
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ImagesContentUri private val imagesContentUri: Uri
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /** 活跃的 MediaStorePagingSource 集合;失效时通过回调移除,ContentObserver 触发 invalidateAll 遍历。 */
    private val activeSources = CopyOnWriteArraySet<MediaStorePagingSource>()

    /** selection 非空时做条件分页(智能相册):时间线传空即全量。 */
    fun imagesPagingSource(
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): PagingSource<Int, MediaPhoto> {
        val source = MediaStorePagingSource(
            contentResolver = contentResolver,
            contentUri = imagesContentUri,
            projection = MediaStoreProjection.IMAGE_COLUMNS,
            selection = selection,
            selectionArgs = selectionArgs
        )
        activeSources.add(source)
        // 源被 Pager 触发失效时自动从集合移除,避免泄漏。
        source.registerInvalidatedCallback { activeSources.remove(source) }
        return source
    }

    /**
     * 条件筛选的概览流:命中条数 + 最近一张的 mediaId,ContentObserver 注册在 images Uri
     * (descendants),条目增删或筛选列变化都会重查。
     *
     * onChange 可能连发,conflate 把连续变化合并为一次查询;查询在 [ioDispatcher] 上执行
     * (flowOn 连同 callbackFlow 一起移到 IO,observer 回调线程由构造时的主线程 Handler 保证),
     * 结果经 distinctUntilChanged 去掉无意义重发。
     */
    fun observeSummary(
        selection: String,
        selectionArgs: Array<String>,
        useQueryArgs: Boolean = MediaStoreProjection.supportsQueryArgs(Build.VERSION.SDK_INT)
    ): Flow<MediaSummary> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            imagesContentUri,
            /* notifyForDescendants = */
            true,
            observer
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }
        .conflate()
        .map { querySummary(selection, selectionArgs, useQueryArgs) }
        .flowOn(ioDispatcher)
        .distinctUntilChanged()

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
        val uri = ContentUris.withAppendedId(imagesContentUri, mediaId)
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
     * 按 mediaId 批量查询照片(分享 / 壁纸 / 重命名需要选中项的 Uri 与文件名,
     * 而多选状态只存 id)。IN 查询,返回顺序按 _ID 升序;查不到的 id 自然缺席。
     */
    suspend fun photosByIds(mediaIds: Collection<Long>): List<MediaPhoto> = withContext(ioDispatcher) {
        if (mediaIds.isEmpty()) return@withContext emptyList()
        val placeholders = mediaIds.joinToString(",") { "?" }
        val cursor = contentResolver.query(
            imagesContentUri,
            MediaStoreProjection.IMAGE_COLUMNS,
            /* selection = */
            "${MediaStore.Images.Media._ID} IN ($placeholders)",
            /* selectionArgs = */
            mediaIds.map { it.toString() }.toTypedArray(),
            /* sortOrder = */
            null
        ) ?: return@withContext emptyList()

        cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    c.toMediaPhotoOrNull(imagesContentUri)?.let(::add)
                }
            }
        }
    }

    /**
     * 概览查询:条数走 COUNT 聚合投影,最近一张与时间线共用同一排序基准、只取第一行。
     * 取第一行的分页方式与 PagingSource 一致按版本分流(30+ 走 Bundle,LIMIT 拼 sortOrder
     * 会被 MediaProvider 校验拒绝)。
     */
    private fun querySummary(selection: String, selectionArgs: Array<String>, useQueryArgs: Boolean): MediaSummary =
        MediaSummary(
            count = queryCount(selection, selectionArgs),
            latestMediaId = queryLatestId(selection, selectionArgs, useQueryArgs)
        )

    private fun queryCount(selection: String, selectionArgs: Array<String>): Int {
        val cursor = contentResolver.query(
            imagesContentUri,
            arrayOf(MediaStoreProjection.COUNT_AGGREGATE),
            selection,
            selectionArgs,
            null
        ) ?: return 0
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun queryLatestId(selection: String, selectionArgs: Array<String>, useQueryArgs: Boolean): Long? {
        val cursor = if (useQueryArgs) {
            contentResolver.query(
                imagesContentUri,
                arrayOf(MediaStore.Images.Media._ID),
                MediaStoreProjection.modernQueryArgs(
                    loadSize = 1,
                    offset = 0,
                    selection = selection,
                    selectionArgs = selectionArgs
                ),
                /* cancellationSignal = */
                null
            )
        } else {
            contentResolver.query(
                imagesContentUri,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                selectionArgs,
                MediaStoreProjection.legacySortOrder(loadSize = 1, offset = 0)
            )
        } ?: return null
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /**
     * 全库图片 mediaId 集合(孤儿清扫用):只查 _ID 一列,行数与大库同量级,调用方须在
     * 派发器上执行([queryAllImageIds] 内部已切 IO)。无排序无分页 —— MediaProvider 的
     * sortOrder 校验只针对分页拼接,这里不涉及。
     */
    suspend fun queryAllImageIds(): Set<Long> = withContext(ioDispatcher) {
        val cursor = contentResolver.query(
            imagesContentUri,
            arrayOf(MediaStore.Images.Media._ID),
            /* selection = */
            null,
            /* selectionArgs = */
            null,
            /* sortOrder = */
            null
        ) ?: return@withContext emptySet()
        cursor.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    /**
     * MediaStore 端收藏的 mediaId 集合(收藏反向同步用)。IS_FAVORITE 列 API 30 起才有,
     * 更低版本没有调用方(PhotoRepository 按 API 分流);selection 单列条件不涉及
     * sortOrder 校验,各版本都可走普通 query。
     */
    suspend fun queryFavoriteIds(): Set<Long> = withContext(ioDispatcher) {
        val cursor = contentResolver.query(
            imagesContentUri,
            arrayOf(MediaStore.Images.Media._ID),
            MediaStoreProjection.FAVORITE_SELECTION,
            /* selectionArgs = */
            null,
            /* sortOrder = */
            null
        ) ?: return@withContext emptySet()
        cursor.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    /**
     * 按文件名片段搜索(阶段 4):DISPLAY_NAME LIKE 包含匹配,结果按时间线同款排序基准
     * 取前 [DISPLAY_SEARCH_LIMIT] 条,不分页(搜索结果天然有界)。片段里的 % / _ 通配符
     * 剥掉 —— 用户输入按字面量匹配,不承担 SQL 通配语义。
     */
    suspend fun queryByDisplayNameFragment(fragment: String): List<MediaPhoto> = withContext(ioDispatcher) {
        val literal = fragment.filterNot { it == '%' || it == '_' }
        if (literal.isEmpty()) return@withContext emptyList()
        val cursor = contentResolver.query(
            imagesContentUri,
            MediaStoreProjection.PHOTO_DETAIL_COLUMNS,
            /* selection = */
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            /* selectionArgs = */
            arrayOf("%$literal%"),
            /* sortOrder = */
            null
        ) ?: return@withContext emptyList()
        cursor.use { c ->
            buildList {
                while (c.moveToNext() && size < DISPLAY_SEARCH_LIMIT) {
                    c.toMediaPhotoWithDetailsOrNull(imagesContentUri)?.let(::add)
                }
            }
        }
    }

    /**
     * 注册 ContentObserver 到 MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
     * 返回 observer 实例,调用方持有以便在不需要时通过 [unregisterObserver] 解注册。
     * 一般在 Application.onCreate() 调用一次,进程生命周期内不必 unregister。
     * [onMediaChanged] 在每次变更时额外回调(Paging 失效之外的数据对齐钩子)。
     */
    fun observeImages(onMediaChanged: () -> Unit = {}): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        return object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                invalidateAll()
                onMediaChanged()
            }
        }.also { observer ->
            contentResolver.registerContentObserver(
                imagesContentUri,
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
}
