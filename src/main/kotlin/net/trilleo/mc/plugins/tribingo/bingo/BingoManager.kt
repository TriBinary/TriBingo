package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.Main
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.init
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.onTimerExpired
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.plugin
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager.remainingSeconds
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
import org.bukkit.scheduler.BukkitTask

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
     * Convenience accessor for the typed plugin configuration.
     *
     * Returns `null` when [plugin] has not been initialised yet or is not an
     * instance of [Main].
     */
    private val pluginConfig get() = (plugin as? Main)?.pluginConfig

    /**
     * The currently active (or most-recently-created) [BingoGame], or `null`
     * if no game has been set up yet.
     */
    var currentGame: BingoGame? = null
        private set

    /** The running countdown task, or `null` when no countdown is active. */
    private var countdownTask: BukkitTask? = null

    /**
     * Remaining seconds on the active countdown.
     *
     * Updated by the countdown task every second. `0` before a game has been
     * started or after the countdown ends.
     */
    private var remainingSeconds: Int = 0

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
            val needed = BingoBoard.SIZE * BingoBoard.SIZE
            if (BingoObjectiveRegistry.getAll().size >= needed) {
                newGame()
            } else {
                plugin.logger.info(
                    "[BingoManager] Not enough objectives for a ${BingoBoard.SIZE}×${BingoBoard.SIZE} board; " +
                            "create a game manually with /bingo refresh"
                )
            }
        }
    }

    /**
     * Serialises the current game to [BingoServerData] so that
     * [ServerDataManager.save] can persist it to disk.
     *
     * If the game is currently [GameState.ACTIVE] (i.e. the server is stopping
     * mid-game), the countdown is cancelled and the game data is cleared so that
     * a fresh game is created on the next server start.
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

        // If the server stops while a game is active, reset the game so the
        // next startup begins with a clean INACTIVE game.
        if (game.state == GameState.ACTIVE) {
            cancelCountdown()
            data.clearGameData()
            plugin.logger.info(
                "[BingoManager] Server stopped during an active game; game data cleared (will reset on restart)"
            )
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

    // ── Timer configuration ───────────────────────────────────────────────

    /**
     * Returns the configured countdown duration in seconds.
     *
     * Falls back to [BingoServerData.DEFAULT_TIMER_SECONDS] (3 600 s) when no
     * value has been stored yet.
     */
    fun getTimerSeconds(): Int {
        val data = ServerDataManager.get() as? BingoServerData
        return data?.timerSeconds ?: BingoServerData.DEFAULT_TIMER_SECONDS
    }

    /**
     * Persists the countdown duration.
     *
     * @param seconds total seconds; must be in `1..86_400`
     * @throws IllegalArgumentException if [seconds] is out of range
     */
    fun setTimerSeconds(seconds: Int) {
        require(seconds in 1..86_400) {
            "Timer must be between 1 and 86 400 seconds (got $seconds)"
        }
        val data = ServerDataManager.get() as? BingoServerData ?: return
        data.timerSeconds = seconds
    }

    // ── Game management ───────────────────────────────────────────────────

    /**
     * Creates a new [BingoGame] with a randomly-selected set of objectives
     * drawn from [BingoObjectiveRegistry], replacing any existing game.
     *
     * @return the newly created game
     * @throws IllegalStateException if the registry doesn't have enough objectives
     */
    fun newGame(): BingoGame {
        val objectives = BingoObjectiveRegistry.getAll()
        val needed = BingoBoard.SIZE * BingoBoard.SIZE
        check(objectives.size >= needed) {
            "Need at least $needed objectives for a ${BingoBoard.SIZE}×${BingoBoard.SIZE} board; " +
                    "only ${objectives.size} are registered"
        }

        val cells = objectives.shuffled()
            .take(needed)
            .mapIndexed { i, obj -> BingoCell(i, obj) }
        val board = BingoBoard(cells)
        val game = BingoGame(board, plugin)
        currentGame = game

        plugin.logger.info("[BingoManager] Created new ${BingoBoard.SIZE}×${BingoBoard.SIZE} game")
        return game
    }

    /**
     * Starts the current game and begins the global countdown.
     *
     * The countdown duration is read from [BingoServerData.timerSeconds] at
     * start time. Every second, the remaining time is sent to all online players
     * as an action bar message. Players who join mid-game see the countdown on
     * the next tick. When the timer reaches zero, [onTimerExpired] is called to
     * determine the winner.
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
        remainingSeconds = getTimerSeconds()
        startCountdown()
    }

    /**
     * Stops the current game without a winner and cancels the countdown.
     *
     * Does nothing when there is no active game.
     */
    fun stopGame() {
        val game = currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        cancelCountdown()
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

    // ── Completion ────────────────────────────────────────────────────────

    /**
     * Called by event objectives when a triggering event has been processed.
     *
     * This method:
     * 1. Verifies the game is active and the corresponding cell has not yet
     *    been completed for [player].
     * 2. Marks the cell as complete in the player's [BingoPlayerState].
     * 3. Awards objective points (A) and any newly-earned line/diagonal bonuses
     *    (B for rows/columns, C for diagonals) to the player's point total.
     * 4. Optionally broadcasts a completion announcement (see
     *    [net.trilleo.mc.plugins.tribingo.config.PluginConfig.announceCompletions]).
     * 5. Refreshes the player's open board GUI if any.
     * 6. Ends the game (and cancels the countdown) when the player has completed
     *    every cell on the board.
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

        // Extract config values once for use throughout this method
        val config = pluginConfig
        val objPts = config?.objectivePoints ?: 1
        val linePts = config?.linePoints ?: 3
        val diagPts = config?.diagonalPoints ?: 5

        // Award objective points
        state.points += objPts

        // Award line-completion bonuses
        val row = cell.cellIndex / BingoBoard.SIZE
        val col = cell.cellIndex % BingoBoard.SIZE

        if ("row_$row" !in state.completedLines && game.board.isRowComplete(state, row)) {
            state.completedLines.add("row_$row")
            state.points += linePts
        }
        if ("col_$col" !in state.completedLines && game.board.isColComplete(state, col)) {
            state.completedLines.add("col_$col")
            state.points += linePts
        }
        if (row == col && "diag_main" !in state.completedLines && game.board.isDiagMainComplete(state)) {
            state.completedLines.add("diag_main")
            state.points += diagPts
        }
        if (row + col == BingoBoard.SIZE - 1 && "diag_anti" !in state.completedLines && game.board.isDiagAntiComplete(
                state
            )
        ) {
            state.completedLines.add("diag_anti")
            state.points += diagPts
        }

        // Announce completion
        if (config?.announceCompletions == true) {
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

        // Win condition: first player to complete the full board wins
        if (game.board.isBoardFull(state)) {
            cancelCountdown()
            game.end(player, state.points)
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────

    /**
     * Starts the repeating countdown task.
     *
     * Ticks every 20 server ticks (= 1 second). On each tick the remaining
     * time is sent to all online players via the action bar. When [remainingSeconds]
     * reaches zero, [onTimerExpired] is invoked.
     */
    private fun startCountdown() {
        cancelCountdown()
        countdownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val game = currentGame
            if (game == null || game.state != GameState.ACTIVE) {
                cancelCountdown()
                return@Runnable
            }

            if (remainingSeconds <= 0) {
                cancelCountdown()
                onTimerExpired()
                return@Runnable
            }

            val timeText = formatSeconds(remainingSeconds)
            val bar = Component.text()
                .append(Component.text("⏱ Bingo: ", NamedTextColor.GOLD))
                .append(Component.text(timeText, NamedTextColor.YELLOW))
                .build()
            plugin.server.onlinePlayers.forEach { it.sendActionBar(bar) }
            remainingSeconds--
        }, 0L, 20L)
    }

    /**
     * Cancels the active countdown task, if any.
     */
    private fun cancelCountdown() {
        countdownTask?.cancel()
        countdownTask = null
    }

    /**
     * Called when [remainingSeconds] reaches zero.
     *
     * Finds the player with the highest point total across all recorded
     * [BingoPlayerState]s and ends the game in their favour. If no player has
     * accumulated any points (or no states exist) the game ends without a winner.
     *
     * **Tie-breaking:** when multiple players share the highest score, the winner
     * is whichever entry is returned first by [Map.values] iteration order (insertion
     * order of the underlying `LinkedHashMap`). This is intentionally unspecified
     * beyond that guarantee.
     */
    private fun onTimerExpired() {
        val game = currentGame ?: return
        if (game.state != GameState.ACTIVE) return

        val topState = game.playerStates.values.maxByOrNull { it.points }
        if (topState == null || topState.points == 0) {
            game.end(null)
            return
        }

        val winner = plugin.server.getPlayer(topState.uuid)
        // OfflinePlayer.name is deprecated but needed here to resolve the display
        // name of a player who was online during the game but disconnected before
        // the timer expired.
        @Suppress("DEPRECATION")
        val winnerName = winner?.name
            ?: plugin.server.getOfflinePlayer(topState.uuid).name
            ?: "Unknown"
        game.end(winner, topState.points, winnerName)
    }

    /**
     * Formats a total number of [totalSeconds] as `HH:MM:SS` (hours omitted when
     * zero) for display in the action bar.
     */
    private fun formatSeconds(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Attempts to reconstruct a [BingoGame] from the previously persisted
     * [BingoServerData].
     *
     * If the persisted game state was [GameState.ACTIVE] the game is reset to
     * [GameState.INACTIVE] (the server was stopped mid-game; any active countdown
     * was lost with the JVM process).
     *
     * @return `true` if a game was successfully rehydrated, `false` otherwise
     */
    private fun rehydrate(): Boolean {
        val data = ServerDataManager.get() as? BingoServerData ?: return false
        val boardSize = data.boardSize
        if (boardSize == 0) return false

        if (boardSize != BingoBoard.SIZE) {
            plugin.logger.warning(
                "[BingoManager] Saved board size $boardSize is no longer supported (only ${BingoBoard.SIZE}×${BingoBoard.SIZE} boards are allowed); skipping rehydration"
            )
            return false
        }

        val objectiveIds = data.boardLayout
        if (objectiveIds.size != BingoBoard.SIZE * BingoBoard.SIZE) {
            plugin.logger.warning(
                "[BingoManager] Saved board layout size mismatch " +
                        "(expected ${BingoBoard.SIZE * BingoBoard.SIZE}, got ${objectiveIds.size}); skipping rehydration"
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

        val board = BingoBoard(cells)
        val savedState = runCatching { GameState.valueOf(data.gameStateName) }
            .getOrDefault(GameState.INACTIVE)

        // A game that was ACTIVE when the server stopped must be reset — the
        // countdown is gone and player states from the interrupted run are stale.
        if (savedState == GameState.ACTIVE) {
            plugin.logger.warning(
                "[BingoManager] Saved game was ACTIVE (server stopped mid-game); resetting to INACTIVE"
            )
            val game = BingoGame(board, plugin, GameState.INACTIVE)
            currentGame = game
            plugin.logger.info(
                "[BingoManager] Rehydrated ${BingoBoard.SIZE}×${BingoBoard.SIZE} game (state=INACTIVE, reset from ACTIVE)"
            )
            return true
        }

        val game = BingoGame(board, plugin, savedState)

        data.loadPlayerStates().forEach { (uuid, saved) ->
            val ps = game.getOrCreateState(uuid)
            saved.completedCells.forEach { ps.markCompleted(it) }
            saved.progressData.forEach { (k, v) -> ps.setProgress(k, v) }
            saved.completedLines.forEach { ps.completedLines.add(it) }
            ps.points = saved.points
            saved.stringData.forEach { (k, v) -> ps.stringData[k] = v }
            saved.stepData.forEach { (objectiveId, steps) ->
                steps.forEach { ps.addStep(objectiveId, it) }
            }
        }

        currentGame = game
        plugin.logger.info(
            "[BingoManager] Rehydrated ${BingoBoard.SIZE}×${BingoBoard.SIZE} game (state=${savedState})"
        )
        return true
    }
}
