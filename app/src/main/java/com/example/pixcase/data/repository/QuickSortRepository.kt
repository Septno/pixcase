package com.example.pixcase.data.repository

import androidx.room.withTransaction
import com.example.pixcase.data.db.AppDatabase
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.QuickSortDecisionDao
import com.example.pixcase.data.db.dao.QuickSortSessionDao
import com.example.pixcase.data.db.dao.QuickSortSessionTargetDao
import com.example.pixcase.data.db.entity.QuickSortDecisionEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.db.entity.QuickSortSessionTargetEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 快速整理(阶段 3.5)的会话与决策仓库,规则见计划 § 3.5.3:
 *
 * - 每个相册至多一个 session(主键 = albumId);目标相册列表带顺序,独立表存储(陷阱 5);
 * - 决策发生时立即落库:**decision + album_photo 关联变更同事务**(真删除进回收站的语义
 *   在任务 9 接入,此处只落 KEEP / SKIP / MOVE);
 * - 「继续上次」= 相册顺序流过滤掉已决策的 mediaId;「从头开始」= 只清 decisions 与进度
 *   计数,不删照片关联;
 * - 完成态:processedCount ≥ totalCount 时置 isCompleted。
 */
@Singleton
class QuickSortRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val sessionDao: QuickSortSessionDao,
    private val targetDao: QuickSortSessionTargetDao,
    private val decisionDao: QuickSortDecisionDao,
    private val albumPhotoDao: AlbumPhotoDao,
    private val albumsRepository: AlbumsRepository
) {
    fun observeSession(albumId: Long): Flow<QuickSortSessionEntity?> = sessionDao.observeByAlbum(albumId)

    fun observeTargets(albumId: Long): Flow<List<QuickSortSessionTargetEntity>> = targetDao.observeForSession(albumId)

    suspend fun session(albumId: Long): QuickSortSessionEntity? = sessionDao.getByAlbum(albumId)

    /** 未处理照片:相册内 mediaId 顺序,过滤掉已决策的。 */
    suspend fun pendingMediaIds(albumId: Long): List<Long> = albumPhotoDao.observeMediaIdsByAlbum(albumId).first()
        .filterNot { it in decisionDao.processedMediaIds(albumId).toHashSet() }

    /**
     * 创建 session:目标相册按选择顺序落 position;totalCount 以创建时的相册条数为准。
     * 若已有 session,由调用方先决定「继续 / 从头」再走 [updateTargets] / [resetSession]。
     */
    suspend fun createSession(albumId: Long, targetAlbumIds: List<Long>, now: Long) {
        appDatabase.withTransaction {
            targetDao.deleteForSession(albumId)
            targetDao.insertAll(
                targetAlbumIds.mapIndexed { position, targetAlbumId ->
                    QuickSortSessionTargetEntity(albumId, position, targetAlbumId)
                }
            )
            sessionDao.upsert(
                QuickSortSessionEntity(
                    albumId = albumId,
                    startedAt = now,
                    lastActiveAt = now,
                    totalCount = albumPhotoDao.countInAlbum(albumId),
                    processedCount = 0,
                    currentIndex = 0
                )
            )
        }
    }

    /** 重选目标相册(选择器 [保存]):只重写 target 表,决策与进度不动。 */
    suspend fun updateTargets(albumId: Long, targetAlbumIds: List<Long>) {
        appDatabase.withTransaction {
            targetDao.deleteForSession(albumId)
            targetDao.insertAll(
                targetAlbumIds.mapIndexed { position, targetAlbumId ->
                    QuickSortSessionTargetEntity(albumId, position, targetAlbumId)
                }
            )
        }
    }

    /** 保留:标记已处理,下次不再弹出。 */
    suspend fun recordKeep(albumId: Long, mediaId: Long, now: Long) =
        recordDecision(albumId, mediaId, DECISION_KEEP, now)

    /** 跳过:同样算「已处理」(计划 § 3.5.3),避免重复打扰。 */
    suspend fun recordSkip(albumId: Long, mediaId: Long, now: Long) =
        recordDecision(albumId, mediaId, DECISION_SKIP, now)

    /** 移出当前相册并加入目标相册:decision + 两侧 album_photo 变更同事务(封面顺延随仓库规则)。 */
    suspend fun recordMove(albumId: Long, mediaId: Long, targetAlbumId: Long, now: Long) {
        appDatabase.withTransaction {
            decisionDao.insert(
                QuickSortDecisionEntity(albumId, mediaId, DECISION_MOVE_TO_PREFIX + targetAlbumId, now)
            )
            albumsRepository.removePhoto(albumId, mediaId, now)
            albumsRepository.addPhotos(targetAlbumId, listOf(mediaId), now)
            bumpProgress(albumId, now)
        }
    }

    /** 从头开始:只清 decisions 与进度计数,不删照片关联(计划 § 3.5.3)。 */
    suspend fun resetSession(albumId: Long, now: Long) {
        appDatabase.withTransaction {
            decisionDao.clearSession(albumId)
            sessionDao.getByAlbum(albumId)?.let { session ->
                sessionDao.update(
                    session.copy(processedCount = 0, currentIndex = 0, isCompleted = false, lastActiveAt = now)
                )
            }
        }
    }

    /** 保存翻页位置(退出自动保存)。 */
    suspend fun savePosition(albumId: Long, currentIndex: Int, now: Long) {
        sessionDao.getByAlbum(albumId)?.let { session ->
            sessionDao.update(session.copy(currentIndex = currentIndex, lastActiveAt = now))
        }
    }

    /** 删除 session 及其全部决策与目标(相册删除等场景的整卷清理)。 */
    suspend fun deleteSession(albumId: Long) {
        appDatabase.withTransaction {
            decisionDao.clearSession(albumId)
            targetDao.deleteForSession(albumId)
            sessionDao.delete(albumId)
        }
    }

    private suspend fun recordDecision(albumId: Long, mediaId: Long, decision: String, now: Long) {
        appDatabase.withTransaction {
            decisionDao.insert(QuickSortDecisionEntity(albumId, mediaId, decision, now))
            bumpProgress(albumId, now)
        }
    }

    private suspend fun bumpProgress(albumId: Long, now: Long) {
        val session = sessionDao.getByAlbum(albumId) ?: return
        val processed = session.processedCount + 1
        sessionDao.update(
            session.copy(
                processedCount = processed,
                isCompleted = processed >= session.totalCount,
                lastActiveAt = now
            )
        )
    }

    private companion object {
        const val DECISION_KEEP = "KEEP"
        const val DECISION_SKIP = "SKIP"
        const val DECISION_MOVE_TO_PREFIX = "MOVE_TO_"
    }
}
