package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Abstract base for every Bingo objective.
 *
 * An objective represents a single cell on the Bingo board. Concrete subclasses
 * define the completion logic; the default [displayItem] renders the cell in
 * inventory GUIs and can be overridden to show richer information (e.g.
 * progress counters).
 *
 * ### Implementing a simple objective
 * ```kotlin
 * class HaveFullHealthObjective : BingoObjective(
 *     id          = "full_health",
 *     name        = Component.text("Full Health"),
 *     description = Component.text("Reach maximum health."),
 *     difficulty  = Difficulty.EASY
 * ) {
 *     override fun isCompletedBy(player: Player, state: BingoPlayerState) =
 *         player.health >= player.maxHealth
 * }
 * ```
 *
 * For event-driven objectives, extend [EventBingoObjective] instead.
 *
 * @param id          unique string identifier used for persistence and YAML lookups
 * @param name        display name shown in GUIs and announcements
 * @param description flavour text shown when a player clicks the cell in the GUI
 * @param difficulty  difficulty label; used for display and future board randomisation
 */
abstract class BingoObjective(
    val id: String,
    val name: Component,
    val description: Component,
    val difficulty: Difficulty
) {

    /**
     * Returns `true` when this objective is considered complete for [player]
     * given their current [state].
     *
     * @param player the player being checked
     * @param state  the player's per-game state
     */
    abstract fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean

    /**
     * Called when the board is reset so that per-player progress tracked inside
     * [state] can be cleared.
     *
     * The default implementation is a no-op. Override when the objective stores
     * intermediate counters in [BingoPlayerState.progressData].
     *
     * @param player the player whose state is being reset
     * @param state  the player's per-game state (will be fully cleared after this call)
     */
    open fun onReset(player: Player, state: BingoPlayerState) {}

    /**
     * Returns the [ItemStack] displayed in the board GUI for this cell.
     *
     * The default implementation uses a coloured stained-glass block whose
     * colour reflects [difficulty] (uncompleted) or lime concrete (completed),
     * and renders [name], [description], difficulty, and completion status in
     * the item lore.
     *
     * Override to show progress counters or a custom icon.
     *
     * @param player    the player viewing the board
     * @param completed whether this cell has already been completed by [player]
     */
    open fun displayItem(player: Player, completed: Boolean): ItemStack {
        val material = if (completed) Material.LIME_CONCRETE else when (difficulty) {
            Difficulty.EASY   -> Material.GREEN_STAINED_GLASS
            Difficulty.MEDIUM -> Material.YELLOW_STAINED_GLASS
            Difficulty.HARD   -> Material.RED_STAINED_GLASS
            Difficulty.INSANE -> Material.PURPLE_STAINED_GLASS
        }

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

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
            Component.text("○ Not yet completed", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }
}
