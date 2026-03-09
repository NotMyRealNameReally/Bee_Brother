package com.example.beebrother

import android.app.Application
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beebrother.presets.Preset
import com.example.beebrother.presets.PresetList
import com.example.beebrother.presets.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val presetRepository = PresetRepository(application)
    val uploadHistory = MutableStateFlow<List<UploadLog>>(emptyList())
    var isStarted by mutableStateOf(false)

    var delay by mutableIntStateOf(5)

    var url by mutableStateOf("")

    var apiKey by mutableStateOf("")

    var hiveId by mutableStateOf("")

    var shouldSaveLocally by mutableStateOf(false)

    var shouldUpload by mutableStateOf(true)

    var coordinateTransformer = MutableCoordinateTransformer()

    var cropDrawPoints = mutableStateListOf<Offset>()

    var cropPoints = mutableStateListOf<Offset>()

    val presets = presetRepository.presetsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PresetList()
    )

    fun applyPreset(preset: Preset) {
        url = preset.apiUrl
        apiKey = preset.apiKey
        hiveId = preset.hiveId
    }

    fun saveCurrentAsPreset(presetName: String) {
        // Don't save if the name is blank
        if (presetName.isNotBlank()) {
            viewModelScope.launch {
                val newPreset = Preset(
                    presetName = presetName,
                    apiUrl = url,
                    apiKey = apiKey,
                    hiveId = hiveId
                )
                presetRepository.savePreset(newPreset)
            }
        }
    }

    fun deletePreset(presetName: String) {
        viewModelScope.launch {
            presetRepository.deletePreset(presetName)
        }
    }

    fun addUploadLog(success: Boolean, error: String? = null) {
        val newLog = UploadLog(System.currentTimeMillis(), success, error)
        uploadHistory.value = (listOf(newLog) + uploadHistory.value).take(5)
    }
}