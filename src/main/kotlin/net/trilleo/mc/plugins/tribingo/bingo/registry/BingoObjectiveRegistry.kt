package net.trilleo.mc.plugins.tribingo.bingo.registry

import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.plugin.java.JavaPlugin

/**
 * In-memory registry of all [BingoObjective] instances available for board
 * generation.
 *
 * ### Code-level registration
 * ```kotlin
 * BingoObjectiveRegistry.register(MyObjective())
 * ```
 *
 * ### Event-driven objectives
 * Any [EventBingoObjective] passed to [register] is automatically registered
 * as a Bukkit event listener, so no manual `registerEvents` call is needed.
 *
 * ### Lifecycle
 * Call [init] once during plugin startup (before [register] is first invoked)
 * to provide the plugin reference needed for listener registration.
 */
object BingoObjectiveRegistry {

    private val objectives = mutableMapOf<String, BingoObjective>()
    private lateinit var plugin: JavaPlugin

    /**
     * Stores the plugin reference used for registering event listeners.
     *
     * Must be called **before** any call to [register].
     *
     * @param plugin the owning plugin instance
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    /**
     * Registers [objective] in the registry.
     *
     * If [objective] is an [EventBingoObjective] it is also registered as a
     * Bukkit event listener.  Duplicate IDs are silently ignored with a
     * warning so that plugins can safely call this method multiple times.
     *
     * @param objective the objective to register
     */
    fun register(objective: BingoObjective) {
        if (objectives.containsKey(objective.id)) {
            plugin.logger.warning(
                "[BingoObjectiveRegistry] Duplicate objective id '${objective.id}' — skipping"
            )
            return
        }
        objectives[objective.id] = objective
        if (objective is EventBingoObjective<*>) {
            plugin.server.pluginManager.registerEvents(objective, plugin)
        }
        plugin.logger.fine("[BingoObjectiveRegistry] Registered objective: ${objective.id}")
    }

    /**
     * Removes the objective with [id] from the registry.
     *
     * Note: if the objective was an [EventBingoObjective] its Bukkit event
     * listener registration cannot be undone at runtime; the objective will
     * simply be a no-op while the game is inactive.
     *
     * @param id the [BingoObjective.id] to remove
     */
    fun unregister(id: String) {
        objectives.remove(id)
    }

    /**
     * Returns all registered objectives in insertion order.
     */
    fun getAll(): List<BingoObjective> = objectives.values.toList()

    /**
     * Returns all registered objectives that have the given [difficulty].
     *
     * @param difficulty the difficulty level to filter by
     */
    fun getByDifficulty(difficulty: Difficulty): List<BingoObjective> =
        objectives.values.filter { it.difficulty == difficulty }

    /**
     * Returns the objective with the given [id], or `null` if none is registered.
     *
     * @param id the [BingoObjective.id] to look up
     */
    fun get(id: String): BingoObjective? = objectives[id]

    /**
     * Removes all registered objectives from the registry.
     * Intended for use in tests or full plugin reloads.
     */
    fun clear() {
        objectives.clear()
    }
}
