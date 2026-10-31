# Coin Generation and Transfer Implementation Summary

This document serves as the centralized documentation for the **coin generation** and **transfer** system in the Coins app.

---

## 📌 Overview

The system allows users without bank accounts to:
1. **Generate virtual coins** based on their name, location, and timestamp.
2. **Transfer coins** to other users via Bluetooth, with 25 km location validation.
3. **Track coin status** (`GENERATED`, `SENT`, `DESTROYED`) while enforcing the 25€ limit per wallet.

---

## 📁 Project Architecture & Components

### 1. Core Models & Services

* **[Coin.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/coins/model/Coin.kt)**
  - Represents a virtual coin with a unique, human-readable ID.
  - Formats Coin IDs: `<fullName>_<gpsCoordinate>_<timestamp>_<giverId>_<receiverId>`
  - Performs distance validation using the **Haversine formula** (25 km radius check).
  - Tracks state: `GENERATED`, `SENT`, `DESTROYED`.

* **[Wallet.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/coins/model/Wallet.kt)**
  - Manages a user's coin collection and balance.
  - Enforces the 25€ total limit (`generated + sent - destroyed`).
  - Provides methods: `generateCoin()`, `sendCoin()`, `receiveCoin()`, `destroyCoin()`, and `calculateCurrentValue()`.

* **[BluetoothCoinTransfer.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/coins/bluetooth/BluetoothCoinTransfer.kt)**
  - Manages Bluetooth-based coin transfer workflows.
  - Validates that sender and receiver devices are in the same 25 km zone.
  - Validates receiver location against the coin's generation zone.

### 2. UI & Navigation (Jetpack Compose)

* **[HomeScreen.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/ui/screens/HomeScreen.kt)**: Main dashboard displaying wallet balance, coin generation modal, and live list of generated coins.
* **[GiverScreen.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/ui/screens/GiverScreen.kt)**: Screen for managing coin generation and transfers for givers.
* **[RegistrationScreen.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/ui/screens/RegistrationScreen.kt)**: First-time onboarding screen for user registration.
* **[CoinsViewModel.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/viewmodel/CoinsViewModel.kt)**: State management providing reactive `StateFlow` streams for balance and coin list updates.
* **[CoinRepository.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/data/repository/CoinRepository.kt)**: In-memory singleton repository holding global coin and balance state.
* **[PreferencesHelper.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/main/java/com/example/coins/data/PreferencesHelper.kt)**: Utility for storing first-time launch and registration preferences.

### 3. Unit Testing

* **[CoinTest.kt](file:///C:/Users/gniva/StudioProjects/coins/app/src/test/java/com/coins/model/CoinTest.kt)**: Unit tests for coin ID generation, 25 km location checks, wallet limits, and transfer validations.

---

## 🔑 Key Features & Validation Rules

### 1. Human-Readable Coin ID Format
```
Format:  <fullName>_<gpsCoordinate>_<timestamp>_<giverId>_<receiverId>
Example: JohnDoe_48.8566,2.3522_1712345678900_GIVER123_RECEIVER456
```

### 2. Validation Matrix

| Rule | Enforcement Mechanism |
|------|-----------------------|
| **Coin Uniqueness** | Formatted using user name, GPS coordinates, timestamp, and transaction IDs |
| **25€ Limit** | Wallet restricts creation if total `generated + sent - destroyed` exceeds 25€ |
| **Location Validation** | Haversine formula verifies coordinates within a 25 km radius |
| **Bluetooth Proximity** | Both devices must be within the same 25 km zone during transfer |
| **Recipient Zone** | Recipient must be within 25 km of the original coin generation coordinate |

---

## 📖 Code Usage Examples

### 1. Generating a Coin
```kotlin
val wallet = Wallet(userId = "USER123", fullName = "John Doe")
val parisGps = "48.8566,2.3522"
val coin = wallet.generateCoin(parisGps, System.currentTimeMillis())

if (coin != null) {
    val coinId = coin.generateCoinId()
    println("Generated coin: $coinId")
}
```

### 2. Transferring a Coin via Bluetooth
```kotlin
val transferSuccess = BluetoothCoinTransfer().transferCoin(
    senderWallet = senderWallet,
    receiverWallet = receiverWallet,
    coinId = coinId,
    senderGps = "48.8566,2.3522",
    receiverGps = "48.8566,2.3522"
)
if (transferSuccess) {
    println("Transfer successful!")
}
```

### 3. Checking Wallet Balance
```kotlin
val currentValue = wallet.calculateCurrentValue()
println("Current wallet value: $currentValue €")
```

---

## 🧪 Testing

Run all unit tests using Gradle:
```bash
./gradlew test
```

Covered Scenarios:
- ✅ Coin ID formatting and uniqueness
- ✅ Haversine 25 km radius calculation
- ✅ Wallet balance calculation and 25€ limit enforcement
- ✅ Bluetooth same-zone and receiver location verification

---

## 🚀 Next Steps

1. **Bluetooth Low Energy (BLE) Integration**: Connect `BluetoothCoinTransfer` logic to actual BLE advertising/scanning APIs.
2. **Backend Sync**: Optional server backup for coin registry and offline verification tokens.
3. **Location Services**: Integrate FusedLocationProviderClient for automatic GPS coordinates during coin generation.
