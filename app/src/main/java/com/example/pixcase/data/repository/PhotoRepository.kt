package com.example.pixcase.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.pixcase.core.util.ApplicationScope
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 照片仓库。1.1 阶段对外唯一方法:images()。
 *
 * 时间线用图 = MediaStore Paging(数据源) + Room 收藏/隐藏覆盖层。
 * combine 把 PagingData 与 favorite / hidden 媒体 ID 集合同步,paging.map 用 copy 覆盖 isFavorite / isHidden;
 * cachedIn 把 Pager 缓存到 ApplicationScope,跨 ViewModel 生命周期共享,避免配置变更 / 翻页重拉。
 *
 * isHidden = true 的照片本阶段不 filter(留到阶段 2 引入"显示/隐藏隐藏照片"toggle 时再做);
 * 1.1 时间线仅展示,不做隐藏过滤。
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val favoriteDao: FavoriteDao,
    private val hiddenDao: HiddenDao,
    @ApplicationScope private val externalScope: CoroutineScope
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
        ).flow.cachedIn(externalScope),
        favoriteDao.observeAllIds(),
        hiddenDao.observeAllIds()
    ) { paging, favIds, hiddenIds ->
        val favSet = favIds.toHashSet()
        val hiddenSet = hiddenIds.toHashSet()
        paging.map { p ->
            p.copy(
                isFavorite = p.id in favSet,
                isHidden = p.id in hiddenSet
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val PREFETCH_DISTANCE = 10
    }
}
