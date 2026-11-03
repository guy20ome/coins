package com.coins.bluetooth

import com.coins.model.Coin
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Bluetooth protocol messages.
 * These tests don't require any dependencies and can run standalone.
 */
class BluetoothProtocolTest {

    @Test
    fun `test message type constants`() {
        assertEquals("HANDSHAKE", MESSAGE_TYPE_HANDSHAKE)
        assertEquals("HANDSHAKE_RESPONSE", MESSAGE_TYPE_HANDSHAKE_RESPONSE)
        assertEquals("ACK", MESSAGE_TYPE_ACK)
        assertEquals("NACK", MESSAGE_TYPE_NACK)
        assertEquals("COIN", MESSAGE_TYPE_COIN)
        assertEquals("\n", MESSAGE_DELIMITER)
    }

    @Test
    fun `test create BluetoothCoinTransfer instance`() {
        // We can't create a real instance without a Context
        // But we can test the companion object
        assertNotNull("COIN_TRANSFER_UUID should not be null", COIN_TRANSFER_UUID)
    }

    @Test
    fun `test UUID format`() {
        val uuidString = COIN_TRANSFER_UUID.toString()
        assertEquals("UUID should match expected value", 
            "00001101-0000-1000-8000-00805F9B34FB",
            uuidString)
    }

    // ============================================
    // CoinSerializer Tests (can run without mockk)
    // ============================================

    @Test
    fun `test CoinSerializer basic serialization`() {
        val serializer = CoinSerializer.instance
        val coin = Coin(
            coinId = "test-123",
            giverId = "giver-1",
            gpsCoordinate = "48.8566,2.3522"
        )
        
        val json = serializer.serialize(coin)
        
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should contain coinId", json.contains("test-123"))
    }

    @Test
    fun `test CoinSerializer basic deserialization`() {
        val serializer = CoinSerializer.instance
        val json = "{\"coinId\":\"test-456\",\"giverId\":\"giver-2\"}"
        
        val coin = serializer.deserialize(json)
        
        assertNotNull("Coin should not be null", coin)
        assertEquals("coinId should match", "test-456", coin?.coinId)
    }

    @Test
    fun `test CoinSerializer list serialization`() {
        val serializer = CoinSerializer.instance
        val coins = listOf(
            Coin(coinId = "coin-1", giverId = "giver-1"),
            Coin(coinId = "coin-2", giverId = "giver-2")
        )
        
        val json = serializer.serializeList(coins)
        
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should be an array", json.startsWith("["))
    }

    @Test
    fun `test CoinSerializer list deserialization`() {
        val serializer = CoinSerializer.instance
        val json = "[{\"coinId\":\"c1\"},{\"coinId\":\"c2\"}]"
        
        val coins = serializer.deserializeList(json)
        
        assertEquals("Should have 2 coins", 2, coins.size)
    }

    // ============================================
    // Geographical Calculation Tests
    // ============================================

    @Test
    fun `test haversine distance calculation`() {
        // Test with known coordinates and distances
        val bluetoothTransfer = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        )
        
        // Distance between two points that are exactly 1 degree apart in latitude
        // 1 degree of latitude ≈ 111 km
        val lat1 = 0.0
        val lon1 = 0.0
        val lat2 = 1.0
        val lon2 = 0.0
        
        val distance = bluetoothTransfer.haversine(lat1, lon1, lat2, lon2)
        
        // Should be approximately 111 km (with some margin for Earth's curvature)
        assertTrue("Distance should be approximately 111 km", distance > 110 && distance < 112)
    }

    @Test
    fun `test haversine same point distance is zero`() {
        val bluetoothTransfer = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        )
        
        val distance = bluetoothTransfer.haversine(48.8566, 2.3522, 48.8566, 2.3522)
        
        assertEquals("Distance to same point should be 0", 0.0, distance, 0.001)
    }

    @Test
    fun `test isSameGeographicalZone for same location`() {
        val bluetoothTransfer = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        )
        
        val gps1 = "48.8566,2.3522"
        val gps2 = "48.8566,2.3522"
        
        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        
        assertTrue("Same location should be in same zone", result)
    }

    @Test
    fun `test isSameGeographicalZone for nearby locations`() {
        val bluetoothTransfer = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        )
        
        // Two points about 1km apart in Paris
        val gps1 = "48.8566,2.3522"
        val gps2 = "48.8636,2.3582"
        
        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        
        assertTrue("Nearby locations should be in same zone (within 25km)", result)
    }

    @Test
    fun `test isSameGeographicalZone for far locations`() {
        val bluetoothTransfer = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        )
        
        // Paris to London
        val gps1 = "48.8566,2.3522"
        val gps2 = "51.5074,-0.1278"
        
        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        
        assertTrue("Paris and London should NOT be in same zone", !result)
    }

    // ============================================
    // Protocol Message Format Tests
    // ============================================

    @Test
    fun `test handshake message format`() {
        val message = ProtocolMessage.Handshake("CoinTransfer")
        val serialized = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).serializeProtocolMessage(message)
        
        assertEquals("HANDSHAKE:CoinTransfer", serialized)
    }

    @Test
    fun `test handshake response message format`() {
        val message = ProtocolMessage.HandshakeResponse("1.0")
        val serialized = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).serializeProtocolMessage(message)
        
        assertEquals("HANDSHAKE_RESPONSE:1.0", serialized)
    }

    @Test
    fun `test ack message format`() {
        val message = ProtocolMessage.Acknowledgment("msg-123")
        val serialized = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).serializeProtocolMessage(message)
        
        assertEquals("ACK:msg-123", serialized)
    }

    @Test
    fun `test nack message format`() {
        val message = ProtocolMessage.NegativeAcknowledgment("msg-123", "Invalid coin")
        val serialized = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).serializeProtocolMessage(message)
        
        assertEquals("NACK:msg-123:Invalid coin", serialized)
    }

    @Test
    fun `test parse handshake message`() {
        val message = "HANDSHAKE:CoinTransfer"
        val parsed = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).parseProtocolMessage(message)
        
        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Handshake", parsed is ProtocolMessage.Handshake)
    }

    @Test
    fun `test parse ack message`() {
        val message = "ACK:msg-123"
        val parsed = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).parseProtocolMessage(message)
        
        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Acknowledgment", parsed is ProtocolMessage.Acknowledgment)
    }

    @Test
    fun `test parse unknown message returns null`() {
        val message = "UNKNOWN:data"
        val parsed = BluetoothCoinTransfer.create(
            android.content.ContextWrapper(android.app.Application()) 
        ).parseProtocolMessage(message)
        
        assertNull("Unknown message type should return null", parsed)
    }
}
