package com.example.pixcase.ui.feature.timeline

import android.net.Uri
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineGroupingTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneId.of("UTC")

    private fun photo(id: Long, dateTakenMs: Long? = null, dateAddedSec: Long = 0L): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = dateAddedSec,
        dateTakenMs = dateTakenMs
    )

    private fun millisAt(date: LocalDate, hour: Int, zone: ZoneId): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun secondsAt(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toEpochSecond()

    /** 列表首项的表头日期;before = null 即列表头部的边界情形。 */
    private fun firstHeaderDate(item: MediaPhoto, zone: ZoneId = shanghai): LocalDate? =
        headerBetween(before = null, after = item, zone = zone)?.date

    @Test
    fun `first item always gets a header`() {
        val first = photo(1L, dateTakenMs = millisAt(LocalDate.of(2026, 5, 1), 9, shanghai))

        assertEquals(LocalDate.of(2026, 5, 1), firstHeaderDate(first))
    }

    @Test
    fun `same calendar day produces no header`() {
        val morning = photo(1L, dateTakenMs = millisAt(LocalDate.of(2026, 5, 1), 9, shanghai))
        val evening = photo(2L, dateTakenMs = millisAt(LocalDate.of(2026, 5, 1), 18, shanghai))

        assertNull(headerBetween(morning, evening, shanghai))
    }

    @Test
    fun `different calendar day produces a header`() {
        val newer = photo(1L, dateTakenMs = millisAt(LocalDate.of(2026, 5, 2), 9, shanghai))
        val older = photo(2L, dateTakenMs = millisAt(LocalDate.of(2026, 5, 1), 9, shanghai))

        assertNotNull(headerBetween(newer, older, shanghai))
    }

    @Test
    fun `null after produces no header`() {
        assertNull(headerBetween(photo(1L), after = null, zone = shanghai))
    }

    @Test
    fun `same instant lands on different calendar days across zones`() {
        // 2026-03-15T20:00Z 在上海(UTC+8)已是 2026-03-16 04:00
        val takenMs = Instant.parse("2026-03-15T20:00:00Z").toEpochMilli()
        val item = photo(1L, dateTakenMs = takenMs)

        assertEquals(LocalDate.of(2026, 3, 15), firstHeaderDate(item, utc))
        assertEquals(LocalDate.of(2026, 3, 16), firstHeaderDate(item, shanghai))
    }

    @Test
    fun `falls back to date added when date taken is null`() {
        val item = photo(1L, dateAddedSec = secondsAt(LocalDate.of(2026, 5, 1), shanghai))

        assertEquals(LocalDate.of(2026, 5, 1), firstHeaderDate(item))
    }

    @Test
    fun `treats zero date taken as missing`() {
        // 华为 / 荣耀部分机型把 DATE_TAKEN 写成 0,必须与 NULL 同样回退到入库时间
        val item = photo(1L, dateTakenMs = 0L, dateAddedSec = secondsAt(LocalDate.of(2026, 5, 1), shanghai))

        assertEquals(LocalDate.of(2026, 5, 1), firstHeaderDate(item))
    }

    @Test
    fun `date taken wins over date added`() {
        val item = photo(
            id = 1L,
            dateTakenMs = millisAt(LocalDate.of(2019, 3, 8), 12, shanghai),
            dateAddedSec = secondsAt(LocalDate.of(2026, 5, 1), shanghai)
        )

        assertEquals(LocalDate.of(2019, 3, 8), firstHeaderDate(item))
    }
}
