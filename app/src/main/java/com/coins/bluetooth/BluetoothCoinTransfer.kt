package com.coins.bluetooth

import com.coins.model.Wallet

/**
 * Handles Bluetooth-based coin transfers between users.
 * Validates geographical zones before transferring coins.
 */
class BluetoothCoinTransfer {

    /**
     * Initiates a coin transfer from the sender's wallet to the receiver's wallet.
     *
     * @param senderWallet The wallet of the sender.
     * @param receiverWallet The wallet of the receiver.
     * @param coinId The ID of the coin to transfer.
     * @param senderGps The GPS coordinate of the sender.
     * @param receiverGps The GPS coordinate of the receiver.
     * @return true if the transfer was successful, false otherwise.
     */
    fun transferCoin(
        senderWallet: Wallet,
        receiverWallet: Wallet,
        coinId: String,
        senderGps: String,
        receiverGps: String,
    ): Boolean {
        // Step 1: Validate that both devices are in the same geographical zone
        if (!isSameGeographicalZone(senderGps, receiverGps)) {
            return false
        }

        // Step 2: Find the coin in the sender's wallet
        val coin = senderWallet.getAllCoins().find { it.generateCoinId() == coinId }
            ?: return false

        // Step 3: Validate that the receiver is in the valid zone for this coin
        if (!coin.isWithin25Km(receiverGps)) {
            return false
        }

        // Step 4: Transfer the coin to the receiver's wallet
        val transferSuccess = receiverWallet.receiveCoin(coin, receiverGps)
        if (!transferSuccess) {
            return false
        }

        // Step 5: Mark the coin as sent in the sender's wallet
        return senderWallet.sendCoin(coinId, receiverWallet.userId, receiverGps)
    }

    /**
     * Checks if two GPS coordinates are within the same 25 km geographical zone.
     *
     * @param gps1 The first GPS coordinate (latitude,longitude).
     * @param gps2 The second GPS coordinate (latitude,longitude).
     * @return true if both coordinates are within 25 km of each other, false otherwise.
     */
    private fun isSameGeographicalZone(gps1: String, gps2: String): Boolean {
        val (lat1, lon1) = gps1.split(",").map { it.trim().toDouble() }
        val (lat2, lon2) = gps2.split(",").map { it.trim().toDouble() }
        val distance = haversine(lat1, lon1, lat2, lon2)
        return distance <= 25.0
    }

    /**
     * Calculates the distance between two GPS coordinates using the Haversine formula.
     *
     * @param lat1 Latitude of the first coordinate.
     * @param lon1 Longitude of the first coordinate.
     * @param lat2 Latitude of the second coordinate.
     * @param lon2 Longitude of the second coordinate.
     * @return Distance in kilometers.
     */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}