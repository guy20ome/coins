package com.coins.bluetooth

import android.annotation.SuppressLint
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
import android.util.Log
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 * Constants for Bluetooth operations
 */
private const val TAG = "BluetoothCoinTransfer"
private const val CONNECTION_TIMEOUT_MS = 30000L // 30 seconds
private const val MAX_MESSAGE_SIZE = 1024 * 10 // 10KB
private const val HANDSHAKE_TIMEOUT_MS = 10000L // 10 seconds for handshake
private const val ACK_TIMEOUT_MS = 5000L // 5 seconds for acknowledgment

/**
 * Protocol message types
 */
internal const val MESSAGE_TYPE_HANDSHAKE = "HANDSHAKE"
internal const val MESSAGE_TYPE_HANDSHAKE_RESPONSE = "HANDSHAKE_RESPONSE"
internal const val MESSAGE_TYPE_ACK = "ACK"
internal const val MESSAGE_TYPE_NACK = "NACK"
internal const val MESSAGE_TYPE_COIN = "COIN"
internal const val MESSAGE_DELIMITER = "\n"

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
 * Internal message types for the protocol
 */
internal sealed class ProtocolMessage {
    data class Handshake(val appName: String = "CoinTransfer") : ProtocolMessage()
    data class HandshakeResponse(val version: String = "1.0") : ProtocolMessage()
    data class Acknowledgment(val messageId: String) : ProtocolMessage()
    data class NegativeAcknowledgment(val messageId: String, val reason: String) : ProtocolMessage()
    data class CoinMessage(val messageId: String, val coin: Coin) : ProtocolMessage()
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
    
    // Bluetooth state monitoring
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    
    // Track discovered devices to avoid duplicates
    private val discoveredDevicesSet = mutableSetOf<String>()
    
    // Message ID counter for tracking
    private var messageCounter = 0
    
    /**
     * Generate a unique message ID
     */
    internal fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${++messageCounter}"
    }
    
    /**
     * Serialize a protocol message to string
     */
    internal fun serializeProtocolMessage(message: ProtocolMessage): String {
        return when (message) {
            is ProtocolMessage.Handshake -> "$MESSAGE_TYPE_HANDSHAKE:${message.appName}"
            is ProtocolMessage.HandshakeResponse -> "$MESSAGE_TYPE_HANDSHAKE_RESPONSE:${message.version}"
            is ProtocolMessage.Acknowledgment -> "$MESSAGE_TYPE_ACK:${message.messageId}"
            is ProtocolMessage.NegativeAcknowledgment -> "$MESSAGE_TYPE_NACK:${message.messageId}:${message.reason}"
            is ProtocolMessage.CoinMessage -> "$MESSAGE_TYPE_COIN:${message.messageId}:${coinSerializer.serialize(message.coin)}"
        }
    }
    
    /**
     * Parse a protocol message from string
     */
    internal fun parseProtocolMessage(message: String): ProtocolMessage? {
        return try {
            val parts = message.split(":", limit = 3)
            when (parts[0]) {
                MESSAGE_TYPE_HANDSHAKE -> ProtocolMessage.Handshake(parts.getOrElse(1) { "CoinTransfer" })
                MESSAGE_TYPE_HANDSHAKE_RESPONSE -> ProtocolMessage.HandshakeResponse(parts.getOrElse(1) { "1.0" })
                MESSAGE_TYPE_ACK -> ProtocolMessage.Acknowledgment(parts[1])
                MESSAGE_TYPE_NACK -> ProtocolMessage.NegativeAcknowledgment(parts[1], parts.getOrElse(2) { "Unknown error" })
                MESSAGE_TYPE_COIN -> {
                    if (parts.size >= 3) {
                        val messageId = parts[1]
                        val coinJson = parts.drop(2).joinToString(":")
                        val coin = coinSerializer.deserialize(coinJson)
                        if (coin != null) ProtocolMessage.CoinMessage(messageId, coin) else null
                    } else {
                        // Legacy format without messageId (should not happen with new code)
                        val coinJson = parts.drop(1).joinToString(":")
                        val coin = coinSerializer.deserialize(coinJson)
                        if (coin != null) ProtocolMessage.CoinMessage("", coin) else null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing protocol message", e)
            null
        }
    }
    
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
        return hasBluetoothPermissions(android.os.Build.VERSION.SDK_INT)
    }

    /**
     * Check if we have the required Bluetooth permissions for the given SDK version.
     *
     * @param sdkInt The Android SDK version to check against
     * @return true if all required permissions are granted for this SDK version
     */
    @SuppressLint("InlinedApi")
    internal fun hasBluetoothPermissions(sdkInt: Int): Boolean {
        if (sdkInt >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        // Older versions only need BLUETOOTH and BLUETOOTH_ADMIN
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Safely get the device name or fallback to its MAC address or unknown.
     */
    @SuppressLint("MissingPermission")
    fun BluetoothDevice.getSafeName(): String {
        return try {
            name ?: address ?: "Unknown Device"
        } catch (e: SecurityException) {
            address ?: "Unknown Device"
        }
    }
    
    /**
     * Start Bluetooth server to receive coin transfers.
     * This creates a listening socket that accepts incoming connections.
     * 
     * @param onCoinReceived Callback when a coin is received
     * @param onError Callback when an error occurs
     */
    @SuppressLint("MissingPermission")
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
                
                tempServerSocket?.let { socket ->
                    Log.d(TAG, "Server waiting for connection...")
                    onError("Waiting for connection...")
                    
                    // Server loop - keep accepting connections
                    var shouldContinue = true
                    while (shouldContinue && !Thread.currentThread().isInterrupted) {
                        try {
                            val clientSocket = socket.accept()
                            Log.d(TAG, "Server accepted connection from ${clientSocket.remoteDevice?.getSafeName()}")
                            handleConnectedSocket(clientSocket, onCoinReceived, onError)
                        } catch (e: IOException) {
                            Log.e(TAG, "Error accepting connection", e)
                            shouldContinue = false
                        }
                    }
                    
                    Log.d(TAG, "Server loop exited")
                }
                
                // Clear reference when done
                serverSocket = null
                Log.d(TAG, "Server stopped")
                
            } catch (e: SecurityException) {
                serverSocket = null
                Log.e(TAG, "Permission denied starting server", e)
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required: ${e.message}")
            } catch (e: IOException) {
                serverSocket = null
                Log.e(TAG, "Error starting server", e)
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
            Log.d(TAG, "Server socket closed")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket", e)
        } finally {
            serverSocket = null
            _state.tryEmit(BluetoothState.Idle)
        }
    }
    
    /**
     * Connect to a remote device to send coins.
     * 
     * @param device The device to connect to
     * @param onConnected Callback when connection is established
     * @param onCoinReceived Callback when a coin is received (optional, for bidirectional communication)
     * @param onError Callback when an error occurs
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onCoinReceived: (Coin) -> Unit = {},
        onError: (String) -> Unit
    ) {
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
                Log.d(TAG, "Connecting to device: ${device.getSafeName()}")
                
                // Create client socket
                val socket = device.createRfcommSocketToServiceRecord(COIN_TRANSFER_UUID)
                
                // Connect with timeout
                try {
                    withTimeout(CONNECTION_TIMEOUT_MS) {
                        socket.connect()
                    }
                    clientSocket = socket
                    Log.d(TAG, "Connected to device: ${device.getSafeName()}")
                    
                    // Start the connected thread to handle communication
                    handleConnectedSocket(socket, onCoinReceived, onError)
                    
                    _state.emit(BluetoothState.Connected(device))
                    onConnected()
                    
                } catch (e: TimeoutCancellationException) {
                    socket.close()
                    throw IOException("Connection timed out after ${CONNECTION_TIMEOUT_MS}ms")
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied connecting to device", e)
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required: ${e.message}")
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to device", e)
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
     * Uses protocol with ACK/NACK for reliable delivery.
     * 
     * @param coin The coin to send
     * @param onSuccess Callback when coin is sent and acknowledged
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
                
                if (connectedThread == null) {
                    _transferChannel.send(TransferResult.Error("No active connection thread"))
                    onError("No active connection thread")
                    return@launch
                }
                
                // Validate coin before sending
                if (coin.coinId.isEmpty() || coin.giverId.isEmpty()) {
                    _transferChannel.send(TransferResult.Error("Invalid coin data"))
                    onError("Invalid coin data")
                    return@launch
                }
                
                Log.d(TAG, "Sending coin with protocol: ${coin.coinId}")
                _state.emit(BluetoothState.Transferring(coin.coinId, 0))
                
                // Send coin with protocol and wait for ACK
                val messageId = generateMessageId()
                
                // Use a channel to wait for ACK/NACK
                val ackChannel = Channel<Pair<Boolean, String?>>(Channel.UNLIMITED)
                
                // Register callback with connected thread
                connectedThread?.sendCoinMessage(coin) { success, errorMessage ->
                    scope.launch {
                        ackChannel.send(success to errorMessage)
                    }
                }
                
                // Wait for ACK/NACK with timeout
                try {
                    withTimeout(ACK_TIMEOUT_MS) {
                        val (success, errorMessage) = ackChannel.receive()
                        if (success) {
                            Log.d(TAG, "Coin acknowledged: ${coin.coinId}")
                            _state.emit(BluetoothState.TransferComplete(coin.coinId))
                            _transferChannel.send(TransferResult.Success(coin))
                            onSuccess()
                        } else {
                            Log.e(TAG, "Coin rejected: ${coin.coinId}, reason: $errorMessage")
                            _transferChannel.send(TransferResult.Error(errorMessage ?: "Transfer rejected"))
                            onError(errorMessage ?: "Transfer rejected")
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "ACK timeout for coin: ${coin.coinId}")
                    _transferChannel.send(TransferResult.Error("Transfer timeout: No acknowledgment received"))
                    onError("Transfer timeout: No acknowledgment received after ${ACK_TIMEOUT_MS}ms")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending coin", e)
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

    /**
     * Start discovering nearby Bluetooth devices.
     *
     * @param onDeviceDiscovered Callback when a device is discovered
     * @param onError Callback when an error occurs
     */
    @SuppressLint("MissingPermission")
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
                
                if (bluetoothAdapter?.startDiscovery() != true) {
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

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied starting discovery", e)
                _state.emit(BluetoothState.PermissionsDenied)
                onError("Bluetooth permissions required: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting discovery", e)
                _state.emit(BluetoothState.Error("Error starting discovery: ${e.message}", e))
                onError("Error starting discovery: ${e.message}")
            }
        }
    }
    
    /**
     * Stop device discovery.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            Log.d(TAG, "Discovery cancelled")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied cancelling discovery", e)
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
    
    /**
     * Thread for handling connected socket communication.
     * Implements handshake protocol and message acknowledgment.
     */
    @SuppressLint("MissingPermission")
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val onCoinReceived: (Coin) -> Unit,
        private val onError: (String) -> Unit
    ) : Thread() {
        
        private var isRunning = true
        private var inputStream: InputStream? = null
        private var outputStream: java.io.OutputStream? = null
        private var handshakeComplete = false
        private var isSender = false
        
        // Map of message IDs to callbacks for ACK/NACK
        private val pendingAcks = mutableMapOf<String, (Boolean, String?) -> Unit>()
        
        override fun run() {
            try {
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                val buffer = ByteArray(1024)
                var data = ""
                
                Log.d(TAG, "ConnectedThread started for ${socket.remoteDevice?.getSafeName()}")
                
                // Determine if we're the sender (initiated connection) or receiver
                isSender = socket == clientSocket
                
                // If we're the receiver (server), wait for handshake from sender
                // If we're the sender (client), send handshake
                if (isSender) {
                    // Sender: send handshake
                    sendHandshake()
                }
                
                // Main read loop
                while (isRunning && !isInterrupted) {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        data += String(buffer, 0, bytes, Charsets.UTF_8)
                        
                        // Check for size limit
                        if (data.length > MAX_MESSAGE_SIZE) {
                            Log.e(TAG, "Message too large: ${data.length} bytes")
                            sendNack("msg_overflow", "Message too large")
                            _state.tryEmit(BluetoothState.Error("Message too large", null))
                            onError("Message too large")
                            return
                        }
                        
                        // Check for delimiter (newline)
                        if (data.contains(MESSAGE_DELIMITER)) {
                            val parts = data.split(MESSAGE_DELIMITER)
                            for (part in parts.dropLast(1)) {
                                if (part.isNotEmpty()) {
                                    processProtocolMessage(part)
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
        
        /**
         * Process an incoming protocol message
         */
        private fun processProtocolMessage(message: String) {
            try {
                val protocolMessage = parseProtocolMessage(message)
                when (protocolMessage) {
                    is ProtocolMessage.Handshake -> {
                        Log.d(TAG, "Received handshake from ${socket.remoteDevice?.getSafeName()}")
                        // Receiver: respond to handshake
                        if (!isSender) {
                            sendHandshakeResponse()
                            handshakeComplete = true
                            _state.tryEmit(BluetoothState.Connected(socket.remoteDevice))
                        }
                    }
                    is ProtocolMessage.HandshakeResponse -> {
                        Log.d(TAG, "Received handshake response")
                        // Sender: handshake complete
                        if (isSender) {
                            handshakeComplete = true
                            _state.tryEmit(BluetoothState.Connected(socket.remoteDevice))
                        }
                    }
                    is ProtocolMessage.CoinMessage -> {
                        Log.d(TAG, "Received coin: ${protocolMessage.coin.coinId} with messageId: ${protocolMessage.messageId}")
                        // Validate coin
                        if (protocolMessage.coin.coinId.isNotEmpty() && protocolMessage.coin.giverId.isNotEmpty()) {
                            // Send ACK with the messageId from the received message
                            sendAck(protocolMessage.messageId)
                            
                            // Notify callbacks
                            _state.tryEmit(BluetoothState.CoinReceived(protocolMessage.coin))
                            onCoinReceived(protocolMessage.coin)
                            _transferChannel.trySend(TransferResult.Success(protocolMessage.coin))
                        } else {
                            Log.e(TAG, "Received invalid coin data")
                            sendNack(protocolMessage.messageId, "Invalid coin data")
                            onError("Received invalid coin data")
                        }
                    }
                    is ProtocolMessage.Acknowledgment -> {
                        Log.d(TAG, "Received ACK for ${protocolMessage.messageId}")
                        // Notify waiting callback
                        pendingAcks[protocolMessage.messageId]?.invoke(true, null)
                        pendingAcks.remove(protocolMessage.messageId)
                    }
                    is ProtocolMessage.NegativeAcknowledgment -> {
                        Log.e(TAG, "Received NACK for ${protocolMessage.messageId}: ${protocolMessage.reason}")
                        // Notify waiting callback
                        pendingAcks[protocolMessage.messageId]?.invoke(false, protocolMessage.reason)
                        pendingAcks.remove(protocolMessage.messageId)
                    }
                    null -> {
                        Log.e(TAG, "Received unknown message: $message")
                        onError("Received unknown message")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing protocol message", e)
                onError("Error processing message: ${e.message}")
            }
        }
        
        /**
         * Send handshake message (called by sender)
         */
        private fun sendHandshake() {
            try {
                val handshake = ProtocolMessage.Handshake()
                val message = serializeProtocolMessage(handshake)
                outputStream?.write("$message$MESSAGE_DELIMITER".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent handshake")
                
                // Start handshake timeout
                // Note: In a real implementation, we'd wait for response here
                // For now, we just mark it as complete after sending
                // The actual handshake completion is handled in processProtocolMessage
            } catch (e: IOException) {
                Log.e(TAG, "Error sending handshake", e)
                onError("Error sending handshake: ${e.message}")
            }
        }
        
        /**
         * Send handshake response (called by receiver)
         */
        private fun sendHandshakeResponse() {
            try {
                val response = ProtocolMessage.HandshakeResponse()
                val message = serializeProtocolMessage(response)
                outputStream?.write("$message$MESSAGE_DELIMITER".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent handshake response")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending handshake response", e)
                onError("Error sending handshake response: ${e.message}")
            }
        }
        
        /**
         * Send a coin message with tracking
         */
        fun sendCoinMessage(coin: Coin, onAck: (Boolean, String?) -> Unit) {
            try {
                val messageId = generateMessageId()
                val coinMessage = ProtocolMessage.CoinMessage(messageId, coin)
                val message = serializeProtocolMessage(coinMessage)
                
                // Store callback for ACK/NACK
                pendingAcks[messageId] = onAck
                
                outputStream?.write("$message$MESSAGE_DELIMITER".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent coin message with ID: $messageId")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending coin message", e)
                onAck(false, "Error sending coin: ${e.message}")
            }
        }
        
        /**
         * Send ACK for a message
         */
        private fun sendAck(messageId: String) {
            try {
                val ack = ProtocolMessage.Acknowledgment(messageId)
                val message = serializeProtocolMessage(ack)
                outputStream?.write("$message$MESSAGE_DELIMITER".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent ACK for: $messageId")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending ACK", e)
            }
        }
        
        /**
         * Send NACK for a message
         */
        private fun sendNack(messageId: String, reason: String) {
            try {
                val nack = ProtocolMessage.NegativeAcknowledgment(messageId, reason)
                val message = serializeProtocolMessage(nack)
                outputStream?.write("$message$MESSAGE_DELIMITER".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent NACK for: $messageId, reason: $reason")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending NACK", e)
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
            pendingAcks.clear()
            try {
                inputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing input stream", e)
            }
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing output stream", e)
            }
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket", e)
            }
            inputStream = null
            outputStream = null
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
    internal fun isSameGeographicalZone(gps1: String, gps2: String): Boolean {
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
    internal fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
    
    companion object {
        /**
         * Create a BluetoothCoinTransfer instance with the given context.
         * 
         * @param context The application context
         * @return A new BluetoothCoinTransfer instance
         */
        fun create(context: Context): BluetoothCoinTransfer {
            return BluetoothCoinTransfer(context).apply {
                registerBluetoothStateReceiver()
            }
        }
    }
}
