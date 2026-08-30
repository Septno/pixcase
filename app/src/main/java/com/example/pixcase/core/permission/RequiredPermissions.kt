package com.example.pixcase.core.permission

/**
 * 本阶段请求的媒体权限常量集合。
 *
 * 字符串值与 AndroidManifest 声明一一对应;不要新增未在 manifest 声明的权限。
 */
object RequiredPermissions {
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
}
