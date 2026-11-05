package com.example.coins.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.coins.ui.theme.CoinsPrimary
import com.example.coins.viewmodel.CoinsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: CoinsViewModel = viewModel(),
) {
    val balance by viewModel.balance.collectAsState()
    val error by viewModel.error.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(value = false) }
    var coinCountInput by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

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
                OutlinedTextField(
                    value = coinCountInput,
                    onValueChange = { coinCountInput = it },
                    label = { Text("Number of €1 coins (max 25)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = coinCountInput.trim().toIntOrNull() ?: 0
                        val success = viewModel.generateCoins(count, giverId = "default-giver")
                        if (success) {
                            showDialog = false
                        }
                    },
                ) {
                    Text(text = "Generate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
