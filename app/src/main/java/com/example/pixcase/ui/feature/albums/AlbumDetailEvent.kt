package com.example.pixcase.ui.feature.albums

import androidx.annotation.StringRes

/**
 * 相册详情页的一次性事件,由 ViewModel 发出、UI 收集后用 Snackbar 消费。
 * 详情页暂无需要 Activity 上下文的操作,只有结果反馈一类。
 */
sealed interface AlbumDetailEvent {

    /** 操作结果反馈,UI 用 Snackbar 展示。 */
    data class Message(@StringRes val resId: Int, val count: Int = 0) : AlbumDetailEvent
}
