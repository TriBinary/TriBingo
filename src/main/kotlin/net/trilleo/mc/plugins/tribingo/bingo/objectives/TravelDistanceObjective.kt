package net.trilleo.mc.plugins.tribingo.bingo.objectives

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent

/**
 * An objective that is completed when [player] travels [blocks] blocks.
 *
 * Each call to [PlayerMoveEvent] that crosses a block boundary is counted as
 * one block of travel, regardless of direction or speed.
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param blocks      number of block-crossings required
 */
class TravelDistanceObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val blocks: Int
) : EventBingoObjective<PlayerMoveEvent>(
    id, name, description, difficulty, PlayerMoveEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!event.hasMovedBlock()) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        val state = game.getOrCreateState(event.player.uniqueId)
        // Skip further processing once the cell is already completed
        val cell = game.board.cells.find { it.objective.id == id }
        if (cell != null && state.isCompleted(cell.cellIndex)) return
        onEvent(event, event.player, state)
    }

    override fun onEvent(event: PlayerMoveEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= blocks) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= blocks

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}
