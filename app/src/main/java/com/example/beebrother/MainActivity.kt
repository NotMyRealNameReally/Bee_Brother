package com.example.beebrother

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

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
                            monitoringService = service!!
                        ) { screen -> visibleScreen = screen }

                        Screen.SETTINGS -> SettingsScreen() { screen -> visibleScreen = screen }
                        Screen.CAPTURE_STATUS -> CaptureStatusScreen() { screen ->
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

    fun exitApplication(context: Context, config: ConfigViewModel) {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }

        val serviceIntent = Intent(this, MonitoringService::class.java)
        stopService(serviceIntent)
        PeriodicCaptureController.stopCapture(context, config)
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

    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { activity?.exitApplication(context, config) },
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Exit Application")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            val buttonModifier = Modifier.fillMaxWidth(0.7f)

            Button(onClick = { onSelectScreen(Screen.PREVIEW) }, buttonModifier) {
                Text("Open camera preview")
            }
            Button(onClick = {
                PeriodicCaptureController.startCapture(context, config, service!!)
                onSelectScreen(Screen.CAPTURE_STATUS)
            }, buttonModifier) {
                Text("Start")
            }

            Button(onClick = { onSelectScreen(Screen.SETTINGS) }, buttonModifier) {
                Text("Upload Settings")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Capture interval in seconds:")
            OutlinedTextField(
                value = config.delay.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { value -> config.delay = value }
                },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.shouldSaveLocally,
                    onCheckedChange = { config.shouldSaveLocally = it }
                )
                Text(text = "Enable local saving")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.shouldUpload,
                    onCheckedChange = { config.shouldUpload = it }
                )
                Text(text = "Enable uploading")
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    monitoringService: MonitoringService,
    onSelectScreen: (Screen) -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            monitoringService.unbindPreview()
        }
    }
    val config: ConfigViewModel = viewModel()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }

    // Writer: MutableStateFlow we can update from CameraX callbacks
    val surfaceRequests = remember { MutableStateFlow<SurfaceRequest?>(null) }

    // Reader: Compose state derived from the flow
    val surfaceRequest by surfaceRequests.collectAsState(initial = null)
    LaunchedEffect(Unit) {
        monitoringService.bindPreview()
        monitoringService.previewUseCase.apply {
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
                    onClick = {
                        config.cropDrawPoints.clear()
                        config.cropPoints.clear()
                    },
                    enabled = config.cropDrawPoints.isNotEmpty(),
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        transformAndSaveCrop(config)
                        onSelectScreen(Screen.MENU)
                    }
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
fun SettingsScreen(onSelectScreen: (Screen) -> Unit) {
    val config: ConfigViewModel = viewModel()
    val presetsState = config.presets.collectAsState()

    var showPresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

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
            val buttonModifier = Modifier.fillMaxWidth(0.7f)
            UploadEditFields()
            Box(modifier = Modifier.wrapContentSize(Alignment.Center)) {
                Button(
                    onClick = { expanded = true },
                    modifier = buttonModifier
                ) {
                    Text("Load Preset")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(IntrinsicSize.Max)
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
            }
            Button(onClick = { showPresetDialog = true }, buttonModifier) {
                Text("Save Current as Preset")
            }
            Button(onClick = { onSelectScreen(Screen.MENU) }, buttonModifier) {
                Text("Back")
            }
        }
    }
}

@Composable
fun CaptureStatusScreen(onSelectScreen: (Screen) -> Unit) {
    val config: ConfigViewModel = viewModel()
    val context = LocalContext.current
    val history by config.uploadHistory.collectAsState()

    Scaffold(
        bottomBar = {
            BottomAppBar(containerColor = Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            PeriodicCaptureController.stopCapture(context, config)
                            onSelectScreen(Screen.MENU)
                        },
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Capture")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
                Text("Capture is Active", style = MaterialTheme.typography.headlineMedium)
                Text("Interval: ${config.delay}s", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Configuration Summary",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusRow(
                        label = "Local Saving",
                        isActive = config.shouldSaveLocally,
                        activeText = "Enabled",
                        inactiveText = "Disabled"
                    )
                    StatusRow(
                        label = "Cropping",
                        isActive = config.cropPoints.isNotEmpty(),
                        activeText = "Active (${config.cropPoints.size} points)",
                        inactiveText = "Disabled"
                    )
                    StatusRow(
                        label = "Remote Upload",
                        isActive = config.shouldUpload,
                        activeText = "Enabled",
                        inactiveText = "Disabled"
                    )
                    if (config.shouldUpload) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp
                        )
                        Text("URL: ${config.url}", style = MaterialTheme.typography.bodySmall)
                        if (config.hiveId.isNotEmpty()) {
                            Text(
                                "Hive ID: ${config.hiveId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (config.shouldUpload) {
                Text("Last 5 Uploads", style = MaterialTheme.typography.titleLarge)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (history.isEmpty()) {
                        item {
                            Text(
                                "Waiting for first capture...",
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    items(history) { log ->
                        UploadHistoryItem(log)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatusRow(label: String, isActive: Boolean, activeText: String, inactiveText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (isActive) Color(0xFF4CAF50) else Color.Gray
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, shape = CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isActive) activeText else inactiveText,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
fun UploadHistoryItem(log: UploadLog) {
    val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    val timeString = sdf.format(java.util.Date(log.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (log.isSuccess) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (log.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (log.isSuccess) "Upload Successful" else "Upload Failed",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Time: $timeString", style = MaterialTheme.typography.bodySmall)
                if (!log.isSuccess && log.errorMessage != null) {
                    Text(
                        text = log.errorMessage,
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun UploadEditFields() {
    val config: ConfigViewModel = viewModel()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Api url")
        OutlinedTextField(
            value = config.url,
            onValueChange = {
                config.url = it
            }
        )
        Text(text = "api key")
        OutlinedTextField(
            value = config.apiKey,
            onValueChange = {
                config.apiKey = it
            }
        )
        Text(text = "Hive id")
        OutlinedTextField(
            value = config.hiveId,
            onValueChange = {
                config.hiveId = it
            }
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
