package com.coins.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.coins.data.PreferencesHelper
import com.coins.location.LocationHelper
import com.coins.model.Coin
import com.coins.ui.theme.CoinsPrimary
import com.coins.viewmodel.CoinsViewModel

/**
 * Screen for sending coins to other users via Bluetooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    appContext: Context,
    viewModel: CoinsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coins by viewModel.coins.collectAsState()
    val balance by viewModel.balance.collectAsState()
    var availableCoins by rememberSaveable { mutableStateOf<List<Coin>>(emptyList()) }
    var selectedCoinIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var discoveredDevices by rememberSaveable { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDevice by rememberSaveable { mutableStateOf<BluetoothDevice?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by rememberSaveable { mutableStateOf<String?>(null) }
    
    val fullName = remember { PreferencesHelper.getFullName(context) }
    var gpsCoordinate by rememberSaveable { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    fun startBluetoothScan() {
        isScanning = true
        discoveredDevices = emptyList()
        // TODO: Implement actual Bluetooth scanning
        // For now, we'll simulate discovering devices
        // In a real implementation, you would use:
        // val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // bluetoothAdapter.startDiscovery()
        // and register a BroadcastReceiver for ACTION_DISCOVERY_FOUND
        
        // Simulate finding some devices
        isScanning = false
        // This is a placeholder - real implementation would discover actual devices
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
    
    // Location permissions (required for Bluetooth scanning on Android 12+)
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
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter == null) {
            errorMessage = "Bluetooth not supported on this device"
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(enableBtIntent)
            } else {
                errorMessage = "Bluetooth permission required to enable Bluetooth"
            }
            return
        }
        
        // Check Bluetooth permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            // For older Android versions, just start scanning
            startBluetoothScan()
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
    
    // Filter coins that can be sent (not destroyed, owned by current user)
    LaunchedEffect(coins) {
        availableCoins = coins.filter { 
            !it.isDestroyed && (it.ownerId == fullName || it.giverId == fullName)
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
                title = { Text("Send Coins") },
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
                
                // Available coins to send
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Select Coins to Send",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (availableCoins.isEmpty()) {
                            Text(
                                text = "No coins available to send",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                            ) {
                                items(availableCoins) { coin ->
                                    val isSelected = selectedCoinIds.contains(coin.coinId)
                                    Card(
                                        onClick = {
                                            selectedCoinIds = if (isSelected) {
                                                selectedCoinIds - coin.coinId
                                            } else {
                                                selectedCoinIds + coin.coinId
                                            }
                                        },
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
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectedCoinIds = if (isSelected) {
                                                        selectedCoinIds - coin.coinId
                                                    } else {
                                                        selectedCoinIds + coin.coinId
                                                    }
                                                },
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Coin ID: ${coin.generateCoinId().take(30)}${if (coin.generateCoinId().length > 30) "..." else ""}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Text(
                                                    text = "Value: €${coin.amountCents / 100}",
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
                }
                
                // Bluetooth device selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Select Recipient Device",
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
                                text = "Scanning for devices...",
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
                            Text("Scan for Devices")
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
                        Button(
                            onClick = { fetchDeviceLocation() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Refresh Location")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Send button
                Button(
                    onClick = {
                        if (selectedCoinIds.isEmpty()) {
                            errorMessage = "Please select at least one coin to send"
                            return@Button
                        }
                        if (selectedDevice == null) {
                            errorMessage = "Please select a recipient device"
                            return@Button
                        }
                        if (gpsCoordinate.isEmpty()) {
                            errorMessage = "Please wait for location to be fetched"
                            return@Button
                        }
                        
                        // TODO: Implement actual coin transfer via Bluetooth
                        // For now, we'll just show a success message
                        val coinCount = selectedCoinIds.size
                        successMessage = "Successfully sent $coinCount coin(s) to ${selectedDevice?.name ?: "device"}"
                        
                        // Clear selection
                        selectedCoinIds = emptySet()
                        selectedDevice = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedCoinIds.isNotEmpty() && selectedDevice != null && gpsCoordinate.isNotEmpty(),
                ) {
                    Text("Send Selected Coins")
                }
            }
        },
    )
}