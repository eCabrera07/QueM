package com.quem.data.repository

import com.quem.core.model.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSearchTest {
    @Test
    fun archiveStatusesAreDoneAndDismissed() {
        assertEquals(listOf(QueueStatus.DONE, QueueStatus.DISMISSED), QueueFilters.archiveStatuses)
    }

    @Test
    fun escapeLikeQueryEscapesWildcardsAndEscapeCharacter() {
        assertEquals("""\%\_\\contract""", QueueSearch.escapeLikeQuery("""%_\contract"""))
    }
}
