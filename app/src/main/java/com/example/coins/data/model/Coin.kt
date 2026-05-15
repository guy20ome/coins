package com.example.coins.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a virtual coin.
 *
 * - `coinId` is a globally unique identifier (UUID).
 * - `amount` is stored in cents to avoid floating‑point errors.
 * - `giverId` is the identifier of the user who minted the coin.
 * - `ownerId` is the current holder (null → not yet assigned).
 * - `latitude` / `longitude` define the geographic zone the coin belongs to.
 */
@Entity(tableName = "coins")
data class Coin(
    @PrimaryKey val coinId: String,
    val amountCents: Int,
    val giverId: String,
    val ownerId: String?,
    val latitude: Double,
    val longitude: Double,
    val isDestroyed: Boolean = false
)
