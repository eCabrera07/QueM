package com.quem.data.sync

import kotlinx.serialization.json.Json

object MetadataSerializer {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: MetadataSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String): MetadataSnapshot = json.decodeFromString(value)
}
