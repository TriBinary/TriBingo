package net.trilleo.mc.plugins.tribingo.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.data.BingoServerData.Companion.DEFAULT_TIMER_SECONDS
import net.trilleo.mc.plugins.tribingo.data.BingoServerData.Companion.KEY_PLAYER_STATES
import net.trilleo.mc.plugins.tribingo.enums.GameState
import java.util.*

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
 * | Key                        | Type          | Description                              |
 * |:---------------------------|:--------------|:-----------------------------------------|
 * | `bingo_board_size`         | Int           | Side-length of the last board (0 = none) |
 * | `bingo_game_state`         | String        | Serialised [GameState] name              |
 * | `bingo_board_layout`       | JsonArray     | Objective IDs in cell order              |
 * | `bingo_player_states`      | JsonObject    | Per-player completion/progress/points data |
 * | `bingo_timer_seconds`      | Int           | Countdown duration in seconds (default 3600) |
 */
class BingoServerData : ServerData() {

    companion object {
        private const val KEY_BOARD_SIZE = "bingo_board_size"
        private const val KEY_GAME_STATE = "bingo_game_state"
        private const val KEY_BOARD_LAYOUT = "bingo_board_layout"
        private const val KEY_PLAYER_STATES = "bingo_player_states"
        private const val KEY_TIMER_SECONDS = "bingo_timer_seconds"
        const val DEFAULT_TIMER_SECONDS = 3600
    }

    /**
     * Deserialized per-player state returned by [loadPlayerStates].
     *
     * @param completedCells  set of completed cell indices
     * @param progressData    per-objective progress counters
     * @param completedLines  set of line keys that have received bonus points
     * @param points          accumulated point total
     * @param stringData      arbitrary string values for code objectives,
     *                        keyed by `"objectiveId:fieldName"`
     * @param stepData        ordered step-token sets for sequential objectives,
     *                        keyed by objective ID
     */
    data class PersistedPlayerState(
        val completedCells: Set<Int>,
        val progressData: Map<String, Int>,
        val completedLines: Set<String>,
        val points: Int,
        val stringData: Map<String, String> = emptyMap(),
        val stepData: Map<String, Set<String>> = emptyMap()
    )

    // ── Typed properties ─────────────────────────────────────────────────

    /**
     * Countdown duration in seconds used when a new game starts.
     *
     * Defaults to [DEFAULT_TIMER_SECONDS] (3 600 s = 1 hour) when not yet set.
     * Must be a positive value (validated by the command layer before writing).
     */
    var timerSeconds: Int
        get() = getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
        set(value) = set(KEY_TIMER_SECONDS, value)

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

            val lines = JsonArray()
            ps.completedLines.forEach { lines.add(it) }
            obj.add("l", lines)

            obj.addProperty("pts", ps.points)

            // Extended state for code objectives
            if (ps.stringData.isNotEmpty()) {
                val sd = JsonObject()
                ps.stringData.forEach { (k, v) -> sd.addProperty(k, v) }
                obj.add("sd", sd)
            }

            if (ps.stepData.isNotEmpty()) {
                val ssd = JsonObject()
                ps.stepData.forEach { (objectiveId, steps) ->
                    val arr = JsonArray()
                    steps.forEach { arr.add(it) }
                    ssd.add(objectiveId, arr)
                }
                obj.add("ssd", ssd)
            }

            root.add(uuid.toString(), obj)
        }
        set(KEY_PLAYER_STATES, root)
    }

    /**
     * Deserialises and returns all previously saved player states.
     *
     * The returned map is keyed by [UUID]; each value is a [PersistedPlayerState]
     * containing cells, progress, completed lines, and point total.
     */
    fun loadPlayerStates(): Map<UUID, PersistedPlayerState> {
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

                val lines: Set<String> = if (obj.has("l") && obj.get("l").isJsonArray) {
                    obj.getAsJsonArray("l").map { it.asString }.toSet()
                } else emptySet()

                val points: Int = if (obj.has("pts")) obj.get("pts").asInt else 0

                val stringData: Map<String, String> =
                    if (obj.has("sd") && obj.get("sd").isJsonObject) {
                        obj.getAsJsonObject("sd").entrySet()
                            .associate { (k, v) -> k to v.asString }
                    } else emptyMap()

                val stepData: Map<String, Set<String>> =
                    if (obj.has("ssd") && obj.get("ssd").isJsonObject) {
                        obj.getAsJsonObject("ssd").entrySet().associate { (oid, el) ->
                            oid to if (el.isJsonArray) {
                                // Use LinkedHashSet to preserve the insertion order stored in the JSON
                                // array; order matters for SequentialBingoObjective step tracking.
                                el.asJsonArray.map { it.asString }.toCollection(LinkedHashSet())
                            } else LinkedHashSet()
                        }
                    } else emptyMap()

                put(uuid, PersistedPlayerState(cells, progress, lines, points, stringData, stepData))
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
