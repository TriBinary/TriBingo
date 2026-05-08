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
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.ItemStack

/**
 * An objective that is completed when [player] crafts [count] items of
 * [material].
 *
 * Each craft operation (regardless of output stack size) is counted as one
 * progress tick. If [material] is `null`, any crafted item counts.
 *
 * @param id         unique objective ID
 * @param name       display name
 * @param description flavour text
 * @param difficulty  difficulty label
 * @param material    the crafted item material to track, or `null` to match any
 * @param count       number of craft operations required (default: 1)
 */
class CraftItemObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val material: Material?,
    val count: Int = 1
) : EventBingoObjective<CraftItemEvent>(
    id, name, description, difficulty, CraftItemEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (material != null && event.recipe.result.type != material) return
        val player = event.whoClicked as? Player ?: return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, player, game.getOrCreateState(player.uniqueId))
    }

    override fun onEvent(event: CraftItemEvent, player: Player, state: BingoPlayerState) {
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
