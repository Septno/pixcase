package com.example.pixcase.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pixcase.data.db.AppDatabase
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QuickSortRepository 对真实内存 Room 的行为覆盖(Robolectric,事务语义真实执行):
 * session 创建与目标顺序、pending 过滤已决策、KEEP/SKIP 推进计数与完成态、
 * MOVE 两侧相册关联变更、从头开始只清决策不动照片、重选目标不动决策。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickSortRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var albumsRepository: AlbumsRepository
    private lateinit var repository: QuickSortRepository
    private val photoWriter = mockk<MediaStorePhotoWriter>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumsRepository = AlbumsRepository(db.albumDao(), db.albumPhotoDao())
        val photoRepository = PhotoRepository(
            dataSource = mockk<MediaStoreDataSource>(relaxed = true),
            writer = photoWriter,
            favoriteDao = db.favoriteDao(),
            hiddenDao = db.hiddenDao(),
            albumPhotoDao = db.albumPhotoDao(),
            photoTagDao = db.photoTagDao()
        )
        repository = QuickSortRepository(
            appDatabase = db,
            sessionDao = db.quickSortSessionDao(),
            targetDao = db.quickSortSessionTargetDao(),
            decisionDao = db.quickSortDecisionDao(),
            albumPhotoDao = db.albumPhotoDao(),
            albumsRepository = albumsRepository,
            photoRepository = photoRepository
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    /** 三个自定义相册:1 = Source(被整理),2 / 3 = 目标(创建顺序保证 id 分配)。 */
    private suspend fun seedAlbums() {
        albumsRepository.createAlbum("Source", 0L)
        albumsRepository.createAlbum("TargetA", 0L)
        albumsRepository.createAlbum("TargetB", 0L)
    }

    /** 往源相册放 N 张照片。 */
    private suspend fun seedSourcePhotos(count: Int) {
        albumsRepository.addPhotos(1L, (1L..count).toList(), 0L)
    }

    @Test
    fun `createSession stores targets in order and counts album photos`() = runTest {
        seedAlbums()
        seedSourcePhotos(4)

        repository.createSession(albumId = 1L, targetAlbumIds = listOf(2L, 3L), now = 100L)

        val session = repository.session(1L)!!
        assertEquals(4, session.totalCount)
        assertEquals(0, session.processedCount)
        assertFalse(session.isCompleted)
        val targets = db.quickSortSessionTargetDao().observeForSession(1L)
        assertEquals(listOf(2L, 3L), targets.first().map { it.targetAlbumId })
        assertEquals(listOf(0, 1), targets.first().map { it.position })
    }

    @Test
    fun `pendingMediaIds excludes already decided photos in album order`() = runTest {
        seedAlbums()
        seedSourcePhotos(3)
        repository.createSession(1L, listOf(2L), 0L)

        repository.recordKeep(1L, 2L, 10L)
        repository.recordSkip(1L, 3L, 11L)

        assertEquals(listOf(1L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `keep and skip bump progress and completion flag at total`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)

        repository.recordKeep(1L, 1L, 10L)
        assertFalse(repository.session(1L)!!.isCompleted)

        repository.recordSkip(1L, 2L, 11L)
        val session = repository.session(1L)!!
        assertEquals(2, session.processedCount)
        assertTrue(session.isCompleted)
    }

    @Test
    fun `move records decision and swaps album associations`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)

        repository.recordMove(albumId = 1L, mediaId = 1L, targetAlbumId = 2L, now = 10L)

        assertEquals(listOf(2L), repository.pendingMediaIds(1L))
        assertEquals(setOf(2L), db.albumPhotoDao().albumIdsOf(1L).toSet())
        // 目标相册收到了照片;封面顺延随仓库规则
        val targetPhoto = db.albumPhotoDao().observeMediaIdsByAlbum(2L).first()
        assertEquals(listOf(1L), targetPhoto)
        assertEquals(1, repository.session(1L)!!.processedCount)
    }

    @Test
    fun `resetSession clears decisions and progress but keeps photo links`() = runTest {
        seedAlbums()
        seedSourcePhotos(3)
        repository.createSession(1L, listOf(2L), 0L)
        repository.recordKeep(1L, 1L, 10L)

        repository.resetSession(1L, now = 20L)

        val session = repository.session(1L)!!
        assertEquals(0, session.processedCount)
        assertFalse(session.isCompleted)
        // 照片关联没动,已决策全部回到待处理
        assertEquals(listOf(1L, 2L, 3L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `updateTargets rewrites order without touching decisions`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)
        repository.recordKeep(1L, 1L, 10L)

        repository.updateTargets(1L, listOf(3L, 2L))

        val targets = db.quickSortSessionTargetDao().observeForSession(1L).first()
        assertEquals(listOf(3L, 2L), targets.map { it.targetAlbumId })
        assertEquals(1, repository.session(1L)!!.processedCount)
        assertEquals(listOf(2L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `savePosition persists current index`() = runTest {
        seedAlbums()
        seedSourcePhotos(5)
        repository.createSession(1L, listOf(2L), 0L)

        repository.savePosition(1L, currentIndex = 3, now = 10L)

        assertEquals(3, repository.session(1L)!!.currentIndex)
    }

    @Test
    fun `createSession clears stale decisions for a clean start`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)
        repository.recordKeep(1L, 1L, 10L)
        repository.recordSkip(1L, 2L, 11L)
        assertTrue(repository.session(1L)!!.isCompleted)

        // 完成后重开新一轮:createSession 必须清掉旧 decisions,
        // 否则已决策照片仍在,新一轮起步即完成态。
        repository.createSession(1L, listOf(2L), now = 20L)

        val session = repository.session(1L)!!
        assertEquals(0, session.processedCount)
        assertEquals(2, session.totalCount)
        assertEquals(listOf(1L, 2L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `trash with photo in multiple albums only unbinds and is undoable`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)
        // 照片 1 同时加入相册 2 → 所属相册数 > 1,走仅解绑分支
        albumsRepository.addPhotos(2L, listOf(1L), 5L)

        val outcome = repository.trashFromAlbum(albumId = 1L, mediaId = 1L, now = 10L)

        assertEquals(TrashOutcome.Unbound(1L), outcome)
        assertEquals(setOf(2L), db.albumPhotoDao().albumIdsOf(1L).toSet())
        assertEquals(1, repository.session(1L)!!.processedCount)
        assertEquals(listOf(2L), repository.pendingMediaIds(1L))

        // 撤销:关联恢复、决策删除、进度回退,照片回到待处理
        repository.undoUnbind(albumId = 1L, mediaId = 1L, now = 20L)
        assertEquals(setOf(1L, 2L), db.albumPhotoDao().albumIdsOf(1L).toSet())
        assertEquals(0, repository.session(1L)!!.processedCount)
        assertEquals(listOf(1L, 2L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `trash with photo in single album performs real delete and records decision`() = runTest {
        seedAlbums()
        seedSourcePhotos(2)
        repository.createSession(1L, listOf(2L), 0L)
        coEvery { photoWriter.deleteImages(listOf(1L)) } returns
            MediaStorePhotoWriter.DeleteOutcome.Completed(deletedCount = 1, deletedIds = listOf(1L))

        val outcome = repository.trashFromAlbum(albumId = 1L, mediaId = 1L, now = 10L)

        // Robolectric sdk = 34 → 30+,非永久删除(进系统回收站语义)
        assertEquals(TrashOutcome.Deleted(permanent = false), outcome)
        assertEquals(1, repository.session(1L)!!.processedCount)
        // deletePhotos Completed 分支已清照片 1 的全部 Room 关联(不挂任何相册)
        assertEquals(emptySet<Long>(), db.albumPhotoDao().albumIdsOf(1L).toSet())
        assertEquals(listOf(2L), repository.pendingMediaIds(1L))
    }

    @Test
    fun `trash consent pending keeps Room untouched until approval`() = runTest {
        seedAlbums()
        seedSourcePhotos(1)
        repository.createSession(1L, listOf(2L), 0L)
        val sender = mockk<android.content.IntentSender>()
        coEvery { photoWriter.deleteImages(listOf(1L)) } returns
            MediaStorePhotoWriter.DeleteOutcome.NeedsConsent(sender)

        val outcome = repository.trashFromAlbum(albumId = 1L, mediaId = 1L, now = 10L)

        assertEquals(TrashOutcome.NeedsConsent(sender), outcome)
        // 确认在途:决策与进度都不动,照片仍在队列
        assertEquals(0, repository.session(1L)!!.processedCount)
        assertEquals(listOf(1L), repository.pendingMediaIds(1L))

        // 同意后 finalize 补决策 + 进度
        repository.finalizeConsentedTrash(albumId = 1L, mediaId = 1L, now = 20L)
        assertEquals(1, repository.session(1L)!!.processedCount)
        assertEquals(emptyList<Long>(), repository.pendingMediaIds(1L))
    }
}
