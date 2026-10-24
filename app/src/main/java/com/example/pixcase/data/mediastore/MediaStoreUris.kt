package com.example.pixcase.data.mediastore

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** mediaId 转图片 content Uri;UI 层给封面/缩略图构造 Coil model 用。 */
fun imageContentUri(mediaId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
