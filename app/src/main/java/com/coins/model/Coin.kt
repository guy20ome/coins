package com.coins.model



/**
 * Represents a virtual coin with a unique identifier and location-based validation.
 *
 * @param fullName The full name of the user who generated the coin.
 * @param gpsCoordinate The GPS coordinate (latitude,longitude) where the coin was generated.
 * @param timestamp The timestamp (in milliseconds) when the coin was generated.
 * @param giverId The unique identifier of the giver.
 * @param receiverId The unique identifier of the receiver (optional, set during transfer).
 * @param status The current status of the coin (GENERATED, SENT, DESTROYED).
 */
data class Coin(
    val fullName: String,
    val gpsCoordinate: String,
    val timestamp: Long,
    val giverId: String,
    var receiverId: String? = null,
    var status: CoinStatus = CoinStatus.GENERATED,
) {

    /**
     * Generates a unique coin ID in a readable format.
     * Format: <fullName>_<gpsCoordinate>_<timestamp>_<giverId>_<receiverId>
     */
    fun generateCoinId(): String {
        return "${fullName}_${gpsCoordinate}_${timestamp}_${giverId}_${receiverId ?: ""}"
    }

    /**
     * Validates if the receiver's GPS location is within 25 km of the coin's GPS location.
     *
     * @param receiverGps The GPS coordinate of the receiver (latitude,longitude).
     * @return true if the receiver is within 25 km, false otherwise.
     */
    fun isWithin25Km(receiverGps: String): Boolean {
        val (coinLat, coinLon) = gpsCoordinate.split(",").map { it.trim().toDouble() }
        val (receiverLat, receiverLon) = receiverGps.split(",").map { it.trim().toDouble() }
        val distance = haversine(coinLat, coinLon, receiverLat, receiverLon)
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
        val sinDLatOver2 = Math.sin(dLat / 2)
        val sinDLonOver2 = Math.sin(dLon / 2)
        val cosLat1 = Math.cos(Math.toRadians(lat1))
        val cosLat2 = Math.cos(Math.toRadians(lat2))
        val a = sinDLatOver2 * sinDLatOver2 + cosLat1 * cosLat2 * sinDLonOver2 * sinDLonOver2
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Updates the coin status to SENT.
     */
    fun markAsSent(receiverId: String) {
        this.receiverId = receiverId
        this.status = CoinStatus.SENT
    }

    /**
     * Updates the coin status to DESTROYED.
     */
    fun markAsDestroyed() {
        this.status = CoinStatus.DESTROYED
    }
}

enum class CoinStatus {
    GENERATED,
    SENT,
    DESTROYED
}