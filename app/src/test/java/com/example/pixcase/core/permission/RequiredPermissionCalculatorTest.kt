package com.example.pixcase.core.permission

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class RequiredPermissionCalculatorTest {
    @Test
    fun `sdk 26 returns legacy storage permission`() {
        assertEquals(
            listOf(RequiredPermissions.READ_EXTERNAL_STORAGE),
            requiredMediaPermissions(Build.VERSION_CODES.O)
        )
    }

    @Test
    fun `sdk 32 returns legacy storage permission`() {
        assertEquals(
            listOf(RequiredPermissions.READ_EXTERNAL_STORAGE),
            requiredMediaPermissions(Build.VERSION_CODES.S_V2)
        )
    }

    @Test
    fun `sdk 33 returns READ_MEDIA_IMAGES only`() {
        assertEquals(
            listOf(RequiredPermissions.READ_MEDIA_IMAGES),
            requiredMediaPermissions(Build.VERSION_CODES.TIRAMISU)
        )
    }

    @Test
    fun `sdk 35 returns READ_MEDIA_IMAGES only`() {
        assertEquals(
            listOf(RequiredPermissions.READ_MEDIA_IMAGES),
            requiredMediaPermissions(35)
        )
    }

    @Test
    fun `result never contains READ_MEDIA_VIDEO in 1_1`() {
        // 1.1 阶段只请求图片权限;视频权限推迟到查看器/相册筛选需要时再加
        (26..35).forEach { sdk ->
            val perms = requiredMediaPermissions(sdk)
            assert(RequiredPermissions.READ_MEDIA_VIDEO !in perms) {
                "SDK $sdk 不应包含 READ_MEDIA_VIDEO"
            }
        }
    }
}
