package com.coins.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.coins.data.PreferencesHelper
import com.coins.location.LocationHelper
import com.coins.ui.theme.CoinsPrimary
import com.coins.viewmodel.CoinsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiverScreen(
    navController: NavController,
    appContext: Context,
    viewModel: CoinsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val balance by viewModel.balance.collectAsState()
    val error by viewModel.error.collectAsState()
    var coinCountInput by rememberSaveable { mutableStateOf("") }
    var gpsCoordinateInput by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
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
                title = { Text("Giver - Generate Coins") },
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
                    text = "Current Balance: €$balance",
                    style = MaterialTheme.typography.headlineSmall,
                )
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
                    label = { Text("Number of €1 coins to generate") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val count = coinCountInput.trim().toIntOrNull() ?: 0
                        val success = viewModel.generateCoins(
                            count = count,
                            fullName = fullName,
                            gpsCoordinate = gpsCoordinateInput.trim()
                        )
                        if (success) {
                            coinCountInput = ""
                            gpsCoordinateInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Generate Coins")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to Home")
                }
            }
        },
    )
}
