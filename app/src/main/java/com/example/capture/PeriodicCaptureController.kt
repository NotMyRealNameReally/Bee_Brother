package com.example.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.example.beebrother.ConfigViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedList
import java.util.Queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

object PeriodicCaptureController {
    private const val TAG = "PeriodicCaptureController"
    private var captureJob: Job? = null
    private var saveAndUploadJob: Job? = null
    private var imageQueue: Queue<Uri> = LinkedList()

    fun startCapture(
        context: Context,
        config: ConfigViewModel,
        service: MonitoringService
    ) {
        imageQueue.clear()
        captureJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (config.shouldUpload || config.shouldSaveLocally) {
                    takePictureAndSave(context, service.imageCaptureUseCase, config)
                }
                delay(config.delay.seconds)
            }
        }
        saveAndUploadJob = CoroutineScope(Dispatchers.IO).launch {
            delay(60)
            while (isActive) {
                if (config.shouldUpload) {
                    tryUploadImages(context, config, !config.shouldSaveLocally)
                } else if (config.shouldSaveLocally) {
                    imageQueue.clear() // just clear the queue so it doesn't grow indefinitely
                }
                delay(60)
            }
        }
    }

    fun stopCapture(
        context: Context,
        config: ConfigViewModel
    ) {
        captureJob?.cancel()
        saveAndUploadJob?.cancel()
        if (!config.shouldSaveLocally) {
            while (imageQueue.isNotEmpty()) {
                context.contentResolver.delete(imageQueue.poll()!!, null, null)
            }
        }
    }

    private suspend fun takePictureAndSave(
        context: Context,
        imageCapture: ImageCapture,
        config: ConfigViewModel
    ): Boolean = suspendCoroutine { continuation ->
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    if (config.cropPoints.isNotEmpty()) {
                        val cropped = cropBitmap(bitmap, config.cropPoints)
                        saveBitmap(context, cropped, config)
                    } else {
                        saveBitmap(context, bitmap, config)
                    }
                    image.close()
                    continuation.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun cropBitmap(bitmap: Bitmap, cropPoints: List<Offset>): Bitmap {
        val rect = mapPolygonBoundingRect(cropPoints)
        val rectCroppedBitMap = Bitmap.createBitmap(
            bitmap,
            rect.left,
            rect.top,
            rect.width,
            rect.height
        )
        return maskOutsidePolygon(rectCroppedBitMap, cropPoints, rect)
    }

    fun mapPolygonBoundingRect(points: List<Offset>): IntRect {
        val minX = points.minOf { it.x }.toInt()
        val minY = points.minOf { it.y }.toInt()
        val maxX = points.maxOf { it.x }.toInt()
        val maxY = points.maxOf { it.y }.toInt()

        return IntRect(minX, minY, maxX, maxY)
    }

    fun maskOutsidePolygon(
        cropped: Bitmap,
        polygon: List<Offset>,
        cropRect: IntRect
    ): Bitmap {
        val result = createBitmap(cropped.width, cropped.height)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val path = Path().apply {
            polygon.forEachIndexed { index, point ->
                val x = point.x - cropRect.left
                val y = point.y - cropRect.top
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.withClip(path) {
            drawBitmap(cropped, 0f, 0f, paint)
        }
        paint.xfermode = null
        return result
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, config: ConfigViewModel) {
        var filename =
            "${LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss"))}.png"
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                filename
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BeeBrother")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return

        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        imageQueue.add(uri)
    }

    private suspend fun tryUploadImages(
        context: Context,
        config: ConfigViewModel,
        deleteOnSuccess: Boolean
    ) {
        val api = Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create())
            .baseUrl("http://localhost/")
            .build()
            .create(ImageUploadApi::class.java)

        while (imageQueue.isNotEmpty()) {
            val uri = imageQueue.peek()
            val type = context.contentResolver.getType(uri!!)
            val fileName = extractFileName(context, uri)
            val bytes = context.contentResolver.openInputStream(uri).use { it!!.readBytes() }
            val requestBody = bytes.toRequestBody(contentType = type!!.toMediaTypeOrNull())
            val multiPart = MultipartBody.Part.createFormData(
                name = "file",
                filename = fileName,
                body = requestBody
            )
            try {
                val response = api.uploadImage(config.url, config.apiKey, config.hiveId, multiPart)
                if (response.isSuccessful) {
                    config.addUploadLog(true)
                    imageQueue.poll()
                    if (deleteOnSuccess) {
                        context.contentResolver.delete(uri, null, null)
                    }
                    Log.d(TAG, "Uploaded image: $fileName")
                } else {
                    config.addUploadLog(false, response.errorBody()?.string())
                    Log.e(TAG, "Upload failed: ${response.code()}")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                break
            }


        }
    }

    private fun extractFileName(context: Context, uri: Uri): String {
        val proj = arrayOf<String?>(MediaStore.Images.Media.DISPLAY_NAME)
        return context.contentResolver.query(uri, proj, null, null, null).use {
            val index = it!!.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            it.moveToFirst()
            return it.getString(index)
        }
    }
}