package com.example.pixcase.ui.feature.timeline

import android.content.IntentSender
import androidx.annotation.StringRes

/**
 * 时间线的一次性事件,由 ViewModel 发出、UI 收集后消费。
 *
 * 需要 Activity 上下文的操作(启动 PendingIntent)与需要用户确认的对话框都建模为事件:
 * ViewModel 不持有 Activity,UI 侧用 rememberLauncherForActivityResult 接住。
 */
sealed interface TimelineEvent {

    /** API 30+ 删除:启动系统确认框;用户同意后回调 viewModel.onSystemDeleteApproved()。 */
    data class LaunchDeleteConsent(val sender: IntentSender) : TimelineEvent

    /** API 29 及以下删除:App 内确认框(没有系统回收站,删除不可撤销);数量由 UI 取当前选中集渲染。 */
    data object ConfirmPermanentDelete : TimelineEvent

    /** API 30+ 收藏同步:静默启动,结果不影响 Room 已写入的收藏状态。 */
    data class LaunchFavoriteSync(val sender: IntentSender) : TimelineEvent

    /** 操作结果反馈,UI 用 Snackbar 展示。 */
    data class Message(@StringRes val resId: Int, val count: Int = 0) : TimelineEvent
}
