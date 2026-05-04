package net.trilleo.mc.plugins.tribingo.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.*

/**
 * Per-player data container backed by a JSON file stored on disk.
 *
 * Each instance is keyed by the player's [uuid]. The underlying [JsonObject]
 * is populated when the player joins and flushed to disk when they quit (managed
 * by [PlayerDataManager]).
 *
 * Extend this class to add strongly-typed properties for your own data:
 *
 * ```kotlin
 * class MyPlayerData(uuid: UUID) : PlayerData(uuid) {
 *     var kills: Int
 *         get() = getInt("kills")
 *         set(value) = set("kills", value)
 * }
 * ```
 *
 * Then register the factory once during plugin startup:
 *
 * ```kotlin
 * PlayerDataManager.setFactory { uuid -> MyPlayerData(uuid) }
 * ```
 *
 * @param uuid the unique identifier of the player this data belongs to
 */
open class PlayerData(val uuid: UUID) {

    /** The raw JSON object holding all persisted values for this player. */
    internal val json: JsonObject = JsonObject()

    // ── Typed Getters ───────────────────────────────────────────────────

    /**
     * Returns the [String] value stored at [key], or [default] when absent.
     */
    fun getString(key: String, default: String = ""): String =
        if (json.has(key)) json.get(key).asString else default

    /**
     * Returns the [Int] value stored at [key], or [default] when absent.
     */
    fun getInt(key: String, default: Int = 0): Int =
        if (json.has(key)) json.get(key).asInt else default

    /**
     * Returns the [Double] value stored at [key], or [default] when absent.
     */
    fun getDouble(key: String, default: Double = 0.0): Double =
        if (json.has(key)) json.get(key).asDouble else default

    /**
     * Returns the [Boolean] value stored at [key], or [default] when absent.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        if (json.has(key)) json.get(key).asBoolean else default

    /**
     * Returns the [JsonArray] stored at [key], or an empty [JsonArray] when absent.
     */
    fun getJsonArray(key: String): JsonArray =
        if (json.has(key) && json.get(key).isJsonArray) json.getAsJsonArray(key) else JsonArray()

    // ── Typed Setters ───────────────────────────────────────────────────

    /** Stores a [String] value at [key]. */
    fun set(key: String, value: String) {
        json.addProperty(key, value)
    }

    /** Stores an [Int] value at [key]. */
    fun set(key: String, value: Int) {
        json.addProperty(key, value)
    }

    /** Stores a [Double] value at [key]. */
    fun set(key: String, value: Double) {
        json.addProperty(key, value)
    }

    /** Stores a [Boolean] value at [key]. */
    fun set(key: String, value: Boolean) {
        json.addProperty(key, value)
    }

    /** Stores a [JsonArray] value at [key]. */
    fun set(key: String, value: JsonArray) {
        json.add(key, value)
    }

    /** Stores a [JsonObject] value at [key]. */
    fun set(key: String, value: JsonObject) {
        json.add(key, value)
    }

    // ── Utilities ───────────────────────────────────────────────────────

    /** Removes the entry at [key] from the backing store. */
    fun remove(key: String) {
        json.remove(key)
    }

    /** Returns `true` when [key] exists in the backing store. */
    fun has(key: String): Boolean = json.has(key)
}
