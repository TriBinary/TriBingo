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
import org.bukkit.event.player.PlayerFishEvent

/**
 * An objective that is completed when [player] catches [count] fish (or any
 * item on a fishing rod if [countAll] is true).
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param count       number of catches required (default: 1)
 * @param countAll    if `true`, counts any fishing rod catch (fish, treasure,
 *                    junk); if `false`, only counts [PlayerFishEvent.State.CAUGHT_FISH]
 */
class FishItemObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val count: Int = 1,
    val countAll: Boolean = false
) : EventBingoObjective<PlayerFishEvent>(
    id, name, description, difficulty, PlayerFishEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        val validState = if (countAll) {
            event.state == PlayerFishEvent.State.CAUGHT_FISH
                    || event.state == PlayerFishEvent.State.CAUGHT_ENTITY
        } else {
            event.state == PlayerFishEvent.State.CAUGHT_FISH
        }
        if (!validState) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, event.player, game.getOrCreateState(event.player.uniqueId))
    }

    override fun onEvent(event: PlayerFishEvent, player: Player, state: BingoPlayerState) {
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
