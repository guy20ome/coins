package com.coins.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.coins.data.PreferencesHelper
import com.coins.location.LocationHelper
import com.coins.model.Coin
import com.coins.ui.theme.CoinsPrimary
import com.coins.viewmodel.CoinsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: CoinsViewModel,
) {
    val context = LocalContext.current
    
    // Collect flows
    var balance by remember { mutableIntStateOf(0) }
    var coins by remember { mutableStateOf(emptyList<Coin>()) }
    val error by viewModel.error.collectAsState()
    
    var showDialog by rememberSaveable { mutableStateOf(value = false) }
    var coinCountInput by rememberSaveable { mutableStateOf("") }
    var gpsCoordinateInput by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Collect flows
    LaunchedEffect(Unit) {
        viewModel.balance.collectLatest { balanceValue ->
            balance = balanceValue
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.coins.collectLatest { coinsList ->
            coins = coinsList
        }
    }
    
    // Get the stored full name
    val fullName = remember { PreferencesHelper.getFullName(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            LocationHelper.getCurrentLocation(context) { loc ->
                gpsCoordinateInput = loc
            }
        }
    }

    fun fetchDeviceLocation() {
        if (LocationHelper.hasLocationPermission(context)) {
            LocationHelper.getCurrentLocation(context) { loc ->
                gpsCoordinateInput = loc
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

    LaunchedEffect(Unit) {
        fetchDeviceLocation()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coins") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Balance: €$balance",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        showDialog = true
                        coinCountInput = ""
                        fetchDeviceLocation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Generate Coins")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("send") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send Coins")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("receiver") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Receive Coins")
                }

                // Display wallet
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Wallet:",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (coins.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        items(coins) { coin ->
                            Text(
                                text = "Coin: €${coin.amountCents / 100} (ID: ${coin.generateCoinId()})",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No coins in wallet yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = "Generate Coins")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your Name: $fullName",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gpsCoordinateInput,
                        onValueChange = { gpsCoordinateInput = it },
                        label = { Text("GPS Coordinate (lat,lng)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = coinCountInput,
                        onValueChange = { coinCountInput = it },
                        label = { Text("Number of €1 coins (max 25)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = coinCountInput.trim().toIntOrNull() ?: 0
                        val success = viewModel.generateCoins(
                            count = count,
                            fullName = fullName,
                            gpsCoordinate = gpsCoordinateInput.trim(),
                            userId = fullName
                        )
                        if (success) {
                            showDialog = false
                            gpsCoordinateInput = ""
                            coinCountInput = ""
                        }
                    },
                ) {
                    Text(text = "Generate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
