# Coin Generation and Transfer Implementation

This document explains the implementation of the **coin generation** and **transfer** system for the Coins app, based on the rules defined in the README.

## Overview

The system allows users without bank accounts to:
1. **Generate virtual coins** based on their name, location, and timestamp.
2. **Transfer coins** to other users via Bluetooth, with location validation.
3. **Track coin status** (GENERATED, SENT, DESTROYED) to ensure compliance with the 25€ limit.

---

## Key Components

### 1. `Coin` Class

Represents a virtual coin with:
- **Full name** of the user who generated the coin.
- **GPS coordinate** (latitude,longitude) where the coin was generated.
- **Timestamp** (in milliseconds) when the coin was generated.
- **Giver ID** and **Receiver ID** for tracking.
- **Status** (GENERATED, SENT, DESTROYED).

#### Coin ID Format

The coin ID is generated in a **human-readable format**:
```
<fullName>_<gpsCoordinate>_<timestamp>_<giverId>_<receiverId>
```

**Example:**
```
JohnDoe_48.8566,2.3522_1712345678900_GIVER123_RECEIVER456
```

#### Location Validation

The `isWithin25Km` method validates if the receiver's location is within 25 km of the coin's location using the **Haversine formula**.

---

### 2. `Wallet` Class

Manages a user's wallet of coins, ensuring the total value does not exceed 25€.

#### Key Methods

| Method | Description |
|--------|-------------|
| `generateCoin(gpsCoordinate, timestamp)` | Generates a new coin and adds it to the wallet. Returns `null` if the wallet is full. |
| `sendCoin(coinId, receiverId, receiverGps)` | Sends a coin to another user after validating the geographical zone. |
| `receiveCoin(coin, receiverGps)` | Receives a coin from another user. |
| `destroyCoin(coinId)` | Destroys a coin when converted back to fiat currency. |
| `calculateCurrentValue()` | Returns the current value of the wallet (generated + sent - destroyed). |

---

### 3. `BluetoothCoinTransfer` Class

Handles Bluetooth-based coin transfers between users, with location validation.

#### Key Method

| Method | Description |
|--------|-------------|
| `transferCoin(senderWallet, receiverWallet, coinId, senderGps, receiverGps)` | Transfers a coin from the sender's wallet to the receiver's wallet after validating both devices are in the same geographical zone and the receiver is in the valid zone for the coin. |

---

## Usage Examples

### Generating a Coin

```kotlin
val wallet = Wallet(
    userId = "USER123",
    fullName = "John Doe"
)

val parisGps = "48.8566,2.3522"
val coin = wallet.generateCoin(parisGps, System.currentTimeMillis())

if (coin != null) {
    val coinId = coin.generateCoinId()
    println("Generated coin: $coinId")
}
```

### Sending a Coin via Bluetooth

```kotlin
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

if (coin != null) {
    val transferSuccess = BluetoothCoinTransfer().transferCoin(
        senderWallet = senderWallet,
        receiverWallet = receiverWallet,
        coinId = coin.generateCoinId(),
        senderGps = parisGps,
        receiverGps = parisGps
    )

    if (transferSuccess) {
        println("Coin transferred successfully!")
    } else {
        println("Transfer failed: Invalid location or other error.")
    }
}
```

### Checking Wallet Balance

```kotlin
val wallet = Wallet(
    userId = "USER123",
    fullName = "John Doe"
)

val currentValue = wallet.calculateCurrentValue()
println("Current wallet value: $currentValue €")
```

---

## Validation Rules

1. **Coin Generation**:
   - A user can generate coins as long as the total value (generated + sent - destroyed) is ≤ 25€.
   - Each coin has a unique ID based on the user's name, location, and timestamp.

2. **Coin Transfer**:
   - Both devices must be in the **same 25 km geographical zone** for Bluetooth transfer to initiate.
   - The receiver must be in the **valid 25 km zone** for the specific coin being transferred.
   - If validation fails, the transfer is rejected.

3. **Coin Destruction**:
   - A coin can be destroyed when converted back to fiat currency, reducing the wallet's total value.

---

## Testing

Unit tests are provided in `CoinTest.kt` to verify:
- Coin ID generation.
- Location validation (within 25 km).
- Wallet operations (generate, send, receive, destroy).
- Transfer validation.

Run the tests using:
```bash
./gradlew test
```

---

## Next Steps

1. **Integrate with Bluetooth API**: Implement actual Bluetooth discovery and connection logic.
2. **Add UI**: Create screens for generating, sending, and receiving coins.
3. **Backend Integration**: Connect to a backend for storing coin data and validating transactions.
4. **Error Handling**: Add more robust error handling and user feedback.

---

## Files Implemented

| File | Purpose |
|------|---------|
| `app/src/main/java/com/coins/model/Coin.kt` | Defines the `Coin` class and its logic. |
| `app/src/main/java/com/coins/model/Wallet.kt` | Manages a user's wallet of coins. |
| `app/src/main/java/com/coins/bluetooth/BluetoothCoinTransfer.kt` | Handles Bluetooth-based coin transfers. |
| `app/src/test/java/com/coins/model/CoinTest.kt` | Unit tests for the coin system. |

---

## Summary

This implementation provides a **secure**, **location-validated** system for generating and transferring virtual coins between users without bank accounts. The system ensures compliance with the 25€ limit and validates geographical zones for all transactions.
