package com.coins.data.repository

import android.content.Context
import com.coins.data.dao.CoinDao
import com.coins.data.database.AppDatabase
import com.coins.model.Coin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Repository for managing coins using Room Database for persistence.
 * Provides reactive data access and business logic for coin operations.
 */
class CoinRepository private constructor(
    private val coinDao: CoinDao
) {
    
    // Use SupervisorJob so that one failing operation doesn't cancel others
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Private mutable state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Public reactive data
    val allCoins: Flow<List<Coin>> = coinDao.getAll()
    
    /**
     * Get the total balance in euros (sum of non-destroyed coins).
     */
    val balanceEuros: Flow<Int> = coinDao.getNonDestroyed().map { coins ->
        coins.sumOf { it.amountCents } / 100 // Convert cents to euros
    }
    
    /**
     * Get coins by owner.
     */
    fun getCoinsByOwner(ownerId: String): Flow<List<Coin>> = coinDao.getByOwner(ownerId)
    
    /**
     * Get coins by giver.
     */
    fun getCoinsByGiver(giverId: String): Flow<List<Coin>> = coinDao.getByGiver(giverId)
    
    /**
     * Get active (non-destroyed) coins by giver.
     */
    fun getActiveCoinsByGiver(giverId: String): Flow<List<Coin>> = coinDao.getActiveCoinsByGiver(giverId)
    
    /**
     * Get the count of active coins for a giver.
     */
    suspend fun getActiveCoinCountByGiver(giverId: String): Int {
        return coinDao.getActiveCoinCountByGiver(giverId)
    }
    
    /**
     * Get the total value of active coins for a giver.
     */
    suspend fun getActiveValueByGiver(giverId: String): Int {
        return coinDao.getActiveValueByGiver(giverId)
    }
    
    /**
     * Add a new coin to the database.
     */
    fun addCoin(coin: Coin) {
        scope.launch {
            try {
                println("CoinRepository: Adding coin ${coin.coinId}")
                coinDao.insert(coin)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to add coin: ${e.message}"
                println("CoinRepository: Error adding coin: ${e.message}")
            }
        }
    }
    
    /**
     * Add multiple coins to the database.
     */
    fun addCoins(coins: List<Coin>) {
        scope.launch {
            try {
                println("CoinRepository: Adding ${coins.size} coins")
                coinDao.insertAll(coins)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to add coins: ${e.message}"
                println("CoinRepository: Error adding coins: ${e.message}")
            }
        }
    }
    
    /**
     * Update a coin in the database.
     */
    fun updateCoin(coin: Coin) {
        scope.launch {
            try {
                println("CoinRepository: Updating coin ${coin.coinId}")
                coinDao.update(coin)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to update coin: ${e.message}"
                println("CoinRepository: Error updating coin: ${e.message}")
            }
        }
    }
    
    /**
     * Delete a coin from the database.
     */
    fun deleteCoin(coin: Coin) {
        scope.launch {
            try {
                println("CoinRepository: Deleting coin ${coin.coinId}")
                coinDao.delete(coin)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete coin: ${e.message}"
                println("CoinRepository: Error deleting coin: ${e.message}")
            }
        }
    }
    
    /**
     * Delete a coin by ID.
     */
    fun deleteCoinById(coinId: String) {
        scope.launch {
            try {
                println("CoinRepository: Deleting coin by ID: $coinId")
                coinDao.deleteById(coinId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete coin: ${e.message}"
                println("CoinRepository: Error deleting coin: ${e.message}")
            }
        }
    }
    
    /**
     * Get a coin by its ID.
     */
    suspend fun getCoinById(coinId: String): Coin? {
        return try {
            coinDao.getById(coinId)
        } catch (e: Exception) {
            _error.value = "Failed to get coin: ${e.message}"
            null
        }
    }
    
    /**
     * Get a coin by its generated ID (formatted ID).
     */
    suspend fun getCoinByGeneratedId(generatedId: String): Coin? {
        return try {
            coinDao.getByGeneratedId(generatedId)
        } catch (e: Exception) {
            _error.value = "Failed to get coin: ${e.message}"
            null
        }
    }
    
    /**
     * Get all coins as a list (non-reactive).
     */
    suspend fun getAllCoinsList(): List<Coin> {
        return try {
            coinDao.getAllList()
        } catch (e: Exception) {
            _error.value = "Failed to get coins: ${e.message}"
            emptyList()
        }
    }
    
    /**
     * Clear all errors.
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Delete all coins from the database.
     * WARNING: This will remove all coins permanently.
     */
    fun deleteAllCoins() {
        scope.launch {
            try {
                println("CoinRepository: Deleting all coins")
                coinDao.deleteAll()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete all coins: ${e.message}"
                println("CoinRepository: Error deleting all coins: ${e.message}")
            }
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: CoinRepository? = null
        
        /**
         * Get the singleton repository instance.
         * 
         * @param context The application context (used to get the database)
         * @return The repository instance
         */
        fun getInstance(context: Context): CoinRepository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getDatabase(context)
                val instance = CoinRepository(database.coinDao())
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Reset the repository instance.
         * Useful for testing.
         */
        fun resetInstance() {
            INSTANCE = null
        }
        
        /**
         * Legacy instance for backward compatibility.
         * Note: This uses an in-memory database and will be deprecated.
         */
        @Deprecated(
            message = "Use getInstance(context) for persistent storage",
            replaceWith = ReplaceWith("getInstance(context)")
        )
        val instance: CoinRepository
            get() = throw UnsupportedOperationException(
                "Legacy instance is no longer supported. Use getInstance(context) instead."
            )
    }
}