package com.example.pixcase.data.mediastore

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.pixcase.data.model.MediaPhoto

/**
 * 把 MediaStore Cursor 行映射为 MediaPhoto。
 *
 * 投影列(5 列,见 MediaStoreProjection)用 getColumnIndexOrThrow 严格读取;
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
