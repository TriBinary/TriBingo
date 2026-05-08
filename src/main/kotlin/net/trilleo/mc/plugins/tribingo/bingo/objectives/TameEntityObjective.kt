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
import org.bukkit.event.entity.EntityTameEvent

/**
 * An objective that is completed when [player] tames [count] animals.
 *
 * The player is identified as the tamer via [EntityTameEvent.getOwner].
 * If [entityTypeName] is non-null, only taming events for that entity type count.
 *
 * @param id             unique objective ID
 * @param name           display name
 * @param description     flavour text
 * @param difficulty      difficulty label
 * @param entityTypeName  entity type name (e.g. "WOLF"), or `null` for any tameable entity
 * @param count           number of taming events required (default: 1)
 */
class TameEntityObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val entityTypeName: String?,
    val count: Int = 1
) : EventBingoObjective<EntityTameEvent>(
    id, name, description, difficulty, EntityTameEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTame(event: EntityTameEvent) {
        if (entityTypeName != null &&
            !event.entity.type.name.equals(entityTypeName, ignoreCase = true)
        ) return
        val player = event.owner as? Player ?: return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, player, game.getOrCreateState(player.uniqueId))
    }

    override fun onEvent(event: EntityTameEvent, player: Player, state: BingoPlayerState) {
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
