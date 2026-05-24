package com.quem.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class SyncSchedulerTest {
    @Test
    fun periodicSyncIntervalIsFifteenMinutes() {
        assertEquals(Duration.ofMinutes(15), SyncScheduler.periodicInterval)
    }
}
