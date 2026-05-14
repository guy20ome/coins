package com.example.coins.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CoinsViewModel : ViewModel() {
    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance

    fun updateBalance(newBalance: Int) {
        _balance.value = newBalance
    }
}
