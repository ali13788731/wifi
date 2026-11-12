package com.example.netspeed

import com.example.netspeed.core.database.SegmentEntity
import com.example.netspeed.core.downloader.SegmentPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentPlannerTest {

    private fun done(s: SegmentEntity) = s.copy(downloadedBytes = s.length, state = "DONE", validated = true)

    @Test fun planCoversFileExactly() {
        for (total in listOf(1L, 1023L, 512 * 1024L, 100_000_000L, 100_000_001L, 5L * 1024 * 1024 * 1024)) {
            val segs = SegmentPlanner.plan(1, total)
            assertEquals(0L, segs.first().startByte)
            assertEquals(total - 1, segs.last().endByte)
            assertEquals(total, segs.sumOf { it.length })
            segs.zipWithNext().forEach { (a, b) -> assertEquals(a.endByte + 1, b.startByte) }
        }
    }

    @Test fun verifyAcceptsCompleteSet() {
        val segs = SegmentPlanner.plan(1, 10_000_000).map(::done)
        assertNull(SegmentPlanner.verify(segs, 10_000_000))
    }

    @Test fun verifyRejectsMissingSegment() {
        val segs = SegmentPlanner.plan(1, 10_000_000).map(::done).toMutableList()
        segs.removeAt(2)
        assertNotNull(SegmentPlanner.verify(segs, 10_000_000))
    }

    @Test fun verifyRejectsShortSegment() {
        val segs = SegmentPlanner.plan(1, 10_000_000).map(::done).toMutableList()
        segs[1] = segs[1].copy(downloadedBytes = segs[1].length - 1)
        assertNotNull(SegmentPlanner.verify(segs, 10_000_000))
    }

    @Test fun verifyRejectsUnvalidatedSegment() {
        val segs = SegmentPlanner.plan(1, 10_000_000).map(::done).toMutableList()
        segs[0] = segs[0].copy(validated = false)
        assertNotNull(SegmentPlanner.verify(segs, 10_000_000))
    }

    @Test fun verifyRejectsWrongTotal() {
        val segs = SegmentPlanner.plan(1, 10_000_000).map(::done)
        assertNotNull(SegmentPlanner.verify(segs, 10_000_001))
    }
}
