package com.example.pixcase.data.db.entity

/** photo_tag 的查询 POJO:一次拿一批照片的全部标签关联,交叉计算交集用。 */
data class PhotoTagRow(
    val mediaId: Long,
    val tagId: Long
)
