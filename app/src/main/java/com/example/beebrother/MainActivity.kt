package com.example.beebrother

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beebrother.ui.theme.BeeBrotherTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    var monitoringService by mutableStateOf<MonitoringService?>(null)
        private set
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MonitoringService.LocalBinder
            monitoringService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            monitoringService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Intent(this, MonitoringService::class.java).also { intent ->
            startForegroundService(intent)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        setContent {
            CheckCameraPermissionsAndInit()
            var visibleScreen by remember { mutableStateOf(Screen.MENU) }
            val service = this.monitoringService

            BeeBrotherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (visibleScreen) {
                        Screen.MENU -> MainMenu(service) { screen -> visibleScreen = screen }
                        Screen.PREVIEW -> CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            previewUseCase = service!!.previewUseCase
                        ) { screen ->
                            visibleScreen = screen
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
        }
    }

    fun exitApplication() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }

        val serviceIntent = Intent(this, MonitoringService::class.java)
        stopService(serviceIntent)
        PeriodicCaptureController.stopCapture()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

@Composable
fun CheckCameraPermissionsAndInit() {
    val context = LocalContext.current
    val permissions =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == false) {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { launcher.launch(permissions) }
}

@SuppressLint("ContextCastToActivity")
@Composable
fun MainMenu(service: MonitoringService?, onSelectScreen: (Screen) -> Unit) {
    val context = LocalContext.current
    val activity = (LocalContext.current as? MainActivity)
    val config: ConfigViewModel = viewModel()
    val presetsState = config.presets.collectAsState()

    var showPresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(service) {
        service?.let {
            config.imageCapture = it.imageCaptureUseCase
        }
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    config.saveCurrentAsPreset(presetNameInput)
                    presetNameInput = ""
                    showPresetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { showPresetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            if (!config.isStarted) {
                Button(onClick = { onSelectScreen(Screen.PREVIEW) }) {
                    Text("Open camera preview")
                }
            }
            Button(onClick = {
                if (config.isStarted) {
                    PeriodicCaptureController.stopCapture()
                    config.isStarted = false
                } else {
                    PeriodicCaptureController.startCapture(
                        context,
                        config
                    )
                    config.isStarted = true
                }
            }) {
                if (config.isStarted) {
                    Text("Stop")
                } else {
                    Text("Start")
                }
            }
            ConfigEditFields(
                onDelayChange = { newDelay -> config.delay = newDelay },
                config.delay.toString()
            )
            Button(onClick = { expanded = true }) {
                Text("Load Preset")
            }
            DropdownMenu(
                expanded = expanded,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onDismissRequest = { expanded = false }
            ) {
                presetsState.value.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.presetName) },
                        onClick = {
                            config.applyPreset(preset)
                            expanded = false
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                // Stop the menu from closing when the icon is clicked
                                // and delete the preset.
                                config.deletePreset(preset.presetName)
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Preset",
                                    tint = MaterialTheme.colorScheme.error
                                )

                            }
                        }
                    )
                }
                if (presetsState.value.presets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No presets saved") },
                        onClick = { expanded = false },
                        enabled = false
                    )
                }
            }
            Button(onClick = { showPresetDialog = true }) {
                Text("Save Current as Preset")
            }
            Button(onClick = { activity?.exitApplication() }) {
                Text("Exit Application")
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    previewUseCase: Preview,
    onSelectScreen: (Screen) -> Unit
) {
    val config: ConfigViewModel = viewModel()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }

    // Writer: MutableStateFlow we can update from CameraX callbacks
    val surfaceRequests = remember { MutableStateFlow<SurfaceRequest?>(null) }

    // Reader: Compose state derived from the flow
    val surfaceRequest by surfaceRequests.collectAsState(initial = null)
    LaunchedEffect(Unit) {
        previewUseCase.apply {
            setSurfaceProvider { request ->
                surfaceRequests.value = request
            }
        }
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = config.coordinateTransformer,
                modifier = modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val hit = findPointIndex(
                                config.cropDrawPoints,
                                offset,
                                70f
                            )
                            if (hit == null) {
                                config.cropDrawPoints.add(offset)
                            } else {
                                config.cropDrawPoints.removeAt(hit)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                draggingIndex = findPointIndex(
                                    config.cropDrawPoints,
                                    offset,
                                    70f
                                )
                            },
                            onDrag = { change, dragAmount ->
                                draggingIndex?.let { index ->
                                    config.cropDrawPoints[index] =
                                        config.cropDrawPoints[index] + dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                            },
                            onDragCancel = {
                                draggingIndex = null
                            }
                        )
                    }
            )
        }
        CropOverlay(points = config.cropDrawPoints)
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .align(Alignment.BottomEnd)
        ) {
            Button(
                onClick = { transformAndSaveCrop(config) }
            ) {
                Text("Confirm crop")
            }
            Button(
                onClick = { onSelectScreen(Screen.MENU) }
            ) {
                Text("Back")
            }
        }
    }
    }

}

@Composable
fun CropOverlay(points: List<Offset>) {

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size >= 3) {
                val path = Path().apply {
                    points.forEachIndexed { index, p ->
                        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(
                    path = path,
                    color = Color.Red.copy(alpha = 0.2f)
                )
            }
            // Draw edges
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = Color.Red,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 4f
                )
            }
            // Close polygon
            if (points.size >= 3) {
                drawLine(
                    color = Color.Red,
                    start = points.last(),
                    end = points.first(),
                    strokeWidth = 4f
                )
            }
            // Draw points
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 10f + 4f,
                    center = point
                )
                drawCircle(
                    color = Color.Red,
                    radius = 10f,
                    center = point
                )
            }
        }
    }
}

@Composable
fun ConfigEditFields(onDelayChange: (Int) -> Unit, initialDelay: String) {
    val config: ConfigViewModel = viewModel()
    var delayString by remember { mutableStateOf(initialDelay) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Capture interval in seconds:")
        TextField(
            value = delayString,
            onValueChange = {
                delayString = it
                it.toIntOrNull()?.let { value -> onDelayChange(value) }
            },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )

        Text(text = "Api url")
        TextField(
            value = config.url,
            onValueChange = {
                config.url = it
            }
        )
        Text(text = "api key")
        TextField(
            value = config.apiKey,
            onValueChange = {
                config.apiKey = it
            }
        )
        Text(text = "Hive id")
        TextField(
            value = config.hiveId,
            onValueChange = {
                config.hiveId = it
            }
        )
        Text(text = "Enable local saving")
        Checkbox(
            checked = config.shouldSaveLocally,
            onCheckedChange = { config.shouldSaveLocally = it }
        )
        Text(text = "Enable uploading")
        Checkbox(
            checked = config.shouldUpload,
            onCheckedChange = { config.shouldUpload = it }
        )
    }
}

fun transformAndSaveCrop(config: ConfigViewModel) {
    config.cropPoints.clear()
    for (point in config.cropDrawPoints) {
        config.cropPoints.add(with(config.coordinateTransformer) { point.transform() })
    }
}

fun findPointIndex(
    points: List<Offset>,
    touch: Offset,
    hitRadius: Float
): Int? {
    return points.indexOfFirst {
        (it - touch).getDistance() <= hitRadius
    }.takeIf { it >= 0 }
}
