package net.trilleo.mc.plugins.tribingo.guis

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

/**
 * Chest-based Bingo board viewer.
 *
 * Displays the current [net.trilleo.mc.plugins.tribingo.bingo.BingoGame]'s board
 * as a centred grid within a 6-row (54-slot) double-chest inventory.
 *
 * ### Board centering formula
 * For a board of side-length N:
 * - `vertPad  = (6 - N) / 2`  (rows above the board)
 * - `horizPad = (9 - N) / 2`  (columns to the left of the board)
 * - Slot for cell `(row, col)` = `(vertPad + row) * 9 + (horizPad + col)`
 *
 * All unoccupied slots are pre-filled with dark glass panes (via [FillMode.DARK]).
 *
 * ### Cell interaction
 * Clicking a board cell (not just hovering) sends the objective's full
 * description and current completion status to the player in chat.
 *
 * ### Live updates
 * Call [refreshFor] with the player whenever a cell is completed; the open
 * inventory is updated in-place without closing it.
 */
class BingoBoardGUI : PluginGUI(
    id = "bingo_board",
    title = Component.text("✦ Bingo Board ✦").color(NamedTextColor.GOLD)
        .decoration(TextDecoration.BOLD, true),
    rows = 6,
    fillMode = FillMode.DARK
) {

    /** Inventories currently open, keyed by player. */
    private val openInventories = mutableMapOf<Player, Inventory>()

    // ── PluginGUI overrides ───────────────────────────────────────────────

    override fun setup(player: Player, inventory: Inventory) {
        openInventories[player] = inventory
        populateBoard(player, inventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        openInventories.remove(event.player as? Player)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val game = BingoManager.currentGame ?: return

        val slot = event.rawSlot
        if (slot !in 0 until 54) return

        val n = game.board.size
        val vertPad = (6 - n) / 2
        val horizPad = (9 - n) / 2

        val row = slot / 9 - vertPad
        val col = slot % 9 - horizPad
        if (row !in 0 until n || col !in 0 until n) return

        val cell = game.board.getCell(row, col)
        val state = game.getOrCreateState(player.uniqueId)
        val completed = state.isCompleted(cell.cellIndex)

        val header = Component.text("── ", NamedTextColor.DARK_GRAY)
            .append(cell.objective.name)
            .append(Component.text(" ──", NamedTextColor.DARK_GRAY))
        val statusLine = if (completed) {
            Component.text("  ✓ Completed!", NamedTextColor.GREEN)
        } else {
            Component.text("  ○ Not yet completed", NamedTextColor.RED)
        }

        player.sendMessage(header)
        player.sendMessage(cell.objective.description.color(NamedTextColor.GRAY))
        player.sendMessage(statusLine)
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Re-renders the board in the inventory the player currently has open.
     *
     * Does nothing if the player does not have this GUI open.
     *
     * @param player the player whose open board should be refreshed
     */
    fun refreshFor(player: Player) {
        val inventory = openInventories[player] ?: return
        populateBoard(player, inventory)
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Populates [inventory] with board cells for [player], or a "no game"
     * placeholder if there is no active game.
     */
    private fun populateBoard(player: Player, inventory: Inventory) {
        val game = BingoManager.currentGame
        if (game == null) {
            inventory.setItem(
                22,
                itemStack(Material.BARRIER) {
                    name("<red>No Bingo Game")
                    lore(
                        "<gray>No game has been set up yet.",
                        "<gray>Ask an admin to run <white>/bingo size <3-6><gray>."
                    )
                }
            )
            return
        }

        val board = game.board
        val n = board.size
        val state = game.getOrCreateState(player.uniqueId)
        val vertPad = (6 - n) / 2
        val horizPad = (9 - n) / 2

        for (row in 0 until n) {
            for (col in 0 until n) {
                val cell = board.getCell(row, col)
                val completed = state.isCompleted(cell.cellIndex)
                val slot = (vertPad + row) * 9 + (horizPad + col)
                inventory.setItem(slot, cell.objective.displayItem(player, completed))
            }
        }
    }
}
