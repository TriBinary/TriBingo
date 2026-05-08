package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.Main
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.init
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.resetGame
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.save
import net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry
import net.trilleo.mc.plugins.tribingo.data.BingoServerData
import net.trilleo.mc.plugins.tribingo.data.ServerDataManager
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.guis.BingoBoardGUI
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * Singleton facade for the entire Bingo system.
 *
 * All gameplay operations (creating games, starting, stopping, resetting,
 * refreshing, and checking completion) flow through this object so that
 * objective implementations and commands have a single, stable entry-point.
 *
 * ### Initialisation
 * Call [init] once in `Main.onEnable` **after** objectives have been loaded
 * into [BingoObjectiveRegistry]:
 * ```kotlin
 * BingoManager.init(this)
 * ```
 *
 * ### Shutdown
 * Call [save] in `Main.onDisable` **before** [ServerDataManager.save]:
 * ```kotlin
 * BingoManager.save()
 * ServerDataManager.save()
 * ```
 */
object BingoManager {

    private lateinit var plugin: JavaPlugin

    /**
     * The currently active (or most-recently-created) [BingoGame], or `null`
     * if no game has been set up yet.
     */
    var currentGame: BingoGame? = null
        private set

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Initialises the manager, stores the plugin reference, and attempts to
     * rehydrate a previously persisted game from [BingoServerData].
     *
     * If no persisted game is found and the registry contains enough objectives
     * for the configured default board size, a new game is created automatically.
     *
     * @param plugin the owning plugin instance
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        if (!rehydrate()) {
            val main = plugin as? Main ?: return
            val defaultSize = main.pluginConfig.boardDefaultSize
            val needed = defaultSize * defaultSize
            if (BingoObjectiveRegistry.getAll().size >= needed) {
                newGame(defaultSize)
            } else {
                plugin.logger.info(
                    "[BingoManager] Not enough objectives for a ${defaultSize}×${defaultSize} board; " +
                            "create a game manually with /bingo size <3-6>"
                )
            }
        }
    }

    /**
     * Serialises the current game to [BingoServerData] so that
     * [ServerDataManager.save] can persist it to disk.
     *
     * Call this from `Main.onDisable` before [ServerDataManager.save].
     */
    fun save() {
        val data = ServerDataManager.get() as? BingoServerData ?: return
        val game = currentGame
        if (game == null) {
            data.clearGameData()
            return
        }
        data.boardSize = game.board.size
        data.gameStateName = game.state.name
        data.boardLayout = game.board.cells.map { it.objective.id }
        data.savePlayerStates(game.playerStates)
    }

    // ── Query ────────────────────────────────────────────────────────────

    /** Returns `true` while a game is in [GameState.ACTIVE] state. */
    fun isGameActive(): Boolean = currentGame?.state == GameState.ACTIVE

    // ── Game management ───────────────────────────────────────────────────

    /**
     * Creates a new [BingoGame] with a randomly-selected set of objectives
     * drawn from [BingoObjectiveRegistry], replacing any existing game.
     *
     * @param size the side-length of the new board (`3..6`)
     * @return the newly created game
     * @throws IllegalArgumentException if [size] is not in `3..6`
     * @throws IllegalStateException if the registry doesn't have enough objectives
     */
    fun newGame(size: Int): BingoGame {
        require(size in 3..6) { "Board size must be between 3 and 6, got $size" }

        val objectives = BingoObjectiveRegistry.getAll()
        val needed = size * size
        check(objectives.size >= needed) {
            "Need at least $needed objectives for a ${size}×${size} board; " +
                    "only ${objectives.size} are registered"
        }

        val cells = objectives.shuffled()
            .take(needed)
            .mapIndexed { i, obj -> BingoCell(i, obj) }
        val board = BingoBoard(size, cells)
        val game = BingoGame(board, plugin)
        currentGame = game

        plugin.logger.info("[BingoManager] Created new ${size}×${size} game")
        return game
    }

    /**
     * Starts the current game.
     *
     * Does nothing (with a warning) when no game exists or the game is not
     * in [GameState.INACTIVE] state.
     */
    fun startGame() {
        val game = currentGame
        if (game == null) {
            plugin.logger.warning("[BingoManager] startGame() called but no game exists")
            return
        }
        if (game.state != GameState.INACTIVE) {
            plugin.logger.warning(
                "[BingoManager] startGame() called but game is in state ${game.state}"
            )
            return
        }
        game.start()
    }

    /**
     * Stops the current game without a winner.
     *
     * Does nothing when there is no active game.
     */
    fun stopGame() {
        val game = currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        game.end(null)
    }

    /**
     * Resets all player progress and transitions the current game back to
     * [GameState.INACTIVE], keeping the existing board layout.
     *
     * Does nothing when no game exists.
     */
    fun resetGame() {
        currentGame?.reset()
    }

    /**
     * Selects a new random set of objectives from the registry and rebuilds the
     * board of the current game.
     *
     * The game must be in [GameState.INACTIVE] (call [resetGame] first if needed).
     * Does nothing (with a warning) when no game exists.
     */
    fun refreshBoard() {
        val game = currentGame
        if (game == null) {
            plugin.logger.warning("[BingoManager] refreshBoard() called but no game exists")
            return
        }
        game.refresh(BingoObjectiveRegistry.getAll())
    }

    /**
     * Changes the board size to [size], creating a new game if necessary.
     *
     * If a game already exists with the requested size and is in
     * [GameState.INACTIVE] state, the board is refreshed instead of creating a
     * new game object.
     *
     * @param size the desired board side-length (`3..6`)
     */
    fun setBoardSize(size: Int) {
        val game = currentGame
        if (game != null && game.board.size == size && game.state == GameState.INACTIVE) {
            refreshBoard()
        } else {
            newGame(size)
        }
    }

    // ── Completion ────────────────────────────────────────────────────────

    /**
     * Called by event objectives when a triggering event has been processed.
     *
     * This method:
     * 1. Verifies the game is active and the corresponding cell has not yet
     *    been completed for [player].
     * 2. Marks the cell as complete in the player's [BingoPlayerState].
     * 3. Optionally broadcasts a completion announcement (see
     *    [net.trilleo.mc.plugins.tribingo.config.PluginConfig.announceCompletions]).
     * 4. Refreshes the player's open board GUI if any.
     * 5. Checks the configured win condition and ends the game if met.
     *
     * @param player    the player who completed the objective
     * @param objective the objective that was completed
     */
    fun checkCompletion(player: Player, objective: BingoObjective) {
        val game = currentGame ?: return
        if (game.state != GameState.ACTIVE) return

        val state = game.getOrCreateState(player.uniqueId)
        val cell = game.board.cells.find { it.objective.id == objective.id } ?: return

        if (state.isCompleted(cell.cellIndex)) return

        state.markCompleted(cell.cellIndex)

        // Announce completion
        val main = plugin as? Main
        if (main?.pluginConfig?.announceCompletions == true) {
            val msg = Component.text()
                .append(Component.text("[Bingo] ", NamedTextColor.GOLD))
                .append(Component.text(player.name, NamedTextColor.YELLOW))
                .append(Component.text(" completed: ", NamedTextColor.GRAY))
                .append(objective.name)
                .build()
            plugin.server.onlinePlayers.forEach { it.sendMessage(msg) }
        }

        // Refresh the player's open board GUI
        (GUIManager.getGUI("bingo_board") as? BingoBoardGUI)?.refreshFor(player)

        // Win condition check
        val winByLine = main?.pluginConfig?.winConditionLine ?: true
        val won = if (winByLine) game.board.isLineComplete(state) else game.board.isBoardFull(state)
        if (won) {
            game.end(player)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Attempts to reconstruct a [BingoGame] from the previously persisted
     * [BingoServerData].
     *
     * @return `true` if a game was successfully rehydrated, `false` otherwise
     */
    private fun rehydrate(): Boolean {
        val data = ServerDataManager.get() as? BingoServerData ?: return false
        val boardSize = data.boardSize
        if (boardSize == 0) return false

        val objectiveIds = data.boardLayout
        if (objectiveIds.size != boardSize * boardSize) {
            plugin.logger.warning(
                "[BingoManager] Saved board layout size mismatch " +
                        "(expected ${boardSize * boardSize}, got ${objectiveIds.size}); skipping rehydration"
            )
            return false
        }

        val cells = mutableListOf<BingoCell>()
        for ((i, id) in objectiveIds.withIndex()) {
            val obj = BingoObjectiveRegistry.get(id)
            if (obj == null) {
                plugin.logger.warning(
                    "[BingoManager] Saved objective '$id' not found in registry; skipping rehydration"
                )
                return false
            }
            cells += BingoCell(i, obj)
        }

        val board = BingoBoard(boardSize, cells)
        val gameState = runCatching { GameState.valueOf(data.gameStateName) }
            .getOrDefault(GameState.INACTIVE)
        val game = BingoGame(board, plugin, gameState)

        data.loadPlayerStates().forEach { (uuid, pair) ->
            val ps = game.getOrCreateState(uuid)
            pair.first.forEach { ps.markCompleted(it) }
            pair.second.forEach { (k, v) -> ps.setProgress(k, v) }
        }

        currentGame = game
        plugin.logger.info(
            "[BingoManager] Rehydrated ${boardSize}×${boardSize} game (state=${gameState})"
        )
        return true
    }
}
