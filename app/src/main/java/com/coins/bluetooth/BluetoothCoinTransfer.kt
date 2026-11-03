package com.coins.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.coins.model.Coin
import com.coins.model.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bluetooth service UUID for coin transfer
 */
val COIN_TRANSFER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * Result class for Bluetooth operations
 */
sealed class BluetoothResult {
    data class Success(val data: Any? = null) : BluetoothResult()
    data class Error(val message: String, val exception: Exception? = null) : BluetoothResult()
    object PermissionDenied : BluetoothResult()
    object BluetoothNotAvailable : BluetoothResult()
    object BluetoothNotEnabled : BluetoothResult()
}

/**
 * Result class for transfer operations.
 */
sealed class TransferResult {
    data class Success(val coin: Coin?) : TransferResult()
    data class Error(val message: String) : TransferResult()
}

/**
 * Handles Bluetooth-based coin transfers between users using Classic Bluetooth.
 * Validates geographical zones before transferring coins.
 * 
 * This implementation uses RFCOMM sockets for reliable, connection-oriented
 * communication between devices.
 */
class BluetoothCoinTransfer(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null
    
    private val coinSerializer = CoinSerializer.instance
    
    // State management
    private val _state = MutableStateFlow<BluetoothState>(BluetoothState.Idle)
    val state: Flow<BluetoothState> = _state.asStateFlow()
    
    // Device discovery
    private val _discoveredDevices = MutableSharedFlow<BluetoothDevice>()
    val discoveredDevices: Flow<BluetoothDevice> = _discoveredDevices.asSharedFlow()
    
    // Coin transfer
    private val _transferChannel = Channel<TransferResult>(Channel.UNLIMITED)
    val transferResults: Flow<TransferResult> = _transferChannel.receiveAsFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Check if Bluetooth is available on this device.
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    /**
     * Check if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if we have the required Bluetooth permissions.
     */
    fun hasBluetoothPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
    
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
                
                // Create server socket
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "CoinTransferServer",
                    COIN_TRANSFER_UUID
                )
                
                serverSocket?.use { socket ->
                    onError("Waiting for connection...")
                    val clientSocket = socket.accept()
                    handleConnectedSocket(clientSocket, onCoinReceived, onError)
                }
            } catch (e: IOException) {
                _state.emit(BluetoothState.Error("Error starting server: ${e.message}", e))
                onError("Error starting server: ${e.message}")
            }
        }
    }
    
    /**
     * Stop the Bluetooth server.
     */
    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
            _state.tryEmit(BluetoothState.Idle)
        } catch (_: IOException) {
            // Ignore
        }
    }
    
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
                
                // Create client socket
                val socket = device.createRfcommSocketToServiceRecord(COIN_TRANSFER_UUID)
                socket.connect()
                clientSocket = socket
                
                _state.emit(BluetoothState.Connected(device))
                onConnected()
                
            } catch (e: IOException) {
                _state.emit(BluetoothState.Error("Error connecting to device: ${e.message}", e))
                onError("Error connecting to device: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        try {
            clientSocket?.close()
            clientSocket = null
            connectedThread?.cancel()
            connectedThread = null
            _state.tryEmit(BluetoothState.Idle)
        } catch (e: IOException) {
            // Ignore
        }
    }
    
    /**
     * Handle a connected socket for data transfer.
     */
    private fun handleConnectedSocket(
        socket: BluetoothSocket,
        onCoinReceived: (Coin) -> Unit,
        onError: (String) -> Unit
    ) {
        connectedThread = ConnectedThread(socket, onCoinReceived, onError)
        connectedThread?.start()
    }
    
    /**
     * Send a coin to the connected device.
     * 
     * @param coin The coin to send
     * @param onSuccess Callback when coin is sent successfully
     * @param onError Callback when an error occurs
     */
    fun sendCoin(coin: Coin, onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (clientSocket == null) {
                    _transferChannel.send(TransferResult.Error("Not connected to any device"))
                    onError("Not connected to any device")
                    return@launch
                }
                
                val outputStream = clientSocket?.outputStream
                if (outputStream == null) {
                    _transferChannel.send(TransferResult.Error("Output stream not available"))
                    onError("Output stream not available")
                    return@launch
                }
                
                // Serialize coin to JSON
                val coinJson = coinSerializer.serialize(coin)
                
                // Send the coin data
                outputStream.write(coinJson.toByteArray(Charsets.UTF_8))
                outputStream.write("\n".toByteArray(Charsets.UTF_8)) // Delimiter
                outputStream.flush()
                
                _state.emit(BluetoothState.TransferComplete(coin.coinId))
                _transferChannel.send(TransferResult.Success(coin))
                onSuccess()
                
            } catch (e: Exception) {
                _transferChannel.send(TransferResult.Error("Error sending coin: ${e.message}"))
                onError("Error sending coin: ${e.message}")
            }
        }
    }
    
    /**
     * Start discovering nearby Bluetooth devices.
     *
     * @param onDeviceDiscovered Callback when a device is discovered
     * @param onError Callback when an error occurs
     */
    private var discoveryReceiver: BroadcastReceiver? = null

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

                if (!bluetoothAdapter?.startDiscovery()!!) {
                    onError("Failed to start discovery")
                    return@launch
                }

                _state.emit(BluetoothState.Scanning)

                // Register IntentFilter for ACTION_FOUND to discover devices
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                discoveryReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent) {
                        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                        device?.let {
                            _discoveredDevices.tryEmit(it)
                            onDeviceDiscovered(it)
                        }
                    }
                }
                context.registerReceiver(discoveryReceiver, filter)

                // Also register for discovery ended
                val filter2 = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent) {
                        _state.tryEmit(BluetoothState.Idle)
                    }
                }, filter2)

            } catch (e: Exception) {
                _state.emit(BluetoothState.Error("Error starting discovery: ${e.message}", e))
                onError("Error starting discovery: ${e.message}")
            }
        }
    }
    
    /**
     * Stop device discovery.
     */
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            // Unregister the discovery receiver if it was registered
            discoveryReceiver?.let { receiver ->
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    // Receiver was not registered
                }
            }
            discoveryReceiver = null
            _state.tryEmit(BluetoothState.Idle)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Thread for handling connected socket communication.
     * Reads incoming data and deserializes it into Coin objects.
     */
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val onCoinReceived: (Coin) -> Unit,
        private val onError: (String) -> Unit
    ) : Thread() {
        
        override fun run() {
            try {
                val inputStream: InputStream = socket.inputStream
                val buffer = ByteArray(1024)
                var data = ""
                
                while (!isInterrupted) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        data += String(buffer, 0, bytes, Charsets.UTF_8)
                        
                        // Check for delimiter (newline)
                        if (data.contains("\n")) {
                            val parts = data.split("\n")
                            for (part in parts.dropLast(1)) { // Skip the last incomplete part
                                if (part.isNotEmpty()) {
                                    val coin = coinSerializer.deserialize(part)
                                    if (coin != null) {
                                        _state.tryEmit(BluetoothState.CoinReceived(coin))
                                        onCoinReceived(coin)
                                        _transferChannel.trySend(TransferResult.Success(coin))
                                    }
                                }
                            }
                            data = parts.last() // Keep the incomplete part
                        }
                    }
                }
            } catch (e: IOException) {
                _state.tryEmit(BluetoothState.Error("Connection lost: ${e.message}", e))
                onError("Connection lost: ${e.message}")
            }
        }
        
        fun cancel() {
            interrupt()
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
    
    /**
     * Initiates a coin transfer from the sender's wallet to the receiver's wallet.
     * This method performs geographical validation before transferring.
     * 
     * @param senderWallet The wallet of the sender
     * @param receiverWallet The wallet of the receiver
     * @param coinId The ID of the coin to transfer
     * @param senderGps The GPS coordinate of the sender (lat,lng)
     * @param receiverGps The GPS coordinate of the receiver (lat,lng)
     * @return true if the transfer was successful, false otherwise
     */
    suspend fun transferCoin(
        senderWallet: Wallet,
        receiverWallet: Wallet,
        coinId: String,
        senderGps: String,
        receiverGps: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Step 1: Validate that both devices are in the same geographical zone
        if (!isSameGeographicalZone(senderGps, receiverGps)) {
            _transferChannel.send(TransferResult.Error("Sender and receiver must be in the same geographical zone"))
            return@withContext false
        }

        // Step 2: Find the coin in the sender's wallet
        val coin = senderWallet.getAllCoins().find { it.generateCoinId() == coinId }
            ?: run {
                _transferChannel.send(TransferResult.Error("Coin not found in sender's wallet"))
                return@withContext false
            }

        // Step 3: Validate that the receiver is in the valid zone for this coin
        if (!coin.isWithin25Km(receiverGps)) {
            _transferChannel.send(TransferResult.Error("Receiver is not within 25km of the coin's location"))
            return@withContext false
        }

        // Step 4: Transfer the coin to the receiver's wallet
        val transferSuccess = receiverWallet.receiveCoin(coin, receiverGps)
        if (!transferSuccess) {
            _transferChannel.send(TransferResult.Error("Failed to receive coin"))
            return@withContext false
        }

        // Step 5: Mark the coin as sent in the sender's wallet
        val finalSuccess = senderWallet.sendCoin(coinId, receiverWallet.userId, receiverGps)
        
        if (finalSuccess) {
            _transferChannel.send(TransferResult.Success(coin))
            _state.tryEmit(BluetoothState.TransferComplete(coinId))
        } else {
            _transferChannel.send(TransferResult.Error("Failed to mark coin as sent"))
        }
        
        return@withContext finalSuccess
    }
    
    /**
     * Checks if two GPS coordinates are within the same 25 km geographical zone.
     * 
     * @param gps1 The first GPS coordinate (latitude,longitude)
     * @param gps2 The second GPS coordinate (latitude,longitude)
     * @return true if both coordinates are within 25 km of each other, false otherwise
     */
    private fun isSameGeographicalZone(gps1: String, gps2: String): Boolean {
        val (lat1, lon1) = gps1.split(",").map { it.trim().toDouble() }
        val (lat2, lon2) = gps2.split(",").map { it.trim().toDouble() }
        val distance = haversine(lat1, lon1, lat2, lon2)
        return distance <= 25.0
    }
    
    /**
     * Calculates the distance between two GPS coordinates using the Haversine formula.
     * 
     * @param lat1 Latitude of the first coordinate
     * @param lon1 Longitude of the first coordinate
     * @param lat2 Latitude of the second coordinate
     * @param lon2 Longitude of the second coordinate
     * @return Distance in kilometers
     */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLatOver2 = sin(dLat / 2)
        val sinDLonOver2 = sin(dLon / 2)
        val a = sinDLatOver2 * sinDLatOver2 +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sinDLonOver2 * sinDLonOver2
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
    
    /**
     * Stop all Bluetooth operations and clean up resources.
     */
    fun stop() {
        stopServer()
        stopDiscovery()
        disconnect()
        _transferChannel.close()
    }
    
    companion object {
        /**
         * Create a BluetoothCoinTransfer instance with the given context.
         * 
         * @param context The application context
         * @return A new BluetoothCoinTransfer instance
         */
        fun create(context: Context): BluetoothCoinTransfer {
            return BluetoothCoinTransfer(context)
        }
    }
}
