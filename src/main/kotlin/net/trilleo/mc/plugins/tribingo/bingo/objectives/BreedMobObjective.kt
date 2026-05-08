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
import org.bukkit.event.entity.EntityBreedEvent

/**
 * An objective that is completed when [player] breeds [count] animals.
 *
 * The player is identified as the breeder via [EntityBreedEvent.getBreeder].
 * If [entityType] is non-null, only breeding events for that specific entity
 * type count.
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param entityTypeName entity type name string (e.g. "COW"), or `null` for any
 * @param count       number of breed events required (default: 1)
 */
class BreedMobObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val entityTypeName: String?,
    val count: Int = 1
) : EventBingoObjective<EntityBreedEvent>(
    id, name, description, difficulty, EntityBreedEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreed(event: EntityBreedEvent) {
        if (entityTypeName != null &&
            !event.entity.type.name.equals(entityTypeName, ignoreCase = true)
        ) return
        val player = event.breeder as? Player ?: return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, player, game.getOrCreateState(player.uniqueId))
    }

    override fun onEvent(event: EntityBreedEvent, player: Player, state: BingoPlayerState) {
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
