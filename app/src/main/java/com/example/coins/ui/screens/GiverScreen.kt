package com.example.coins.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.coins.viewmodel.CoinsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiverScreen(
    navController: NavController,
    viewModel: CoinsViewModel,
) {
    var amountText by remember { mutableStateOf("") }
    val balance by viewModel.balance.collectAsState()
    val maxCanGenerate = 25 - balance
    
    var showError by remember { mutableStateOf(value = false) }
    var errorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Generate Coins") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Current Balance: €$balance")
            Text(text = "You can generate up to €$maxCanGenerate more.")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = amountText,
                onValueChange = { 
                    amountText = it
                    showError = false
                },
                label = { Text("Amount to generate (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                },
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    val amount = amountText.toIntOrNull()
                    if ((amount == null) || (amount <= 0)) {
                        errorMessage = "Please enter a valid amount"
                        showError = true
                    } else if ((balance + amount) > 25) {
                        errorMessage = "Total balance cannot exceed €25"
                        showError = true
                    } else {
                        viewModel.generateCoins(amount, giverId = "default-giver")
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = maxCanGenerate > 0,
            ) {
                Text("Generate")
            }
        }
    }
}
