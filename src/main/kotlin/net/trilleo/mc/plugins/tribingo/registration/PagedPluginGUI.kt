package net.trilleo.mc.plugins.tribingo.registration

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.enums.PagedGUIMode
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * A [PluginGUI] subclass that automatically splits content across
 * multiple pages with **Previous** and **Next** navigation buttons.
 *
 * Extend this class and place the subclass anywhere inside the
 * `net.trilleo.mc.plugins.trihunt.guis` package (or any subpackage) to
 * have it automatically discovered and registered at startup.
 *
 * The bottom row of the inventory is reserved for navigation controls.
 * Content slots are every slot **except** the last row. For example, a
 * 6-row GUI provides 45 content slots per page (rows 1–5).
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter (the plugin
 *   instance will be injected automatically).
 *
 * Two item-supply modes are available via [mode]:
 * - [PagedGUIMode.LIST] *(default)* – override [getItems] to provide a flat
 *   list of items that are distributed automatically across pages.
 * - [PagedGUIMode.SET] – override [getSetItems] to provide a map of
 *   `page → (slot → item)`, giving full control over each item's position.
 *
 * Example (LIST mode):
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.guis
 *
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * class RewardsGUI : PagedPluginGUI(
 *     id = "rewards",
 *     title = Component.text("Rewards"),
 *     rows = 6
 * ) {
 *     override fun getItems(player: Player): List<ItemStack> {
 *         return List(100) { index ->
 *             val item = ItemStack(Material.DIAMOND)
 *             val meta = item.itemMeta
 *             meta.displayName(Component.text("Reward #${index + 1}"))
 *             item.itemMeta = meta
 *             item
 *         }
 *     }
 * }
 * ```
 *
 * Example (SET mode):
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.guis
 *
 * import net.trilleo.mc.plugins.trihunt.enums.PagedGUIMode
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * class CustomGUI : PagedPluginGUI(
 *     id = "custom",
 *     title = Component.text("Custom"),
 *     rows = 4,
 *     mode = PagedGUIMode.SET
 * ) {
 *     override fun getSetItems(player: Player): Map<Int, Map<Int, ItemStack>> {
 *         return mapOf(
 *             0 to mapOf(4 to ItemStack(Material.DIAMOND)),
 *             1 to mapOf(4 to ItemStack(Material.EMERALD))
 *         )
 *     }
 * }
 * ```
 */
abstract class PagedPluginGUI(
    id: String,
    title: Component,
    rows: Int = 6,
    fillMode: FillMode = FillMode.NONE,
    val mode: PagedGUIMode = PagedGUIMode.LIST
) : PluginGUI(id, title, rows, fillMode) {

    /** Tracks the current page for each player viewing this GUI. */
    private val playerPages = mutableMapOf<UUID, Int>()

    /**
     * Returns all items that should be distributed across pages for the
     * given player. The list may be of any size; items are automatically
     * split into pages of [contentSlots] each.
     *
     * Used when [mode] is [PagedGUIMode.LIST]. Override this method to supply
     * the items to paginate.
     *
     * @param player the player the GUI is being opened for
     * @return the full list of items to paginate
     */
    open fun getItems(player: Player): List<ItemStack> = emptyList()

    /**
     * Returns a map of items to place at specific pages and slots.
     *
     * The outer map key is the **zero-based page index**; the inner map key is
     * the **zero-based slot index** within the content area of that page (slots
     * 0 to [contentSlots]`- 1`).  Pages that are missing from the map are
     * rendered empty.
     *
     * Used when [mode] is [PagedGUIMode.SET]. Override this method to supply
     * manually positioned items.
     *
     * @param player the player the GUI is being opened for
     * @return a map of `page → (slot → item)` describing the full contents
     */
    open fun getSetItems(player: Player): Map<Int, Map<Int, ItemStack>> = emptyMap()

    /**
     * Called when a player clicks a **content slot** (not a navigation
     * button). Override to add custom click handling.
     *
     * Clicks are cancelled by default to prevent item theft.
     *
     * @param event the inventory click event
     * @param page  the page the player is currently viewing (zero-based)
     */
    open fun onContentClick(event: InventoryClickEvent, page: Int) {}

    /** The number of usable content slots per page (all rows except the last). */
    private val contentSlots: Int
        get() = (rows - 1) * ROW_SIZE

    /** The first slot index of the navigation row (the last row). */
    private val navRowStart: Int
        get() = contentSlots

    // ----- PluginGUI overrides ------------------------------------------------

    override fun setup(player: Player, inventory: Inventory) {
        playerPages[player.uniqueId] = 0
        renderPage(player, inventory, 0)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val page = playerPages[player.uniqueId] ?: return
        val slot = event.rawSlot

        // Ignore clicks outside the GUI inventory
        if (slot < 0 || slot >= rows * ROW_SIZE) return

        when (slot) {
            navRowStart + PREVIOUS_OFFSET -> {
                if (page > 0) {
                    openPage(player, event.inventory, page - 1)
                    player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))
                }
            }

            navRowStart + NEXT_OFFSET -> {
                val totalPages = totalPages(player)
                if (page < totalPages - 1) {
                    openPage(player, event.inventory, page + 1)
                    player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))
                }
            }

            else -> {
                if (slot < contentSlots) onContentClick(event, page)
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        playerPages.remove(player.uniqueId)
    }

    // ----- Internal helpers ---------------------------------------------------

    /**
     * Calculates the total number of pages for the given [player] based on the
     * active [mode].
     */
    private fun totalPages(player: Player): Int = when (mode) {
        PagedGUIMode.LIST -> {
            val itemCount = getItems(player).size
            if (itemCount == 0) 1 else (itemCount + contentSlots - 1) / contentSlots
        }

        PagedGUIMode.SET -> {
            val maxPage = getSetItems(player).keys.maxOrNull() ?: -1
            maxOf(maxPage + 1, 1)
        }
    }

    /** Switches the player to the given [page] and re-renders the inventory. */
    private fun openPage(player: Player, inventory: Inventory, page: Int) {
        playerPages[player.uniqueId] = page
        renderPage(player, inventory, page)
    }

    /** Clears the inventory and populates it with the items for [page]. */
    private fun renderPage(player: Player, inventory: Inventory, page: Int) {
        inventory.clear()

        fillInventory(this, inventory)

        val totalPages = totalPages(player)

        when (mode) {
            PagedGUIMode.LIST -> {
                val items = getItems(player)
                val start = page * contentSlots
                val end = minOf(start + contentSlots, items.size)
                for (i in start until end) {
                    inventory.setItem(i - start, items[i])
                }
            }

            PagedGUIMode.SET -> {
                val pageItems = getSetItems(player)[page] ?: emptyMap()
                for ((slot, item) in pageItems) {
                    if (slot in 0 until contentSlots) {
                        inventory.setItem(slot, item)
                    }
                }
            }
        }

        // Navigation row – fill all slots with gray stained glass panes first
        for (offset in 0 until ROW_SIZE) {
            inventory.setItem(navRowStart + offset, itemStack(Material.GRAY_STAINED_GLASS_PANE) {
                name(" ")
                hideTooltip(true)
            })
        }

        // Place navigation items on top of the filler
        if (page > 0) {
            inventory.setItem(
                navRowStart + PREVIOUS_OFFSET, createNavItem(
                    Material.ARROW,
                    "<yellow>Previous Page"
                )
            )
        }

        inventory.setItem(
            navRowStart + PAGE_INDICATOR_OFFSET, createNavItem(
                Material.PAPER,
                "<white>Page ${page + 1}/$totalPages"
            )
        )

        if (page < totalPages - 1) {
            inventory.setItem(
                navRowStart + NEXT_OFFSET, createNavItem(
                    Material.ARROW,
                    "<yellow>Next Page"
                )
            )
        }
    }

    /** Creates a navigation item with the given material and display name. */
    private fun createNavItem(material: Material, name: String): ItemStack {
        val item = itemStack(material) {
            this.name(name)
        }
        return item
    }

    companion object {
        private const val ROW_SIZE = 9
        private const val PREVIOUS_OFFSET = 0
        private const val PAGE_INDICATOR_OFFSET = 4
        private const val NEXT_OFFSET = 8
    }

    /**
     * Pre-fills all slots in [inventory] with a filler glass pane determined
     * by the GUI's [FillMode].  Does nothing when the mode is [FillMode.NONE].
     */
    private fun fillInventory(gui: PluginGUI, inventory: Inventory) {
        val material = when (gui.fillMode) {
            FillMode.LIGHT -> Material.WHITE_STAINED_GLASS_PANE
            FillMode.DARK -> Material.BLACK_STAINED_GLASS_PANE
            FillMode.NONE -> return
        }
        val filler = itemStack(material) {
            name(" ")
            hideTooltip(true)
        }
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, filler.clone())
        }
    }
}
