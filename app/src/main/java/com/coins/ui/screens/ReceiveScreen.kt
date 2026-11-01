package com.coins.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.coins.bluetooth.BluetoothCoinTransfer
import com.coins.bluetooth.TransferResult
import com.coins.data.PreferencesHelper
import com.coins.location.LocationHelper
import com.coins.model.Coin
import com.coins.ui.theme.CoinsPrimary
import com.coins.viewmodel.CoinsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Screen for receiving coins from other users via Bluetooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    navController: NavController,
    appContext: Context,
    viewModel: CoinsViewModel,
) {
    val context = LocalContext.current
    var coins by remember { mutableStateOf(emptyList<Coin>()) }
    var balance by remember { mutableStateOf(0) }
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var discoveredDevices by rememberSaveable { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDevice by rememberSaveable { mutableStateOf<BluetoothDevice?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by rememberSaveable { mutableStateOf<String?>(null) }
    
    val fullName = remember { PreferencesHelper.getFullName(context) }
    var gpsCoordinate by rememberSaveable { mutableStateOf("") }
    
    // Bluetooth transfer instance
    val bluetoothTransfer = remember { BluetoothCoinTransfer.create(context) }
    
    // Collect flows
    LaunchedEffect(Unit) {
        viewModel.coins.collectLatest { coinsList ->
            coins = coinsList
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.balance.collectLatest { balanceValue ->
            balance = balanceValue
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    fun startBluetoothScan() {
        isScanning = true
        discoveredDevices = emptyList()
        
        // Use the new Bluetooth API
        if (bluetoothTransfer.hasBluetoothPermissions()) {
            if (bluetoothTransfer.isBluetoothEnabled()) {
                // Start BLE scanning
                bluetoothTransfer.startDiscovery(
                    onDeviceDiscovered = { device ->
                        // Add device to discovered list if not already present
                        if (!discoveredDevices.any { it.address == device.address }) {
                            discoveredDevices = discoveredDevices + device
                        }
                    },
                    onError = { error ->
                        errorMessage = error
                        isScanning = false
                    }
                )
            } else {
                errorMessage = "Bluetooth is not enabled"
                isScanning = false
            }
        } else {
            errorMessage = "Bluetooth permissions required"
            isScanning = false
        }
    }
    
    // Bluetooth permissions
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            startBluetoothScan()
        } else {
            errorMessage = "Bluetooth permissions required to scan for devices"
        }
    }
    
    // Location permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            LocationHelper.getCurrentLocation(context) { loc ->
                gpsCoordinate = loc
            }
        }
    }
    
    fun checkAndRequestPermissions() {
        // Use the new Bluetooth API
        if (!bluetoothTransfer.isBluetoothAvailable()) {
            errorMessage = "Bluetooth not supported on this device"
            return
        }
        
        if (!bluetoothTransfer.isBluetoothEnabled()) {
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(enableBtIntent)
            } else {
                errorMessage = "Bluetooth permission required to enable Bluetooth"
            }
            return
        }
        
        // Check and request Bluetooth permissions
        if (bluetoothTransfer.hasBluetoothPermissions()) {
            startBluetoothScan()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            } else {
                // For older Android versions, we need location permission
                bluetoothPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }
    }
    
    fun fetchDeviceLocation() {
        if (LocationHelper.hasLocationPermission(context)) {
            LocationHelper.getCurrentLocation(context) { loc ->
                gpsCoordinate = loc
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }
    
    // Fetch location on startup
    LaunchedEffect(Unit) {
        fetchDeviceLocation()
    }
    
    // Show error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }
    
    // Show success messages
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            successMessage = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Coins") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoinsPrimary,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Current balance
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Current Balance",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "€$balance",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                
                // Received coins list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Recently Received Coins",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Filter coins received by this user
                        val receivedCoins = coins.filter { 
                            it.receiverId == fullName && !it.isDestroyed
                        }
                        
                        if (receivedCoins.isEmpty()) {
                            Text(
                                text = "No coins received yet",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                            ) {
                                items(receivedCoins) { coin ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                        ) {
                                            Text(
                                                text = "Coin ID: ${coin.generateCoinId().take(30)}${if (coin.generateCoinId().length > 30) "..." else ""}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = "Value: €${coin.amountCents / 100}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                text = "From: ${coin.giverId}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                text = "Location: ${coin.gpsCoordinate}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bluetooth device selection for receiving
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Select Sender Device",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (discoveredDevices.isEmpty() && !isScanning) {
                            Text(
                                text = "No devices discovered yet",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        } else if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Text(
                                text = "Scanning for sender devices...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp),
                            ) {
                                items(discoveredDevices) { device ->
                                    val isSelected = selectedDevice == device
                                    Card(
                                        onClick = { selectedDevice = device },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedDevice = device },
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = device.name ?: "Unknown Device",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Text(
                                                    text = device.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { checkAndRequestPermissions() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan",
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan for Sender Devices")
                        }
                    }
                }
                
                // Current location
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Your Location",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (gpsCoordinate.isNotEmpty()) gpsCoordinate else "Fetching location...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: You can only receive coins that were generated within 25km of your current location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { fetchDeviceLocation() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Refresh Location")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Receive button - Start server to receive coins
                Button(
                    onClick = {
                        if (gpsCoordinate.isEmpty()) {
                            errorMessage = "Please wait for location to be fetched"
                            return@Button
                        }
                        
                        // Start Bluetooth server to receive coins
                        bluetoothTransfer.startServer(
                            onCoinReceived = { coin ->
                                // Validate the coin's geographical zone
                                if (coin.isWithin25Km(gpsCoordinate)) {
                                    // Coin is valid, add it to the wallet
                                    // In a real implementation, you would update the ViewModel
                                    successMessage = "Received coin: ${coin.generateCoinId()}"
                                } else {
                                    errorMessage = "Coin rejected: Not in your geographical zone"
                                }
                            },
                            onError = { error ->
                                errorMessage = "Server error: $error"
                            }
                        )
                        
                        successMessage = "Server started. Waiting for coins..."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = gpsCoordinate.isNotEmpty(),
                ) {
                    Text("Start Receiving")
                }
            }
        },
    )
}