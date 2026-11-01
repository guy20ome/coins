package com.coins.bluetooth

import android.bluetooth.BluetoothDevice

/**
 * Sealed class representing the state of Bluetooth operations.
 * Used for reactive state management in Bluetooth transfer workflows.
 */
sealed class BluetoothState {
    
    /**
     * Bluetooth is idle (not scanning, not advertising, not connected).
     */
    object Idle : BluetoothState()
    
    /**
     * Bluetooth is currently scanning for devices.
     */
    object Scanning : BluetoothState()
    
    /**
     * Bluetooth is currently advertising as a server.
     */
    object Advertising : BluetoothState()
    
    /**
     * Bluetooth is connected to a device.
     * 
     * @property device The connected Bluetooth device
     */
    data class Connected(val device: BluetoothDevice) : BluetoothState()
    
    /**
     * Bluetooth connection is in progress.
     * 
     * @property device The device being connected to
     */
    data class Connecting(val device: BluetoothDevice) : BluetoothState()
    
    /**
     * A device has been discovered during scanning.
     * 
     * @property device The discovered Bluetooth device
     */
    data class DeviceDiscovered(val device: BluetoothDevice) : BluetoothState()
    
    /**
     * A coin transfer is in progress.
     * 
     * @property coinId The ID of the coin being transferred
     * @property progress The transfer progress (0-100)
     */
    data class Transferring(val coinId: String, val progress: Int) : BluetoothState()
    
    /**
     * A coin has been successfully transferred.
     * 
     * @property coinId The ID of the transferred coin
     */
    data class TransferComplete(val coinId: String) : BluetoothState()
    
    /**
     * A coin has been successfully received.
     * 
     * @property coin The received coin
     */
    data class CoinReceived(val coin: com.coins.model.Coin) : BluetoothState()
    
    /**
     * An error occurred during Bluetooth operations.
     * 
     * @property message The error message
     * @property exception The exception that occurred (optional)
     */
    data class Error(val message: String, val exception: Exception? = null) : BluetoothState()
    
    /**
     * Bluetooth has been disabled.
     */
    object BluetoothDisabled : BluetoothState()
    
    /**
     * Bluetooth permissions have been denied.
     */
    object PermissionsDenied : BluetoothState()
    
    /**
     * Bluetooth is not available on this device.
     */
    object BluetoothNotAvailable : BluetoothState()
}
