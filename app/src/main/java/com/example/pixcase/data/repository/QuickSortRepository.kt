package com.example.pixcase.data.repository

import android.content.IntentSender
import android.os.Build
import androidx.room.withTransaction
import com.example.pixcase.data.db.AppDatabase
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.QuickSortDecisionDao
import com.example.pixcase.data.db.dao.QuickSortSessionDao
import com.example.pixcase.data.db.dao.QuickSortSessionTargetDao
import com.example.pixcase.data.db.entity.QuickSortDecisionEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.db.entity.QuickSortSessionTargetEntity
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** [QuickSortRepository.trashFromAlbum] 的三分支结果,UI 据此决定 Snackbar / 系统确认 / 撤销。 */
sealed interface TrashOutcome {
    /** 仅解绑(照片还在其他相册 / 系统相册),可撤销。 */
    data class Unbound(val mediaId: Long) : TrashOutcome

    /** 真删除已完成:permanent = true 表示 29 及以下无系统回收站的永久删除。 */
    data class Deleted(val permanent: Boolean) : TrashOutcome

    /** API 30+:等待系统确认框,同意后回调 finalizeConsentedTrash。 */
    data class NeedsConsent(val sender: IntentSender) : TrashOutcome
}

/**
 * 快速整理(阶段 3.5)的会话与决策仓库,规则见计划 § 3.5.3:
 *
 * - 每个相册至多一个 session(主键 = albumId);目标相册列表带顺序,独立表存储(陷阱 5);
 * - 决策发生时立即落库:**decision + album_photo 关联变更同事务**(真删除进回收站的语义
 *   在 [trashFromAlbum],KEEP / SKIP / MOVE 见各自方法);
 * - 「继续上次」= 相册顺序流过滤掉已决策的 mediaId;「从头开始」= 只清 decisions 与进度
 *   计数,不删照片关联;
 * - 完成态:processedCount ≥ totalCount 时置 isCompleted。
 */
@Suppress("LongParameterList") // 事务壳(appDatabase)+ 三个 quicksort DAO + 相册/照片仓库,打包成对象反而模糊事务边界
@Singleton
class QuickSortRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val sessionDao: QuickSortSessionDao,
    private val targetDao: QuickSortSessionTargetDao,
    private val decisionDao: QuickSortDecisionDao,
    private val albumPhotoDao: AlbumPhotoDao,
    private val albumsRepository: AlbumsRepository,
    private val photoRepository: PhotoRepository
) {
    fun observeSession(albumId: Long): Flow<QuickSortSessionEntity?> = sessionDao.observeByAlbum(albumId)

    fun observeTargets(albumId: Long): Flow<List<QuickSortSessionTargetEntity>> = targetDao.observeForSession(albumId)

    suspend fun session(albumId: Long): QuickSortSessionEntity? = sessionDao.getByAlbum(albumId)

    /** 未处理照片:相册内 mediaId 顺序,过滤掉已决策的。 */
    suspend fun pendingMediaIds(albumId: Long): List<Long> = albumPhotoDao.observeMediaIdsByAlbum(albumId).first()
        .filterNot { it in decisionDao.processedMediaIds(albumId).toHashSet() }

    /**
     * 创建 session:目标相册按选择顺序落 position;totalCount 以创建时的相册条数为准。
     * 新 session 总是干净开局 —— 旧 session(REPLACE 覆盖)遗留的 decisions 一并清掉,
     * 否则已决策照片仍在,processedCount 起步即等于 totalCount、直接完成态。
     * 若已有 session,由调用方先决定「继续 / 从头」再走 [updateTargets] / [resetSession]。
     */
    suspend fun createSession(albumId: Long, targetAlbumIds: List<Long>, now: Long) {
        appDatabase.withTransaction {
            decisionDao.clearSession(albumId)
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

    /**
     * 上划删除的双重语义(计划 § 3.5.4):按照片所属相册数分支 ——
     * - 仅属于当前相册:MediaStore 真删除(30+ 走系统确认框 → [TrashOutcome.NeedsConsent],
     *   同意后回调 [finalizeConsentedTrash];29 及以下直接删,无系统回收站即永久删);
     * - 同时属于其他相册:仅解绑当前相册关联,原图不动(返回 [TrashOutcome.Unbound],可撤销)。
     *
     * 顺序保证:MediaStore 删除抛异常时,Room 侧(decision / 关联)一律不动;
     * 真删除成功后的关联清理由 PhotoRepository.deletePhotos 的 Completed 分支完成。
     */
    suspend fun trashFromAlbum(albumId: Long, mediaId: Long, now: Long): TrashOutcome {
        return if (albumPhotoDao.countAlbumsOf(mediaId) > 1) {
            appDatabase.withTransaction {
                decisionDao.insert(QuickSortDecisionEntity(albumId, mediaId, DECISION_TRASH, now))
                albumsRepository.removePhoto(albumId, mediaId, now)
                bumpProgress(albumId, now)
            }
            TrashOutcome.Unbound(mediaId)
        } else {
            when (val outcome = photoRepository.deletePhotos(listOf(mediaId))) {
                is MediaStorePhotoWriter.DeleteOutcome.NeedsConsent -> TrashOutcome.NeedsConsent(outcome.sender)
                is MediaStorePhotoWriter.DeleteOutcome.Completed -> {
                    appDatabase.withTransaction {
                        decisionDao.insert(QuickSortDecisionEntity(albumId, mediaId, DECISION_TRASH, now))
                        bumpProgress(albumId, now)
                    }
                    TrashOutcome.Deleted(permanent = Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                }
            }
        }
    }

    /** 30+ 系统确认框同意后收尾:Room 关联已被 onSystemDeleteApproved 清掉,这里补 decision + 进度。 */
    suspend fun finalizeConsentedTrash(albumId: Long, mediaId: Long, now: Long) {
        appDatabase.withTransaction {
            decisionDao.insert(QuickSortDecisionEntity(albumId, mediaId, DECISION_TRASH, now))
            bumpProgress(albumId, now)
        }
    }

    /** 撤销「仅解绑」分支:删决策、恢复关联、进度回退(照片回到待处理队列头部)。 */
    suspend fun undoUnbind(albumId: Long, mediaId: Long, now: Long) {
        appDatabase.withTransaction {
            decisionDao.delete(albumId, mediaId)
            albumsRepository.addPhotos(albumId, listOf(mediaId), now)
            sessionDao.getByAlbum(albumId)?.let { session ->
                val processed = (session.processedCount - 1).coerceAtLeast(0)
                sessionDao.update(
                    session.copy(
                        processedCount = processed,
                        isCompleted = processed >= session.totalCount,
                        lastActiveAt = now
                    )
                )
            }
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
        const val DECISION_TRASH = "TRASH"
        const val DECISION_MOVE_TO_PREFIX = "MOVE_TO_"
    }
}
