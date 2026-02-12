package com.example.beebrother

import androidx.camera.core.ImageCapture
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel

class ConfigViewModel : ViewModel() {
    var isStarted by mutableStateOf(false)

    var delay by mutableIntStateOf(5)

    var url by mutableStateOf<String?>(null)

    var apiKey by mutableStateOf<String?>(null)

    var hiveId by mutableStateOf<String?>(null)

    var shouldSaveLocally by mutableStateOf(false)

    var shouldUpload by mutableStateOf(true)

    var imageCapture by mutableStateOf<ImageCapture?>(null)

    var coordinateTransformer = MutableCoordinateTransformer()

    var cropDrawPoints = mutableStateListOf<Offset>()

    var cropPoints = mutableStateListOf<Offset>()
}