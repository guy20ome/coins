package com.coins.bluetooth

import android.content.Context
import com.coins.model.Coin
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Bluetooth protocol messages.
 * These tests don't require any dependencies and can run standalone.
 */
class BluetoothProtocolTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val transfer = BluetoothCoinTransfer(mockContext)

    @Test
    fun testMessageTypeConstants() {
        assertEquals("HANDSHAKE", MESSAGE_TYPE_HANDSHAKE)
        assertEquals("HANDSHAKE_RESPONSE", MESSAGE_TYPE_HANDSHAKE_RESPONSE)
        assertEquals("ACK", MESSAGE_TYPE_ACK)
        assertEquals("NACK", MESSAGE_TYPE_NACK)
        assertEquals("COIN", MESSAGE_TYPE_COIN)
        assertEquals("\n", MESSAGE_DELIMITER)
    }

    @Test
    fun testCreateInstanceAccessible() {
        assertNotNull("COIN_TRANSFER_UUID should not be null", COIN_TRANSFER_UUID)
    }

    @Test
    fun testUUIDFormat() {
        val uuidString = COIN_TRANSFER_UUID.toString()
        assertEquals("UUID should match expected value",
            "00001101-0000-1000-8000-00805f9b34fb",
            uuidString)
    }

    // ============================================
    // CoinSerializer Tests (can run without mockk)
    // ============================================

    @Test
    fun testCoinSerializerBasicSerialization() {
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
    fun testCoinSerializerBasicDeserialization() {
        val serializer = CoinSerializer.instance
        val json = "{\"coinId\":\"test-456\",\"giverId\":\"giver-2\"}"

        val coin = serializer.deserialize(json)

        assertNotNull("Coin should not be null", coin)
        assertEquals("coinId should match", "test-456", coin?.coinId)
    }

    @Test
    fun testCoinSerializerListSerialization() {
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
    fun testCoinSerializerListDeserialization() {
        val serializer = CoinSerializer.instance
        val json = "[{\"coinId\":\"c1\"},{\"coinId\":\"c2\"}]"

        val coins = serializer.deserializeList(json)

        assertEquals("Should have 2 coins", 2, coins.size)
    }

    // ============================================
    // Geographical Calculation Tests
    // ============================================

    @Test
    fun testHaversineDistanceCalculation() {
        // Distance between two points exactly 1 degree apart in latitude
        // 1 degree of latitude ≈ 111 km
        val lat1 = 0.0
        val lon1 = 0.0
        val lat2 = 1.0
        val lon2 = 0.0

        val distance = transfer.haversine(lat1, lon1, lat2, lon2)

        // Should be approximately 111 km (with some margin for Earth's curvature)
        assertTrue("Distance should be approximately 111 km", distance > 110 && distance < 112)
    }

    @Test
    fun testHaversineSamePointDistanceIsZero() {
        val distance = transfer.haversine(48.8566, 2.3522, 48.8566, 2.3522)

        assertEquals("Distance to same point should be 0", 0.0, distance, 0.001)
    }

    @Test
    fun testIsSameGeographicalZoneForSameLocation() {
        val gps1 = "48.8566,2.3522"
        val gps2 = "48.8566,2.3522"

        val result = transfer.isSameGeographicalZone(gps1, gps2)

        assertTrue("Same location should be in same zone", result)
    }

    @Test
    fun testIsSameGeographicalZoneForNearbyLocations() {
        // Two points about 1km apart in Paris
        val gps1 = "48.8566,2.3522"
        val gps2 = "48.8636,2.3582"

        val result = transfer.isSameGeographicalZone(gps1, gps2)

        assertTrue("Nearby locations should be in same zone (within 25km)", result)
    }

    @Test
    fun testIsSameGeographicalZoneForFarLocations() {
        // Paris to London
        val gps1 = "48.8566,2.3522"
        val gps2 = "51.5074,-0.1278"

        val result = transfer.isSameGeographicalZone(gps1, gps2)

        assertTrue("Paris and London should NOT be in same zone", !result)
    }

    // ============================================
    // Protocol Message Format Tests
    // ============================================

    @Test
    fun testHandshakeMessageFormat() {
        val message = ProtocolMessage.Handshake("CoinTransfer")
        val serialized = transfer.serializeProtocolMessage(message)

        assertEquals("HANDSHAKE:CoinTransfer", serialized)
    }

    @Test
    fun testHandshakeResponseMessageFormat() {
        val message = ProtocolMessage.HandshakeResponse("1.0")
        val serialized = transfer.serializeProtocolMessage(message)

        assertEquals("HANDSHAKE_RESPONSE:1.0", serialized)
    }

    @Test
    fun testAckMessageFormat() {
        val message = ProtocolMessage.Acknowledgment("msg-123")
        val serialized = transfer.serializeProtocolMessage(message)

        assertEquals("ACK:msg-123", serialized)
    }

    @Test
    fun testNackMessageFormat() {
        val message = ProtocolMessage.NegativeAcknowledgment("msg-123", "Invalid coin")
        val serialized = transfer.serializeProtocolMessage(message)

        assertEquals("NACK:msg-123:Invalid coin", serialized)
    }

    @Test
    fun testParseHandshakeMessage() {
        val message = "HANDSHAKE:CoinTransfer"
        val parsed = transfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Handshake", parsed is ProtocolMessage.Handshake)
    }

    @Test
    fun testParseAckMessage() {
        val message = "ACK:msg-123"
        val parsed = transfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Acknowledgment", parsed is ProtocolMessage.Acknowledgment)
    }

    @Test
    fun testParseUnknownMessageReturnsNull() {
        val message = "UNKNOWN:data"
        val parsed = transfer.parseProtocolMessage(message)

        assertNull("Unknown message type should return null", parsed)
    }
}
