package net.trilleo.mc.plugins.tribingo.commands.bingo

import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.commands.bingo.BingoActions.resetGame
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import org.bukkit.entity.Player
/**
 * Encapsulates all Bingo game management actions so they can be invoked from
 * both [BingoCommand] and future GUI implementations without duplicating logic.
 *
 * Each method performs validation, delegates to [BingoManager], and returns an
 * [ActionResult] describing the outcome.  Callers are responsible for presenting
 * the result message to the user.
 *
 * ### Example usage from a GUI
 * ```kotlin
 * val result = BingoActions.startGame()
 * if (!result.success) player.sendMessage(result.message)
 * ```
 */
object BingoActions {

    /**
     * Represents the outcome of a [BingoActions] call.
     *
     * @param success `true` when the action completed without error
     * @param message a human-readable description of the outcome
     *                (MiniMessage-formatted for player display)
     */
    data class ActionResult(val success: Boolean, val message: String)

    // ── Board ─────────────────────────────────────────────────────────────

    /**
     * Opens the Bingo board GUI for [player].
     *
     * @param player the player to open the board for
     * @return [ActionResult] indicating success or an error description
     */
    fun openBoard(player: Player): ActionResult {
        val opened = GUIManager.open(player, "bingo_board")
        return if (opened) {
            ActionResult(true, "<green>Board opened.")
        } else {
            ActionResult(false, "<red>The board GUI could not be opened. Is it registered?")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Starts the current Bingo game.
     *
     * Fails if no game has been created or the game is not in
     * [GameState.INACTIVE] state.
     *
     * @return [ActionResult] indicating success or the reason for failure
     */
    fun startGame(): ActionResult {
        val game = BingoManager.currentGame
            ?: return ActionResult(false, "<red>No game exists. Create one first with /bingo refresh.")
        if (game.state != GameState.INACTIVE) {
            return ActionResult(
                false,
                "<red>The game cannot be started from state ${game.state}. Use /bingo reset first."
            )
        }
        BingoManager.startGame()
        return ActionResult(true, "<green>Bingo game started!")
    }

    /**
     * Stops the currently active Bingo game without a winner.
     *
     * Fails if there is no active game.
     *
     * @return [ActionResult] indicating success or the reason for failure
     */
    fun stopGame(): ActionResult {
        if (BingoManager.currentGame?.state != GameState.ACTIVE) {
            return ActionResult(false, "<red>There is no active game to stop.")
        }
        BingoManager.stopGame()
        return ActionResult(true, "<yellow>Bingo game stopped.")
    }

    /**
     * Resets all player progress and returns the game to [GameState.INACTIVE].
     *
     * Fails if no game exists.
     *
     * @return [ActionResult] indicating success or the reason for failure
     */
    fun resetGame(): ActionResult {
        if (BingoManager.currentGame == null) {
            return ActionResult(false, "<red>No game exists to reset.")
        }
        BingoManager.resetGame()
        return ActionResult(true, "<yellow>Bingo game reset. All player progress has been cleared.")
    }

    /**
     * Picks a new random set of objectives and rebuilds the board.
     *
     * The game must be in [GameState.INACTIVE]; call [resetGame] first if needed.
     *
     * @return [ActionResult] indicating success or the reason for failure
     */
    fun refreshBoard(): ActionResult {
        val game = BingoManager.currentGame
        if (game == null) {
            val result = runCatching { BingoManager.newGame() }
            return if (result.isSuccess) {
                ActionResult(true, "<green>New 5×5 board created.")
            } else {
                ActionResult(
                    false,
                    "<red>Could not create game: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                )
            }
        }
        if (game.state != GameState.INACTIVE) {
            return ActionResult(
                false,
                "<red>The board can only be refreshed when the game is INACTIVE. Use /bingo reset first."
            )
        }
        val result = runCatching { BingoManager.refreshBoard() }
        return if (result.isSuccess) {
            ActionResult(true, "<green>Board objectives have been refreshed.")
        } else {
            ActionResult(false, "<red>Refresh failed: ${result.exceptionOrNull()?.message ?: "unknown error"}")
        }
    }

    // ── Status ────────────────────────────────────────────────────────────

    /**
     * Returns a multi-line status summary of the current game.
     *
     * @return [ActionResult] where [ActionResult.message] contains the formatted
     *         status lines separated by `\n`
     */
    fun getStatus(): ActionResult {
        val game = BingoManager.currentGame
            ?: return ActionResult(true, "<gray>No Bingo game has been set up.")
        val lines = listOf(
            "<gold>── Bingo Status ──",
            "<gray>Board size: <white>${game.board.size}×${game.board.size}",
            "<gray>State: <white>${game.state}",
            "<gray>Objectives loaded: <white>${game.board.cells.size}",
            "<gray>Active players: <white>${game.playerStates.size}",
            "<gray>Timer: <white>${formatSeconds(BingoManager.getTimerSeconds())}"
        )
        return ActionResult(true, lines.joinToString("\n"))
    }

    // ── Timer ─────────────────────────────────────────────────────────────

    /**
     * Sets the countdown duration that will be used when the next game starts.
     *
     * The game must not currently be [GameState.ACTIVE]; the timer cannot be
     * changed mid-game.
     *
     * @param hours   hours component (0–23)
     * @param minutes minutes component (0–59)
     * @param seconds seconds component (0–59)
     * @return [ActionResult] indicating success or the reason for failure
     */
    fun setTimer(hours: Int, minutes: Int, seconds: Int): ActionResult {
        if (hours < 0 || minutes < 0 || seconds < 0) {
            return ActionResult(false, "<red>Time values must not be negative.")
        }
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        if (totalSeconds <= 0) {
            return ActionResult(false, "<red>Timer must be greater than 0 seconds.")
        }
        if (totalSeconds > 86_400) {
            return ActionResult(false, "<red>Timer cannot exceed 24 hours (86 400 seconds).")
        }
        if (BingoManager.isGameActive()) {
            return ActionResult(false, "<red>Cannot change the timer while a game is active.")
        }
        val result = runCatching { BingoManager.setTimerSeconds(totalSeconds) }
        return if (result.isSuccess) {
            ActionResult(true, "<green>Timer set to ${formatSeconds(hours, minutes, seconds)}.")
        } else {
            ActionResult(false, "<red>Failed to set timer: ${result.exceptionOrNull()?.message ?: "unknown error"}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Formats a duration given as [h]/[m]/[s] components as `HH:MM:SS`
     * (hours component omitted when zero).
     */
    private fun formatSeconds(h: Int, m: Int, s: Int): String =
        if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)

    /**
     * Formats a total [totalSeconds] value as `HH:MM:SS`
     * (hours component omitted when zero).
     */
    private fun formatSeconds(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return formatSeconds(h, m, s)
    }
}
