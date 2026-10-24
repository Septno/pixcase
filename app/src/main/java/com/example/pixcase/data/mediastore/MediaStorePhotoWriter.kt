package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.pixcase.core.util.IoDispatcher
import com.example.pixcase.di.ImagesContentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * MediaStore 写操作:删除与收藏标记。
 *
 * 按系统版本分两条路径:
 * - API 30+:媒体属于其他 App 时不能直接改,走 `createDeleteRequest` / `createFavoriteRequest`
 *   拿 PendingIntent 交由 UI 启动。删除会弹系统确认框,同意后原图进系统相册回收站;
 *   收藏静默生效,不弹框。
 * - API 29:manifest 已声明 legacy storage + WRITE_EXTERNAL_STORAGE,可直接 delete / update;
 * - API 28 及以下:经典存储,delete 可用;MediaStore 没有 IS_FAVORITE 列,收藏同步跳过
 *   (收藏语义由 Room 覆盖层独立承载)。
 *
 * 30+ 的两条 createRequest 路径依赖真机 MediaProvider,无法在 JVM 单测覆盖,只能真机验证。
 */
@Singleton
class MediaStorePhotoWriter @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ImagesContentUri private val contentUri: Uri
) {
    private val contentResolver: ContentResolver = context.contentResolver

    sealed interface DeleteOutcome {

        /** API 30+:需要用户在系统确认框点头;[sender] 由 UI 启动,同意后系统执行删除。 */
        data class NeedsConsent(val sender: IntentSender) : DeleteOutcome

        /**
         * API 29 及以下:已直接执行。**永久删除**(系统回收站 API 30 才有),
         * [deletedIds] 只含真正删掉的(无权限的会被跳过),调用方据此清理本地覆盖层。
         */
        data class Completed(val deletedCount: Int, val deletedIds: List<Long>) : DeleteOutcome
    }

    /**
     * 删除照片。空集合直接返回 [DeleteOutcome.Completed],不发任何请求。
     */
    suspend fun deleteImages(mediaIds: Collection<Long>): DeleteOutcome = withContext(ioDispatcher) {
        if (mediaIds.isEmpty()) return@withContext DeleteOutcome.Completed(0, emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = mediaIds.map { imageUri(it) }
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
            DeleteOutcome.NeedsConsent(pendingIntent.intentSender)
        } else {
            val deletedIds = mediaIds.filter { id -> contentResolver.deleteQuietly(imageUri(id)) > 0 }
            DeleteOutcome.Completed(deletedIds.size, deletedIds)
        }
    }

    /**
     * 把 MediaStore.IS_FAVORITE 同步成 [favorite]。
     *
     * 返回 null 表示无需 UI 启动任何东西(≤28 无该列、29 已直接写完、集合为空);
     * 非 null 时由 UI 静默启动(API 30+),结果不影响 Room 已写入的状态 —— 同步是尽力而为。
     */
    suspend fun syncFavorite(mediaIds: Collection<Long>, favorite: Boolean): IntentSender? = withContext(ioDispatcher) {
        if (mediaIds.isEmpty()) return@withContext null
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createFavoriteRequest(contentResolver, mediaIds.map { imageUri(it) }, favorite)
                    .intentSender
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
                }
                // 单张失败(非本 App 创建且无 legacy 权限)只跳过该张,不中断批量;
                // Room 已记录,UI 收藏状态不受影响,MediaStore 侧留待下次同步收敛。
                mediaIds.forEach { id -> contentResolver.updateQuietly(imageUri(id), values) }
                null
            }
            else -> null
        }
    }

    sealed interface RenameOutcome {

        /** 重命名已执行;[renamed] 为 false 表示系统侧更新 0 行(权限被回收等)。 */
        data class Done(val renamed: Boolean) : RenameOutcome

        /**
         * API 30+ 且照片不属于本 App:直接 update DISPLAY_NAME 被拒,须先经
         * `createWriteRequest` 拿到用户授权([sender] 由 UI 启动,同意后重试直接更新)。
         */
        data class NeedsConsent(val sender: IntentSender) : RenameOutcome
    }

    /**
     * 重命名单张照片(改 DISPLAY_NAME)。
     *
     * 扩展名由调用方([DisplayNameValidator.buildDisplayName])拼好,这里只写库。
     * API 29 及以下配合 WRITE / legacy 权限直接更新;API 30+ 先试直接更新,
     * 照片不属于本 App 时被系统拒绝,退到 `createWriteRequest` 走授权。
     */
    suspend fun renameImage(mediaId: Long, newDisplayName: String): RenameOutcome = withContext(ioDispatcher) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                RenameOutcome.Done(contentResolver.update(imageUri(mediaId), values, null, null) > 0)
            } catch (ignored: SecurityException) {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(imageUri(mediaId)))
                RenameOutcome.NeedsConsent(pendingIntent.intentSender)
            }
        } else {
            RenameOutcome.Done(contentResolver.updateQuietly(imageUri(mediaId), values) > 0)
        }
    }

    /**
     * 单张删除;无权限等场景计 0 行,不中断批量。
     * 异常按约定命名 `ignored`:该张删除失败是预期内的降级路径,不逐条上报。
     */
    private fun ContentResolver.deleteQuietly(uri: Uri): Int = try {
        delete(
            uri,
            /* selection = */
            null,
            /* selectionArgs = */
            null
        )
    } catch (ignored: SecurityException) {
        0
    }

    /** 单张收藏同步;失败计 0 行,语义同上。 */
    private fun ContentResolver.updateQuietly(uri: Uri, values: ContentValues): Int = try {
        update(
            uri,
            values,
            /* selection = */
            null,
            /* selectionArgs = */
            null
        )
    } catch (ignored: SecurityException) {
        0
    }

    private fun imageUri(mediaId: Long): Uri = ContentUris.withAppendedId(contentUri, mediaId)
}
