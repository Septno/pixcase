package com.example.pixcase.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好持久化。当前只有时间线网格列数;后续阶段的全局设置(排序方式等)也放这里。
 *
 * 单个整型的读写,SharedPreferences 足够 —— 不为此引入 DataStore 依赖(计划 §3.3 反过度设计)。
 * 写走 apply()(异步落盘),读在 ViewModel 构造时发生,文件极小,主线程同步读可接受。
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 时间线网格列数;无记录时返回 [DEFAULT_GRID_COLUMNS]。 */
    fun gridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)

    fun setGridColumns(count: Int) {
        prefs.edit { putInt(KEY_GRID_COLUMNS, count) }
    }

    private companion object {
        const val FILE_NAME = "pixcase_prefs"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val DEFAULT_GRID_COLUMNS = 3
    }
}
