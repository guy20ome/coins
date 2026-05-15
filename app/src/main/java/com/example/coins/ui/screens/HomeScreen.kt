package com.example.coins.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.coins.ui.theme.CoinsPrimary
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.coins.viewmodel.CoinsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: CoinsViewModel = viewModel()
) {
    val balance = viewModel.balance.collectAsState(initial = 0)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coins") },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = CoinsPrimary
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Balance: €${balance.value}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { navController.navigate("giver") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate Coins")
                }
                Button(
                    onClick = { navController.navigate("send") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Coins")
                }
                Button(
                    onClick = { navController.navigate("receiver") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Receive Coins")
                }
            }
        }
    )
}
