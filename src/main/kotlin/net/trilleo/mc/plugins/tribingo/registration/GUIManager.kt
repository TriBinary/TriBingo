package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers all concrete [PluginGUI] subclasses inside the `guis`
 * package (and its subpackages), stores them by their [PluginGUI.id],
 * and opens the target GUI when requested.
 *
 * Also registers itself as a Bukkit [Listener] to route inventory
 * click and close events to the correct [PluginGUI] instance.
 *
 * Each GUI class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single [JavaPlugin] parameter.
 */
object GUIManager : Listener {

    private const val GUIS_PACKAGE = "net.trilleo.mc.plugins.trihunt.guis"

    private val guis = mutableMapOf<String, PluginGUI>()
    private val openGUIs = mutableMapOf<Player, Pair<PluginGUI, Inventory>>()

    /**
     * Scans the GUIs package, instantiates every [PluginGUI] found,
     * stores them by id, and registers this manager as an event listener.
     */
    fun registerAll(plugin: JavaPlugin) {
        val guiClasses = PackageScanner.findClasses(
            plugin, GUIS_PACKAGE, PluginGUI::class.java
        )

        for (guiClass in guiClasses) {
            try {
                val gui = instantiate(guiClass, plugin)
                guis[gui.id] = gui
                plugin.logger.info("Registered GUI: ${gui.id}")
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to register GUI ${guiClass.simpleName}: ${e.message}"
                )
            }
        }

        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.info("Registered ${guiClasses.size} GUI(s)")
    }

    /**
     * Opens a registered GUI for the given player.
     *
     * @param player the player to open the GUI for
     * @param id     the unique identifier of the GUI to open
     * @return `true` if the GUI was found and opened, `false` otherwise
     */
    fun open(player: Player, id: String): Boolean {
        val gui = guis[id] ?: return false
        val inventory = Bukkit.createInventory(null, gui.rows * 9, gui.title)
        fillInventory(gui, inventory)
        gui.setup(player, inventory)
        openGUIs[player] = Pair(gui, inventory)
        player.openInventory(inventory)
        return true
    }

    /**
     * Returns the [PluginGUI] registered under the given [id],
     * or `null` if no GUI with that id exists.
     */
    fun getGUI(id: String): PluginGUI? = guis[id]

    /**
     * Returns an unmodifiable view of all registered GUI ids.
     */
    fun getRegisteredIds(): Set<String> = guis.keys.toSet()

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val (gui, inventory) = openGUIs[player] ?: return
        if (event.inventory !== inventory) return
        gui.onClick(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val (gui, inventory) = openGUIs[player] ?: return
        if (event.inventory !== inventory) return
        openGUIs.remove(player)
        gui.onClose(event)
    }

    /**
     * Tries to create an instance of [clazz] using a constructor that accepts
     * a [JavaPlugin]; falls back to a no-arg constructor.
     */
    private fun instantiate(clazz: Class<out PluginGUI>, plugin: JavaPlugin): PluginGUI {
        return try {
            clazz.getDeclaredConstructor(JavaPlugin::class.java).newInstance(plugin)
        } catch (_: NoSuchMethodException) {
            try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (_: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "${clazz.simpleName} must declare either a no-arg constructor " +
                            "or a constructor accepting a single JavaPlugin parameter"
                )
            }
        }
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
