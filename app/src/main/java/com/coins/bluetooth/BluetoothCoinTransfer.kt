package com.coins.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.coins.model.Coin
import com.coins.model.Wallet
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * Handles Bluetooth-based coin transfers between users.
 * Validates geographical zones before transferring coins.
 */
class BluetoothCoinTransfer(private val context: Context? = null) {
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if we have the required Bluetooth permissions
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
    
    /**
     * Start Bluetooth server to receive coin transfers
     */
    fun startServer(onCoinReceived: (Coin) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (!hasBluetoothPermissions(context ?: return@launch)) {
                    onError("Bluetooth permissions required")
                    return@launch
                }
                
                if (!isBluetoothAvailable()) {
                    onError("Bluetooth not available")
                    return@launch
                }
                
                if (!isBluetoothEnabled()) {
                    onError("Bluetooth not enabled")
                    return@launch
                }
                
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
                onError("Error starting server: ${e.message}")
            }
        }
    }
    
    /**
     * Connect to a remote device to send coins
     */
    fun connectToDevice(device: BluetoothDevice, onConnected: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (!hasBluetoothPermissions(context ?: return@launch)) {
                    onError("Bluetooth permissions required")
                    return@launch
                }
                
                if (!isBluetoothAvailable()) {
                    onError("Bluetooth not available")
                    return@launch
                }
                
                if (!isBluetoothEnabled()) {
                    onError("Bluetooth not enabled")
                    return@launch
                }
                
                // Create client socket
                val socket = device.createRfcommSocketToServiceRecord(COIN_TRANSFER_UUID)
                socket.connect()
                clientSocket = socket
                onConnected()
                
                // Keep the socket open for communication
                // In a real implementation, you would send/receive data here
            } catch (e: IOException) {
                onError("Error connecting to device: ${e.message}")
            }
        }
    }
    
    /**
     * Handle a connected socket for data transfer
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
     * Send a coin to the connected device
     */
    fun sendCoin(coin: Coin, onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (clientSocket == null) {
                    onError("Not connected to any device")
                    return@launch
                }
                
                // In a real implementation, you would serialize the coin and send it
                // For now, we'll just simulate the transfer
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                onError("Error sending coin: ${e.message}")
            }
        }
    }
    
    /**
     * Stop all Bluetooth operations
     */
    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: IOException) {
            // Ignore
        }
        
        try {
            clientSocket?.close()
            clientSocket = null
        } catch (e: IOException) {
            // Ignore
        }
        
        connectedThread?.cancel()
        connectedThread = null
    }
    
    /**
     * Thread for handling connected socket communication
     */
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val onCoinReceived: (Coin) -> Unit,
        private val onError: (String) -> Unit
    ) : Thread() {
        
        override fun run() {
            try {
                // In a real implementation, you would read data from the socket
                // and deserialize it into Coin objects
                // For now, we'll just keep the connection open
                val inputStream = socket.inputStream
                val buffer = ByteArray(1024)
                
                while (!isInterrupted) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        // Parse the received data
                        val data = String(buffer, 0, bytes)
                        // TODO: Deserialize coin data and call onCoinReceived
                    }
                }
            } catch (e: IOException) {
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
     *
     * @param senderWallet The wallet of the sender.
     * @param receiverWallet The wallet of the receiver.
     * @param coinId The ID of the coin to transfer.
     * @param senderGps The GPS coordinate of the sender.
     * @param receiverGps The GPS coordinate of the receiver.
     * @return true if the transfer was successful, false otherwise.
     */
    fun transferCoin(
        senderWallet: Wallet,
        receiverWallet: Wallet,
        coinId: String,
        senderGps: String,
        receiverGps: String,
    ): Boolean {
        // Step 1: Validate that both devices are in the same geographical zone
        if (!isSameGeographicalZone(senderGps, receiverGps)) {
            return false
        }

        // Step 2: Find the coin in the sender's wallet
        val coin = senderWallet.getAllCoins().find { it.generateCoinId() == coinId }
            ?: return false

        // Step 3: Validate that the receiver is in the valid zone for this coin
        if (!coin.isWithin25Km(receiverGps)) {
            return false
        }

        // Step 4: Transfer the coin to the receiver's wallet
        val transferSuccess = receiverWallet.receiveCoin(coin, receiverGps)
        if (!transferSuccess) {
            return false
        }

        // Step 5: Mark the coin as sent in the sender's wallet
        return senderWallet.sendCoin(coinId, receiverWallet.userId, receiverGps)
    }

    /**
     * Checks if two GPS coordinates are within the same 25 km geographical zone.
     *
     * @param gps1 The first GPS coordinate (latitude,longitude).
     * @param gps2 The second GPS coordinate (latitude,longitude).
     * @return true if both coordinates are within 25 km of each other, false otherwise.
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
     * @param lat1 Latitude of the first coordinate.
     * @param lon1 Longitude of the first coordinate.
     * @param lat2 Latitude of the second coordinate.
     * @param lon2 Longitude of the second coordinate.
     * @return Distance in kilometers.
     */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}