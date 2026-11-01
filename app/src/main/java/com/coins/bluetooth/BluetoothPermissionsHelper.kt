package com.coins.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper class for managing Bluetooth permissions.
 * Handles different permission requirements across Android versions.
 */
class BluetoothPermissionsHelper(private val context: Context) {
    
    /**
     * Check if all required Bluetooth permissions are granted.
     * 
     * @return true if all permissions are granted, false otherwise
     */
    fun hasAllPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Older versions only need BLUETOOTH and BLUETOOTH_ADMIN
            hasPermission(Manifest.permission.BLUETOOTH) &&
            hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    
    /**
     * Check if a specific permission is granted.
     * 
     * @param permission The permission to check
     * @return true if the permission is granted, false otherwise
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get the list of permissions that need to be requested.
     * 
     * @return List of permissions that are not yet granted
     */
    fun getPermissionsToRequest(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (!hasPermission(Manifest.permission.BLUETOOTH)) {
                permissions.add(Manifest.permission.BLUETOOTH)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
                permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        
        return permissions
    }
    
    /**
     * Check if Bluetooth is available on this device.
     * 
     * @return true if Bluetooth is available, false otherwise
     */
    fun isBluetoothAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
    
    /**
     * Check if Bluetooth LE (Low Energy) is available on this device.
     * 
     * @return true if Bluetooth LE is available, false otherwise
     */
    fun isBluetoothLeAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    
    companion object {
        /**
         * Create a BluetoothPermissionsHelper with the given context.
         * 
         * @param context The application context
         * @return A new BluetoothPermissionsHelper instance
         */
        fun create(context: Context): BluetoothPermissionsHelper {
            return BluetoothPermissionsHelper(context)
        }
    }
}
