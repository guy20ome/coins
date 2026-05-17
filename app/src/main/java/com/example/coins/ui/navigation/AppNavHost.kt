package com.example.coins.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.coins.ui.screens.GiverScreen
import com.example.coins.ui.screens.HomeScreen
import com.example.coins.viewmodel.CoinsViewModel

@Composable
fun AppNavHost(navController: NavHostController) {
    val coinsViewModel: CoinsViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                navController = navController,
                viewModel = coinsViewModel,
            )
        }
        composable("giver") {
            GiverScreen(
                navController = navController,
                viewModel = coinsViewModel,
            )
        }
        composable("send") {
            // Placeholder for Send screen
        }
        composable("receiver") {
            // Placeholder for Receiver screen
        }
    }
}
