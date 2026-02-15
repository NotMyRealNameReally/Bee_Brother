package com.example.beebrother.presets

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val presetName: String,
    val apiUrl: String,
    val apiKey: String,
    val hiveId: String
)

// We also need a wrapper class for the list of presets,
// as DataStore works with top-level objects.
@Serializable
data class PresetList(
    val presets: List<Preset> = emptyList()
)