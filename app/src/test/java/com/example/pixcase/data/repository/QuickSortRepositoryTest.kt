package com.example.pixcase.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pixcase.data.db.AppDatabase
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

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumsRepository = AlbumsRepository(db.albumDao(), db.albumPhotoDao())
        repository = QuickSortRepository(
            appDatabase = db,
            sessionDao = db.quickSortSessionDao(),
            targetDao = db.quickSortSessionTargetDao(),
            decisionDao = db.quickSortDecisionDao(),
            albumPhotoDao = db.albumPhotoDao(),
            albumsRepository = albumsRepository
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
}
