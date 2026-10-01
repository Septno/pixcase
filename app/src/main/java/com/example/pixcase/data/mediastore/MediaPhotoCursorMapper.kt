package com.example.pixcase.data.mediastore

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.pixcase.data.model.MediaPhoto

/**
 * 把 MediaStore Cursor 行映射为 MediaPhoto。
 *
 * 投影列(见 MediaStoreProjection)用 getColumnIndexOrThrow 严格读取;
 * width / height / sizeBytes 走 MediaPhoto model 默认值 0,不在此 mapper 范围内。
 *
 * 返回 null 表示该行无法映射(例如 _ID == 0),由 PagingSource.load() 跳过。
 */
internal fun Cursor.toMediaPhotoOrNull(contentUri: Uri): MediaPhoto? {
    val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
    if (id == 0L) return null

    return MediaPhoto(
        id = id,
        uri = ContentUris.withAppendedId(contentUri, id),
        displayName = getStringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME),
        mimeType = getStringOrEmpty(MediaStore.Images.Media.MIME_TYPE),
        dateAddedSec = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
        dateTakenMs = getLongOrNull(MediaStore.MediaColumns.DATE_TAKEN),
        bucketName = getStringOrNull(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
    )
}

private fun Cursor.getStringOrEmpty(column: String): String {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) "" else getString(idx)
}

private fun Cursor.getStringOrNull(column: String): String? {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) null else getString(idx)
}

private fun Cursor.getLongOrNull(column: String): Long? {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) null else getLong(idx)
}

private fun Cursor.getIntOrNull(column: String): Int? {
    val idx = getColumnIndexOrThrow(column)
    return if (isNull(idx)) null else getInt(idx)
}

/**
 * 查看器用的映射:在 [toMediaPhotoOrNull] 之上补 WIDTH / HEIGHT / SIZE。
 *
 * 这三列只存在于 [MediaStoreProjection.PHOTO_DETAIL_COLUMNS],列表投影里没有;
 * 用同一个 cursor 行读,不额外查库。列缺失(理论上不会)时退回 model 默认值 0。
 */
internal fun Cursor.toMediaPhotoWithDetailsOrNull(contentUri: Uri): MediaPhoto? = toMediaPhotoOrNull(contentUri)?.copy(
    width = getIntOrNull(MediaStore.MediaColumns.WIDTH) ?: 0,
    height = getIntOrNull(MediaStore.MediaColumns.HEIGHT) ?: 0,
    sizeBytes = getLongOrNull(MediaStore.MediaColumns.SIZE) ?: 0L
)
