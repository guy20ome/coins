package com.coins.data.database

import androidx.room.TypeConverter
import com.coins.model.CoinStatus
import java.util.Date

/**
 * Type converters for Room Database to handle custom types.
 */
class Converters {
    
    @TypeConverter
    fun fromCoinStatus(status: CoinStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toCoinStatus(status: String): CoinStatus {
        return CoinStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
