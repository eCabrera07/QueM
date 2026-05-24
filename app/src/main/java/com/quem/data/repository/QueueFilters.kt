package com.quem.data.repository

import com.quem.core.model.QueueStatus

object QueueFilters {
    val archiveStatuses = listOf(QueueStatus.DONE, QueueStatus.DISMISSED)
}
