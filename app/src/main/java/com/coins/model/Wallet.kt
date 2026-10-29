package com.coins.model

/**
 * Manages a user's wallet of coins, ensuring the total value does not exceed 25€.
 *
 * @param userId The unique identifier of the user.
 * @param fullName The full name of the user.
 */
data class Wallet(
    val userId: String,
    val fullName: String,
    private val maxCoinsValue: Int = 25,
) {
    private val generatedCoins = mutableListOf<Coin>()
    private val sentCoins = mutableListOf<Coin>()
    private val destroyedCoins = mutableListOf<Coin>()

    /**
     * Generates a new coin and adds it to the wallet.
     *
     * @param gpsCoordinate The GPS coordinate where the coin is generated.
     * @param timestamp The timestamp when the coin is generated.
     * @return The generated coin, or null if the wallet is full.
     */
    fun generateCoin(gpsCoordinate: String, timestamp: Long): Coin? {
        val currentValue = calculateCurrentValue()
        if (currentValue >= maxCoinsValue) {
            return null // Wallet is full
        }

        val coin = Coin(
            fullName = fullName,
            gpsCoordinate = gpsCoordinate,
            timestamp = timestamp,
            giverId = userId
        )
        generatedCoins.add(coin)
        return coin
    }

    /**
     * Sends a coin to another user after validating the geographical zone.
     *
     * @param coinId The ID of the coin to send.
     * @param receiverId The unique identifier of the receiver.
     * @param receiverGps The GPS coordinate of the receiver.
     * @return true if the coin was sent successfully, false otherwise.
     */
    fun sendCoin(coinId: String, receiverId: String, receiverGps: String): Boolean {
        val coin = findCoinById(coinId) ?: return false
        
        if (!coin.isWithin25Km(receiverGps)) {
            return false // Receiver is not in the valid zone
        }

        coin.markAsSent(receiverId)
        generatedCoins.remove(coin)
        sentCoins.add(coin)
        return true
    }

    /**
     * Receives a coin from another user.
     *
     * @param coin The coin to receive.
     * @param receiverGps The GPS coordinate of the receiver.
     * @return true if the coin was received successfully, false otherwise.
     */
    fun receiveCoin(coin: Coin, receiverGps: String): Boolean {
        if (!coin.isWithin25Km(receiverGps)) {
            return false // Receiver is not in the valid zone
        }

        generatedCoins.add(coin)
        return true
    }

    /**
     * Destroys a coin when it is converted back to fiat currency.
     *
     * @param coinId The ID of the coin to destroy.
     * @return true if the coin was destroyed successfully, false otherwise.
     */
    fun destroyCoin(coinId: String): Boolean {
        val coin = findCoinById(coinId) ?: return false
        
        coin.markAsDestroyed()
        generatedCoins.remove(coin)
        destroyedCoins.add(coin)
        return true
    }

    /**
     * Calculates the current value of the wallet (generated + sent - destroyed).
     *
     * @return The current value in €.
     */
    fun calculateCurrentValue(): Int {
        return (generatedCoins.size + sentCoins.size - destroyedCoins.size)
    }

    /**
     * Finds a coin by its ID.
     *
     * @param coinId The ID of the coin to find.
     * @return The coin if found, null otherwise.
     */
    private fun findCoinById(coinId: String): Coin? {
        return generatedCoins.find { it.generateCoinId() == coinId } ?:
               sentCoins.find { it.generateCoinId() == coinId }
    }

    /**
     * Gets all coins in the wallet.
     *
     * @return A list of all coins.
     */
    fun getAllCoins(): List<Coin> {
        return generatedCoins + sentCoins
    }
}