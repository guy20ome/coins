package com.example.coins.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.coins.ui.screens.HomeScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("giver") {
            // Placeholder for Giver screen
        }
        composable("send") {
            // Placeholder for Send screen
        }
        composable("receiver") {
            // Placeholder for Receiver screen
        }
    }
}
