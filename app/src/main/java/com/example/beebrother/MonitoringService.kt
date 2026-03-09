package com.example.beebrother

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class MonitoringService : Service(), LifecycleOwner {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "BeeBrotherMonitoringChannel"
    }

    lateinit var cameraProvider: ProcessCameraProvider
    val previewUseCase: Preview = Preview.Builder().setTargetRotation(Surface.ROTATION_0).build()
    val imageCaptureUseCase: ImageCapture =
        ImageCapture.Builder().setTargetRotation(Surface.ROTATION_0).build()

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val cameraFuture = ProcessCameraProvider.getInstance(this)
        cameraFuture.addListener({
            cameraProvider = cameraFuture.get()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, // The service is the LifecycleOwner
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageCaptureUseCase
            )
        }, ContextCompat.getMainExecutor(this))
    }

    fun unbindPreview() {
        cameraProvider.unbind(previewUseCase)
    }

    fun bindPreview() {
        cameraProvider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        // The camera use cases will be automatically unbound by CameraX
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoring Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bee Brother is active")
            .setContentText("Monitoring the hive.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    inner class LocalBinder : Binder() {
        fun getService(): MonitoringService = this@MonitoringService
    }
}