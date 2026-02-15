package com.example.beebrother.presets

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

// --- Define the Serializer for our PresetList object ---
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

// --- Create the DataStore instance via a property delegate ---
private val Context.presetDataStore by dataStore(
    fileName = "presets.json",
    serializer = PresetListSerializer
)

// --- The Repository Class ---
class PresetRepository(private val context: Context) {

    val presetsFlow = context.presetDataStore.data

    suspend fun savePreset(preset: Preset) {
        context.presetDataStore.updateData { currentPresetList ->
            val updatedList = currentPresetList.presets.toMutableList()
            // Remove existing preset with the same name to avoid duplicates
            updatedList.removeAll { it.presetName == preset.presetName }
            updatedList.add(preset)
            currentPresetList.copy(presets = updatedList)
        }
    }

    suspend fun deletePreset(presetName: String) {
        context.presetDataStore.updateData { currentPresetList ->
            val updatedList = currentPresetList.presets.toMutableList()
            // The 'removeAll' function removes all elements matching the predicate.
            // Since preset names are unique, it will remove exactly one.
            updatedList.removeAll { it.presetName == presetName }
            currentPresetList.copy(presets = updatedList)
        }
    }

    suspend fun getPresets(): List<Preset> {
        return presetsFlow.first().presets
    }
}