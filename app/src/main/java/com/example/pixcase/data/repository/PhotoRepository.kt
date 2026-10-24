package com.example.pixcase.data.repository

import android.content.IntentSender
import android.os.Build
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.entity.FavoriteEntity
import com.example.pixcase.data.db.entity.HiddenEntity
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.mediastore.MediaStoreProjection
import com.example.pixcase.data.mediastore.MediaSummary
import com.example.pixcase.data.model.MediaPhoto
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map as mapFlow

/**
 * 按 Room 覆盖层的 id 顺序重排补查结果;已从 MediaStore 消失的照片(补查缺席)跳过。
 * 独立成函数便于单测覆盖顺序与缺席语义。
 */
internal fun orderByIds(ids: List<Long>, photos: List<MediaPhoto>): List<MediaPhoto> {
    val byId = photos.associateBy { it.id }
    return ids.mapNotNull { byId[it] }
}

/**
 * 照片仓库:读 = MediaStore Paging(数据源) + Room 收藏/隐藏覆盖层;写 = Room 真源 + MediaStore 尽力同步。
 *
 * 覆盖层的流式叠加见 [images];写操作约定:Room 先写(UI 状态即时生效),
 * MediaStore(IS_FAVORITE / 删除)随后尽力同步;删除走 [MediaStorePhotoWriter],完成后清理
 * 对应照片的 Room 覆盖层,避免 MediaStore 真删后留下孤儿行(计划陷阱 3)。
 *
 * images() 返回冷流:每次收集都新建 Pager,缓存由消费方(ViewModel)用 cachedIn 管理。
 * 仓库层不做 cachedIn —— 用 ApplicationScope 缓存会把已加载的照片窗口钉在进程生命周期上。
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val writer: MediaStorePhotoWriter,
    private val favoriteDao: FavoriteDao,
    private val hiddenDao: HiddenDao,
    private val albumPhotoDao: AlbumPhotoDao,
    private val photoTagDao: PhotoTagDao
) {
    /** 收藏反向同步的在途标记:onChange 连发时合并重复对齐(见 [reconcileFavorites])。 */
    private val reconciling = AtomicBoolean(false)

    /**
     * 时间线数据流。收藏/隐藏元数据用 [flatMapLatest] 包住 Pager:任一变化时取消旧收集、
     * 重新收集冷 Pager 流,产出**全新的分页代**再叠加覆盖层并过滤隐藏。
     *
     * 不能用 combine 直接包住同一个 PagingData 实例:元数据变化会让 combine 重跑变换、
     * 对同一个底层分页代二次收集,cachedIn 里抛
     * `IllegalStateException: Attempt to collect twice from pageEventFlow`(真机收藏时验证过)。
     *
     * 过滤发生在分页加载之后:整页全被隐藏时会出现「看着没加载满」的窗口,换来的是不必在
     * MediaStore 查询里塞 Room 条件(两者无法 join);隐藏条目通常稀疏,代价可接受。
     */
    fun images(): Flow<PagingData<MediaPhoto>> = combine(
        favoriteDao.observeAllIds(),
        hiddenDao.observeAllIds()
    ) { favIds, hiddenIds -> favIds to hiddenIds }
        .distinctUntilChanged()
        .flatMapLatest { (favIds, hiddenIds) ->
            Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    prefetchDistance = PREFETCH_DISTANCE,
                    initialLoadSize = PAGE_SIZE,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { dataSource.imagesPagingSource() }
            ).flow.mapFlow { paging ->
                val favSet = favIds.toHashSet()
                paging
                    .map { p ->
                        p.copy(
                            isFavorite = p.id in favSet,
                            isHidden = p.id in hiddenIds
                        )
                    }
                    .filter { !it.isHidden }
            }
        }

    //region 智能相册(收藏 / 截图):MediaStore 条件筛选,概览与内容共用同一 selection

    /**
     * 收藏智能相册的条数与封面(最近一张)。
     * API 30+ 直查 MediaStore `IS_FAVORITE`(含系统相册打的收藏);更低版本没有该列,
     * 收藏只有本 App 的 Room 覆盖层一条数据源(封面即最近收藏的一条,孤儿行由清理流程收敛)。
     */
    fun observeFavoritesSummary(sdkInt: Int = Build.VERSION.SDK_INT): Flow<MediaSummary> =
        if (MediaStoreProjection.supportsQueryArgs(sdkInt)) {
            dataSource.observeSummary(MediaStoreProjection.FAVORITE_SELECTION, NO_SELECTION_ARGS, useQueryArgs = true)
        } else {
            favoriteDao.observeAllIds().mapFlow { ids -> MediaSummary(ids.size, ids.firstOrNull()) }
        }

    /**
     * 截图智能相册的条数与封面。RELATIVE_PATH 列 API 29 起才有,低版本没有调用方
     * (截图卡片按 Q 分流展示)。
     */
    fun observeScreenshotsSummary(): Flow<MediaSummary> =
        dataSource.observeSummary(MediaStoreProjection.SCREENSHOTS_SELECTION, NO_SELECTION_ARGS)

    /** 收藏智能相册内容;分流语义同 [observeFavoritesSummary]。 */
    fun smartFavorites(sdkInt: Int = Build.VERSION.SDK_INT): Flow<PagingData<MediaPhoto>> =
        if (MediaStoreProjection.supportsQueryArgs(sdkInt)) {
            pagedImages(MediaStoreProjection.FAVORITE_SELECTION)
        } else {
            roomFavoritesPaging()
        }

    /** 截图智能相册内容:RELATIVE_PATH 条件分页(仅 Q+ 有入口)。 */
    fun smartScreenshots(): Flow<PagingData<MediaPhoto>> = pagedImages(MediaStoreProjection.SCREENSHOTS_SELECTION)

    /** MediaStore 条件分页的统一壳:与时间线同一套 Paging 配置与失效机制。 */
    private fun pagedImages(selection: String): Flow<PagingData<MediaPhoto>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            initialLoadSize = PAGE_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { dataSource.imagesPagingSource(selection, NO_SELECTION_ARGS) }
    ).flow

    /**
     * API 29 及以下的收藏内容:Room 收藏覆盖层(按收藏时间倒序)→ MediaStore 按需补查,
     * 照片可能已被系统删除,补查缺席即跳过。一次性列表装进 PagingData —— 收藏规模由本 App
     * 自己的收藏行为决定,与相册详情同思路不分页。
     */
    private fun roomFavoritesPaging(): Flow<PagingData<MediaPhoto>> = favoriteDao.observeAllIds()
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            flow { emit(orderByIds(ids, photosByIds(ids))) }
                .mapFlow { ordered -> PagingData.from(ordered) }
        }

    //endregion

    /** 选中项里已收藏的媒体 ID 集合;空集合不触达 DAO(空 IN 子句 SQL 会报错)。 */
    suspend fun favoritesOf(mediaIds: Collection<Long>): Set<Long> =
        if (mediaIds.isEmpty()) emptySet() else favoriteDao.favoritesOf(mediaIds).toSet()

    /** [favoritesOf] 的流版本,供多选操作栏按选择集变化持续观察。 */
    fun observeFavoritesOf(mediaIds: Collection<Long>): Flow<Set<Long>> = when {
        mediaIds.isEmpty() -> flowOf(emptySet())
        else -> favoriteDao.observeFavoritesOf(mediaIds).mapFlow { it.toSet() }
    }

    /**
     * 收藏 / 取消收藏:先写 Room(UI 真源),再同步 MediaStore.IS_FAVORITE。
     * API 30+ 同步返回待启动的 IntentSender,由调用方交给 UI 静默启动。
     */
    suspend fun setFavorite(mediaIds: Collection<Long>, favorite: Boolean): IntentSender? {
        if (mediaIds.isEmpty()) return null
        val now = System.currentTimeMillis()
        if (favorite) {
            favoriteDao.insertAll(mediaIds.map { FavoriteEntity(it, now) })
        } else {
            favoriteDao.deleteAll(mediaIds)
        }
        return writer.syncFavorite(mediaIds, favorite)
    }

    /** 隐藏:仅本 App 内生效(Room 覆盖层),不动 MediaStore。 */
    suspend fun hidePhotos(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val now = System.currentTimeMillis()
        hiddenDao.insertAll(mediaIds.map { HiddenEntity(it, now) })
    }

    /**
     * 删除照片。API 30+ 返回 [MediaStorePhotoWriter.DeleteOutcome.NeedsConsent],UI 启动系统
     * 确认框、用户同意后须回调 [onSystemDeleteApproved];API 29 及以下返回 Completed,
     * 真正删掉的条目已同步清理 Room 覆盖层。
     */
    suspend fun deletePhotos(mediaIds: Collection<Long>): MediaStorePhotoWriter.DeleteOutcome =
        when (val outcome = writer.deleteImages(mediaIds)) {
            is MediaStorePhotoWriter.DeleteOutcome.NeedsConsent -> outcome
            is MediaStorePhotoWriter.DeleteOutcome.Completed -> {
                clearLocalMetadataFor(outcome.deletedIds)
                outcome
            }
        }

    /** 按 mediaId 批量取照片(分享 / 壁纸 / 重命名要 Uri 与文件名,选中状态只有 id)。 */
    suspend fun photosByIds(mediaIds: Collection<Long>): List<MediaPhoto> = dataSource.photosByIds(mediaIds)

    /**
     * 重命名单张照片。API 30+ 遇到非本 App 的照片会返回 NeedsConsent(先 createWriteRequest
     * 拿用户授权),调用方在授权成功后用相同参数重试本方法即可。
     */
    suspend fun renamePhoto(mediaId: Long, newDisplayName: String): MediaStorePhotoWriter.RenameOutcome =
        writer.renameImage(mediaId, newDisplayName)

    /** API 30+ 系统确认框同意后调用:清理这些照片的本地收藏/隐藏记录。 */
    suspend fun onSystemDeleteApproved(mediaIds: Collection<Long>) {
        clearLocalMetadataFor(mediaIds)
    }

    /**
     * 收藏反向同步(计划陷阱 2):把 Room 收藏覆盖层对齐到 MediaStore.IS_FAVORITE ——
     * 系统相册取消收藏后,本 App 不再显示收藏。
     *
     * 竞态防护:App 内刚写入的收藏(addedAt 在宽限期内)不参与对齐 —— 30+ 的收藏同步走
     * createFavoriteRequest 静默授权,MediaStore 端生效稍有延迟,立刻对齐会把刚打的收藏
     * 误判为「已被系统取消」。错过的变更由下一次触发(ContentObserver / 进程启动)兜住。
     * 并发触发用原子标记合并:onChange 常连发,排队的重复对齐没有意义。
     *
     * API 29 及以下没有 IS_FAVORITE 列,收藏只有 Room 一条数据源,无需对齐。
     */
    suspend fun reconcileFavorites(sdkInt: Int = Build.VERSION.SDK_INT, now: Long = System.currentTimeMillis()) {
        if (!MediaStoreProjection.supportsQueryArgs(sdkInt)) return
        if (!reconciling.compareAndSet(false, true)) return
        try {
            val remoteFavorites = dataSource.queryFavoriteIds()
            val staleIds = favoriteDao.getAll()
                .filter { it.mediaId !in remoteFavorites && it.addedAt < now - FAVORITE_SYNC_GRACE_MILLIS }
                .map { it.mediaId }
            if (staleIds.isNotEmpty()) favoriteDao.deleteAll(staleIds)
        } finally {
            reconciling.set(false)
        }
    }

    /**
     * 孤儿清扫([MediaStoreSyncWorker] 的执行体,计划陷阱 3 的兜底):删除路径内的即时清理
     * 只覆盖「本 App 发起的删除」;库外删除(系统相册 / 文件管理器 / 连电脑)后,Room 的
     * 收藏 / 隐藏 / 相册关联 / 标签关联会留下指向不存在照片的孤儿行,这里统一清扫。
     *
     * 幂等:每次都以「MediaStore 全量 id 集合」为真源做差,重跑不产生额外删除。
     * 孤儿批量分块删除,避开 SQLite 绑定变量数上限(API 28 为 999)。
     *
     * @return 清掉的孤儿照片数
     */
    suspend fun sweepOrphans(): Int {
        val knownIds = dataSource.queryAllImageIds()
        val candidateIds = buildList {
            addAll(favoriteDao.getAll().map { it.mediaId })
            addAll(hiddenDao.allMediaIds())
            addAll(albumPhotoDao.allMediaIds())
            addAll(photoTagDao.allMediaIds())
        }.distinct()
        var removed = 0
        for (chunk in candidateIds.filterNot { it in knownIds }.chunked(DELETE_CHUNK_SIZE)) {
            favoriteDao.deleteAll(chunk)
            hiddenDao.deleteAll(chunk)
            albumPhotoDao.deleteByMediaIds(chunk)
            photoTagDao.deleteByMediaIds(chunk)
            removed += chunk.size
        }
        return removed
    }

    private suspend fun clearLocalMetadataFor(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        favoriteDao.deleteAll(mediaIds)
        hiddenDao.deleteAll(mediaIds)
        // 相册 / 标签关联同样以 mediaId 为外键(无级联),一并清理孤儿(计划陷阱 3)
        albumPhotoDao.deleteByMediaIds(mediaIds)
        photoTagDao.deleteByMediaIds(mediaIds)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val PREFETCH_DISTANCE = 10

        /** IS_FAVORITE / RELATIVE_PATH 筛选都不带绑定参数,共享同一空数组。 */
        val NO_SELECTION_ARGS = emptyArray<String>()

        /** 收藏反向同步的宽限期:App 内刚写入的收藏,在这段时间内不参与对齐(竞态防护)。 */
        const val FAVORITE_SYNC_GRACE_MILLIS = 30_000L

        /** 孤儿批量删除的分块大小:SQLite 绑定变量数上限在 API 28 为 999,留出余量。 */
        const val DELETE_CHUNK_SIZE = 500
    }
}
