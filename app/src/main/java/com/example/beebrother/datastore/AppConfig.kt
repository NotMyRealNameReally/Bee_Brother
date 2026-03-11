package com.example.beebrother.datastore

import kotlinx.serialization.Serializable

@Serializable
data class SerializableOffset(val x: Float, val y: Float)

@Serializable
data class AppConfig(
    val url: String = "https://bee-monitoring.duckdns.org/api/v1/photo/upload",
    val apiKey: String = "",
    val hiveId: String = "",
    val delay: Int = 10,
    val shouldSaveLocally: Boolean = false,
    val shouldUpload: Boolean = true,
    val cropDrawPoints: List<SerializableOffset> = emptyList(),
    val cropPoints: List<SerializableOffset> = emptyList()
)