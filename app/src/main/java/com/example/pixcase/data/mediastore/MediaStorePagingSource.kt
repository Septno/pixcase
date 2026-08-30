package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.pixcase.data.model.MediaPhoto

/**
 * 按页偏移翻页的 PagingSource。
 *
 * MediaStore 没有原生分页 API,用 `LIMIT <loadSize> OFFSET <page*loadSize>`
 * 配合 ContentResolver.query 实现。ContentObserver 触发 invalidate() 时整体重新加载。
 */
internal class MediaStorePagingSource(
    private val contentResolver: ContentResolver,
    private val contentUri: Uri,
    private val projection: Array<String>,
    private val sortOrderFactory: (loadSize: Int, offset: Int) -> String =
        { loadSize, offset -> MediaStoreProjection.imageSortOrder(loadSize, offset) },
    private val mapper: (Cursor, Uri) -> MediaPhoto? = { cursor, uri -> cursor.toMediaPhotoOrNull(uri) }
) : PagingSource<Int, MediaPhoto>() {

    @Suppress("TooGenericExceptionCaught") // PagingSource.load 约定:任何运行时错误都转为 LoadResult.Error
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaPhoto> {
        val page = params.key ?: 0
        val safeLoadSize = params.loadSize.coerceAtMost(MAX_LOAD_SIZE)
        val offset = page * safeLoadSize

        return try {
            val sortOrder = sortOrderFactory(safeLoadSize, offset)
            val items = queryAndMap(sortOrder)
            LoadResult.Page(
                data = items,
                prevKey = (page - 1).takeIf { it >= 0 },
                nextKey = if (items.size == safeLoadSize) page + 1 else null
            )
        } catch (e: Exception) {
            // Exception 范围(不含 Error 子类如 OutOfMemoryError)对 ContentResolver 调用足够;
            // Paging 文档要求吞掉所有可恢复错误转为 LoadResult.Error 让上层展示。
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaPhoto>): Int? = state.anchorPosition
        ?.let { state.closestPageToPosition(it) }
        ?.prevKey
        ?.plus(1)

    private fun queryAndMap(sortOrder: String): List<MediaPhoto> {
        val cursor = contentResolver.query(
            contentUri,
            projection,
            /* selection = */
            null,
            /* selectionArgs = */
            null,
            sortOrder
        ) ?: return emptyList()

        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    mapper(c, contentUri)?.let { add(it) }
                }
            }
        }
    }

    internal companion object {
        /** 防 Paging 内部异常 loadSize 时单页拉满导致内存爆炸。 */
        const val MAX_LOAD_SIZE = 5_000
    }
}
