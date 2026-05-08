package net.trilleo.mc.plugins.tribingo.bingo.objectives

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack

/**
 * An objective that is completed when [player] places [count] blocks of
 * [material].
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param material    the block material to place
 * @param count       number of blocks that must be placed (default: 1)
 */
class PlaceBlockObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val material: Material,
    val count: Int = 1
) : EventBingoObjective<BlockPlaceEvent>(
    id, name, description, difficulty, BlockPlaceEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type != material) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, event.player, game.getOrCreateState(event.player.uniqueId))
    }

    override fun onEvent(event: BlockPlaceEvent, player: Player, state: BingoPlayerState) {
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

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val mat = if (completed) Material.LIME_CONCRETE else when (difficulty) {
            Difficulty.EASY   -> Material.GREEN_STAINED_GLASS
            Difficulty.MEDIUM -> Material.YELLOW_STAINED_GLASS
            Difficulty.HARD   -> Material.RED_STAINED_GLASS
            Difficulty.INSANE -> Material.PURPLE_STAINED_GLASS
        }
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getProgress(id) ?: 0

        val lore = mutableListOf<Component>()
        lore += Component.text("Difficulty: ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(difficulty.displayName().decoration(TextDecoration.ITALIC, false))
        lore += Component.empty()
        lore += description.decoration(TextDecoration.ITALIC, false)
        lore += Component.empty()
        lore += if (completed) {
            Component.text("✓ Completed", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        } else {
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        }
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }
}
