package com.coins.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.coins.data.PreferencesHelper
import com.coins.ui.screens.GiverScreen
import com.coins.ui.screens.HomeScreen
import com.coins.ui.screens.RegistrationScreen
import com.coins.viewmodel.CoinsViewModel

@Composable
fun AppNavHost(navController: NavHostController, appContext: android.content.Context) {
    val coinsViewModel: CoinsViewModel = viewModel()
    val startDestination = if (PreferencesHelper.isRegistered(appContext)) "home" else "registration"

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable("home") {
            HomeScreen(
                navController = navController,
                appContext = appContext,
                viewModel = coinsViewModel,
            )
        }
        composable("giver") {
            GiverScreen(
                navController = navController,
                appContext = appContext,
                viewModel = coinsViewModel,
            )
        }
        composable("send") {
            com.coins.ui.screens.SendScreen(
                navController = navController,
                appContext = appContext,
                viewModel = coinsViewModel,
            )
        }
        composable("receiver") {
            com.coins.ui.screens.ReceiveScreen(
                navController = navController,
                appContext = appContext,
                viewModel = coinsViewModel,
            )
        }
        composable("registration") {
            RegistrationScreen(
                onRegistrationComplete = { fullName, email ->
                    PreferencesHelper.setRegistered(appContext, true)
                    PreferencesHelper.setFullName(appContext, fullName)
                    PreferencesHelper.setEmail(appContext, email)
                    navController.navigate("home") {
                        popUpTo("registration") { inclusive = true }
                    }
                },
            )
        }
        composable("settings") {
            com.coins.ui.screens.SettingsScreen(
                navController = navController,
                appContext = appContext,
            )
        }
    }
}
