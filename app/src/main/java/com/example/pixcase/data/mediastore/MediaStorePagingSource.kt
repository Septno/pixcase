package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.pixcase.data.model.MediaPhoto

/**
 * 按页偏移翻页的 PagingSource。
 *
 * MediaStore 没有原生分页 API,分页方式按系统版本分两路(见 [MediaStoreProjection.supportsQueryArgs]):
 * - API 30+:ContentResolver 的 Bundle 查询参数(QUERY_ARG_LIMIT / QUERY_ARG_OFFSET);
 * - API 26-29:把 "LIMIT n OFFSET n" 拼进 sortOrder。
 *
 * ContentObserver 触发 invalidate() 时整体重新加载。
 */
internal class MediaStorePagingSource(
    private val contentResolver: ContentResolver,
    private val contentUri: Uri,
    private val projection: Array<String>,
    private val mapper: (Cursor, Uri) -> MediaPhoto? = { cursor, uri -> cursor.toMediaPhotoOrNull(uri) },
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : PagingSource<Int, MediaPhoto>() {

    @Suppress("TooGenericExceptionCaught") // PagingSource.load 约定:任何运行时错误都转为 LoadResult.Error
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaPhoto> {
        val page = params.key ?: 0
        val safeLoadSize = params.loadSize.coerceAtMost(MAX_LOAD_SIZE)
        val offset = page * safeLoadSize

        return try {
            val items = queryAndMap(safeLoadSize, offset)
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

    private fun queryAndMap(loadSize: Int, offset: Int): List<MediaPhoto> {
        val cursor = if (MediaStoreProjection.supportsQueryArgs(sdkInt)) {
            contentResolver.query(
                contentUri,
                projection,
                MediaStoreProjection.modernQueryArgs(loadSize, offset),
                /* cancellationSignal = */
                null
            )
        } else {
            contentResolver.query(
                contentUri,
                projection,
                /* selection = */
                null,
                /* selectionArgs = */
                null,
                MediaStoreProjection.legacySortOrder(loadSize, offset)
            )
        } ?: return emptyList()

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
