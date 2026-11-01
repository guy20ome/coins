package com.coins.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coins.model.Coin
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Coin entities.
 * Provides CRUD operations and queries for the coins table.
 */
@Dao
interface CoinDao {
    
    /**
     * Insert a single coin.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: Coin): Long
    
    /**
     * Insert multiple coins.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coins: List<Coin>): List<Long>
    
    /**
     * Update a single coin.
     */
    @Update
    suspend fun update(coin: Coin): Int
    
    /**
     * Update multiple coins.
     */
    @Update
    suspend fun updateAll(coins: List<Coin>): Int
    
    /**
     * Delete a single coin.
     */
    @Delete
    suspend fun delete(coin: Coin): Int
    
    /**
     * Delete a coin by ID.
     */
    @Query("DELETE FROM coins WHERE coinId = :coinId")
    suspend fun deleteById(coinId: String): Int
    
    /**
     * Get a coin by its ID.
     */
    @Query("SELECT * FROM coins WHERE coinId = :coinId LIMIT 1")
    suspend fun getById(coinId: String): Coin?
    
    /**
     * Get all coins.
     */
    @Query("SELECT * FROM coins ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Coin>>
    
    /**
     * Get all coins as a list (non-reactive).
     */
    @Query("SELECT * FROM coins ORDER BY timestamp DESC")
    suspend fun getAllList(): List<Coin>
    
    /**
     * Get coins by owner ID.
     */
    @Query("SELECT * FROM coins WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun getByOwner(ownerId: String): Flow<List<Coin>>
    
    /**
     * Get coins by giver ID.
     */
    @Query("SELECT * FROM coins WHERE giverId = :giverId ORDER BY timestamp DESC")
    fun getByGiver(giverId: String): Flow<List<Coin>>
    
    /**
     * Get coins by receiver ID.
     */
    @Query("SELECT * FROM coins WHERE receiverId = :receiverId ORDER BY timestamp DESC")
    fun getByReceiver(receiverId: String): Flow<List<Coin>>
    
    /**
     * Get coins by status.
     */
    @Query("SELECT * FROM coins WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatus(status: String): Flow<List<Coin>>
    
    /**
     * Get non-destroyed coins.
     */
    @Query("SELECT * FROM coins WHERE isDestroyed = 0 ORDER BY timestamp DESC")
    fun getNonDestroyed(): Flow<List<Coin>>
    
    /**
     * Get destroyed coins.
     */
    @Query("SELECT * FROM coins WHERE isDestroyed = 1 ORDER BY timestamp DESC")
    fun getDestroyed(): Flow<List<Coin>>
    
    /**
     * Get coins within a specific GPS coordinate range (for geographical validation).
     */
    @Query("""
        SELECT * FROM coins 
        WHERE isDestroyed = 0 
        AND giverId = :giverId
        ORDER BY timestamp DESC
    """)
    fun getActiveCoinsByGiver(giverId: String): Flow<List<Coin>>
    
    /**
     * Get the count of non-destroyed coins for a giver.
     */
    @Query("""
        SELECT COUNT(*) FROM coins 
        WHERE isDestroyed = 0 AND giverId = :giverId
    """)
    suspend fun getActiveCoinCountByGiver(giverId: String): Int
    
    /**
     * Get the total value (in cents) of non-destroyed coins for a giver.
     */
    @Query("""
        SELECT SUM(amountCents) FROM coins 
        WHERE isDestroyed = 0 AND giverId = :giverId
    """)
    suspend fun getActiveValueByGiver(giverId: String): Int
    
    /**
     * Get the total value (in cents) of all non-destroyed coins.
     */
    @Query("SELECT SUM(amountCents) FROM coins WHERE isDestroyed = 0")
    suspend fun getTotalActiveValue(): Int
    
    /**
     * Delete all coins.
     */
    @Query("DELETE FROM coins")
    suspend fun deleteAll(): Int
    
    /**
     * Get coins by generated coin ID (the formatted ID).
     */
    @Query("SELECT * FROM coins WHERE :coinId LIKE '%' || fullName || '%' AND :coinId LIKE '%' || gpsCoordinate || '%' LIMIT 1")
    suspend fun getByGeneratedId(coinId: String): Coin?
}
