package net.trilleo.mc.plugins.tribingo.commands.bingo

import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginCommand
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main command dispatcher for all Bingo game management commands.
 *
 * Registered as `/bingo` and dispatches to the following sub-commands:
 *
 * | Sub-command            | Permission            | Description                              |
 * |:-----------------------|:----------------------|:-----------------------------------------|
 * | `board`                | (none)                | Opens the board GUI for the sender       |
 * | `start`                | `tribingo.bingo.manage` | Starts the current game               |
 * | `stop`                 | `tribingo.bingo.manage` | Ends the current active game           |
 * | `reset`                | `tribingo.bingo.manage` | Resets all player progress            |
 * | `refresh`              | `tribingo.bingo.manage` | Picks new objectives (INACTIVE only)  |
 * | `size <3-6>`           | `tribingo.bingo.manage` | Sets the board size                   |
 * | `status`               | (none)                | Shows the current game status            |
 */
class BingoCommand : PluginCommand(
    name = "bingo",
    description = "Manage and view the Bingo game",
    usage = "/bingo <board|start|stop|reset|refresh|size <3-6>|status>",
    isMainCommand = true
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "board" -> handleBoard(sender)
            "start" -> handleStart(sender)
            "stop" -> handleStop(sender)
            "reset" -> handleReset(sender)
            "refresh" -> handleRefresh(sender)
            "size" -> handleSize(sender, args.drop(1))
            "status" -> handleStatus(sender)
            else -> {
                showUsage(sender); true
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subs = listOf("board", "start", "stop", "reset", "refresh", "size", "status")
            return subs.filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() == "size") {
            return listOf("3", "4", "5", "6").filter { it.startsWith(args[1]) }
        }
        return emptyList()
    }

    // ── Sub-command handlers ─────────────────────────────────────────────

    private fun handleBoard(sender: CommandSender): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can open the board.")
            return true
        }
        val opened = GUIManager.open(player, "bingo_board")
        if (!opened) {
            player.sendPrefixed("<red>The board GUI could not be opened. Is it registered?")
        }
        return true
    }

    private fun handleStart(sender: CommandSender): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            denyPermission(sender); return true
        }
        val game = BingoManager.currentGame
        if (game == null) {
            sendMsg(sender, "<red>No game exists. Create one first with /bingo size <3-6>.")
            return true
        }
        if (game.state != GameState.INACTIVE) {
            sendMsg(sender, "<red>The game cannot be started from state ${game.state}. Use /bingo reset first.")
            return true
        }
        BingoManager.startGame()
        sendMsg(sender, "<green>Bingo game started!")
        return true
    }

    private fun handleStop(sender: CommandSender): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            denyPermission(sender); return true
        }
        if (BingoManager.currentGame?.state != GameState.ACTIVE) {
            sendMsg(sender, "<red>There is no active game to stop.")
            return true
        }
        BingoManager.stopGame()
        sendMsg(sender, "<yellow>Bingo game stopped.")
        return true
    }

    private fun handleReset(sender: CommandSender): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            denyPermission(sender); return true
        }
        if (BingoManager.currentGame == null) {
            sendMsg(sender, "<red>No game exists to reset.")
            return true
        }
        BingoManager.resetGame()
        sendMsg(sender, "<yellow>Bingo game reset. All player progress has been cleared.")
        return true
    }

    private fun handleRefresh(sender: CommandSender): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            denyPermission(sender); return true
        }
        val game = BingoManager.currentGame
        if (game == null) {
            sendMsg(sender, "<red>No game exists. Create one first with /bingo size <3-6>.")
            return true
        }
        if (game.state != GameState.INACTIVE) {
            sendMsg(
                sender,
                "<red>The board can only be refreshed when the game is INACTIVE. Use /bingo reset first."
            )
            return true
        }
        runCatching { BingoManager.refreshBoard() }
            .onSuccess { sendMsg(sender, "<green>Board objectives have been refreshed.") }
            .onFailure { e -> sendMsg(sender, "<red>Refresh failed: ${e.message}") }
        return true
    }

    private fun handleSize(sender: CommandSender, args: List<String>): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            denyPermission(sender); return true
        }
        val sizeStr = args.firstOrNull()
        if (sizeStr == null) {
            sendMsg(sender, "<red>Usage: /bingo size <3-6>")
            return true
        }
        val size = sizeStr.toIntOrNull()
        if (size == null || size !in 3..6) {
            sendMsg(sender, "<red>Board size must be a number from 3 to 6.")
            return true
        }
        runCatching { BingoManager.setBoardSize(size) }
            .onSuccess { sendMsg(sender, "<green>Board size set to ${size}×${size}. Use /bingo start to begin.") }
            .onFailure { e -> sendMsg(sender, "<red>Could not set board size: ${e.message}") }
        return true
    }

    private fun handleStatus(sender: CommandSender): Boolean {
        val game = BingoManager.currentGame
        if (game == null) {
            sendMsg(sender, "<gray>No Bingo game has been set up.")
            return true
        }
        sendMsg(sender, "<gold>── Bingo Status ──")
        sendMsg(sender, "<gray>Board size: <white>${game.board.size}×${game.board.size}")
        sendMsg(sender, "<gray>State: <white>${game.state}")
        sendMsg(sender, "<gray>Objectives loaded: <white>${game.board.cells.size}")
        sendMsg(sender, "<gray>Active players: <white>${game.playerStates.size}")
        return true
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun showUsage(sender: CommandSender) {
        sendMsg(sender, "<gray>Usage: $usage")
    }

    private fun denyPermission(sender: CommandSender) {
        sendMsg(sender, "<red>You don't have permission to use this sub-command.")
    }

    private fun sendMsg(sender: CommandSender, message: String) {
        if (sender is Player) sender.sendPrefixed(message)
        else sender.sendMessage(message)
    }
}
