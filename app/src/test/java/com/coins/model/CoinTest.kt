package com.coins.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Coin and Wallet classes.
 */
class CoinTest {

    @Test
    fun testGenerateCoinId() {
        val fullName = "John Doe"
        val gpsCoordinate = "48.8566,2.3522"
        val timestamp = System.currentTimeMillis()

        val coin = Coin(
            fullName = fullName,
            gpsCoordinate = gpsCoordinate,
            timestamp = timestamp,
            giverId = "GIVER123"
        )

        val coinId = coin.generateCoinId()
        assertTrue(coinId.contains(fullName))
        assertTrue(coinId.contains(gpsCoordinate))
        // Check that the timestamp is formatted as YYYYMMDDHHMMSS (14 digits)
        val timestampPattern = "\\d{14}".toRegex()
        assertTrue("Coin ID should contain 14-digit timestamp", timestampPattern.containsMatchIn(coinId))
        // Check that it does NOT contain giverId or receiverId
        assertFalse("Coin ID should not contain giverId", coinId.contains("GIVER123"))
    }

    @Test
    fun testIsWithin25Km() {
        // Paris coordinates
        val parisGps = "48.8566,2.3522"
        
        // Create a coin with Paris coordinates
        val coin = Coin(
            fullName = "John Doe",
            gpsCoordinate = parisGps,
            timestamp = System.currentTimeMillis(),
            giverId = "GIVER123"
        )

        // Test with a location within 25 km of Paris (e.g., Versailles)
        val versaillesGps = "48.8049,2.1346"
        assertTrue(coin.isWithin25Km(versaillesGps))

        // Test with a location far from Paris (e.g., Lyon)
        val lyonGps = "45.7640,4.8357"
        assertFalse(coin.isWithin25Km(lyonGps))
    }

    @Test
    fun testWalletGenerateCoin() {
        val wallet = Wallet(
            userId = "USER123",
            fullName = "John Doe"
        )

        val parisGps = "48.8566,2.3522"
        val coin = wallet.generateCoin(parisGps, System.currentTimeMillis())

        assertNotNull(coin)
        assertEquals(1, wallet.calculateCurrentValue())
    }

    @Test
    fun testWalletGenerateCoinWhenFull() {
        val wallet = Wallet(
            userId = "USER123",
            fullName = "John Doe",
            maxCoinsValue = 2 // Set low for testing
        )

        // Generate 2 coins
        val parisGps = "48.8566,2.3522"
        val coin1 = wallet.generateCoin(parisGps, System.currentTimeMillis())
        val coin2 = wallet.generateCoin(parisGps, System.currentTimeMillis())

        assertNotNull(coin1)
        assertNotNull(coin2)
        assertEquals(2, wallet.calculateCurrentValue())

        // Try to generate a third coin (should fail)
        val coin3 = wallet.generateCoin(parisGps, System.currentTimeMillis())
        assertTrue(coin3 == null)
    }

    @Test
    fun testWalletSendCoin() {
        val senderWallet = Wallet(
            userId = "SENDER123",
            fullName = "John Doe"
        )

        val receiverWallet = Wallet(
            userId = "RECEIVER456",
            fullName = "Jane Smith"
        )

        val parisGps = "48.8566,2.3522"
        val coin = senderWallet.generateCoin(parisGps, System.currentTimeMillis())
        assertNotNull(coin)

        // Transfer the coin to the receiver via BluetoothCoinTransfer
        val transferSuccess = com.coins.bluetooth.BluetoothCoinTransfer().transferCoin(
            senderWallet = senderWallet,
            receiverWallet = receiverWallet,
            coinId = coin!!.generateCoinId(),
            senderGps = parisGps,
            receiverGps = parisGps
        )

        assertTrue(transferSuccess)
        assertEquals(0, senderWallet.calculateCurrentValue())
        assertEquals(1, receiverWallet.calculateCurrentValue())
    }

    @Test
    fun testWalletSendCoinFailsWhenOutOfZone() {
        val senderWallet = Wallet(
            userId = "SENDER123",
            fullName = "John Doe"
        )

        val receiverWallet = Wallet(
            userId = "RECEIVER456",
            fullName = "Jane Smith"
        )

        val parisGps = "48.8566,2.3522"
        val lyonGps = "45.7640,4.8357" // Far from Paris
        
        val coin = senderWallet.generateCoin(parisGps, System.currentTimeMillis())
        assertNotNull(coin)

        // Try to send the coin to a receiver in a different zone
        val sendSuccess = senderWallet.sendCoin(
            coinId = coin!!.generateCoinId(),
            receiverId = receiverWallet.userId,
            receiverGps = lyonGps
        )

        assertFalse(sendSuccess)
        assertEquals(1, senderWallet.calculateCurrentValue()) // Coin not sent
    }

    @Test
    fun testWalletDestroyCoin() {
        val wallet = Wallet(
            userId = "USER123",
            fullName = "John Doe"
        )

        val parisGps = "48.8566,2.3522"
        val coin = wallet.generateCoin(parisGps, System.currentTimeMillis())
        assertNotNull(coin)

        // Destroy the coin
        val destroySuccess = wallet.destroyCoin(coin!!.generateCoinId())
        assertTrue(destroySuccess)
        assertEquals(0, wallet.calculateCurrentValue())
    }
}