package com.example.pixcase.ui.feature.timeline

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.pixcase.R
import com.example.pixcase.core.permission.PermissionUiState
import com.example.pixcase.core.permission.requiredMediaPermissions
import com.example.pixcase.data.db.entity.TagEntity
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.mediastore.baseNameOf
import com.example.pixcase.data.mediastore.buildDisplayName
import com.example.pixcase.data.prefs.UserPreferencesDataSource
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.TagsRepository
import com.example.pixcase.data.repository.autoTagColor
import com.example.pixcase.data.repository.normalizeTagName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 时间线 ViewModel。
 *
 * 持有三类状态:
 * - permissionState:权限引导 UI 的状态机(Checking / Granted / NeedsRequest / PermanentlyDenied);
 * - timelineFlow:PhotoRepository.images() 转成 TimelineItem 并插入日期表头后在 viewModelScope 内 cachedIn;
 * - selectedIds:多选模式的选中集合(非空即处于多选模式),及基于它派生的操作事件。
 *
 * 权限状态机约定(简化版,后续引入 Activity 上下文后再细化):
 * - 初始检查(missing 列表非空)默认走 NeedsRequest;
 * - onRequestResult 回调后 [hasRequestedBefore] 置 true,下次 refresh 把状态推到 PermanentlyDenied
 *   (因为系统不再弹窗 → UI 引导用户去设置);
 * - Android 的 shouldShowRequestPermissionRationale 是 Activity-only API,
 *   本 ViewModel 持有 ApplicationContext 不应访问;后续在 Composable 层拿到 Activity 后
 *   再做更精确的"系统是否仍允许弹窗"判定,这里用 session 级 hasRequestedBefore 兜底。
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: PhotoRepository,
    private val albumsRepository: AlbumsRepository,
    private val tagsRepository: TagsRepository,
    private val userPreferences: UserPreferencesDataSource
) : ViewModel() {

    /**
     * 时间线数据流:MediaStore 分页 → 列表项类型统一 → 按日期插入表头。
     *
     * 先把每张照片 map 成 [TimelineItem.Photo] 再 insertSeparators,而不是让 MediaPhoto
     * 直接充当列表项 —— 后者要求数据模型实现 UI 层的 sealed 接口,数据层会反向依赖 UI。
     *
     * 时区在每次 PagingData 发射时取一次,而不是每个相邻对都调 systemDefault()。
     */
    val timelineFlow: Flow<PagingData<TimelineItem>> = repository.images()
        .map { paging ->
            val zone = ZoneId.systemDefault()
            paging
                .map { TimelineItem.Photo(it) }
                .insertSeparators<TimelineItem.Photo, TimelineItem> { before, after ->
                    headerBetween(before?.photo, after?.photo, zone)
                }
        }
        .cachedIn(viewModelScope)

    private val _permissionState = MutableStateFlow<PermissionUiState>(PermissionUiState.Checking)
    val permissionState: StateFlow<PermissionUiState> = _permissionState.asStateFlow()

    /** 本次进程生命周期内是否已经触发过权限请求;true 后再 refresh 就把状态推到 PermanentlyDenied。 */
    private var hasRequestedBefore: Boolean = false

    // ---- 多选模式 ----

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 选中集合;空集 = 不在多选模式。 */
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // ---- 网格列数(捏合变列数) ----

    private val _columnCount = MutableStateFlow(
        userPreferences.gridColumns().coerceIn(MIN_COLUMNS, MAX_ABSOLUTE_COLUMNS)
    )

    /**
     * 时间线网格列数,用户偏好:切 tab / 转屏 / 进程重启都不丢(SharedPreferences 持久化)。
     * 这里只夹绝对边界;按屏幕宽度反推的上限由 UI 层在调用 [setColumnCount] 前夹取。
     */
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    /** 捏合改变列数时调用;值不变时不写偏好,避免捏合过程反复落盘。 */
    fun setColumnCount(count: Int) {
        val clamped = count.coerceIn(MIN_COLUMNS, MAX_ABSOLUTE_COLUMNS)
        if (clamped == _columnCount.value) return
        _columnCount.value = clamped
        userPreferences.setGridColumns(clamped)
    }

    /**
     * 选中项的收藏覆盖情况:null = 未选中;true = 所选全部已收藏;false = 存在未收藏项。
     * 操作栏的收藏按钮据此在「收藏 / 取消收藏」两种形态间切换。
     */
    val selectionAllFavorited: StateFlow<Boolean?> = _selectedIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(null) else repository.observeFavoritesOf(ids).map { it.containsAll(ids) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _events = Channel<TimelineEvent>(Channel.BUFFERED)

    /** 一次性事件(启动系统 PendingIntent / 确认框 / Snackbar),UI 收集后消费。 */
    val events: Flow<TimelineEvent> = _events.receiveAsFlow()

    /** 长按进入多选并选中该照片。 */
    fun selectPhoto(photoId: Long) {
        _selectedIds.value = _selectedIds.value + photoId
    }

    /** 多选模式下点击:切换选中;选空自动退出多选。 */
    fun togglePhotoSelection(photoId: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (photoId in current) current - photoId else current + photoId
    }

    /** 退出多选。 */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ---- 图像操作(阶段 2) ----

    /**
     * 收藏 / 取消收藏当前选中项:任一未收藏 → 全部收藏;全部已收藏 → 全部取消。
     * Room 先写,MediaStore.IS_FAVORITE 同步(API 30+)以事件交 UI 静默启动。
     */
    fun toggleFavoriteForSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val target = !repository.favoritesOf(ids).containsAll(ids)
            val sender = repository.setFavorite(ids, target)
            _events.send(
                TimelineEvent.Message(
                    if (target) R.string.msg_favorited else R.string.msg_unfavorited,
                    ids.size
                )
            )
            sender?.let { _events.send(TimelineEvent.LaunchFavoriteSync(it)) }
        }
    }

    /** 隐藏选中项(App 内生效),随即退出多选。 */
    fun hideSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.hidePhotos(ids)
            _selectedIds.value = emptySet()
            _events.send(TimelineEvent.Message(R.string.msg_hidden, ids.size))
        }
    }

    /**
     * 删除选中项。API 30+ 直接向系统要删除确认(同意后进系统相册回收站);
     * API 29 及以下没有系统回收站,先发 [TimelineEvent.ConfirmPermanentDelete] 让用户在
     * App 内确认,确认后由 [deleteSelectionConfirmed] 执行永久删除。
     */
    fun deleteSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModelScope.launch {
                when (val outcome = repository.deletePhotos(ids)) {
                    is MediaStorePhotoWriter.DeleteOutcome.NeedsConsent ->
                        _events.send(TimelineEvent.LaunchDeleteConsent(outcome.sender))
                    is MediaStorePhotoWriter.DeleteOutcome.Completed ->
                        finishDirectDelete(outcome.deletedCount)
                }
            }
        } else {
            viewModelScope.launch { _events.send(TimelineEvent.ConfirmPermanentDelete) }
        }
    }

    /** App 内确认框(API 29 及以下)点「删除」后调用:永久删除。 */
    fun deleteSelectionConfirmed() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val outcome = repository.deletePhotos(ids)
            if (outcome is MediaStorePhotoWriter.DeleteOutcome.Completed) {
                finishDirectDelete(outcome.deletedCount)
            }
        }
    }

    /** 系统确认框(API 30+)用户同意后调用:清理本地覆盖层并退出多选。 */
    fun onSystemDeleteApproved() {
        // 系统对话框期间 UI 被挡住,选择集不会再变,直接以当前选中集合为准。
        val ids = _selectedIds.value
        viewModelScope.launch {
            repository.onSystemDeleteApproved(ids)
            _selectedIds.value = emptySet()
            if (ids.isNotEmpty()) _events.send(TimelineEvent.Message(R.string.msg_deleted, ids.size))
        }
    }

    private suspend fun finishDirectDelete(deletedCount: Int) {
        _selectedIds.value = emptySet()
        _events.send(
            TimelineEvent.Message(
                if (deletedCount > 0) R.string.msg_deleted else R.string.msg_delete_failed,
                deletedCount
            )
        )
    }

    // ---- 分享 / 壁纸 / 重命名(阶段 2) ----

    /** 重命名等待写授权期间挂起的参数(id 到 新名),授权成功后重试。 */
    private var pendingRename: Pair<Long, String>? = null

    /** 分享选中照片:取齐 Uri 后交 UI 启动系统 chooser(纯只读操作,不改任何状态)。 */
    fun shareSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val uris = repository.photosByIds(ids).map { it.uri }
            if (uris.isNotEmpty()) _events.send(TimelineEvent.SharePhotos(uris))
        }
    }

    /**
     * 设为壁纸:走系统「裁剪并设为壁纸」流程,单张语义 —— 选中数不是 1 时明确提示,
     * 而不是悄悄取第一张。Intent 在 ViewModel 构造(appContext 可用,同 appSettingsIntent 惯例)。
     */
    fun setWallpaperForSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val photos = repository.photosByIds(ids)
            if (photos.size != 1) {
                _events.send(TimelineEvent.Message(R.string.msg_only_one_photo))
                return@launch
            }
            val intent = try {
                WallpaperManager.getInstance(appContext)
                    .getCropAndSetWallpaperIntent(photos.single().uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: IllegalArgumentException) {
                // Uri 不是可授权的 MediaProvider 媒体 Uri(理论上不会发生):降级为失败提示
                null
            }
            _events.send(
                if (intent != null) {
                    TimelineEvent.LaunchSetWallpaper(intent)
                } else {
                    TimelineEvent.Message(R.string.msg_wallpaper_failed)
                }
            )
        }
    }

    /** 点「重命名」:单张语义校验后,取当前文件名拆出的主体名,交 UI 弹输入对话框。 */
    fun requestRename() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val photos = repository.photosByIds(ids)
            if (photos.size != 1) {
                _events.send(TimelineEvent.Message(R.string.msg_only_one_photo))
                return@launch
            }
            _events.send(TimelineEvent.OpenRenameDialog(baseNameOf(photos.single().displayName)))
        }
    }

    /**
     * 执行重命名(UI 侧已用 [isValidDisplayNameBase] 做过行内校验,这里拼回扩展名后写库)。
     * API 30+ 需要写授权时挂起 [pendingRename],等 [onWriteConsentApproved] 重试。
     */
    fun renameSelection(baseName: String) {
        val ids = _selectedIds.value
        if (ids.size != 1) return
        viewModelScope.launch {
            val photo = repository.photosByIds(ids).firstOrNull()
            if (photo == null) {
                _events.send(TimelineEvent.Message(R.string.msg_rename_failed))
                return@launch
            }
            val newName = buildDisplayName(baseName, photo.displayName)
            if (newName == photo.displayName) return@launch
            when (val outcome = repository.renamePhoto(photo.id, newName)) {
                is MediaStorePhotoWriter.RenameOutcome.Done ->
                    finishRename(outcome.renamed)
                is MediaStorePhotoWriter.RenameOutcome.NeedsConsent -> {
                    pendingRename = photo.id to newName
                    _events.send(TimelineEvent.LaunchWriteConsent(outcome.sender))
                }
            }
        }
    }

    /** 写授权同意后重试重命名;授权在本次开机内持续有效,直接更新即可成功。 */
    fun onWriteConsentApproved() {
        val (mediaId, newName) = pendingRename ?: return
        viewModelScope.launch {
            val outcome = repository.renamePhoto(mediaId, newName)
            if (outcome is MediaStorePhotoWriter.RenameOutcome.Done) finishRename(outcome.renamed)
        }
    }

    /** 写授权被拒:丢弃挂起的重命名,保持现场不变。 */
    fun onWriteConsentDenied() {
        pendingRename = null
    }

    /** 打标签对话框的数据:全部标签 + 选中照片的标签交集。 */
    private suspend fun tagDialogData(ids: Set<Long>): Pair<List<TagEntity>, Set<Long>> {
        val tags = tagsRepository.observeAll().first()
        val rows = tagsRepository.rowsForMedia(ids)
        val tagIdsPerPhoto: Map<Long, Set<Long>> = rows.groupBy({ it.mediaId }, { it.tagId })
            .mapValues { (_, tagIds) -> tagIds.toSet() }
        val intersection = ids.asSequence()
            .map { id -> tagIdsPerPhoto[id] ?: emptySet() }
            .fold(null as Set<Long>?) { acc, set -> acc?.intersect(set) ?: set }
            ?.toSet()
            ?: emptySet()
        return tags to intersection
    }

    private suspend fun finishRename(renamed: Boolean) {
        pendingRename = null
        _selectedIds.value = emptySet()
        _events.send(
            TimelineEvent.Message(if (renamed) R.string.msg_renamed else R.string.msg_rename_failed)
        )
    }

    // ---- 打标签(阶段 3) ----

    /** 对话框打开时刻的「全部选中都带着」的标签交集;确认时与此做差量。 */
    private var checkedTagIdsAtDialogOpen: Set<Long> = emptySet()

    /**
     * 点「添加标签」:取全部标签与选中照片的标签关联,算出交集后交 UI 弹对话框。
     * 勾选框初始只勾交集 —— 对话框里的勾选语义是「该标签同时打在所有选中照片上」。
     */
    fun requestTagSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val (tags, intersection) = tagDialogData(ids)
            checkedTagIdsAtDialogOpen = intersection
            _events.send(TimelineEvent.OpenTagDialog(tags, intersection))
        }
    }

    /**
     * 对话框确认:按勾选差量批量打 / 摘标签。UI 侧保证 finalCheckedIds 与打开时
     * 的交集类型一致,这里只做集合差。
     */
    // ---- 加入相册(阶段 3) ----

    /** 点「加入相册」:取全部自定义相册交 UI 弹选择对话框(可为空,对话框内支持新建)。 */
    fun requestAddToAlbum() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val albums = albumsRepository.observeUserAlbums().first()
            _events.send(TimelineEvent.OpenAlbumPicker(albums))
        }
    }

    /** 加入选中相册后退出多选并提示;相册无封面时由仓库自动补第一张。 */
    fun addSelectionToAlbum(albumId: Long) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            albumsRepository.addPhotos(albumId, ids, System.currentTimeMillis())
            _selectedIds.value = emptySet()
            _events.send(TimelineEvent.Message(R.string.msg_added_to_album, ids.size))
        }
    }

    /** 对话框内新建相册;名字非法/重名由仓库层拦截并提示,仍重开选择器让用户改名重试。 */
    fun createAlbumInDialog(rawName: String) {
        viewModelScope.launch {
            val newId = albumsRepository.createAlbum(rawName, System.currentTimeMillis())
            if (newId == null) {
                _events.send(TimelineEvent.Message(R.string.msg_album_name_invalid_or_taken))
            }
            _events.send(TimelineEvent.OpenAlbumPicker(albumsRepository.observeUserAlbums().first()))
        }
    }

    fun applyTags(finalCheckedIds: Set<Long>) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        val added = finalCheckedIds - checkedTagIdsAtDialogOpen
        val removed = checkedTagIdsAtDialogOpen - finalCheckedIds
        if (added.isEmpty() && removed.isEmpty()) return
        viewModelScope.launch {
            added.forEach { tagId -> tagsRepository.applyToMedia(tagId, ids) }
            removed.forEach { tagId -> tagsRepository.removeFromMedia(tagId, ids) }
            _selectedIds.value = emptySet()
            _events.send(TimelineEvent.Message(R.string.msg_tags_applied))
        }
    }

    /** 对话框内新建标签;成功后重发 OpenTagDialog(对话框内容原地刷新,不打断输入)。 */
    fun createTagInDialog(rawName: String) {
        viewModelScope.launch {
            val newId = tagsRepository.create(rawName, autoTagColor(rawName.trim()), System.currentTimeMillis())
            when {
                newId == null && normalizeTagName(rawName) == null ->
                    _events.send(TimelineEvent.Message(R.string.msg_tag_invalid))
                newId == null -> _events.send(TimelineEvent.Message(R.string.msg_tag_duplicate))
                else -> {
                    val (tags, intersection) = tagDialogData(_selectedIds.value)
                    checkedTagIdsAtDialogOpen = intersection
                    _events.send(TimelineEvent.OpenTagDialog(tags, intersection))
                }
            }
        }
    }

    // ---- 权限 ----

    init {
        refreshPermission()
    }

    /** 权限检查。hasRequestedBefore 决定缺失权限时显示 NeedsRequest 还是 PermanentlyDenied。 */
    fun refreshPermission() {
        val missing = currentMissingPermissions()
        _permissionState.value = when {
            missing.isEmpty() -> PermissionUiState.Granted
            hasRequestedBefore -> PermissionUiState.PermanentlyDenied(missing)
            else -> PermissionUiState.NeedsRequest(missing)
        }
    }

    /** 系统权限请求回调后调用;granted map 不直接用,以 checkSelfPermission 当前结果为准。 */
    fun onRequestResult(@Suppress("UNUSED_PARAMETER") granted: Map<String, Boolean>) {
        hasRequestedBefore = true
        refreshPermission()
    }

    private fun currentMissingPermissions(): List<String> = requiredMediaPermissions(Build.VERSION.SDK_INT)
        .filter { ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
