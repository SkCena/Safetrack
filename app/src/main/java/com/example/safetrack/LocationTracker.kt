package com.example.safetrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

object LocationTracker {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationTracker", "CyberSkOD Free: Location permissions not granted")
            return null
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).await()

            if (location != null) {
                Log.d("LocationTracker", "CyberSkOD Free: Location retrieved: ${location.latitude}, ${location.longitude}")
                Pair(location.latitude, location.longitude)
            } else {
                Log.e("LocationTracker", "CyberSkOD Free: Failed to retrieve location")
                null
            }
        } catch (e: Exception) {
            Log.e("LocationTracker", "CyberSkOD Free: Exception retrieving location: ${e.message}")
            null
        }
    }
}
