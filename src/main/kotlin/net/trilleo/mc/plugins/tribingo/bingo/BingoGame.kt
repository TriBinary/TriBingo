package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * Central state machine for a single Bingo game session.
 *
 * A game transitions through the [GameState] lifecycle:
 * ```
 * INACTIVE ──start()──► ACTIVE ──end()──► ENDED
 *    ▲                                       │
 *    └──────────────── reset() ──────────────┘
 * ```
 *
 * Create a game via [BingoManager.newGame]. Do not instantiate directly.
 *
 * @param board  the initial board layout (may be replaced by [refresh])
 * @param plugin the owning plugin (used for broadcasts and online-player lookups)
 * @param state  the initial [GameState]; defaults to [GameState.INACTIVE]
 */
class BingoGame(
    var board: BingoBoard,
    private val plugin: JavaPlugin,
    var state: GameState = GameState.INACTIVE
) {

    private val _playerStates = mutableMapOf<UUID, BingoPlayerState>()

    /**
     * Read-only snapshot of all player states that have been created this session.
     */
    val playerStates: Map<UUID, BingoPlayerState>
        get() = _playerStates.toMap()

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Starts the game: transitions [state] from [GameState.INACTIVE] to
     * [GameState.ACTIVE] and broadcasts a start message to all online players.
     *
     * @throws IllegalStateException if [state] is not [GameState.INACTIVE]
     */
    fun start() {
        check(state == GameState.INACTIVE) {
            "Cannot start: game is in state $state (expected INACTIVE)"
        }
        state = GameState.ACTIVE

        val msg = Component.text()
            .append(Component.text("⬛ BINGO STARTED! ", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true))
            .append(Component.text(
                "A ${board.size}×${board.size} game has begun! Open the board with ",
                NamedTextColor.YELLOW
            ))
            .append(Component.text("/tb bingo board", NamedTextColor.AQUA))
            .append(Component.text(".", NamedTextColor.YELLOW))
            .build()

        plugin.server.onlinePlayers.forEach { it.sendMessage(msg) }
    }

    /**
     * Ends the game: transitions [state] from [GameState.ACTIVE] to
     * [GameState.ENDED] and broadcasts a result message.
     *
     * @param winner the winning player, or `null` if the game ended without a winner
     * @throws IllegalStateException if [state] is not [GameState.ACTIVE]
     */
    fun end(winner: Player?) {
        check(state == GameState.ACTIVE) {
            "Cannot end: game is in state $state (expected ACTIVE)"
        }
        state = GameState.ENDED

        val msg = if (winner != null) {
            Component.text()
                .append(Component.text("🏆 BINGO! ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                .append(Component.text(winner.name, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" has won the Bingo game!", NamedTextColor.GOLD))
                .build()
        } else {
            Component.text("The Bingo game has ended.", NamedTextColor.GRAY)
        }

        plugin.server.onlinePlayers.forEach { it.sendMessage(msg) }
    }

    /**
     * Resets all player states and transitions [state] back to
     * [GameState.INACTIVE], preserving the current board layout.
     *
     * For any online player, [BingoObjective.onReset] is called on each cell's
     * objective before the state is cleared, allowing objectives to perform
     * custom cleanup.
     *
     * This method may be called from any [GameState].
     */
    fun reset() {
        _playerStates.values.forEach { playerState ->
            val player = plugin.server.getPlayer(playerState.uuid)
            if (player != null) {
                board.cells.forEach { cell -> cell.objective.onReset(player, playerState) }
            }
            playerState.reset()
        }
        _playerStates.clear()
        state = GameState.INACTIVE
    }

    /**
     * Picks a new random selection of objectives from [objectives], rebuilds
     * the board, and resets all player state.
     *
     * The game must be in [GameState.INACTIVE] state. If you need to refresh
     * during an active game, call [reset] first.
     *
     * @param objectives pool of available objectives; must contain at least
     *                   `board.size × board.size` entries
     * @throws IllegalStateException if [state] is not [GameState.INACTIVE]
     * @throws IllegalArgumentException if [objectives] does not have enough entries
     */
    fun refresh(objectives: List<BingoObjective>) {
        check(state == GameState.INACTIVE) {
            "Cannot refresh: game is in state $state (expected INACTIVE). Call reset() first."
        }
        val needed = board.size * board.size
        require(objectives.size >= needed) {
            "Need at least $needed objectives for a ${board.size}×${board.size} board, " +
                    "only ${objectives.size} available"
        }

        reset()
        val cells = objectives.shuffled()
            .take(needed)
            .mapIndexed { i, obj -> BingoCell(i, obj) }
        board = BingoBoard(board.size, cells)
    }

    // ── Player state ─────────────────────────────────────────────────────

    /**
     * Returns the [BingoPlayerState] for [uuid], creating a fresh one if it
     * does not yet exist.
     *
     * @param uuid the player's unique identifier
     */
    fun getOrCreateState(uuid: UUID): BingoPlayerState =
        _playerStates.getOrPut(uuid) { BingoPlayerState(uuid) }
}
