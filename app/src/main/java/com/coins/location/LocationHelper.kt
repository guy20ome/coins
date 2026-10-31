package com.coins.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, onLocationReceived: (String) -> Unit) {
        if (!hasLocationPermission(context)) {
            onLocationReceived("0.0,0.0")
            return
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val formatted = "${formatCoord(location.latitude)},${formatCoord(location.longitude)}"
                    onLocationReceived(formatted)
                } else {
                    // Try last known location
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            val formatted = "${formatCoord(lastLoc.latitude)},${formatCoord(lastLoc.longitude)}"
                            onLocationReceived(formatted)
                        } else {
                            // Fallback to LocationManager
                            onLocationReceived(getLocationFromSystemManager(context))
                        }
                    }.addOnFailureListener {
                        onLocationReceived(getLocationFromSystemManager(context))
                    }
                }
            }.addOnFailureListener {
                onLocationReceived(getLocationFromSystemManager(context))
            }
        } catch (e: Exception) {
            onLocationReceived(getLocationFromSystemManager(context))
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationFromSystemManager(context: Context): String {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return "0.0,0.0"
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
            if (bestLocation != null) {
                return "${formatCoord(bestLocation.latitude)},${formatCoord(bestLocation.longitude)}"
            }
        } catch (e: Exception) {
            // ignore
        }
        return "0.0,0.0"
    }

    private fun formatCoord(value: Double): String {
        return String.format(java.util.Locale.US, "%.4f", value)
    }
}
