package net.trilleo.mc.plugins.tribingo.bingo.objectives

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.enchantment.EnchantItemEvent

/**
 * An objective that is completed when [player] enchants [count] items.
 *
 * If [enchantment] is non-null, only enchanting operations that include that
 * specific enchantment count. Pass `null` to count any enchanting operation.
 *
 * @param id          unique objective ID
 * @param name        display name
 * @param description  flavour text
 * @param difficulty   difficulty label
 * @param enchantment  the enchantment to require, or `null` to match any
 * @param count        number of enchanting operations required (default: 1)
 */
class EnchantItemObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val enchantment: Enchantment?,
    val count: Int = 1
) : EventBingoObjective<EnchantItemEvent>(
    id, name, description, difficulty, EnchantItemEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        if (enchantment != null && enchantment !in event.enchantsToAdd) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, event.enchanter, game.getOrCreateState(event.enchanter.uniqueId))
    }

    override fun onEvent(event: EnchantItemEvent, player: Player, state: BingoPlayerState) {
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
