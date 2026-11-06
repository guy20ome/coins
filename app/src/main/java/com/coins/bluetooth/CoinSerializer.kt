package com.coins.bluetooth

import com.coins.model.Coin
import com.coins.model.CoinStatus

/**
 * Serializer for Coin objects to enable Bluetooth transfer.
 * Uses a pure-Kotlin JSON implementation (no Android dependencies needed).
 */
class CoinSerializer {

    /**
     * Serialize a Coin to a JSON string.
     *
     * @param coin The coin to serialize
     * @return JSON string representation of the coin
     */
    fun serialize(coin: Coin): String {
        return buildString {
            append("{")
            append("\"coinId\":${jsonString(coin.coinId)}")
            append(",\"amountCents\":${coin.amountCents}")
            append(",\"giverId\":${jsonString(coin.giverId)}")
            append(",\"ownerId\":${jsonString(coin.ownerId)}")
            append(",\"latitude\":${coin.latitude}")
            append(",\"longitude\":${coin.longitude}")
            append(",\"isDestroyed\":${coin.isDestroyed}")
            append(",\"fullName\":${jsonString(coin.fullName)}")
            append(",\"gpsCoordinate\":${jsonString(coin.gpsCoordinate)}")
            append(",\"timestamp\":${coin.timestamp}")
            append(",\"receiverId\":${jsonString(coin.receiverId)}")
            append(",\"status\":${jsonString(coin.status.name)}")
            append("}")
        }
    }

    /**
     * Deserialize a JSON string to a Coin object.
     *
     * @param json The JSON string to deserialize
     * @return The deserialized Coin, or null if deserialization fails
     */
    fun deserialize(json: String): Coin? {
        return try {
            val map = parseJsonObject(json) ?: return null

            Coin(
                coinId = map["coinId"] ?: "",
                amountCents = map["amountCents"]?.toIntOrNull() ?: 100,
                giverId = map["giverId"] ?: "",
                ownerId = map["ownerId"],
                latitude = map["latitude"]?.toDoubleOrNull() ?: 0.0,
                longitude = map["longitude"]?.toDoubleOrNull() ?: 0.0,
                isDestroyed = map["isDestroyed"] == "true",
                fullName = map["fullName"] ?: "",
                gpsCoordinate = map["gpsCoordinate"] ?: "0.0,0.0",
                timestamp = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                receiverId = map["receiverId"],
                status = try {
                    CoinStatus.valueOf(map["status"] ?: "GENERATED")
                } catch (_: IllegalArgumentException) {
                    CoinStatus.GENERATED
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serialize a list of Coin objects to a JSON string.
     *
     * @param coins The list of coins to serialize
     * @return JSON string representation of the coin list
     */
    fun serializeList(coins: List<Coin>): String {
        return buildString {
            append("[")
            coins.forEachIndexed { index, coin ->
                if (index > 0) append(",")
                append(serialize(coin))
            }
            append("]")
        }
    }

    /**
     * Deserialize a JSON string to a list of Coin objects.
     *
     * @param json The JSON string to deserialize
     * @return The deserialized list of Coins, or empty list if deserialization fails
     */
    fun deserializeList(json: String): List<Coin> {
        return try {
            val array = parseJsonArray(json) ?: return emptyList()
            array.mapNotNull { deserialize(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // -----------------------------------------------------------------------
    // Minimal pure-Kotlin JSON helpers
    // -----------------------------------------------------------------------

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        return "\"" + escapeJson(value) + "\""
    }

    private fun escapeJson(s: String): String {
        return buildString {
            for (ch in s) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\u0008' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\u000a' -> append("\\n")
                    '\u000d' -> append("\\r")
                    '\u0009' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\u%04x".format(ch.code))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }
    }

    private fun parseJsonObject(json: String): Map<String, String?>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String?>()
        var pos = 0
        while (pos < inner.length) {
            // Skip whitespace
            while (pos < inner.length && inner[pos].isWhitespace()) pos++
            if (pos >= inner.length) break

            // Expect key (string)
            if (inner[pos] != '"') return null
            val key = parseJsonString(inner, pos) ?: return null
            pos = key.second

            // Skip whitespace and colon
            while (pos < inner.length && inner[pos].isWhitespace()) pos++
            if (pos >= inner.length || inner[pos] != ':') return null
            pos++

            // Skip whitespace
            while (pos < inner.length && inner[pos].isWhitespace()) pos++

            // Parse value
            val value = parseJsonValue(inner, pos) ?: return null
            result[key.first] = value.first
            pos = value.second

            // Skip whitespace
            while (pos < inner.length && inner[pos].isWhitespace()) pos++

            // Optional comma
            if (pos < inner.length && inner[pos] == ',') {
                pos++
            }
        }
        return result
    }

    private fun parseJsonArray(json: String): List<String>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var pos = 0
        while (pos < inner.length) {
            while (pos < inner.length && inner[pos].isWhitespace()) pos++
            if (pos >= inner.length) break

            val value = parseJsonValue(inner, pos) ?: return null
            result.add(value.first)
            pos = value.second

            while (pos < inner.length && inner[pos].isWhitespace()) pos++
            if (pos < inner.length && inner[pos] == ',') {
                pos++
            }
        }
        return result
    }

    /**
     * Parses a JSON value starting at [pos]. Returns the raw string representation
     * of the value (for objects/arrays this includes braces/brackets) and the
     * position just past the value.
     */
    private fun parseJsonValue(json: String, pos: Int): Pair<String, Int>? {
        var p = pos
        while (p < json.length && json[p].isWhitespace()) p++
        if (p >= json.length) return null

        return when (json[p]) {
            '"' -> parseJsonString(json, p)
            '{', '[' -> {
                val open = json[p]
                val close = if (open == '{') '}' else ']'
                var depth = 0
                var inString = false
                var escaped = false
                var i = p
                while (i < json.length) {
                    val ch = json[i]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\' && inString) {
                        escaped = true
                    } else if (ch == '"') {
                        inString = !inString
                    } else if (!inString) {
                        if (ch == open) depth++
                        else if (ch == close) {
                            depth--
                            if (depth == 0) {
                                return Pair(json.substring(p, i + 1), i + 1)
                            }
                        }
                    }
                    i++
                }
                null
            }
            else -> {
                // Unquoted value: number, boolean, null
                var i = p
                while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != ']' && !json[i].isWhitespace()) {
                    i++
                }
                Pair(json.substring(p, i).trim(), i)
            }
        }
    }

    /**
     * Parses a JSON string starting at [pos] (which must point at the opening quote).
     * Returns the unescaped string content and the position just past the closing quote.
     */
    private fun parseJsonString(json: String, pos: Int): Pair<String, Int>? {
        if (json[pos] != '"') return null
        var i = pos + 1
        val sb = StringBuilder()
        while (i < json.length) {
            val ch = json[i]
            when {
                ch == '\\' && i + 1 < json.length -> {
                    i++
                    val esc = json[i]
                    val decoded = when (esc) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'b' -> '\u0008'
                        'f' -> '\u000c'
                        'n' -> '\u000a'
                        'r' -> '\u000d'
                        't' -> '\u0009'
                        'u' -> {
                            if (i + 4 < json.length) {
                                val hex = json.substring(i + 1, i + 5)
                                try {
                                    Integer.parseInt(hex, 16).toChar()
                                } catch (_: NumberFormatException) {
                                    '?'
                                }
                            } else {
                                '?'
                            }
                        }
                        else -> esc
                    }
                    sb.append(decoded)
                    i++
                }
                ch == '"' -> return Pair(sb.toString(), i + 1)
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return null
    }

    companion object {
        val instance = CoinSerializer()
    }
}
