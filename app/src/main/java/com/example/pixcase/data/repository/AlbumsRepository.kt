package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.AlbumDao
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.AlbumPhotoCrossRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 相册名非法(空白 / 超长)时返回 null,调用方决定如何提示。 */
internal fun normalizeAlbumName(raw: String, maxLength: Int = 50): String? {
    val trimmed = raw.trim()
    return if (trimmed.isEmpty() || trimmed.length > maxLength) null else trimmed
}

/**
 * 自定义相册仓库:相册 CRUD、照片关联、封面。
 *
 * 封面规则(计划阶段 3):未手动指定时自动取「最近加入该相册的一张」
 * ([AlbumPhotoDao.latestMediaIdInAlbum]);手动指定走 [setCover],只改 coverMediaId。
 *
 * 删除相册必须连关联一起删(album_photo 无外键级联,计划陷阱 3);照片被 MediaStore
 * 真删后的孤儿清理在 [PhotoRepository] 统一做。
 */
@Singleton
class AlbumsRepository @Inject constructor(
    private val albumDao: AlbumDao,
    private val albumPhotoDao: AlbumPhotoDao
) {
    fun observeUserAlbums(): Flow<List<AlbumEntity>> = albumDao.observeUserAlbums()

    fun observeMediaIds(albumId: Long): Flow<List<Long>> = albumPhotoDao.observeMediaIdsByAlbum(albumId)

    suspend fun album(albumId: Long): AlbumEntity? = albumDao.findById(albumId)

    suspend fun countIn(albumId: Long): Int = albumPhotoDao.countInAlbum(albumId)

    suspend fun latestPhotoOf(albumId: Long): Long? = albumPhotoDao.latestMediaIdInAlbum(albumId)

    /** 创建相册,返回新 id;名字非法返回 null。 */
    suspend fun createAlbum(rawName: String, now: Long): Long? {
        val name = normalizeAlbumName(rawName) ?: return null
        return albumDao.insert(AlbumEntity(name = name, coverMediaId = null, createdAt = now, updatedAt = now))
    }

    /** 重命名;名字非法或相册不存在返回 false。 */
    suspend fun renameAlbum(albumId: Long, rawName: String, now: Long): Boolean {
        val updated = normalizeAlbumName(rawName)
            ?.let { name -> albumDao.findById(albumId)?.copy(name = name, updatedAt = now) }
            ?: return false
        albumDao.update(updated)
        return true
    }

    /** 删除相册与其全部照片关联。 */
    suspend fun deleteAlbum(albumId: Long) {
        albumPhotoDao.deleteAllInAlbum(albumId)
        val album = albumDao.findById(albumId) ?: return
        albumDao.delete(album)
    }

    /** 批量加入照片;加入后若相册尚无封面,自动用第一张。 */
    suspend fun addPhotos(albumId: Long, mediaIds: Collection<Long>, now: Long) {
        if (mediaIds.isEmpty()) return
        val album = albumDao.findById(albumId) ?: return
        albumPhotoDao.insertAll(mediaIds.map { AlbumPhotoCrossRef(albumId, it, now) })
        if (album.coverMediaId == null) {
            albumPhotoDao.latestMediaIdInAlbum(albumId)?.let { latest ->
                albumDao.setCover(albumId, latest, now)
            }
        }
    }

    suspend fun removePhoto(albumId: Long, mediaId: Long, now: Long) {
        albumPhotoDao.delete(albumId, mediaId)
        val album = albumDao.findById(albumId)
        // 移走的正是封面时,封面顺延为该相册最近一张(没有照片则清空)
        if (album?.coverMediaId == mediaId) {
            val next = albumPhotoDao.latestMediaIdInAlbum(albumId)
            if (next == null) albumDao.clearCover(albumId, now) else albumDao.setCover(albumId, next, now)
        }
    }

    suspend fun setCover(albumId: Long, mediaId: Long, now: Long) {
        albumDao.setCover(albumId, mediaId, now)
    }
}
