package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.pxOrElse
import coil3.toAndroidUri
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** 尺寸无法解析(Size.ORIGINAL)时用的缩略图边长回退值。 */
internal const val DEFAULT_THUMBNAIL_PX = 512

/**
 * 系统缩略图接口是否可用:API 29 起有 [ContentResolver.loadThumbnail],
 * 更低版本只能退到已废弃的 [MediaStore.Images.Thumbnails.getThumbnail]。
 *
 * 独立成函数是为了让 SDK 边界可在无 Robolectric 的 JVM 单测里覆盖;
 * [ChecksSdkIntAtLeast] 让 lint 认得出调用点已完成 API 级别判断。
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q, parameter = 0)
internal fun useNativeThumbnail(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.Q

/**
 * 解析传给系统缩略图接口的边长像素。
 *
 * Compose 下 Coil 由 ConstraintsSizeResolver 给出真实测量像素;尺寸无法解析时
 * ([Dimension.Undefined],即 Size.ORIGINAL)回退到 [fallbackPx] —— 两个系统接口都不接受非正数。
 */
internal fun thumbnailPx(dimension: Dimension, fallbackPx: Int = DEFAULT_THUMBNAIL_PX): Int =
    dimension.pxOrElse { fallbackPx }

/**
 * 用系统缩略图接口取图,不解码原图。
 *
 * 系统为每张媒体维护一份缩略图,命中时是 O(1) 读取,比 openInputStream 解码原图快一个数量级
 * —— 这是照片墙滚动不掉帧的前提。返回 [ImageFetchResult] 而非 SourceFetchResult:
 * 系统给的已经是解码好的 Bitmap,包成 Source 会让 Coil 再解码一遍。
 *
 * fetch() 返回 null 时 Coil 会继续尝试下一级 Fetcher(ContentUriFetcher 走原图),
 * 因此系统侧失败在这里按「降级」处理而非报错。
 */
internal class MediaStoreThumbnailFetcher(
    private val resolver: ContentResolver,
    private val mediaUri: Uri,
    private val widthPx: Int,
    private val heightPx: Int
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val signal = CancellationSignal()
        // 请求被取消(滑出屏幕 / 列表失效)时中断系统侧解码。
        currentCoroutineContext().job.invokeOnCompletion { signal.cancel() }

        val bitmap = loadThumbnailOrNull(signal) ?: return null
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    // 系统侧取不到缩略图(文件已删 / 损坏 / 无缩略图)不是错误,交给下一级 Fetcher 兜底。
    @Suppress("SwallowedException")
    private fun loadThumbnailOrNull(signal: CancellationSignal): Bitmap? = try {
        if (useNativeThumbnail(Build.VERSION.SDK_INT)) {
            loadNativeThumbnail(signal)
        } else {
            loadLegacyThumbnail()
        }
    } catch (e: IOException) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadNativeThumbnail(signal: CancellationSignal): Bitmap? =
        resolver.loadThumbnail(mediaUri.toAndroidUri(), Size(widthPx, heightPx), signal)

    // API 29 起废弃,但 minSdk 26 仍然需要它兜底。
    // Kotlin 编译期 id 是 DEPRECATION、Android lint 的是 Deprecation,两个都要压。
    @Suppress("DEPRECATION", "Deprecation")
    private fun loadLegacyThumbnail(): Bitmap? = MediaStore.Images.Thumbnails.getThumbnail(
        resolver,
        ContentUris.parseId(mediaUri.toAndroidUri()),
        MediaStore.Images.Thumbnails.MINI_KIND,
        null
    )
}

/**
 * 为 MediaStore 图片注册 [MediaStoreThumbnailFetcher]。
 *
 * 只认 `content://media/...` 的图片 Uri;其余 content Uri 返回 null 让 Coil 走默认 Fetcher。
 * 注册进 ImageLoader 后,对 `content://media/external/images/media/<id>` 的请求会命中这里,
 * 从而绕开 ContentUriFetcher 的原图解码。
 */
internal class MediaStoreThumbnailFetcherFactory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val isMediaStoreUri = data.scheme == ContentResolver.SCHEME_CONTENT &&
            data.authority == MediaStore.AUTHORITY
        if (!isMediaStoreUri) return null
        return MediaStoreThumbnailFetcher(
            resolver = options.context.contentResolver,
            mediaUri = data,
            widthPx = thumbnailPx(options.size.width),
            heightPx = thumbnailPx(options.size.height)
        )
    }
}
