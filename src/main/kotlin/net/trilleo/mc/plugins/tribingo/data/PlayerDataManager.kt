package net.trilleo.mc.plugins.tribingo.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.trilleo.mc.plugins.tribingo.data.PlayerDataManager.get
import net.trilleo.mc.plugins.tribingo.data.PlayerDataManager.init
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Manages per-player [PlayerData] instances, automatically loading data when
 * a player joins and saving it when they quit.
 *
 * JSON files are stored under `<dataFolder>/playerdata/<uuid>.json`.
 *
 * ### Basic usage
 *
 * Initialise the manager once in [JavaPlugin.onEnable] (before any other code
 * that might call [get]):
 *
 * ```kotlin
 * PlayerDataManager.init(this)
 * ```
 *
 * Retrieve data anywhere with a [Player] reference:
 *
 * ```kotlin
 * val data = PlayerDataManager.get(player)
 * val kills = data.getInt("kills")
 * data.set("kills", kills + 1)
 * ```
 *
 * ### Custom subclass
 *
 * Register a factory before calling [init] to use a [PlayerData] subclass:
 *
 * ```kotlin
 * PlayerDataManager.setFactory { uuid -> MyPlayerData(uuid) }
 * PlayerDataManager.init(this)
 * ```
 *
 * Then cast the result of [get]:
 *
 * ```kotlin
 * val data = PlayerDataManager.get(player) as MyPlayerData
 * ```
 */
object PlayerDataManager {

    private val dataMap = ConcurrentHashMap<UUID, PlayerData>()
    private lateinit var dataDirectory: File
    private lateinit var logger: Logger
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var factory: (UUID) -> PlayerData = { uuid -> PlayerData(uuid) }

    /**
     * Overrides the factory used to instantiate [PlayerData] objects.
     * Must be called **before** [init] if a custom subclass is needed.
     *
     * @param factory a function that produces a [PlayerData] instance for the given UUID
     */
    fun <T : PlayerData> setFactory(factory: (UUID) -> T) {
        this.factory = factory
    }

    /**
     * Initialises the manager: creates the storage directory and registers
     * the join/quit listener with [plugin].
     *
     * Call this once during [JavaPlugin.onEnable].
     */
    fun init(plugin: JavaPlugin) {
        logger = plugin.logger
        dataDirectory = File(plugin.dataFolder, "playerdata")
        dataDirectory.mkdirs()
        plugin.server.pluginManager.registerEvents(PlayerDataListener(), plugin)
        plugin.logger.info("PlayerDataManager initialised")
    }

    /**
     * Returns the [PlayerData] for [player].
     *
     * Data is normally loaded on join; this method falls back to creating and
     * loading a fresh instance if it is called before the join event fires.
     */
    fun get(player: Player): PlayerData =
        dataMap.getOrPut(player.uniqueId) { load(player.uniqueId) }

    /**
     * Saves all currently loaded player data to disk.
     * Call this from [JavaPlugin.onDisable] to persist data for online players.
     */
    fun saveAll() {
        dataMap.values.forEach { save(it) }
    }

    // ── Internal ────────────────────────────────────────────────────────

    internal fun onJoin(player: Player) {
        dataMap[player.uniqueId] = load(player.uniqueId)
    }

    internal fun onQuit(player: Player) {
        dataMap.remove(player.uniqueId)?.let { save(it) }
    }

    private fun load(uuid: UUID): PlayerData {
        val data = factory(uuid)
        val file = File(dataDirectory, "$uuid.json")
        if (file.exists()) {
            runCatching {
                val root = JsonParser.parseString(file.readText()).asJsonObject
                root.entrySet().forEach { (key, value) -> data.json.add(key, value) }
            }.onFailure { e ->
                logger.warning("Failed to load player data for $uuid: [${e.javaClass.simpleName}] ${e.message}")
            }
        }
        return data
    }

    private fun save(data: PlayerData) {
        val file = File(dataDirectory, "${data.uuid}.json")
        runCatching { file.writeText(gson.toJson(data.json)) }.onFailure { e ->
            logger.severe("Failed to save player data for ${data.uuid}: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    // ── Listener ────────────────────────────────────────────────────────

    private class PlayerDataListener : Listener {

        @EventHandler
        fun onPlayerJoin(event: PlayerJoinEvent) {
            onJoin(event.player)
        }

        @EventHandler
        fun onPlayerQuit(event: PlayerQuitEvent) {
            onQuit(event.player)
        }
    }
}
