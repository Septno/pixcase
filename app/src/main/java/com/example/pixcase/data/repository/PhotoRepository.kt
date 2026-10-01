package com.example.pixcase.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 照片仓库。对外唯一方法:images()。
 *
 * 时间线用图 = MediaStore Paging(数据源) + Room 收藏/隐藏覆盖层。
 * combine 把 PagingData 与 favorite / hidden 媒体 ID 集合同步,paging.map 用 copy 覆盖 isFavorite / isHidden。
 *
 * images() 返回冷流:每次收集都新建 Pager,缓存由消费方(ViewModel)用 cachedIn 管理。
 * 仓库层不做 cachedIn —— 用 ApplicationScope 缓存会把已加载的照片窗口钉在进程生命周期上,
 * 内存占用随图库规模线性增长,而配置变更复用交给 ViewModel 层即可。
 *
 * isHidden = true 的照片当前不 filter(留到引入"显示隐藏照片"开关时再做)。
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
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
