package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.IOException
import okio.FileSystem
import okio.buffer
import okio.source

/**
 * 「要原图」的显式请求模型。
 *
 * 照片墙直接传 `photo.uri`(android.net.Uri)会命中 [MediaStoreThumbnailFetcherFactory] 走系统缩略图;
 * 查看器要的是全分辨率原图,而两者请求的 URI 完全相同,只能靠 model 类型区分 ——
 * Coil 按 `type.isInstance(data)` 选工厂,[OriginalImage] 与 `Uri` 互不匹配,
 * 所以照片墙完全不受影响,缩略图那条路径也不必加任何判断。
 *
 * 不用「按请求尺寸阈值区分原图与缩略图」:阈值取决于屏幕密度与 size 解析时机,
 * 同一个请求在不同设备或布局下会时对时错。
 */
data class OriginalImage(val uri: Uri)

/**
 * 原图的 memory cache key。
 *
 * Coil 在没有任何 Keyer 命中时会让 key 为 null,而 key 为 null 的请求**不进内存缓存**
 * (见 MemoryCacheService.newCacheKey)—— 缺了它,原图每次显示都会重新读盘解码。
 * 前缀用于和缩略图那条路径(按 Uri 字符串缓存)显式区分。
 */
internal class OriginalImageKeyer : Keyer<OriginalImage> {
    override fun key(data: OriginalImage, options: Options): String = "$KEY_PREFIX${data.uri}"

    private companion object {
        const val KEY_PREFIX = "original:"
    }
}

/**
 * 读取 MediaStore 原图字节流。
 *
 * 返回 [SourceFetchResult] 而不是解码好的 Bitmap:交给 Coil 按目标尺寸降采样,
 * 避免整张原图解码进内存。
 *
 * 这条路径**不参与 Coil 的磁盘缓存** —— 只有带 diskCacheKey 的 `FileImageSource`
 * 才会写盘,流式 source 不会(见 Coil 的 EngineInterceptor)。原图靠内存缓存复用,
 * 进程重启后需重新读盘解码。要落盘得由 Fetcher 自己往 DiskCache 里写。
 */
internal class OriginalImageFetcher(
    private val resolver: ContentResolver,
    private val fileSystem: FileSystem,
    private val uri: Uri
) : Fetcher {

    // 打不开(文件已删 / 无权限 / 介质不可用)不算错误,返回 null 让 Coil 落下一级 Fetcher。
    @Suppress("SwallowedException")
    override suspend fun fetch(): FetchResult? {
        val stream = try {
            resolver.openInputStream(uri)
        } catch (e: IOException) {
            null
        } ?: return null

        return SourceFetchResult(
            source = ImageSource(source = stream.source().buffer(), fileSystem = fileSystem),
            mimeType = resolver.getType(uri),
            dataSource = DataSource.DISK
        )
    }
}

/** 只认 [OriginalImage];传普通 `Uri` 的请求(照片墙)不会命中这里。 */
internal class OriginalImageFetcherFactory : Fetcher.Factory<OriginalImage> {
    override fun create(data: OriginalImage, options: Options, imageLoader: ImageLoader): Fetcher =
        OriginalImageFetcher(
            resolver = options.context.contentResolver,
            fileSystem = options.fileSystem,
            uri = data.uri
        )
}
