package com.example.beebrother

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.beebrother.ui.theme.BeeBrotherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // capture works only with screen on
        setContent {
            CheckCameraPermissions()
            var visibleScreen by remember { mutableStateOf(Screen.MENU) }
            BeeBrotherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (visibleScreen) {
                        Screen.MENU -> MainMenu { screen -> visibleScreen = screen }
                        Screen.PREVIEW -> CameraPreviewView { screen -> visibleScreen = screen }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckCameraPermissions() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(
            context,
            "Camera permission is required",
            Toast.LENGTH_SHORT
        ).show()
    }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }
}

@Composable
fun MainMenu(onSelectScreen: (Screen) -> Unit) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isStarted by remember { mutableStateOf(false) }
    var delay by remember { mutableIntStateOf(5) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { onSelectScreen(Screen.PREVIEW) }) {
            Text("Open camera preview")
        }
        Button(onClick = {
            if (isStarted) {
                PeriodicCaptureController.stopCapture()
                isStarted = false
            } else {
                PeriodicCaptureController.startCapture(lifeCycleOwner, context, delay)
                isStarted = true
            }
        }) {
            if (isStarted) {
                Text("Stop")
            } else {
                Text("Start")
            }
        }
        DelayEditField(onDelayChange = { newDelay -> delay = newDelay }, delay.toString())
    }
}

@Composable
fun CameraPreviewView(onSelectScreen: (Screen) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview()
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd)
        ) {
            Button(
                onClick = { onSelectScreen(Screen.MENU) }
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun CameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraX", "Failed to launch preview", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DelayEditField(onDelayChange: (Int) -> Unit, initialDelay: String) {
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
                it.toIntOrNull()?.let { value -> onDelayChange(value)}
            },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )
    }
}
