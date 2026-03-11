package com.example.beebrother.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object PresetListSerializer : Serializer<PresetList> {
    override val defaultValue: PresetList = PresetList()

    override suspend fun readFrom(input: InputStream): PresetList {
        try {
            return Json.decodeFromString(
                PresetList.serializer(), input.readBytes().decodeToString()
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: PresetList, output: OutputStream) {
        output.write(
            Json.encodeToString(PresetList.serializer(), t).encodeToByteArray()
        )
    }
}

private val Context.presetDataStore by dataStore(
    fileName = "presets.json",
    serializer = PresetListSerializer
)

class PresetRepository(private val context: Context) {

    val presetsFlow = context.presetDataStore.data

    suspend fun savePreset(preset: Preset) {
        context.presetDataStore.updateData { currentPresetList ->
            val updatedList = currentPresetList.presets.toMutableList()
            updatedList.removeAll { it.presetName == preset.presetName }
            updatedList.add(preset)
            currentPresetList.copy(presets = updatedList)
        }
    }

    suspend fun deletePreset(presetName: String) {
        context.presetDataStore.updateData { currentPresetList ->
            val updatedList = currentPresetList.presets.toMutableList()
            updatedList.removeAll { it.presetName == presetName }
            currentPresetList.copy(presets = updatedList)
        }
    }

    suspend fun getPresets(): List<Preset> {
        return presetsFlow.first().presets
    }
}