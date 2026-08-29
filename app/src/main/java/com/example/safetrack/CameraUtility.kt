package com.example.safetrack

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object CameraUtility {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    suspend fun capturePhoto(context: Context): File = suspendCoroutine { continuation ->
        val outputDirectory = File(context.cacheDir, "diagnostic_images")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val photoFile = File(outputDirectory, "diag_${System.currentTimeMillis()}.jpg")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()

                // CameraX bindToLifecycle requires a LifecycleOwner.
                // We pass in the context itself if it is a LifecycleOwner,
                // but since we are in a Service, we need a special approach.
                // For CameraX in a Service, we use a separate handler or
                // bind to a fake owner. A safer way is to use a ProcessLifecycleOwner
                // from the androidx.lifecycle:lifecycle-process dependency,
                // but to keep it simple, we can try using the application context
                // and a lifecycle-aware binding or just ensure context is a lifecycle owner if possible.
                // Since PersistentSyncService is a LifecycleService, it implements LifecycleOwner.

                if (context is androidx.lifecycle.LifecycleOwner) {
                    cameraProvider.bindToLifecycle(
                        context,
                        cameraSelector,
                        imageCapture
                    )
                } else {
                     throw Exception("Context is not a LifecycleOwner")
                }

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            continuation.resume(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
