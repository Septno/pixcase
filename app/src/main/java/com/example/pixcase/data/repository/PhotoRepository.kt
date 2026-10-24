package com.example.pixcase.data.repository

import android.content.IntentSender
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.entity.FavoriteEntity
import com.example.pixcase.data.db.entity.HiddenEntity
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.model.MediaPhoto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map as mapFlow

/**
 * 照片仓库:读 = MediaStore Paging(数据源) + Room 收藏/隐藏覆盖层;写 = Room 真源 + MediaStore 尽力同步。
 *
 * images() 的 combine 把 PagingData 与 favorite / hidden 媒体 ID 集合同步,用 copy 覆盖
 * isFavorite / isHidden,随后**过滤掉隐藏照片** —— hidden_photo 是 App 内隐私层的唯一真源。
 * 过滤发生在分页加载之后:整页全被隐藏时会出现「看着没加载满」的窗口,换来的是不必在
 * MediaStore 查询里塞 Room 条件(两者无法 join);隐藏条目通常稀疏,代价可接受。
 *
 * 写操作约定:Room 先写(UI 状态即时生效),MediaStore(IS_FAVORITE / 删除)随后尽力同步;
 * 删除走 [MediaStorePhotoWriter],完成后清理对应照片的 Room 覆盖层,避免 MediaStore 真删后
 * 留下孤儿行(计划陷阱 3)。
 *
 * images() 返回冷流:每次收集都新建 Pager,缓存由消费方(ViewModel)用 cachedIn 管理。
 * 仓库层不做 cachedIn —— 用 ApplicationScope 缓存会把已加载的照片窗口钉在进程生命周期上。
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val writer: MediaStorePhotoWriter,
    private val favoriteDao: FavoriteDao,
    private val hiddenDao: HiddenDao
) {
    fun images(): Flow<PagingData<MediaPhoto>> = combine(
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dataSource.imagesPagingSource() }
        ).flow,
        favoriteDao.observeAllIds(),
        hiddenDao.observeAllIds()
    ) { paging, favIds, hiddenIds ->
        val favSet = favIds.toHashSet()
        val hiddenSet = hiddenIds.toHashSet()
        paging
            .map { p ->
                p.copy(
                    isFavorite = p.id in favSet,
                    isHidden = p.id in hiddenSet
                )
            }
            .filter { !it.isHidden }
    }

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

    /** API 30+ 系统确认框同意后调用:清理这些照片的本地收藏/隐藏记录。 */
    suspend fun onSystemDeleteApproved(mediaIds: Collection<Long>) {
        clearLocalMetadataFor(mediaIds)
    }

    private suspend fun clearLocalMetadataFor(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        favoriteDao.deleteAll(mediaIds)
        hiddenDao.deleteAll(mediaIds)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val PREFETCH_DISTANCE = 10
    }
}
