package com.example.beebrother.datastore

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val presetName: String,
    val apiUrl: String,
    val apiKey: String,
    val hiveId: String
)

@Serializable
data class PresetList(
    val presets: List<Preset> = emptyList()
)