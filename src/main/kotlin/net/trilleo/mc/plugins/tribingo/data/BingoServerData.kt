package net.trilleo.mc.plugins.tribingo.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.enums.GameState
import java.util.UUID

/**
 * Server-wide persistence for the active [net.trilleo.mc.plugins.tribingo.bingo.BingoGame].
 *
 * Extends [ServerData] to add strongly-typed bingo-specific properties that
 * are serialised to `serverdata.json` by [ServerDataManager].
 *
 * ### Registration
 * Register the factory **before** calling [ServerDataManager.init]:
 * ```kotlin
 * ServerDataManager.setFactory { BingoServerData() }
 * ServerDataManager.init(plugin)
 * ```
 *
 * ### Stored keys
 * | Key                        | Type          | Description                          |
 * |:---------------------------|:--------------|:-------------------------------------|
 * | `bingo_board_size`         | Int           | Side-length of the last board (0 = none) |
 * | `bingo_game_state`         | String        | Serialised [GameState] name          |
 * | `bingo_board_layout`       | JsonArray     | Objective IDs in cell order          |
 * | `bingo_player_states`      | JsonObject    | Per-player completion/progress data  |
 */
class BingoServerData : ServerData() {

    companion object {
        private const val KEY_BOARD_SIZE    = "bingo_board_size"
        private const val KEY_GAME_STATE    = "bingo_game_state"
        private const val KEY_BOARD_LAYOUT  = "bingo_board_layout"
        private const val KEY_PLAYER_STATES = "bingo_player_states"
    }

    // ── Typed properties ─────────────────────────────────────────────────

    /** Side-length of the persisted board; `0` means no game has been saved yet. */
    var boardSize: Int
        get() = getInt(KEY_BOARD_SIZE, 0)
        set(value) = set(KEY_BOARD_SIZE, value)

    /** Serialised [GameState] name of the persisted game. */
    var gameStateName: String
        get() = getString(KEY_GAME_STATE, GameState.INACTIVE.name)
        set(value) = set(KEY_GAME_STATE, value)

    /**
     * Ordered list of objective IDs matching the flat cell layout of the board.
     *
     * The list contains exactly `boardSize × boardSize` entries when a game
     * has been saved.
     */
    var boardLayout: List<String>
        get() = getJsonArray(KEY_BOARD_LAYOUT).map { it.asString }
        set(value) {
            val arr = JsonArray()
            value.forEach { arr.add(it) }
            set(KEY_BOARD_LAYOUT, arr)
        }

    // ── Player state helpers ─────────────────────────────────────────────

    /**
     * Serialises all per-player states in [states] and stores them under
     * [KEY_PLAYER_STATES].
     *
     * @param states map from UUID to player state
     */
    fun savePlayerStates(states: Map<UUID, BingoPlayerState>) {
        val root = JsonObject()
        states.forEach { (uuid, ps) ->
            val obj = JsonObject()

            val cells = JsonArray()
            ps.completedCells.forEach { cells.add(it) }
            obj.add("c", cells)

            val progress = JsonObject()
            ps.progressData.forEach { (k, v) -> progress.addProperty(k, v) }
            obj.add("p", progress)

            root.add(uuid.toString(), obj)
        }
        set(KEY_PLAYER_STATES, root)
    }

    /**
     * Deserialises and returns all previously saved player states.
     *
     * The returned map is keyed by [UUID]; each value is a pair of
     * (completedCells, progressData).
     */
    fun loadPlayerStates(): Map<UUID, Pair<Set<Int>, Map<String, Int>>> {
        if (!json.has(KEY_PLAYER_STATES) || !json.get(KEY_PLAYER_STATES).isJsonObject) {
            return emptyMap()
        }
        val root = json.getAsJsonObject(KEY_PLAYER_STATES)

        return buildMap {
            root.entrySet().forEach { (uuidStr, el) ->
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: return@forEach
                val obj = el.asJsonObject

                val cells: Set<Int> = if (obj.has("c") && obj.get("c").isJsonArray) {
                    obj.getAsJsonArray("c").map { it.asInt }.toSet()
                } else emptySet()

                val progress: Map<String, Int> = if (obj.has("p") && obj.get("p").isJsonObject) {
                    obj.getAsJsonObject("p").entrySet().associate { (k, v) -> k to v.asInt }
                } else emptyMap()

                put(uuid, cells to progress)
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Removes all bingo game data from the backing JSON, effectively resetting
     * the persisted state.
     */
    fun clearGameData() {
        remove(KEY_BOARD_SIZE)
        remove(KEY_GAME_STATE)
        remove(KEY_BOARD_LAYOUT)
        remove(KEY_PLAYER_STATES)
    }
}
