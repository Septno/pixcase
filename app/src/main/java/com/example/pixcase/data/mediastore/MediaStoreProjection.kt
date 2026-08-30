package com.example.pixcase.data.mediastore

import android.provider.MediaStore

/**
 * MediaStore 投影列定义。
 *
 * 1.1 阶段最小投影(计划 §3.2 要求):列表只 query 5 列,
 * ORIENTATION / LATITUDE / LONGITUDE / WIDTH / HEIGHT / SIZE 推迟到查看器按需补齐。
 * Video 投影留作后续阶段;1.1 只实现 images。
 */
internal object MediaStoreProjection {
    val IMAGE_COLUMNS: Array<String> = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE
    )

    /** "DATE_ADDED DESC LIMIT %d OFFSET %d";loadSize / offset 均为 Int 无注入风险。 */
    fun imageSortOrder(loadSize: Int, offset: Int): String =
        "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $loadSize OFFSET $offset"
}
