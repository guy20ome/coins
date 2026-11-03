# Bluetooth Implementation - Required Code Changes

This document provides the specific code changes needed to fix the critical issues in the Bluetooth implementation.

## 📁 Files to Modify

1. `app/src/main/java/com/coins/bluetooth/BluetoothCoinTransfer.kt`
2. `app/src/main/java/com/coins/bluetooth/BluetoothState.kt` (no changes needed, but reference it)

---

## 🔧 Changes for BluetoothCoinTransfer.kt

### 1. Add Imports

**Add these imports at the top of the file:**
```kotlin
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
```

### 2. Add Companion Object Constants

**Add to the companion object:**
```kotlin
companion object {
    /**
     * Bluetooth service UUID for coin transfer
     */
    val COIN_TRANSFER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    // Add these constants
    private const val TAG = "BluetoothCoinTransfer"
    private const val CONNECTION_TIMEOUT_MS = 30000L // 30 seconds
    private const val MAX_MESSAGE_SIZE = 1024 * 10 // 10KB
    
    /**
     * Create a BluetoothCoinTransfer instance with the given context.
     */
    fun create(context: Context): BluetoothCoinTransfer {
        return BluetoothCoinTransfer(context).apply {
            registerBluetoothStateReceiver()
        }
    }
}
```

### 3. Add Fields for State Monitoring

**Add these fields to the class:**
```kotlin
class BluetoothCoinTransfer(private val context: Context) {
    
    // ... existing fields ...
    
    // Add these:
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private val discoveredDevicesSet = mutableSetOf<String>()
    
    // ... rest of class ...
}
```

### 4. Update Thread Management

**Replace the ConnectedThread class with this improved version:**
```kotlin
/**
 * Thread for handling connected socket communication.
 * Reads incoming data and deserializes it into Coin objects.
 */
private inner class ConnectedThread(
    private val socket: BluetoothSocket,
    private val onCoinReceived: (Coin) -> Unit,
    private val onError: (String) -> Unit
) : Thread() {
    
    private var isRunning = true
    private var inputStream: InputStream? = null
    
    override fun run() {
        try {
            inputStream = socket.inputStream
            val buffer = ByteArray(1024)
            var data = ""
            
            Log.d(TAG, "ConnectedThread started for ${socket.remoteDevice?.name}")
            
            while (isRunning && !isInterrupted) {
                val bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    data += String(buffer, 0, bytes, Charsets.UTF_8)
                    
                    // Check for size limit
                    if (data.length > MAX_MESSAGE_SIZE) {
                        Log.e(TAG, "Message too large: ${data.length} bytes")
                        _state.tryEmit(BluetoothState.Error("Message too large", null))
                        onError("Message too large")
                        return
                    }
                    
                    // Check for delimiter (newline)
                    if (data.contains("\n")) {
                        val parts = data.split("\n")
                        for (part in parts.dropLast(1)) {
                            if (part.isNotEmpty()) {
                                // Check individual message size
                                if (part.length > MAX_MESSAGE_SIZE) {
                                    Log.e(TAG, "Individual message too large")
                                    _state.tryEmit(BluetoothState.Error("Message too large", null))
                                    onError("Message too large")
                                    return
                                }
                                
                                try {
                                    val coin = coinSerializer.deserialize(part)
                                    if (coin != null) {
                                        // Additional validation
                                        if (coin.coinId.isNotEmpty() && coin.giverId.isNotEmpty()) {
                                            Log.d(TAG, "Received coin: ${coin.coinId}")
                                            _state.tryEmit(BluetoothState.CoinReceived(coin))
                                            onCoinReceived(coin)
                                            _transferChannel.trySend(TransferResult.Success(coin))
                                        } else {
                                            Log.e(TAG, "Received invalid coin data")
                                            onError("Received invalid coin data")
                                        }
                                    } else {
                                        Log.e(TAG, "Failed to deserialize coin")
                                        onError("Failed to deserialize coin")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing coin", e)
                                    onError("Error processing received data: ${e.message}")
                                }
                            }
                        }
                        data = parts.last()
                    }
                }
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Connection lost", e)
            _state.tryEmit(BluetoothState.Error("Connection lost: ${e.message}", e))
            onError("Connection lost: ${e.message}")
        } finally {
            cleanup()
        }
    }
    
    fun cancel() {
        Log.d(TAG, "Cancelling ConnectedThread")
        isRunning = false
        interrupt()
        cleanup()
    }
    
    private fun cleanup() {
        Log.d(TAG, "Cleaning up ConnectedThread")
        try {
            inputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing input stream", e)
        }
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        inputStream = null
    }
}
```

### 5. Update Socket Cleanup Methods

**Replace stopServer() method:**
```kotlin
/**
 * Stop the Bluetooth server.
 */
fun stopServer() {
    try {
        serverSocket?.close()
        Log.d(TAG, "Server socket closed")
    } catch (e: IOException) {
        Log.e(TAG, "Error closing server socket", e)
    } finally {
        serverSocket = null
        _state.tryEmit(BluetoothState.Idle)
    }
}
```

**Replace disconnect() method:**
```kotlin
/**
 * Disconnect from the current device.
 */
fun disconnect() {
    try {
        clientSocket?.close()
        Log.d(TAG, "Client socket closed")
    } catch (e: IOException) {
        Log.e(TAG, "Error closing client socket", e)
    } finally {
        clientSocket = null
    }
    
    connectedThread?.cancel()
    connectedThread = null
    _state.tryEmit(BluetoothState.Idle)
}
```

### 6. Update connectToDevice() with Timeout

**Replace connectToDevice() method:**
```kotlin
/**
 * Connect to a remote device to send coins.
 * 
 * @param device The device to connect to
 * @param onConnected Callback when connection is established
 * @param onError Callback when an error occurs
 */
fun connectToDevice(device: BluetoothDevice, onConnected: () -> Unit, onError: (String) -> Unit) {
    scope.launch {
        try {
            if (!hasBluetoothPermissions()) {
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required")
                return@launch
            }
            
            if (!isBluetoothAvailable()) {
                _state.emit(BluetoothState.BluetoothNotAvailable)
                onError("Bluetooth not available")
                return@launch
            }
            
            if (!isBluetoothEnabled()) {
                _state.emit(BluetoothState.BluetoothDisabled)
                onError("Bluetooth not enabled")
                return@launch
            }
            
            _state.emit(BluetoothState.Connecting(device))
            Log.d(TAG, "Connecting to device: ${device.name}")
            
            // Create client socket
            val socket = device.createRfcommSocketToServiceRecord(COIN_TRANSFER_UUID)
            
            // Connect with timeout
            try {
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    socket.connect()
                }
                clientSocket = socket
                Log.d(TAG, "Connected to device: ${device.name}")
                
                _state.emit(BluetoothState.Connected(device))
                onConnected()
                
            } catch (e: TimeoutCancellationException) {
                socket.close()
                throw IOException("Connection timed out after ${CONNECTION_TIMEOUT_MS}ms")
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Error connecting to device", e)
            _state.emit(BluetoothState.Error("Error connecting to device: ${e.message}", e))
            onError("Error connecting to device: ${e.message}")
        }
    }
}
```

### 7. Update startServer() to Prevent Race Conditions

**Replace startServer() method:**
```kotlin
/**
 * Start Bluetooth server to receive coin transfers.
 * This creates a listening socket that accepts incoming connections.
 * 
 * @param onCoinReceived Callback when a coin is received
 * @param onError Callback when an error occurs
 */
fun startServer(onCoinReceived: (Coin) -> Unit, onError: (String) -> Unit) {
    scope.launch {
        try {
            if (!hasBluetoothPermissions()) {
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required")
                return@launch
            }
            
            if (!isBluetoothAvailable()) {
                _state.emit(BluetoothState.BluetoothNotAvailable)
                onError("Bluetooth not available")
                return@launch
            }
            
            if (!isBluetoothEnabled()) {
                _state.emit(BluetoothState.BluetoothDisabled)
                onError("Bluetooth not enabled")
                return@launch
            }
            
            _state.emit(BluetoothState.Advertising)
            Log.d(TAG, "Starting server")
            
            // Create server socket
            val tempServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                "CoinTransferServer",
                COIN_TRANSFER_UUID
            )
            
            // Atomic assignment
            serverSocket = tempServerSocket
            
            tempServerSocket?.use { socket ->
                Log.d(TAG, "Server waiting for connection...")
                onError("Waiting for connection...")
                val clientSocket = socket.accept()
                handleConnectedSocket(clientSocket, onCoinReceived, onError)
            }
            
            // Clear reference when done
            serverSocket = null
            Log.d(TAG, "Server stopped")
            
        } catch (e: IOException) {
            serverSocket = null
            Log.e(TAG, "Error starting server", e)
            _state.emit(BluetoothState.Error("Error starting server: ${e.message}", e))
            onError("Error starting server: ${e.message}")
        }
    }
}
```

### 8. Add Bluetooth State Monitoring

**Add these methods to the class:**
```kotlin
/**
 * Register a receiver to monitor Bluetooth adapter state changes.
 */
private fun registerBluetoothStateReceiver() {
    val filter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }
    
    bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.w(TAG, "Bluetooth turned off")
                    _state.tryEmit(BluetoothState.BluetoothDisabled)
                    stop() // Stop all operations
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned on")
                    _state.tryEmit(BluetoothState.Idle)
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    Log.w(TAG, "Bluetooth turning off")
                    _state.tryEmit(BluetoothState.BluetoothDisabled)
                    stop()
                }
                BluetoothAdapter.STATE_TURNING_ON -> {
                    Log.d(TAG, "Bluetooth turning on")
                }
            }
        }
    }
    
    try {
        context.registerReceiver(bluetoothStateReceiver, filter)
        Log.d(TAG, "Bluetooth state receiver registered")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to register Bluetooth state receiver", e)
    }
}

/**
 * Unregister the Bluetooth state receiver.
 */
private fun unregisterBluetoothStateReceiver() {
    bluetoothStateReceiver?.let { receiver ->
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Bluetooth state receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bluetooth state receiver was not registered")
        }
    }
    bluetoothStateReceiver = null
}
```

### 9. Update startDiscovery() with Better Error Handling

**Replace startDiscovery() method:**
```kotlin
/**
 * Start discovering nearby Bluetooth devices.
 *
 * @param onDeviceDiscovered Callback when a device is discovered
 * @param onError Callback when an error occurs
 */
fun startDiscovery(onDeviceDiscovered: (BluetoothDevice) -> Unit, onError: (String) -> Unit) {
    scope.launch {
        try {
            if (!hasBluetoothPermissions()) {
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required")
                return@launch
            }

            if (!isBluetoothAvailable()) {
                _state.emit(BluetoothState.BluetoothNotAvailable)
                onError("Bluetooth not available")
                return@launch
            }

            if (!isBluetoothEnabled()) {
                _state.emit(BluetoothState.BluetoothDisabled)
                onError("Bluetooth not enabled")
                return@launch
            }

            // Clear previous discoveries
            discoveredDevicesSet.clear()
            
            if (!bluetoothAdapter?.startDiscovery()!!) {
                Log.e(TAG, "Failed to start discovery")
                _state.emit(BluetoothState.Error("Failed to start discovery", null))
                onError("Failed to start discovery")
                return@launch
            }

            _state.emit(BluetoothState.Scanning)
            Log.d(TAG, "Discovery started")

            // Register IntentFilter for ACTION_FOUND to discover devices
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    try {
                        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                        device?.let {
                            // Only emit if this is a new device
                            if (discoveredDevicesSet.add(device.address)) {
                                _discoveredDevices.tryEmit(it)
                                onDeviceDiscovered(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing discovered device", e)
                        onError("Error processing discovered device: ${e.message}")
                    }
                }
            }
            
            try {
                context.registerReceiver(discoveryReceiver, filter)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register discovery receiver", e)
                _state.emit(BluetoothState.Error("Failed to register discovery receiver", e))
                onError("Failed to register discovery receiver")
                stopDiscovery()
                return@launch
            }

            // Also register for discovery ended
            val filter2 = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    Log.d(TAG, "Discovery finished")
                    _state.tryEmit(BluetoothState.Idle)
                }
            }, filter2)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting discovery", e)
            _state.emit(BluetoothState.Error("Error starting discovery: ${e.message}", e))
            onError("Error starting discovery: ${e.message}")
        }
    }
}
```

### 10. Update stopDiscovery() to Clear Set

**Replace stopDiscovery() method:**
```kotlin
/**
 * Stop device discovery.
 */
fun stopDiscovery() {
    try {
        bluetoothAdapter?.cancelDiscovery()
        Log.d(TAG, "Discovery cancelled")
    } catch (e: Exception) {
        Log.e(TAG, "Error cancelling discovery", e)
    }
    
    // Unregister the discovery receiver if it was registered
    discoveryReceiver?.let { receiver ->
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Discovery receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Discovery receiver was not registered")
        }
    }
    discoveryReceiver = null
    
    // Clear discovered devices set
    discoveredDevicesSet.clear()
    
    _state.tryEmit(BluetoothState.Idle)
}
```

### 11. Update stop() Method

**Replace stop() method:**
```kotlin
/**
 * Stop all Bluetooth operations and clean up resources.
 */
fun stop() {
    Log.d(TAG, "Stopping all Bluetooth operations")
    unregisterBluetoothStateReceiver()
    stopServer()
    stopDiscovery()
    disconnect()
    _transferChannel.close()
}
```

---

## 📊 Summary of Changes

| Change | File | Lines | Description |
|--------|------|-------|-------------|
| Add imports | BluetoothCoinTransfer.kt | Top | Add logging and timeout imports |
| Add constants | BluetoothCoinTransfer.kt | Companion | Add TAG, timeout, max message size |
| Add fields | BluetoothCoinTransfer.kt | Class | Add bluetoothStateReceiver, discoveredDevicesSet |
| Update ConnectedThread | BluetoothCoinTransfer.kt | 380-420 | Improve thread management and cleanup |
| Update stopServer() | BluetoothCoinTransfer.kt | 145-155 | Add proper cleanup and logging |
| Update disconnect() | BluetoothCoinTransfer.kt | 175-185 | Add proper cleanup and logging |
| Update connectToDevice() | BluetoothCoinTransfer.kt | 157-180 | Add connection timeout |
| Update startServer() | BluetoothCoinTransfer.kt | 115-145 | Prevent race conditions |
| Add Bluetooth state monitoring | BluetoothCoinTransfer.kt | New | Monitor Bluetooth adapter state |
| Update startDiscovery() | BluetoothCoinTransfer.kt | 230-280 | Better error handling and duplicate prevention |
| Update stopDiscovery() | BluetoothCoinTransfer.kt | 285-295 | Clear discovered devices set |
| Update stop() | BluetoothCoinTransfer.kt | End | Add cleanup of state receiver |

---

## 🧪 Testing the Changes

After implementing these changes, test the following scenarios:

### Manual Testing
1. **Connection Test**: Connect two devices and transfer a coin
2. **Timeout Test**: Try to connect to a non-existent device - should timeout after 30 seconds
3. **Bluetooth Off Test**: Turn off Bluetooth during transfer - app should recover gracefully
4. **Malformed Data Test**: Send malformed JSON - app should not crash
5. **Multiple Connections**: Connect and disconnect multiple times - no resource leaks
6. **Server Restart**: Start and stop server multiple times - no race conditions
7. **Device Discovery**: Scan for devices - should show unique devices only

### Automated Testing
Consider adding unit tests for:
- Thread cleanup
- Socket cleanup
- Timeout handling
- Data validation
- Error recovery

---

## 📝 Notes

1. **Logging**: The changes add comprehensive logging. You can adjust the log level (Log.d, Log.e, etc.) as needed.

2. **Timeout Value**: The connection timeout is set to 30 seconds. Adjust this based on your requirements.

3. **Message Size**: The maximum message size is set to 10KB. Adjust this based on the expected size of your coin data.

4. **Thread Safety**: The changes improve thread safety, but you should still be careful when accessing shared state from multiple threads.

5. **Backward Compatibility**: These changes are compatible with Android API 23+ (the app's minSdk).

---

## 🔗 Related Files

- `BluetoothState.kt` - No changes needed, but ensure it's imported correctly
- `CoinSerializer.kt` - No changes needed
- `BluetoothPermissionsHelper.kt` - No changes needed
- `SendScreen.kt` - No changes needed, but will benefit from the fixes
- `ReceiveScreen.kt` - No changes needed, but will benefit from the fixes
