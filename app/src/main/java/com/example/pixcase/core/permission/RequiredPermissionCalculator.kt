package com.example.pixcase.core.permission

import android.os.Build

/**
 * 按 SDK_INT 计算"时间线浏览 + 图像操作"需要的媒体权限列表。
 *
 * - 读取:33+ 用 READ_MEDIA_IMAGES,32 及以下用 READ_EXTERNAL_STORAGE;
 * - 写入:删除 / 收藏同步在 29 及以下直接走 ContentResolver,需要 WRITE_EXTERNAL_STORAGE
 *   (29 配合 manifest 的 legacy storage 标志;26-28 是经典存储);
 *   30+ 的删除/收藏走 createDeleteRequest / createFavoriteRequest,只需读权限,不请求 WRITE。
 *
 * READ_MEDIA_VIDEO 推迟到时间线混合显示或查看器需要时再加。
 * 函数纯 (无 Android Context / PackageManager 依赖),便于单元测试覆盖 SDK 边界。
 */
fun requiredMediaPermissions(sdkInt: Int): List<String> = buildList {
    add(
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            RequiredPermissions.READ_MEDIA_IMAGES
        } else {
            RequiredPermissions.READ_EXTERNAL_STORAGE
        }
    )
    if (sdkInt <= Build.VERSION_CODES.Q) {
        add(RequiredPermissions.WRITE_EXTERNAL_STORAGE)
    }
}
