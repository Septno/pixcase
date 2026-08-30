package com.example.pixcase.data.mediastore

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MediaPhotoCursorMapperTest {
    // MediaStore 常量在 JVM unit test 环境返回 null,改用 mockk Uri 占位
    // (mapper 只用 contentUri 作为前缀 + id 拼 Uri,不依赖真实 framework Uri 行为)
    private val contentUri: Uri = mockk(relaxed = true)
    private val appendedUri: Uri = mockk(relaxed = true)

    @Before
    fun setup() {
        // ContentUris.withAppendedId 在 JVM unit test 抛 "not mocked",用 mockkStatic 拦截。
        mockkStatic(ContentUris::class)
        every { ContentUris.withAppendedId(any(), any<Long>()) } returns appendedUri
    }

    @After
    fun teardown() {
        unmockkStatic(ContentUris::class)
    }

    private fun cursorWith(
        id: Long = 1L,
        displayName: String? = "IMG_001.jpg",
        dateAddedSec: Long = 1_700_000_000L,
        bucketName: String? = "Camera",
        mimeType: String? = "image/jpeg"
    ): Cursor {
        // mockk DSL 块内 `every { mock.method() }` 的 `mock` 解析在 relaxed 模式下有时不稳定,
        // 显式持有 cursor 引用让 stubbing 走外部 mockk API 更可靠。
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID) } returns 0
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME) } returns 1
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) } returns 2
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME) } returns 3
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE) } returns 4

        every { cursor.getLong(0) } returns id
        every { cursor.getLong(2) } returns dateAddedSec

        every { cursor.isNull(1) } answers { displayName == null }
        every { cursor.getString(1) } answers { displayName ?: "" }
        every { cursor.isNull(3) } answers { bucketName == null }
        every { cursor.getString(3) } answers { bucketName }
        every { cursor.isNull(4) } answers { mimeType == null }
        every { cursor.getString(4) } answers { mimeType ?: "" }
        return cursor
    }

    @Test
    fun `maps 5-column cursor to MediaPhoto`() {
        val cursor = cursorWith(id = 42L, displayName = "IMG_0042.jpg", bucketName = "Camera")

        val photo = cursor.toMediaPhotoOrNull(contentUri)

        assertNotNull(photo)
        photo!!
        assertEquals(42L, photo.id)
        assertEquals(ContentUris.withAppendedId(contentUri, 42L), photo.uri)
        assertEquals("IMG_0042.jpg", photo.displayName)
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals(1_700_000_000L, photo.dateAddedSec)
        assertEquals("Camera", photo.bucketName)
        // 投影列未包含的字段走 MediaPhoto model 默认值
        assertEquals(0, photo.width)
        assertEquals(0, photo.height)
        assertEquals(0L, photo.sizeBytes)
        assertNull(photo.dateTakenMs)
    }

    @Test
    fun `id of zero returns null`() {
        val cursor = cursorWith(id = 0L)

        assertNull(cursor.toMediaPhotoOrNull(contentUri))
    }

    @Test
    fun `null displayName mapped to empty string`() {
        val cursor = cursorWith(displayName = null)

        val photo = cursor.toMediaPhotoOrNull(contentUri)

        assertEquals("", photo!!.displayName)
    }

    @Test
    fun `null mimeType mapped to empty string`() {
        val cursor = cursorWith(mimeType = null)

        val photo = cursor.toMediaPhotoOrNull(contentUri)

        assertEquals("", photo!!.mimeType)
    }

    @Test
    fun `null bucketName stays null`() {
        val cursor = cursorWith(bucketName = null)

        val photo = cursor.toMediaPhotoOrNull(contentUri)

        assertNull(photo!!.bucketName)
    }
}
