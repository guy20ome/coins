package com.coins.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coins.data.repository.CoinRepository
import com.coins.model.Coin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CoinsViewModel(private val repository: CoinRepository = CoinRepository.instance) : ViewModel() {
    val balance: StateFlow<Int> = repository.balanceEuros
    val coins: StateFlow<List<Coin>> = repository.allCoins

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun generateCoins(count: Int, fullName: String, gpsCoordinate: String): Boolean {
        val currentBalance = balance.value
        if ((currentBalance + count) > 25) {
            _error.value = "Cannot exceed €25 limit (currently €$currentBalance)"
            return false
        }
        if (count <= 0) {
            _error.value = "Count must be positive"
            return false
        }
        _error.value = null
        viewModelScope.launch {
            repeat(count) {
                val coin = Coin(
                    coinId = UUID.randomUUID().toString(),
                    amountCents = 100, // €1.00
                    ownerId = fullName, // using fullName as owner identifier
                    latitude = 0.0, // placeholder
                    longitude = 0.0,
                    isDestroyed = false,
                    fullName = fullName,
                    gpsCoordinate = gpsCoordinate,
                    timestamp = System.currentTimeMillis(),
                )
                repository.addCoin(coin)
                println("Generated coin: ${coin.generateCoinId()}")
            }
        }
        return true
    }

    fun clearError() {
        _error.value = null
    }
}
