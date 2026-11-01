package com.coins.bluetooth

import com.coins.model.Coin
import com.coins.model.CoinStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializer for Coin objects to enable Bluetooth transfer.
 * Uses org.json for serialization (no additional dependencies needed).
 */
class CoinSerializer {
    
    /**
     * Serialize a Coin to a JSON string.
     * 
     * @param coin The coin to serialize
     * @return JSON string representation of the coin
     */
    fun serialize(coin: Coin): String {
        return JSONObject().apply {
            put("coinId", coin.coinId)
            put("amountCents", coin.amountCents)
            put("giverId", coin.giverId)
            put("ownerId", coin.ownerId)
            put("latitude", coin.latitude)
            put("longitude", coin.longitude)
            put("isDestroyed", coin.isDestroyed)
            put("fullName", coin.fullName)
            put("gpsCoordinate", coin.gpsCoordinate)
            put("timestamp", coin.timestamp)
            put("receiverId", coin.receiverId)
            put("status", coin.status.name)
        }.toString()
    }
    
    /**
     * Deserialize a JSON string to a Coin object.
     * 
     * @param json The JSON string to deserialize
     * @return The deserialized Coin, or null if deserialization fails
     */
    fun deserialize(json: String): Coin? {
        return try {
            val jsonObject = JSONObject(json)
            
            Coin(
                coinId = jsonObject.optString("coinId", ""),
                amountCents = jsonObject.optInt("amountCents", 100),
                giverId = jsonObject.optString("giverId", ""),
                ownerId = if (jsonObject.isNull("ownerId")) null else jsonObject.optString("ownerId"),
                latitude = jsonObject.optDouble("latitude", 0.0),
                longitude = jsonObject.optDouble("longitude", 0.0),
                isDestroyed = jsonObject.optBoolean("isDestroyed", false),
                fullName = jsonObject.optString("fullName", ""),
                gpsCoordinate = jsonObject.optString("gpsCoordinate", "0.0,0.0"),
                timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis()),
                receiverId = if (jsonObject.isNull("receiverId")) null else jsonObject.optString("receiverId"),
                status = try {
                    CoinStatus.valueOf(jsonObject.optString("status", "GENERATED"))
                } catch (e: IllegalArgumentException) {
                    CoinStatus.GENERATED
                }
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Serialize a list of Coin objects to a JSON string.
     * 
     * @param coins The list of coins to serialize
     * @return JSON string representation of the coin list
     */
    fun serializeList(coins: List<Coin>): String {
        return JSONArray().apply {
            coins.forEach { coin ->
                put(JSONObject().apply {
                    put("coinId", coin.coinId)
                    put("amountCents", coin.amountCents)
                    put("giverId", coin.giverId)
                    put("ownerId", coin.ownerId)
                    put("latitude", coin.latitude)
                    put("longitude", coin.longitude)
                    put("isDestroyed", coin.isDestroyed)
                    put("fullName", coin.fullName)
                    put("gpsCoordinate", coin.gpsCoordinate)
                    put("timestamp", coin.timestamp)
                    put("receiverId", coin.receiverId)
                    put("status", coin.status.name)
                })
            }
        }.toString()
    }
    
    /**
     * Deserialize a JSON string to a list of Coin objects.
     * 
     * @param json The JSON string to deserialize
     * @return The deserialized list of Coins, or empty list if deserialization fails
     */
    fun deserializeList(json: String): List<Coin> {
        return try {
            val jsonArray = JSONArray(json)
            val coins = mutableListOf<Coin>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val coin = Coin(
                    coinId = jsonObject.optString("coinId", ""),
                    amountCents = jsonObject.optInt("amountCents", 100),
                    giverId = jsonObject.optString("giverId", ""),
                    ownerId = if (jsonObject.isNull("ownerId")) null else jsonObject.optString("ownerId"),
                    latitude = jsonObject.optDouble("latitude", 0.0),
                    longitude = jsonObject.optDouble("longitude", 0.0),
                    isDestroyed = jsonObject.optBoolean("isDestroyed", false),
                    fullName = jsonObject.optString("fullName", ""),
                    gpsCoordinate = jsonObject.optString("gpsCoordinate", "0.0,0.0"),
                    timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis()),
                    receiverId = if (jsonObject.isNull("receiverId")) null else jsonObject.optString("receiverId"),
                    status = try {
                        CoinStatus.valueOf(jsonObject.optString("status", "GENERATED"))
                    } catch (e: IllegalArgumentException) {
                        CoinStatus.GENERATED
                    }
                )
                coins.add(coin)
            }
            coins
        } catch (e: JSONException) {
            e.printStackTrace()
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    companion object {
        val instance = CoinSerializer()
    }
}

