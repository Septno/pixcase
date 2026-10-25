package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.SearchHistoryDao
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.model.groupingEpochSec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 搜索命中来源(类型 Chip 的过滤维度)。 */
enum class SearchHitType { FILENAME, ALBUM, TAG }

/** 一张搜索结果:照片 + 它被命中的方式集合(多来源命中合并)。 */
data class SearchedPhoto(
    val photo: MediaPhoto,
    val hitTypes: Set<SearchHitType>
)

/** 筛选上下文:结果集之外的 Room 元数据,内存过滤用(一次性加载,随结果快照走)。 */
data class FilterContext(
    val favorites: Set<Long>,
    val hidden: Set<Long>,
    val tagsByMedia: Map<Long, Set<Long>>
)

/** 一条搜索建议:标签名或相册名(高频优先)。 */
data class SearchSuggestion(val text: String, val isTag: Boolean)

/**
 * 阶段 4 搜索仓库:三类命中聚合去重 ——
 * - 文件名:MediaStore DISPLAY_NAME LIKE(片段按字面量匹配);
 * - 自定义相册名 / 标签名:Room 表小,全量读 + 内存 contains(陷阱 4 的 FTS 属阶段 7);
 * - EXIF 相机型号:MediaStore 无公开列,逐张读 EXIF 无法支撑全库搜索 —— 选型为
 *   「后台抽取入库 + Room 索引」,属后续增强(计划 § 六 阶段 4 已记录)。
 *
 * 结果按时间线同款时间基准 DESC;成员反查有界截断,防 photosByIds 的 IN 变量超限。
 */
@Suppress("LongParameterList") // 数据源 + 三个命中 DAO + 照片/历史/隐藏仓储,各维度职责单一
@Singleton
class SearchRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val albumsRepository: AlbumsRepository,
    private val albumPhotoDao: AlbumPhotoDao,
    private val photoTagDao: PhotoTagDao,
    private val tagDao: TagDao,
    private val photoRepository: PhotoRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val hiddenDao: HiddenDao
) {
    suspend fun search(rawQuery: String): List<SearchedPhoto> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        val filenameHits = dataSource.queryByDisplayNameFragment(query)
        val matchedAlbumIds = albumsRepository.observeUserAlbums().first()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.id }
        val matchedTagIds = tagDao.observeAll().first()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.id }

        // 命中的相册 / 标签反查成员照片,与文件名命中合并出照片全集
        val albumMembers = albumPhotoDao.mediaIdsInAlbums(matchedAlbumIds).toHashSet()
        val tagMembers = photoTagDao.mediaIdsForTags(matchedTagIds).toHashSet()
        val memberIds = (albumMembers + tagMembers).take(MEMBER_LIMIT)
        val memberPhotos = photoRepository.photosByIds(memberIds).associateBy { it.id }

        val merged = HashMap<Long, MutableSet<SearchHitType>>(filenameHits.size + memberPhotos.size)
        filenameHits.forEach { merged.getOrPut(it.id) { mutableSetOf() }.add(SearchHitType.FILENAME) }
        memberPhotos.values.forEach { photo ->
            val hits = merged.getOrPut(photo.id) { mutableSetOf() }
            if (photo.id in albumMembers) hits.add(SearchHitType.ALBUM)
            if (photo.id in tagMembers) hits.add(SearchHitType.TAG)
        }

        // 文件名命中可能不在反查集合里(不属于任何命中的相册/标签),统一从两处取照片对象
        val photoById = memberPhotos.toMutableMap()
        filenameHits.forEach { photoById[it.id] = it }

        return merged.entries
            .mapNotNull { (id, hits) -> photoById[id]?.let { SearchedPhoto(it, hits.toSet()) } }
            .sortedByDescending { it.photo.groupingEpochSec() }
    }

    private companion object {
        /** 成员反查上限:photosByIds 走 IN 查询,防超过 SQLite 绑定变量上限(API 28 为 999)。 */
        const val MEMBER_LIMIT = 500

        /** 搜索历史容量(计划 § 六 阶段 4:最近 20 条)。 */
        const val HISTORY_KEEP = 20

        /** 建议条数上限。 */
        const val SUGGESTION_LIMIT = 10
    }

    /** 筛选上下文:收藏 / 隐藏 / 标签关联一次性加载,内存过滤用。 */
    suspend fun filterContextFor(mediaIds: Collection<Long>): FilterContext = FilterContext(
        favorites = photoRepository.favoritesOf(mediaIds),
        hidden = hiddenDao.allMediaIds().toSet(),
        tagsByMedia = photoTagDao.rowsForMedia(mediaIds)
            .groupBy({ it.mediaId }, { it.tagId })
            .mapValues { (_, tags) -> tags.toSet() }
    )

    /** 记录一次搜索:同词先删再插(去重置顶),超出容量裁掉最旧的。 */
    suspend fun recordQuery(query: String, now: Long = System.currentTimeMillis()) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.delete(trimmed)
        searchHistoryDao.insert(SearchHistoryEntity(query = trimmed, timestamp = now))
        searchHistoryDao.trimTo(HISTORY_KEEP)
    }

    fun observeHistory() = searchHistoryDao.observeRecent()

    suspend fun deleteHistory(query: String) = searchHistoryDao.delete(query)

    /** 搜索建议:命中的标签名(按用量降序)+ 相册名,合计前 10。 */
    suspend fun suggestions(query: String): List<SearchSuggestion> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val usageById = photoTagDao.tagUsageCounts().associate { it.tagId to it.usageCount }
        val matchedTags = tagDao.observeAll().first()
            .filter { it.name.contains(needle, ignoreCase = true) }
            .sortedByDescending { usageById[it.id] ?: 0 }
            .map { SearchSuggestion(it.name, isTag = true) }
        val matchedAlbums = albumsRepository.observeUserAlbums().first()
            .filter { it.name.contains(needle, ignoreCase = true) }
            .map { SearchSuggestion(it.name, isTag = false) }
        return (matchedTags + matchedAlbums).take(SUGGESTION_LIMIT)
    }
}
