package com.example.safetrack

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Camera capture utility - class-based wrapper to avoid singleton race conditions.
 * Provides suspend-based capturePhoto() and an explicit shutdown() to release resources.
 */
class CameraUtility {

    @Volatile
    private var imageCapture: ImageCapture? = null
    @Volatile
    private var boundCameraProvider: ProcessCameraProvider? = null

    suspend fun capturePhoto(context: Context): File = suspendCoroutine { continuation ->

        // 1. Check CAMERA permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("Camera", "Camera permission not granted")
            continuation.resumeWithException(SecurityException("Camera permission not granted"))
            return@suspendCoroutine
        }

        val outputDirectory = File(context.cacheDir, "diagnostic_images")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val photoFile = File(outputDirectory, "diag_${System.currentTimeMillis()}.jpg")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("Camera", "Failed to get camera provider: ${e.message}")
                continuation.resumeWithException(e)
                return@addListener
            }

            try {
                // Unbind any previous use cases to avoid "Camera is closed" errors
                cameraProvider.unbindAll()

                // Build a fresh ImageCapture (local to this call - avoids shared-state races)
                val newImageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Preview is REQUIRED by CameraX to keep the camera session alive.
                // We omit setSurfaceProvider entirely - CameraX allows this for capture-only flows.
                val preview = Preview.Builder().build()

                // Select back camera (prefer) or front as fallback
                val cameraSelector = try {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } catch (e: Exception) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                // Bind to a LifecycleOwner - service/activity contexts both qualify
                val lifecycleOwner: LifecycleOwner = when (context) {
                    is LifecycleOwner -> context
                    else -> {
                        // Fall back to ProcessLifecycleOwner (app-wide) when context is not a LifecycleOwner
                        // e.g. DiagnosticWorker / applicationContext
                        androidx.lifecycle.ProcessLifecycleOwner.get()
                    }
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    newImageCapture
                )

                // Track for explicit shutdown
                imageCapture = newImageCapture
                boundCameraProvider = cameraProvider

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                newImageCapture.takePicture(
                    outputOptions,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            // Unbind to release camera resources - prevents "Camera is closed" on next call
                            try { cameraProvider.unbindAll() } catch (_: Exception) {}
                            if (boundCameraProvider === cameraProvider) {
                                boundCameraProvider = null
                                imageCapture = null
                            }
                            continuation.resume(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("Camera", "Photo capture failed: ${exception.message}")
                            try { cameraProvider.unbindAll() } catch (_: Exception) {}
                            if (boundCameraProvider === cameraProvider) {
                                boundCameraProvider = null
                                imageCapture = null
                            }
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("Camera", "Use case binding failed: ${e.message}")
                try { cameraProvider.unbindAll() } catch (_: Exception) {}
                continuation.resumeWithException(e)
            }
        }, mainExecutor)
    }

    /**
     * Explicitly release camera resources. Call from Service.onDestroy() to prevent
     * "Camera is closed" errors on subsequent invocations.
     */
    fun shutdown() {
        try {
            boundCameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w("Camera", "shutdown unbindAll failed: ${e.message}")
        }
        boundCameraProvider = null
        imageCapture = null
    }

    companion object {
        @Volatile
        private var INSTANCE: CameraUtility? = null

        fun get(): CameraUtility {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CameraUtility().also { INSTANCE = it }
            }
        }
    }
}
