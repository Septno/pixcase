package com.example.pixcase.data.model

import android.net.Uri

/**
 * UI 层使用的照片模型。Repository 把 MediaStore 行 + Room 元数据合并后产出此对象。
 *
 * 字段较多是 MediaStore + EXIF + Room 元数据的并集;
 * 列表渲染时只用 id + uri + thumb 字段,其余字段在进入查看器时按需补齐。
 *
 * width / height / sizeBytes 在列表投影(5 列,见 §3.2)中不消费,默认 0;
 * 进入查看器时按 mediaId 重新查询补齐。
 */
data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedSec: Long,
    val dateTakenMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val durationMs: Long? = null,
    val bucketName: String? = null,
    val relativePath: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    // 阶段 0 占位,后续阶段由 Repository 填充
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val categories: List<ImageCategory> = emptyList(),
    val marks: List<String> = emptyList()
)

/** 自定义相册用的标签。 */
data class Tag(
    val id: Long,
    val name: String,
    val colorArgb: Int
)

/** 工具二(阶段 7)用的大类分类。 */
data class ImageCategory(
    val id: Long,
    val name: String,
    val colorArgb: Int
)
