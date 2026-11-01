package com.coins.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coins.data.repository.CoinRepository
import com.coins.model.Coin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing coins and wallet operations.
 * Uses CoinRepository for data access and persistence.
 */
class CoinsViewModel(
    private val repository: CoinRepository
) : ViewModel() {
    
    // Reactive data from repository
    val balance: Flow<Int> = repository.balanceEuros
    val coins: Flow<List<Coin>> = repository.allCoins
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Collect repository errors
    init {
        viewModelScope.launch {
            repository.error.collectLatest { errorMessage ->
                if (errorMessage != null) {
                    _error.value = errorMessage
                }
            }
        }
    }

    /**
     * Generate new coins for a user.
     * 
     * @param count Number of coins to generate (each worth €1)
     * @param fullName The full name of the user generating the coins
     * @param gpsCoordinate The GPS coordinate where coins are generated (lat,lng)
     * @param userId The unique identifier of the user
     * @return true if coins were generated successfully, false otherwise
     */
    fun generateCoins(count: Int, fullName: String, gpsCoordinate: String, userId: String = fullName): Boolean {
        if (count <= 0) {
            _error.value = "Count must be positive"
            return false
        }
        
        // Check the 25€ limit asynchronously
        viewModelScope.launch {
            try {
                val currentValue = repository.getActiveValueByGiver(userId)
                val currentEuros = currentValue / 100
                
                if ((currentEuros + count) > 25) {
                    _error.value = "Cannot exceed €25 limit (currently €$currentEuros)"
                    return@launch
                }
                
                _error.value = null
                val coinsToAdd = mutableListOf<Coin>()
                
                repeat(count) {
                    val coin = Coin(
                        coinId = UUID.randomUUID().toString(),
                        amountCents = 100, // €1.00
                        giverId = userId,
                        ownerId = userId,
                        latitude = 0.0,
                        longitude = 0.0,
                        isDestroyed = false,
                        fullName = fullName,
                        gpsCoordinate = gpsCoordinate,
                        timestamp = System.currentTimeMillis(),
                        receiverId = null,
                        status = com.coins.model.CoinStatus.GENERATED,
                    )
                    coinsToAdd.add(coin)
                    println("Generated coin: ${coin.generateCoinId()}")
                }
                
                repository.addCoins(coinsToAdd)
            } catch (e: Exception) {
                _error.value = "Failed to generate coins: ${e.message}"
            }
        }
        
        return true
    }
    
    /**
     * Send a coin to another user.
     * 
     * @param coinId The ID of the coin to send
     * @param receiverId The ID of the receiver
     * @param receiverGps The GPS coordinate of the receiver
     * @param senderGps The GPS coordinate of the sender
     * @return true if the coin was sent successfully, false otherwise
     */
    suspend fun sendCoin(coinId: String, receiverId: String, receiverGps: String, senderGps: String): Boolean {
        return try {
            val coin = repository.getCoinByGeneratedId(coinId) ?: return false
            
            // Validate geographical zone
            if (!coin.isWithin25Km(receiverGps)) {
                _error.value = "Receiver is not within 25km of the coin's location"
                return false
            }
            
            // Validate sender and receiver are in same zone
            if (!isSameGeographicalZone(senderGps, receiverGps)) {
                _error.value = "Sender and receiver must be in the same geographical zone"
                return false
            }
            
            // Update coin status
            coin.markAsSent(receiverId)
            repository.updateCoin(coin)
            
            _error.value = null
            true
        } catch (e: Exception) {
            _error.value = "Failed to send coin: ${e.message}"
            false
        }
    }
    
    /**
     * Receive a coin from another user.
     * 
     * @param coin The coin to receive
     * @param receiverGps The GPS coordinate of the receiver
     * @return true if the coin was received successfully, false otherwise
     */
    suspend fun receiveCoin(coin: Coin, receiverGps: String): Boolean {
        return try {
            // Validate geographical zone
            if (!coin.isWithin25Km(receiverGps)) {
                _error.value = "Coin is not within 25km of your location"
                return false
            }
            
            // Update coin owner
            coin.ownerId = coin.receiverId
            repository.updateCoin(coin)
            
            _error.value = null
            true
        } catch (e: Exception) {
            _error.value = "Failed to receive coin: ${e.message}"
            false
        }
    }
    
    /**
     * Destroy a coin (cash out).
     * 
     * @param coinId The ID of the coin to destroy
     * @return true if the coin was destroyed successfully, false otherwise
     */
    suspend fun destroyCoin(coinId: String): Boolean {
        return try {
            val coin = repository.getCoinByGeneratedId(coinId) ?: return false
            coin.markAsDestroyed()
            repository.updateCoin(coin)
            
            _error.value = null
            true
        } catch (e: Exception) {
            _error.value = "Failed to destroy coin: ${e.message}"
            false
        }
    }
    
    /**
     * Get coins by owner.
     */
    fun getCoinsByOwner(ownerId: String): Flow<List<Coin>> {
        return repository.getCoinsByOwner(ownerId)
    }
    
    /**
     * Get active coins by giver.
     */
    fun getActiveCoinsByGiver(giverId: String): Flow<List<Coin>> {
        return repository.getActiveCoinsByGiver(giverId)
    }
    
    /**
     * Check if two GPS coordinates are within the same 25km zone.
     */
    private fun isSameGeographicalZone(gps1: String, gps2: String): Boolean {
        val (lat1, lon1) = gps1.split(",").map { it.trim().toDouble() }
        val (lat2, lon2) = gps2.split(",").map { it.trim().toDouble() }
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = 6371.0 * c // Earth's radius in km
        
        return distance <= 25.0
    }

    /**
     * Clear the current error.
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Factory method to create a ViewModel with the repository.
     */
    companion object {
        @Volatile
        private var repositoryInstance: CoinRepository? = null
        
        fun create(context: Context): CoinsViewModel {
            val repo = repositoryInstance ?: CoinRepository.getInstance(context).also {
                repositoryInstance = it
            }
            return CoinsViewModel(repo)
        }
        
        fun resetRepository() {
            repositoryInstance = null
        }
    }
}
