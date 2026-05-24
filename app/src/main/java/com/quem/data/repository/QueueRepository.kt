package com.quem.data.repository

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun observeItems(status: QueueStatus): Flow<List<QueueItem>>

    fun observeItem(id: String): Flow<QueueItem?>

    suspend fun createItem(title: String, description: String?): QueueItem

    suspend fun changeStatus(id: String, status: QueueStatus): QueueItem?
}
