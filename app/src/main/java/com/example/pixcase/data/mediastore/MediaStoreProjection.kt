package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf

/**
 * MediaStore 投影列定义与查询参数构造。
 *
 * 列表查询 6 列:渲染缩略图网格所需的描述字段,加上排序与分组共用的时间基准列。
 * ORIENTATION / LATITUDE / LONGITUDE / WIDTH / HEIGHT / SIZE 推迟到查看器按需补齐。
 * Video 投影留作后续阶段;当前只实现 images。
 */
internal object MediaStoreProjection {
    val IMAGE_COLUMNS: Array<String> = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE
    )

    /**
     * 时间线排序与分组共用的时间基准:拍摄时间优先,缺失(0 或 NULL)时回退入库时间。
     * DATE_TAKEN 是毫秒、DATE_ADDED 是秒,故回退分支乘 1000 对齐。
     *
     * 排序键必须与分组键同源,否则「最近入库的老照片」会排在列表最前却挂旧日期表头,
     * 同一个月的表头在列表里反复出现。Kotlin 侧对应
     * [com.example.pixcase.data.model.groupingEpochSec],两处语义必须同步修改。
     */
    private const val SORT_EPOCH_MS =
        "CASE WHEN ${MediaStore.MediaColumns.DATE_TAKEN} > 0 " +
            "THEN ${MediaStore.MediaColumns.DATE_TAKEN} " +
            "ELSE ${MediaStore.Images.Media.DATE_ADDED} * 1000 END"

    /**
     * 排序子句,**不含分页**。
     *
     * MediaProvider 会校验这段字符串,只接受列名、表达式与 ASC/DESC;
     * 混进 LIMIT / OFFSET 会抛 IllegalArgumentException("Invalid token LIMIT")。
     */
    val SORT_ORDER: String = "$SORT_EPOCH_MS DESC"

    /**
     * 是否走 ContentResolver 的 Bundle 查询参数分页。
     *
     * API 30 起 MediaProvider 收紧了 sortOrder 校验,LIMIT 不再被接受,分页只能走 Bundle;
     * 30 以下没有 QUERY_ARG_LIMIT,只能把分页拼进 sortOrder。
     *
     * 独立成函数是为了让版本边界可在无 Robolectric 的 JVM 单测里覆盖。
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R, parameter = 0)
    fun supportsQueryArgs(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.R

    /** API 30 以下的 sortOrder(把分页拼进去);loadSize / offset 均为 Int 无注入风险。 */
    fun legacySortOrder(loadSize: Int, offset: Int): String = "$SORT_ORDER LIMIT $loadSize OFFSET $offset"

    /** API 30+ 的查询参数:排序走 SQL 排序子句,分页走 QUERY_ARG_LIMIT / QUERY_ARG_OFFSET。 */
    @RequiresApi(Build.VERSION_CODES.R)
    fun modernQueryArgs(loadSize: Int, offset: Int): Bundle = bundleOf(
        ContentResolver.QUERY_ARG_SQL_SORT_ORDER to SORT_ORDER,
        ContentResolver.QUERY_ARG_LIMIT to loadSize,
        ContentResolver.QUERY_ARG_OFFSET to offset
    )
}
