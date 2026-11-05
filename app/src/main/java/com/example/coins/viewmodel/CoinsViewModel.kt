package com.example.coins.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coins.data.model.Coin
import com.example.coins.data.repository.CoinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CoinsViewModel(private val repository: CoinRepository = CoinRepository()) : ViewModel() {
    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance

    private val _coins = MutableStateFlow<List<Coin>>(emptyList())
    val coins: StateFlow<List<Coin>> = _coins

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Initial load of balance from repository (optional)
        viewModelScope.launch {
            repository.balanceEuros.collect { _balance.value = it }
            repository.allCoins.collect { _coins.value = it }
        }
    }

    fun generateCoins(count: Int, giverId: String): Boolean {
        val currentBalance = _balance.value
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
                    giverId = giverId,
                    ownerId = giverId, // initially owned by giver
                    latitude = 0.0, // placeholder
                    longitude = 0.0,
                    isDestroyed = false,
                )
                repository.addCoin(coin)
            }
        }
        return true
    }

    fun clearError() {
        _error.value = null
    }
}
