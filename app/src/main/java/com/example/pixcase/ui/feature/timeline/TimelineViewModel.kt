package com.example.pixcase.ui.feature.timeline

import android.content.Context
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
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.prefs.UserPreferencesDataSource
import com.example.pixcase.data.repository.PhotoRepository
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
