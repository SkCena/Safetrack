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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Camera capture utility - class-based wrapper to avoid singleton race conditions.
 * Provides suspend-based capturePhoto() and an explicit shutdown() to release resources.
 *
 * Supports both front (selfie) and back (rear) camera via the lensFacing parameter.
 */
class CameraUtility {

    enum class LensFacing { FRONT, BACK }

    @Volatile
    private var imageCapture: ImageCapture? = null
    @Volatile
    private var boundCameraProvider: ProcessCameraProvider? = null

    /**
     * Capture a photo with the specified lens (FRONT or BACK).
     * Default is BACK for backward compatibility with existing /photo command.
     */
    suspend fun capturePhoto(context: Context, lens: LensFacing = LensFacing.BACK): File = suspendCoroutine { continuation ->

        // 1. Check CAMERA permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("Camera", "Camera permission not granted")
            continuation.resumeWithException(SecurityException("Camera permission not granted"))
            return@suspendCoroutine
        }

        val outputDirectory = File(context.cacheDir, "diagnostic_images")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val prefix = if (lens == LensFacing.FRONT) "selfie_" else "back_"
        val photoFile = File(outputDirectory, "${prefix}${System.currentTimeMillis()}.jpg")

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

                // Select the requested lens, with graceful fallback when not available.
                val desiredSelector = when (lens) {
                    LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                }
                val cameraSelector = try {
                    if (cameraProvider.hasCamera(desiredSelector)) desiredSelector
                    else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                    else CameraSelector.DEFAULT_FRONT_CAMERA
                } catch (e: Exception) {
                    Log.w("Camera", "hasCamera check failed, falling back to default: ${e.message}")
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                // Bind to a LifecycleOwner - service/activity contexts both qualify
                val lifecycleOwner: LifecycleOwner = findLifecycleOwner(context) ?: run {
                    Log.w("Camera", "Could not find LifecycleOwner, falling back to ProcessLifecycleOwner")
                    androidx.lifecycle.ProcessLifecycleOwner.get()
                }
                Log.d("Camera", "Binding to LifecycleOwner: ${lifecycleOwner::class.java.simpleName}")

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

    private fun findLifecycleOwner(context: Context): LifecycleOwner? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is LifecycleOwner) return current
            current = current.baseContext
        }
        return null
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
