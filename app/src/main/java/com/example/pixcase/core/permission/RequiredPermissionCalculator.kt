package com.example.pixcase.core.permission

import android.os.Build

/**
 * 按 SDK_INT 计算"时间线浏览"需要的媒体权限列表。
 *
 * 1.1 阶段只请求图片权限(READ_MEDIA_VIDEO 推迟到查看器/相册筛选需要时再加);
 * 计算结果按申请顺序排序,SDK_INT >= 33 返回新权限,否则返回旧权限。
 *
 * 函数纯 (无 Android Context / PackageManager 依赖),便于单元测试覆盖 SDK 边界。
 */
fun requiredMediaPermissions(sdkInt: Int): List<String> = when {
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(RequiredPermissions.READ_MEDIA_IMAGES)
    else -> listOf(RequiredPermissions.READ_EXTERNAL_STORAGE)
}
