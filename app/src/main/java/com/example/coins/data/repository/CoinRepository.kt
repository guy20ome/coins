package com.example.coins.data.repository

import com.example.coins.data.model.Coin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Very lightweight in‑memory repository – sufficient for the coin‑generation demo.
 * It keeps a mutable list of coins and updates the balance (in whole euros).
 */
class CoinRepository {
    private val coinList = mutableListOf<Coin>()

    private val _balance = MutableStateFlow(0) // balance in euros
    val balanceEuros: StateFlow<Int> = _balance

    private val _coins = MutableStateFlow<List<Coin>>(emptyList())
    val allCoins: StateFlow<List<Coin>> = _coins

    /** Add a new coin and recalculate the balance. */
    fun addCoin(coin: Coin) {
        coinList.add(coin)
        recalculate()
    }

    private fun recalculate() {
        val totalCents = coinList.asSequence().filter { !it.isDestroyed }.sumOf { it.amountCents }
        _balance.value = totalCents / 100 // convert to euros
        _coins.value = coinList.toList()
    }
}
