package com.coins.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a virtual coin with a unique identifier and location-based validation.
 */
@Entity(tableName = "coins")
data class Coin(
    @PrimaryKey val coinId: String = UUID.randomUUID().toString(),
    val amountCents: Int = 100,
    val giverId: String = "",
    var ownerId: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    var isDestroyed: Boolean = false,
    val fullName: String = "",
    val gpsCoordinate: String = "$latitude,$longitude",
    val timestamp: Long = System.currentTimeMillis(),
    var receiverId: String? = null,
    var status: CoinStatus = CoinStatus.GENERATED,
) {

    /**
     * Generates a unique coin ID in a readable format.
     * Format: <fullName>_<gpsCoordinate>_<YYYYMMDDHHMMSS>
     */
    fun generateCoinId(): String {
        val formattedTimestamp = formatTimestamp(timestamp)
        return if (fullName.isNotEmpty()) {
            "${fullName}_${gpsCoordinate}_${formattedTimestamp}"
        } else {
            coinId
        }
    }

    /**
     * Formats timestamp to YYYYMMDDHHMMSS format
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Validates if the receiver's GPS location is within 25 km of the coin's GPS location.
     *
     * @param receiverGps The GPS coordinate of the receiver (latitude,longitude).
     * @return true if the receiver is within 25 km, false otherwise.
     */
    fun isWithin25Km(receiverGps: String): Boolean {
        val coord = if (gpsCoordinate.contains(",")) gpsCoordinate else "$latitude,$longitude"
        val parts = coord.split(",")
        if (parts.size < 2) return false
        val coinLat = parts[0].trim().toDoubleOrNull() ?: latitude
        val coinLon = parts[1].trim().toDoubleOrNull() ?: longitude

        val rParts = receiverGps.split(",")
        if (rParts.size < 2) return false
        val receiverLat = rParts[0].trim().toDoubleOrNull() ?: return false
        val receiverLon = rParts[1].trim().toDoubleOrNull() ?: return false

        val distance = haversine(coinLat, coinLon, receiverLat, receiverLon)
        return distance <= 25.0
    }

    /**
     * Calculates the distance between two GPS coordinates using the Haversine formula.
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
        this.ownerId = receiverId
        this.status = CoinStatus.SENT
    }

    /**
     * Updates the coin status to DESTROYED.
     */
    fun markAsDestroyed() {
        this.isDestroyed = true
        this.status = CoinStatus.DESTROYED
    }
}

enum class CoinStatus {
    GENERATED,
    SENT,
    DESTROYED
}
