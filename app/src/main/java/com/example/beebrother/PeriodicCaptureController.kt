package com.example.beebrother

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

object PeriodicCaptureController {
    private const val TAG = "PeriodicCaptureController"
    private var imageCapture: ImageCapture? = null
    private var captureJob: Job? = null

    fun startCapture(lifecycleOwner: LifecycleOwner, context: Context, delay: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

            captureJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    delay(delay.seconds)
                    takePicture(context)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCapture() {
        captureJob?.cancel()
    }

    private fun takePicture(context: Context) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${Instant.now()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BeeBrother")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo saved: ${results.savedUri}")
                    //uploadImage(imageFile)
                }

                override fun onError(ex: ImageCaptureException) {
                    Log.e(TAG, "Capture failed: ${ex.message}", ex)
                }
            }
        )
    }

    private fun uploadImage(file: File) {

    }
}