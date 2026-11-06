package com.coins.bluetooth

import com.coins.model.Coin
import com.coins.model.CoinStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CoinSerializer class.
 * Tests serialization and deserialization of Coin objects.
 */
class CoinSerializerTest {

    private val serializer = CoinSerializer.instance

    @Test
    fun `test serialize basic coin`() {
        val coin = Coin(
            coinId = "coin-123",
            amountCents = 100,
            giverId = "giver-1",
            ownerId = "owner-1",
            latitude = 48.8566,
            longitude = 2.3522,
            isDestroyed = false,
            fullName = "Test Coin",
            gpsCoordinate = "48.8566,2.3522",
            timestamp = 1234567890L,
            receiverId = "receiver-1",
            status = CoinStatus.GENERATED
        )
        
        val json = serializer.serialize(coin)
        
        assertNotNull("Serialized JSON should not be null", json)
        assertTrue("JSON should contain coinId", json.contains("coin-123"))
        assertTrue("JSON should contain giverId", json.contains("giver-1"))
        assertTrue("JSON should contain amountCents", json.contains("100"))
        assertTrue("JSON should contain status", json.contains("GENERATED"))
    }

    @Test
    fun `test serialize coin with null optional fields`() {
        val coin = Coin(
            coinId = "coin-456",
            amountCents = 200,
            giverId = "giver-2",
            ownerId = null,
            latitude = 0.0,
            longitude = 0.0,
            isDestroyed = false,
            fullName = "Test Coin 2",
            gpsCoordinate = "0.0,0.0",
            timestamp = System.currentTimeMillis(),
            receiverId = null,
            status = CoinStatus.SENT
        )
        
        val json = serializer.serialize(coin)
        
        assertNotNull("Serialized JSON should not be null", json)
        assertTrue("JSON should contain coinId", json.contains("coin-456"))
        assertTrue("JSON should contain status SENT", json.contains("SENT"))
    }

    @Test
    fun `test deserialize basic coin`() {
        val json = """{
            "coinId": "coin-789",
            "amountCents": 150,
            "giverId": "giver-3",
            "ownerId": "owner-3",
            "latitude": 40.7128,
            "longitude": -74.0060,
            "isDestroyed": false,
            "fullName": "New York Coin",
            "gpsCoordinate": "40.7128,-74.0060",
            "timestamp": 1234567890,
            "receiverId": "receiver-3",
            "status": "GENERATED"
        }"""
        
        val coin = serializer.deserialize(json)
        
        assertNotNull("Deserialized coin should not be null", coin)
        assertEquals("coinId should match", "coin-789", coin?.coinId)
        assertEquals("amountCents should match", 150, coin?.amountCents)
        assertEquals("giverId should match", "giver-3", coin?.giverId)
        assertEquals("ownerId should match", "owner-3", coin?.ownerId)
        assertEquals("status should match", CoinStatus.GENERATED, coin?.status)
    }

    @Test
    fun `test deserialize coin with missing fields uses defaults`() {
        val json = """{
            "coinId": "coin-minimal",
            "amountCents": 100
        }"""
        
        val coin = serializer.deserialize(json)
        
        assertNotNull("Deserialized coin should not be null", coin)
        assertEquals("coinId should match", "coin-minimal", coin?.coinId)
        assertEquals("amountCents should match", 100, coin?.amountCents)
        assertEquals("giverId should be default", "", coin?.giverId)
        assertEquals("latitude should be default", 0.0, coin?.latitude ?: 0.0, 0.001)
        assertEquals("longitude should be default", 0.0, coin?.longitude ?: 0.0, 0.001)
        assertEquals("status should be default", CoinStatus.GENERATED, coin?.status)
    }

    @Test
    fun `test deserialize invalid JSON returns null`() {
        val invalidJson = "not valid json {"
        
        val coin = serializer.deserialize(invalidJson)
        
        assertNull("Deserializing invalid JSON should return null", coin)
    }

    @Test
    fun `test deserialize empty JSON returns null`() {
        val emptyJson = ""
        
        val coin = serializer.deserialize(emptyJson)
        
        assertNull("Deserializing empty JSON should return null", coin)
    }

    @Test
    fun `test deserialize coin with unknown status uses default`() {
        val json = """{
            "coinId": "coin-unknown-status",
            "status": "UNKNOWN_STATUS"
        }"""
        
        val coin = serializer.deserialize(json)
        
        assertNotNull("Deserialized coin should not be null", coin)
        assertEquals("status should default to GENERATED", CoinStatus.GENERATED, coin?.status)
    }

    @Test
    fun `test serialize and deserialize list of coins`() {
        val coins = listOf(
            Coin(
                coinId = "coin-1",
                amountCents = 100,
                giverId = "giver-1",
                gpsCoordinate = "48.8566,2.3522"
            ),
            Coin(
                coinId = "coin-2",
                amountCents = 200,
                giverId = "giver-2",
                gpsCoordinate = "51.5074,-0.1278"
            ),
            Coin(
                coinId = "coin-3",
                amountCents = 300,
                giverId = "giver-3",
                gpsCoordinate = "40.7128,-74.0060"
            )
        )
        
        val json = serializer.serializeList(coins)
        
        assertNotNull("Serialized list JSON should not be null", json)
        assertTrue("JSON should be an array", json.startsWith("["))
        assertTrue("JSON should end with array", json.endsWith("]"))
        
        val deserialized = serializer.deserializeList(json)
        
        assertEquals("List size should match", coins.size, deserialized.size)
        assertEquals("First coin ID should match", coins[0].coinId, deserialized[0].coinId)
        assertEquals("Second coin ID should match", coins[1].coinId, deserialized[1].coinId)
        assertEquals("Third coin ID should match", coins[2].coinId, deserialized[2].coinId)
    }

    @Test
    fun `test deserialize empty list returns empty list`() {
        val json = "[]"
        
        val coins = serializer.deserializeList(json)
        
        assertNotNull("Deserialized list should not be null", coins)
        assertTrue("List should be empty", coins.isEmpty())
    }

    @Test
    fun `test deserialize invalid list JSON returns empty list`() {
        val invalidJson = "not a list"
        
        val coins = serializer.deserializeList(invalidJson)
        
        assertNotNull("Deserialized list should not be null", coins)
        assertTrue("List should be empty for invalid JSON", coins.isEmpty())
    }

    @Test
    fun `test serialize preserves all coin fields`() {
        val coin = Coin(
            coinId = "full-coin-test",
            amountCents = 500,
            giverId = "giver-full",
            ownerId = "owner-full",
            latitude = 34.0522,
            longitude = -118.2437,
            isDestroyed = true,
            fullName = "Full Test Coin",
            gpsCoordinate = "34.0522,-118.2437",
            timestamp = 9876543210L,
            receiverId = "receiver-full",
            status = CoinStatus.DESTROYED
        )
        
        val json = serializer.serialize(coin)
        val deserialized = serializer.deserialize(json)
        
        assertNotNull("Deserialized coin should not be null", deserialized)
        assertEquals("coinId should match", coin.coinId, deserialized?.coinId)
        assertEquals("amountCents should match", coin.amountCents, deserialized?.amountCents)
        assertEquals("giverId should match", coin.giverId, deserialized?.giverId)
        assertEquals("ownerId should match", coin.ownerId, deserialized?.ownerId)
        assertEquals("latitude should match", coin.latitude, deserialized?.latitude ?: 0.0, 0.001)
        assertEquals("longitude should match", coin.longitude, deserialized?.longitude ?: 0.0, 0.001)
        assertEquals("isDestroyed should match", coin.isDestroyed, deserialized?.isDestroyed)
        assertEquals("fullName should match", coin.fullName, deserialized?.fullName)
        assertEquals("gpsCoordinate should match", coin.gpsCoordinate, deserialized?.gpsCoordinate)
        assertEquals("timestamp should match", coin.timestamp, deserialized?.timestamp)
        assertEquals("receiverId should match", coin.receiverId, deserialized?.receiverId)
        assertEquals("status should match", coin.status, deserialized?.status)
    }

    @Test
    fun `test singleton instance`() {
        val instance1 = CoinSerializer.instance
        val instance2 = CoinSerializer.instance
        
        assertTrue("CoinSerializer should be a singleton", instance1 === instance2)
    }
}
