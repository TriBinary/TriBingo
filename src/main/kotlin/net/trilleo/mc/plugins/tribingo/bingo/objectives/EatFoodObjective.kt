package net.trilleo.mc.plugins.tribingo.bingo.objectives

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerItemConsumeEvent

/**
 * An objective that is completed when [player] consumes [count] food items.
 *
 * If [material] is non-null, only that specific food item counts. Pass `null`
 * to count any food consumption.
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param material    the food material to track, or `null` to match any
 * @param count       number of consumption events required (default: 1)
 */
class EatFoodObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val material: Material?,
    val count: Int = 1
) : EventBingoObjective<PlayerItemConsumeEvent>(
    id, name, description, difficulty, PlayerItemConsumeEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (material != null && event.item.type != material) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, event.player, game.getOrCreateState(event.player.uniqueId))
    }

    override fun onEvent(event: PlayerItemConsumeEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}
