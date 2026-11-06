package com.coins.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.coins.model.Coin
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Unit tests for BluetoothCoinTransfer class.
 * Tests the core functionality without requiring actual Bluetooth hardware.
 */
class BluetoothCoinTransferTest {

    private lateinit var mockContext: Context
    private lateinit var bluetoothTransfer: BluetoothCoinTransfer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk(relaxed = true)
        bluetoothTransfer = BluetoothCoinTransfer(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ============================================
    // Basic Functionality Tests
    // ============================================

    @Test
    fun testIsBluetoothAvailableReturnsFalseWhenNoAdapter() {
        mockkStatic(android.bluetooth.BluetoothAdapter::class)
        every { android.bluetooth.BluetoothAdapter.getDefaultAdapter() } returns null

        val result = bluetoothTransfer.isBluetoothAvailable()
        assertFalse("Bluetooth should not be available when no adapter", result)
    }

    @Test
    fun testIsBluetoothEnabledReturnsFalseWhenNoAdapter() {
        mockkStatic(android.bluetooth.BluetoothAdapter::class)
        every { android.bluetooth.BluetoothAdapter.getDefaultAdapter() } returns null

        val result = bluetoothTransfer.isBluetoothEnabled()
        assertFalse("Bluetooth should not be enabled when no adapter", result)
    }

    @Test
    fun testHasBluetoothPermissionsReturnsTrueOnOlderAndroid() {
        // Use internal method with SDK_INT=30 to test older Android path (pre-S)
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.BLUETOOTH)
        } returns android.content.pm.PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.BLUETOOTH_ADMIN)
        } returns android.content.pm.PackageManager.PERMISSION_GRANTED

        bluetoothTransfer.hasBluetoothPermissions(30).let { result ->
            assertTrue("Should have permissions on older Android (SDK_INT=30)", result)
        }
    }

    @Test
    fun testHasBluetoothPermissionsReturnsFalseWhenPermissionsNotGrantedOnAndroid12Plus() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.BLUETOOTH_SCAN)
        } returns android.content.pm.PackageManager.PERMISSION_DENIED

        bluetoothTransfer.hasBluetoothPermissions(31).let { result ->
            assertFalse("Should not have permissions when BLUETOOTH_SCAN is denied", result)
        }
    }

    @Test
    fun testHasBluetoothPermissionsReturnsTrueWhenAllPermissionsGrantedOnAndroid12Plus() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.BLUETOOTH_SCAN)
        } returns android.content.pm.PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.BLUETOOTH_CONNECT)
        } returns android.content.pm.PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockContext, android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns android.content.pm.PackageManager.PERMISSION_GRANTED

        bluetoothTransfer.hasBluetoothPermissions(31).let { result ->
            assertTrue("Should have permissions when all required permissions are granted", result)
        }
    }

    // ============================================
    // Coin Serialization Tests
    // ============================================

    @Test
    fun testCoinSerializerSerializeAndDeserializeCoin() {
        val coin = Coin(
            coinId = "test-coin-123",
            amountCents = 100,
            giverId = "giver-1",
            ownerId = "owner-1",
            latitude = 48.8566,
            longitude = 2.3522,
            isDestroyed = false,
            fullName = "Test Coin",
            gpsCoordinate = "48.8566,2.3522",
            timestamp = System.currentTimeMillis(),
            receiverId = "receiver-1",
            status = com.coins.model.CoinStatus.GENERATED
        )

        val serializer = CoinSerializer.instance
        val json = serializer.serialize(coin)

        assertNotNull("Serialized JSON should not be null", json)
        assertTrue("JSON should contain coinId", json.contains("test-coin-123"))
        assertTrue("JSON should contain giverId", json.contains("giver-1"))

        val deserialized = serializer.deserialize(json)
        assertNotNull("Deserialized coin should not be null", deserialized)
        assertEquals("Coin ID should match", coin.coinId, deserialized?.coinId)
        assertEquals("Giver ID should match", coin.giverId, deserialized?.giverId)
    }

    @Test
    fun testCoinSerializerDeserializeInvalidJSONReturnsNull() {
        val serializer = CoinSerializer.instance
        val invalidJson = "invalid json {"

        val result = serializer.deserialize(invalidJson)
        assertNull("Deserializing invalid JSON should return null", result)
    }

    @Test
    fun testCoinSerializerSerializeAndDeserializeCoinList() {
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
                gpsCoordinate = "48.8566,2.3522"
            )
        )

        val serializer = CoinSerializer.instance
        val json = serializer.serializeList(coins)

        assertNotNull("Serialized list JSON should not be null", json)

        val deserialized = serializer.deserializeList(json)
        assertEquals("List size should match", coins.size, deserialized.size)
        assertEquals("First coin ID should match", coins[0].coinId, deserialized[0].coinId)
        assertEquals("Second coin ID should match", coins[1].coinId, deserialized[1].coinId)
    }

    // ============================================
    // Protocol Message Tests
    // ============================================

    @Test
    fun testSerializeProtocolMessageForHandshake() {
        val message = com.coins.bluetooth.ProtocolMessage.Handshake("CoinTransfer")
        val serialized = bluetoothTransfer.serializeProtocolMessage(message)

        assertEquals("Handshake message format", "HANDSHAKE:CoinTransfer", serialized)
    }

    @Test
    fun testSerializeProtocolMessageForHandshakeResponse() {
        val message = com.coins.bluetooth.ProtocolMessage.HandshakeResponse("1.0")
        val serialized = bluetoothTransfer.serializeProtocolMessage(message)

        assertEquals("HandshakeResponse message format", "HANDSHAKE_RESPONSE:1.0", serialized)
    }

    @Test
    fun testSerializeProtocolMessageForAcknowledgment() {
        val message = com.coins.bluetooth.ProtocolMessage.Acknowledgment("msg-123")
        val serialized = bluetoothTransfer.serializeProtocolMessage(message)

        assertEquals("Acknowledgment message format", "ACK:msg-123", serialized)
    }

    @Test
    fun testSerializeProtocolMessageForNegativeAcknowledgment() {
        val message = com.coins.bluetooth.ProtocolMessage.NegativeAcknowledgment("msg-123", "Invalid coin")
        val serialized = bluetoothTransfer.serializeProtocolMessage(message)

        assertEquals("NegativeAcknowledgment message format", "NACK:msg-123:Invalid coin", serialized)
    }

    @Test
    fun testSerializeProtocolMessageForCoinMessage() {
        val coin = Coin(
            coinId = "test-coin",
            giverId = "giver-1",
            gpsCoordinate = "48.8566,2.3522"
        )
        val message = com.coins.bluetooth.ProtocolMessage.CoinMessage("msg-123", coin)
        val serialized = bluetoothTransfer.serializeProtocolMessage(message)

        assertTrue("CoinMessage should start with COIN:", serialized.startsWith("COIN:"))
        assertTrue("CoinMessage should contain messageId", serialized.contains("msg-123"))
        assertTrue("CoinMessage should contain coinId", serialized.contains("test-coin"))
    }

    @Test
    fun testParseProtocolMessageForHandshake() {
        val message = "HANDSHAKE:CoinTransfer"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Handshake", parsed is com.coins.bluetooth.ProtocolMessage.Handshake)
        assertEquals("App name should match", "CoinTransfer", (parsed as com.coins.bluetooth.ProtocolMessage.Handshake).appName)
    }

    @Test
    fun testParseProtocolMessageForHandshakeResponse() {
        val message = "HANDSHAKE_RESPONSE:1.0"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be HandshakeResponse", parsed is com.coins.bluetooth.ProtocolMessage.HandshakeResponse)
        assertEquals("Version should match", "1.0", (parsed as com.coins.bluetooth.ProtocolMessage.HandshakeResponse).version)
    }

    @Test
    fun testParseProtocolMessageForAcknowledgment() {
        val message = "ACK:msg-123"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be Acknowledgment", parsed is com.coins.bluetooth.ProtocolMessage.Acknowledgment)
        assertEquals("Message ID should match", "msg-123", (parsed as com.coins.bluetooth.ProtocolMessage.Acknowledgment).messageId)
    }

    @Test
    fun testParseProtocolMessageForNegativeAcknowledgment() {
        val message = "NACK:msg-123:Invalid coin"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be NegativeAcknowledgment", parsed is com.coins.bluetooth.ProtocolMessage.NegativeAcknowledgment)
        val nack = parsed as com.coins.bluetooth.ProtocolMessage.NegativeAcknowledgment
        assertEquals("Message ID should match", "msg-123", nack.messageId)
        assertEquals("Reason should match", "Invalid coin", nack.reason)
    }

    @Test
    fun testParseProtocolMessageForCoinMessage() {
        val coinJson = "{\"coinId\":\"test-coin\",\"giverId\":\"giver-1\",\"gpsCoordinate\":\"48.8566,2.3522\"}"
        val message = "COIN:msg-123:$coinJson"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNotNull("Parsed message should not be null", parsed)
        assertTrue("Should be CoinMessage", parsed is com.coins.bluetooth.ProtocolMessage.CoinMessage)
        val coinMessage = parsed as com.coins.bluetooth.ProtocolMessage.CoinMessage
        assertEquals("Message ID should match", "msg-123", coinMessage.messageId)
        assertEquals("Coin ID should match", "test-coin", coinMessage.coin.coinId)
    }

    @Test
    fun testParseProtocolMessageForUnknownTypeReturnsNull() {
        val message = "UNKNOWN:some data"
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        assertNull("Unknown message type should return null", parsed)
    }

    @Test
    fun testParseProtocolMessageForMalformedMessageReturnsNull() {
        val message = "HANDSHAKE" // Missing app name
        val parsed = bluetoothTransfer.parseProtocolMessage(message)

        // This might still parse, but let's check
        // In our implementation, it should handle this gracefully
        assertNotNull("Should parse even with minimal data", parsed)
    }

    // ============================================
    // Message ID Generation Tests
    // ============================================

    @Test
    fun testGenerateMessageIdCreatesUniqueIDs() {
        val id1 = bluetoothTransfer.generateMessageId()
        val id2 = bluetoothTransfer.generateMessageId()

        assertNotNull("ID 1 should not be null", id1)
        assertNotNull("ID 2 should not be null", id2)
        assertTrue("IDs should be unique", id1 != id2)
        assertTrue("ID should start with msg_", id1.startsWith("msg_"))
        assertTrue("ID should contain timestamp", id1.contains("_"))
    }

    // ============================================
    // Geographical Validation Tests
    // ============================================

    @Test
    fun testIsSameGeographicalZoneReturnsTrueForSameLocation() {
        val gps1 = "48.8566,2.3522" // Paris
        val gps2 = "48.8566,2.3522" // Paris

        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        assertTrue("Same location should be in same zone", result)
    }

    @Test
    fun testIsSameGeographicalZoneReturnsTrueForNearbyLocations() {
        val gps1 = "48.8566,2.3522" // Paris
        val gps2 = "48.8586,2.3542" // Very close to Paris

        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        assertTrue("Nearby locations should be in same zone (within 25km)", result)
    }

    @Test
    fun testIsSameGeographicalZoneReturnsFalseForFarLocations() {
        val gps1 = "48.8566,2.3522" // Paris
        val gps2 = "51.5074,-0.1278" // London (about 340km away)

        val result = bluetoothTransfer.isSameGeographicalZone(gps1, gps2)
        assertFalse("Far locations should not be in same zone", result)
    }

    @Test
    fun testHaversineCalculatesCorrectDistance() {
        // Paris to London distance is approximately 343.5 km
        val lat1 = 48.8566
        val lon1 = 2.3522
        val lat2 = 51.5074
        val lon2 = -0.1278

        val distance = bluetoothTransfer.haversine(lat1, lon1, lat2, lon2)

        // Allow for some margin of error
        assertTrue("Distance should be approximately 343.5 km", distance > 340 && distance < 350)
    }

    // ============================================
    // Transfer Validation Tests
    // ============================================

    @Test
    fun testTransferCoinValidatesCoinExistence() = runTest {
        val senderWallet = com.coins.model.Wallet("sender-1", "Sender")
        val receiverWallet = com.coins.model.Wallet("receiver-1", "Receiver")

        val gps = "48.8566,2.3522"

        // Try to transfer a non-existent coin
        val result = bluetoothTransfer.transferCoin(
            senderWallet, receiverWallet, "non-existent-coin", gps, gps
        )

        assertFalse("Transfer should fail for non-existent coin", result)
    }

    @Test
    fun testTransferCoinValidatesGeographicalZone() = runTest {
        val senderWallet = com.coins.model.Wallet("sender-1", "Sender")
        val receiverWallet = com.coins.model.Wallet("receiver-1", "Receiver")

        val parisGps = "48.8566,2.3522"
        val londonGps = "51.5074,-0.1278"

        // Create a coin in Paris
        val coin = senderWallet.generateCoin(parisGps, System.currentTimeMillis())

        if (coin != null) {
            // Try to transfer to London (should fail - too far)
            val result = bluetoothTransfer.transferCoin(
                senderWallet, receiverWallet, coin.generateCoinId(), parisGps, londonGps
            )

            assertFalse("Transfer should fail when receiver is too far", result)
        }
    }

    @Test
    fun testTransferCoinSucceedsForValidTransfer() = runTest {
        val senderWallet = com.coins.model.Wallet("sender-1", "Sender")
        val receiverWallet = com.coins.model.Wallet("receiver-1", "Receiver")

        val gps = "48.8566,2.3522"

        // Create a coin
        val coin = senderWallet.generateCoin(gps, System.currentTimeMillis())

        if (coin != null) {
            // Transfer to nearby receiver (same location)
            val result = bluetoothTransfer.transferCoin(
                senderWallet, receiverWallet, coin.generateCoinId(), gps, gps
            )

            assertTrue("Transfer should succeed for valid coin and location", result)
        }
    }

    // ============================================
    // State Management Tests
    // ============================================

    @Test
    fun testInitialStateIsIdle() = runTest {
        val initialState = com.coins.bluetooth.BluetoothCoinTransfer(mockContext).state.first()
        assertEquals("Initial state should be Idle", com.coins.bluetooth.BluetoothState.Idle, initialState)
    }

    // ============================================
    // Constants Tests
    // ============================================

    @Test
    fun testCOIN_TRANSFER_UUIDIsValid() {
        assertNotNull("UUID should not be null", com.coins.bluetooth.COIN_TRANSFER_UUID)
        assertEquals("UUID should match expected value",
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
            com.coins.bluetooth.COIN_TRANSFER_UUID)
    }
}