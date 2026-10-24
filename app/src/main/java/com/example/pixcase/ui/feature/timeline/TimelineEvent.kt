package com.example.pixcase.ui.feature.timeline

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.annotation.StringRes
import com.example.pixcase.data.db.entity.TagEntity

/**
 * 时间线的一次性事件,由 ViewModel 发出、UI 收集后消费。
 *
 * 需要 Activity 上下文的操作(启动 PendingIntent / Intent)与需要用户确认的对话框都建模为事件:
 * ViewModel 不持有 Activity,UI 侧用 rememberLauncherForActivityResult 接住。
 */
sealed interface TimelineEvent {

    /** API 30+ 删除:启动系统确认框;用户同意后回调 viewModel.onSystemDeleteApproved()。 */
    data class LaunchDeleteConsent(val sender: IntentSender) : TimelineEvent

    /** API 30+ 重命名先要写授权(createWriteRequest):同意后回调 viewModel.onWriteConsentApproved()。 */
    data class LaunchWriteConsent(val sender: IntentSender) : TimelineEvent

    /** API 29 及以下删除:App 内确认框(没有系统回收站,删除不可撤销);数量由 UI 取当前选中集渲染。 */
    data object ConfirmPermanentDelete : TimelineEvent

    /** API 30+ 收藏同步:静默启动,结果不影响 Room 已写入的收藏状态。 */
    data class LaunchFavoriteSync(val sender: IntentSender) : TimelineEvent

    /** 分享选中的照片:UI 用 ACTION_SEND_MULTIPLE + 系统 chooser 启动。 */
    data class SharePhotos(val uris: List<Uri>) : TimelineEvent

    /** 设为壁纸:已构造好的系统「裁剪并设为壁纸」Intent,UI 直接 startActivity。 */
    data class LaunchSetWallpaper(val intent: Intent) : TimelineEvent

    /** 重命名对话框就绪:携带当前文件名拆出的主体名(不含扩展名)作为预填值。 */
    data class OpenRenameDialog(val currentBaseName: String) : TimelineEvent

    /**
     * 打标签对话框就绪:[tags] 为全部标签,[checkedTagIds] 是「同时打在所有选中照片上」
     * 的标签交集(勾选框初始态);确认时把对话框的最终勾选集与它做差量。
     */
    data class OpenTagDialog(val tags: List<TagEntity>, val checkedTagIds: Set<Long>) : TimelineEvent

    /** 操作结果反馈,UI 用 Snackbar 展示。 */
    data class Message(@StringRes val resId: Int, val count: Int = 0) : TimelineEvent
}
