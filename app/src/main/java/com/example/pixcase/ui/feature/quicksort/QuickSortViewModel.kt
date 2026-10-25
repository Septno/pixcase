package com.example.pixcase.ui.feature.quicksort

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.QuickSortRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    albumsRepository: AlbumsRepository
) : ViewModel() {

    val albumId: Long = checkNotNull(savedStateHandle[ALBUM_ID_KEY])

    val albumName: StateFlow<String> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.firstOrNull { it.id == albumId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    /** 配置对话框的目标候选,排除被整理的相册自身。 */
    val candidateAlbums: StateFlow<List<AlbumEntity>> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.filterNot { it.id == albumId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val session: StateFlow<QuickSortSessionEntity?> = quickSortRepository.observeSession(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _stage = MutableStateFlow<QuickSortStage>(QuickSortStage.Loading)
    val stage: StateFlow<QuickSortStage> = _stage

    init {
        viewModelScope.launch {
            val existing = quickSortRepository.session(albumId)
            _stage.value = when {
                existing == null || existing.isCompleted -> QuickSortStage.NeedsConfig
                else -> QuickSortStage.NeedsChoice
            }
        }
    }

    /** 继续上次:session、目标与已决策原样保留。 */
    fun continueSession() {
        _stage.value = QuickSortStage.Active
    }

    /** 从头开始:只重置进度(decisions 清空、计数归零),不动照片关联。 */
    fun restartFromBeginning() {
        viewModelScope.launch {
            quickSortRepository.resetSession(albumId, System.currentTimeMillis())
            _stage.value = QuickSortStage.Active
        }
    }

    /** 新建 session(配置对话框确认);重建即干净开局(仓库清旧 decisions)。 */
    fun startSession(targetAlbumIds: List<Long>) {
        viewModelScope.launch {
            quickSortRepository.createSession(albumId, targetAlbumIds, System.currentTimeMillis())
            _stage.value = QuickSortStage.Active
        }
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
