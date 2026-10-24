package com.example.pixcase.core.permission

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class RequiredPermissionCalculatorTest {
    @Test
    fun `sdk 26 needs read and write legacy storage permissions`() {
        assertEquals(
            listOf(RequiredPermissions.READ_EXTERNAL_STORAGE, RequiredPermissions.WRITE_EXTERNAL_STORAGE),
            requiredMediaPermissions(Build.VERSION_CODES.O)
        )
    }

    @Test
    fun `sdk 29 still needs write permission for direct ContentResolver writes`() {
        assertEquals(
            listOf(RequiredPermissions.READ_EXTERNAL_STORAGE, RequiredPermissions.WRITE_EXTERNAL_STORAGE),
            requiredMediaPermissions(Build.VERSION_CODES.Q)
        )
    }

    @Test
    fun `sdk 30 drops write permission - delete and favorite go through createRequest APIs`() {
        assertEquals(
            listOf(RequiredPermissions.READ_EXTERNAL_STORAGE),
            requiredMediaPermissions(Build.VERSION_CODES.R)
        )
    }

    @Test
    fun `sdk 32 returns read legacy storage permission only`() {
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
    fun `result never contains READ_MEDIA_VIDEO`() {
        // 视频权限推迟到时间线混合显示或查看器需要时再加
        (26..35).forEach { sdk ->
            val perms = requiredMediaPermissions(sdk)
            assert(RequiredPermissions.READ_MEDIA_VIDEO !in perms) {
                "SDK $sdk 不应包含 READ_MEDIA_VIDEO"
            }
        }
    }
}
