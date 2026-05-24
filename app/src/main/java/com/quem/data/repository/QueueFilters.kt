package com.quem.data.repository

import com.quem.core.model.QueueStatus

object QueueFilters {
    val archiveStatuses = listOf(QueueStatus.DONE, QueueStatus.DISMISSED)
}

object QueueSearch {
    fun escapeLikeQuery(query: String): String =
        buildString {
            query.forEach { char ->
                when (char) {
                    '\\', '%', '_' -> append('\\')
                }
                append(char)
            }
        }
}
