package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.PhotoTagCrossRef
import com.example.pixcase.data.db.entity.TagEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

/** 标签名非法(空白 / 超长)时返回 null,调用方决定如何提示。 */
internal fun normalizeTagName(raw: String, maxLength: Int = 30): String? {
    val trimmed = raw.trim()
    return if (trimmed.isEmpty() || trimmed.length > maxLength) null else trimmed
}

@Suppress("MagicNumber") // 色板常量本身即字面量
private val TAG_COLOR_PALETTE = listOf(
    0xFF6750A4.toInt(),
    0xFF386A20.toInt(),
    0xFFB3261E.toInt(),
    0xFF7D5260.toInt(),
    0xFF00639B.toInt(),
    0xFF715147.toInt()
)

/** 新建标签的自动配色:按名字哈希从固定色板取色,同名永远同色。 */
internal fun autoTagColor(name: String): Int =
    TAG_COLOR_PALETTE[abs(name.hashCode()) % TAG_COLOR_PALETTE.size] or 0xFF000000.toInt()

/**
 * 标签仓库:标签 CRUD 与「打标签」。
 *
 * 标签名唯一性交给调用方在 UI 层感知(IGNORE 冲突策略下重名插入返回 -1,视为已存在)。
 * 删除标签必须连 photo_tag 关联一起清(无外键级联,计划陷阱 3);照片被 MediaStore
 * 真删后的孤儿清理在 [PhotoRepository] 统一做。
 */
@Singleton
class TagsRepository @Inject constructor(
    private val tagDao: TagDao,
    private val photoTagDao: PhotoTagDao
) {
    fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    /** 创建标签,返回新 id;名字非法返回 null;重名(IGNORE 冲突)返回 -1。 */
    suspend fun create(rawName: String, colorArgb: Int, now: Long): Long? {
        val name = normalizeTagName(rawName) ?: return null
        val id = tagDao.insert(TagEntity(name = name, colorArgb = colorArgb, createdAt = now))
        return if (id == -1L) null else id
    }

    suspend fun rename(tagId: Long, rawName: String) {
        val name = normalizeTagName(rawName) ?: return
        tagDao.findById(tagId)?.let { tagDao.update(it.copy(name = name)) }
    }

    /** 删除标签与其全部照片关联。 */
    suspend fun delete(tagId: Long) {
        photoTagDao.deleteByTagId(tagId)
        val tag = tagDao.findById(tagId) ?: return
        tagDao.delete(tag)
    }

    /** 把标签打在一批照片上(已在的不受影响,REPLACE 幂等)。 */
    suspend fun applyToMedia(tagId: Long, mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        photoTagDao.insertAll(mediaIds.map { PhotoTagCrossRef(tagId, it) })
    }

    /** 从一批照片上摘掉标签。 */
    suspend fun removeFromMedia(tagId: Long, mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        photoTagDao.deleteForMedia(tagId, mediaIds)
    }

    /** 一批照片的全部标签关联;打标签对话框据此算「全部选中都带着」的标签交集。 */
    suspend fun rowsForMedia(mediaIds: Collection<Long>) = photoTagDao.rowsForMedia(mediaIds)
}
