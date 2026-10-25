package com.example.pixcase.ui.feature.quicksort

import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.QuickSortRepository
import com.example.pixcase.data.repository.TrashOutcome
import com.example.pixcase.data.repository.orderByIds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dock 上的一个目标相册条目。 */
data class DockAlbum(val id: Long, val name: String, val coverMediaId: Long?)

/** 快速整理的一次性事件:需要 Activity 上下文的系统确认框、Snackbar 反馈(含可撤销)。 */
sealed interface QuickSortEvent {
    /** API 30+ 真删除:启动系统确认框;同意后回调 ViewModel.onTrashConsentApproved。 */
    data class LaunchTrashConsent(val sender: IntentSender) : QuickSortEvent

    /** 仅解绑分支的撤销条目:Snackbar 带「撤销」按钮,5 秒内可回。 */
    data class UndoableUnbind(val mediaId: Long) : QuickSortEvent

    /** 普通反馈消息。 */
    data class Message(@StringRes val resId: Int) : QuickSortEvent
}

/**
 * 快速整理(阶段 3.5)入口与配置流的阶段状态:
 * 进入时按「是否有未完成 session」分流(计划 § 3.5.3),配置完成后进入 Active。
 * Active 的卡片手势界面属任务 7,本阶段 Active 只承载顶栏与进度。
 */
sealed interface QuickSortStage {
    /** 进入时的一次性 session 检查。 */
    data object Loading : QuickSortStage

    /** 有未完成 session:弹 Sheet 选「继续上次 / 从头开始」。 */
    data object NeedsChoice : QuickSortStage

    /** 无 session(或上次已完成):弹配置对话框新建。 */
    data object NeedsConfig : QuickSortStage

    /** 配置结束,进入整理界面。 */
    data object Active : QuickSortStage
}

/**
 * 快速整理的 ViewModel:session 检查分流、配置(目标相册选择)、进度展示、退出保存。
 *
 * 目标候选 = 全部自定义相册排除当前相册自己(移回自己没有意义);智能相册不在 Room,
 * 天然不会出现。
 */
@HiltViewModel
class QuickSortViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val quickSortRepository: QuickSortRepository,
    albumsRepository: AlbumsRepository,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    val albumId: Long = checkNotNull(savedStateHandle[ALBUM_ID_KEY])

    /** 待处理照片队列(队首 = 当前卡片),决策后本地推进。 */
    private val _pendingPhotos = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val pendingPhotos: StateFlow<List<MediaPhoto>> = _pendingPhotos

    val albumName: StateFlow<String> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.firstOrNull { it.id == albumId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    /** 配置对话框的目标候选,排除被整理的相册自身。 */
    val candidateAlbums: StateFlow<List<AlbumEntity>> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.filterNot { it.id == albumId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val session: StateFlow<QuickSortSessionEntity?> = quickSortRepository.observeSession(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Dock 的目标相册(按 session target 的 position 顺序;相册被删时缺席即跳过)。 */
    val dockAlbums: StateFlow<List<DockAlbum>> = combine(
        quickSortRepository.observeTargets(albumId),
        albumsRepository.observeUserAlbums()
    ) { targets, albums ->
        val byId = albums.associateBy { it.id }
        targets.mapNotNull { target -> byId[target.targetAlbumId]?.let { DockAlbum(it.id, it.name, it.coverMediaId) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _stage = MutableStateFlow<QuickSortStage>(QuickSortStage.Loading)
    val stage: StateFlow<QuickSortStage> = _stage

    private val _events = Channel<QuickSortEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 30+ 系统确认框在途的照片(同意 → finalize,拒绝 → 照片留在队首)。 */
    private var pendingConsentMediaId: Long? = null

    init {
        viewModelScope.launch {
            val existing = quickSortRepository.session(albumId)
            _stage.value = when {
                existing == null || existing.isCompleted -> QuickSortStage.NeedsConfig
                else -> QuickSortStage.NeedsChoice
            }
        }
    }

    /** 继续上次:session、目标与已决策原样保留,重载待处理队列。 */
    fun continueSession() {
        viewModelScope.launch {
            reloadPending()
            _stage.value = QuickSortStage.Active
        }
    }

    /** 从头开始:只重置进度(decisions 清空、计数归零),不动照片关联。 */
    fun restartFromBeginning() {
        viewModelScope.launch {
            quickSortRepository.resetSession(albumId, System.currentTimeMillis())
            reloadPending()
            _stage.value = QuickSortStage.Active
        }
    }

    /** 新建 session(配置对话框确认);重建即干净开局(仓库清旧 decisions)。 */
    fun startSession(targetAlbumIds: List<Long>) {
        viewModelScope.launch {
            quickSortRepository.createSession(albumId, targetAlbumIds, System.currentTimeMillis())
            reloadPending()
            _stage.value = QuickSortStage.Active
        }
    }

    /** ⟲ 重启(二次确认后):重置进度并重载队列,目标相册设置保留。 */
    fun restartSession() {
        viewModelScope.launch {
            quickSortRepository.resetSession(albumId, System.currentTimeMillis())
            reloadPending()
        }
    }

    /** 左划「保留」:决策立即落库(任务 5 事务),队列本地推进。 */
    fun keepCurrent() {
        decide { head -> quickSortRepository.recordKeep(albumId, head.id, System.currentTimeMillis()) }
    }

    /** 兜底 Sheet 里的「跳过」:同样算已处理(计划 § 3.5.3)。 */
    fun skipCurrent() {
        decide { head -> quickSortRepository.recordSkip(albumId, head.id, System.currentTimeMillis()) }
    }

    /** 拖到 Dock:移出当前相册并加入目标相册(decision + 两侧关联同事务,封面顺延随仓库规则)。 */
    fun moveCurrentTo(targetAlbumId: Long) {
        decide { head -> quickSortRepository.recordMove(albumId, head.id, targetAlbumId, System.currentTimeMillis()) }
    }

    /**
     * 上划删除(计划 § 3.5.4 双重语义):按所属相册数在仓库层分支。
     * 仅解绑 → 队列推进 + 撤销条目;真删除完成 → 队列推进 + 结果提示;
     * 30+ 系统确认 → 队列不动(拒绝时照片留在队首),发 LaunchTrashConsent。
     */
    fun trashCurrent() {
        val head = _pendingPhotos.value.firstOrNull() ?: return
        viewModelScope.launch {
            when (val outcome = quickSortRepository.trashFromAlbum(albumId, head.id, System.currentTimeMillis())) {
                is TrashOutcome.Unbound -> {
                    _pendingPhotos.value = _pendingPhotos.value.drop(1)
                    _events.send(QuickSortEvent.UndoableUnbind(outcome.mediaId))
                }
                is TrashOutcome.Deleted -> {
                    _pendingPhotos.value = _pendingPhotos.value.drop(1)
                    val messageRes = if (outcome.permanent) {
                        R.string.quicksort_msg_deleted_permanent
                    } else {
                        R.string.quicksort_msg_deleted_trash
                    }
                    _events.send(QuickSortEvent.Message(messageRes))
                }
                is TrashOutcome.NeedsConsent -> {
                    pendingConsentMediaId = head.id
                    _events.send(QuickSortEvent.LaunchTrashConsent(outcome.sender))
                }
            }
        }
    }

    /** 撤销「仅解绑」删除:恢复关联 + 删决策 + 进度回退,并重载队列(照片回到队首)。 */
    fun undoUnbind(mediaId: Long) {
        viewModelScope.launch {
            quickSortRepository.undoUnbind(albumId, mediaId, System.currentTimeMillis())
            reloadPending()
        }
    }

    /** 30+ 系统确认同意:Room 关联清理 + decision + 进度,队列推进。 */
    fun onTrashConsentApproved() {
        val mediaId = pendingConsentMediaId ?: return
        pendingConsentMediaId = null
        viewModelScope.launch {
            photoRepository.onSystemDeleteApproved(listOf(mediaId))
            quickSortRepository.finalizeConsentedTrash(albumId, mediaId, System.currentTimeMillis())
            _pendingPhotos.value = _pendingPhotos.value.filterNot { it.id == mediaId }
            _events.send(QuickSortEvent.Message(R.string.quicksort_msg_deleted_trash))
        }
    }

    /** 30+ 系统确认拒绝:照片留在队首,仅提示。 */
    fun onTrashConsentDenied() {
        pendingConsentMediaId = null
        viewModelScope.launch { _events.send(QuickSortEvent.Message(R.string.quicksort_msg_trash_cancelled)) }
    }

    private fun decide(record: suspend (MediaPhoto) -> Unit) {
        val head = _pendingPhotos.value.firstOrNull() ?: return
        viewModelScope.launch {
            record(head)
            _pendingPhotos.value = _pendingPhotos.value.drop(1)
        }
    }

    /** 待处理队列 = 相册顺序过滤已决策(仓库),再按需补查照片详情(MOVE/查看用不到尺寸,列表列足够)。 */
    private suspend fun reloadPending() {
        val ids = quickSortRepository.pendingMediaIds(albumId)
        _pendingPhotos.value = orderByIds(ids, photoRepository.photosByIds(ids))
    }

    /** 退出:自动保存进度(计划 § 3.5.1),再回调导航返回。 */
    fun exit(currentIndex: Int, onBack: () -> Unit) {
        viewModelScope.launch {
            quickSortRepository.savePosition(albumId, currentIndex, System.currentTimeMillis())
            onBack()
        }
    }

    companion object {
        const val ALBUM_ID_KEY = "albumId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
