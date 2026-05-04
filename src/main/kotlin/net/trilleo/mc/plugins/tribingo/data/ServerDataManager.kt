package net.trilleo.mc.plugins.tribingo.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.trilleo.mc.plugins.tribingo.data.ServerDataManager.get
import net.trilleo.mc.plugins.tribingo.data.ServerDataManager.init
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Logger

/**
 * Manages the single server-wide [ServerData] instance, loading it when the
 * plugin enables and saving it when the plugin disables.
 *
 * The JSON file is stored at `<dataFolder>/serverdata.json`.
 *
 * ### Basic usage
 *
 * Initialise the manager once in [JavaPlugin.onEnable]:
 *
 * ```kotlin
 * ServerDataManager.init(this)
 * ```
 *
 * Retrieve the data anywhere:
 *
 * ```kotlin
 * val data = ServerDataManager.get()
 * val count = data.getInt("eventCount")
 * data.set("eventCount", count + 1)
 * ```
 *
 * Save the data in [JavaPlugin.onDisable]:
 *
 * ```kotlin
 * ServerDataManager.save()
 * ```
 *
 * ### Custom subclass
 *
 * Register a factory before calling [init] to use a [ServerData] subclass:
 *
 * ```kotlin
 * ServerDataManager.setFactory { MyServerData() }
 * ServerDataManager.init(this)
 * ```
 *
 * Then cast the result of [get]:
 *
 * ```kotlin
 * val data = ServerDataManager.get() as MyServerData
 * ```
 */
object ServerDataManager {

    private lateinit var dataFile: File
    private lateinit var serverData: ServerData
    private lateinit var logger: Logger
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var factory: () -> ServerData = { ServerData() }

    /**
     * Overrides the factory used to instantiate the [ServerData] object.
     * Must be called **before** [init] if a custom subclass is needed.
     *
     * @param factory a function that produces a [ServerData] instance
     */
    fun <T : ServerData> setFactory(factory: () -> T) {
        this.factory = factory
    }

    /**
     * Initialises the manager: resolves the data file, creates the plugin data
     * folder if necessary, and loads persisted values into memory.
     *
     * Call this once during [JavaPlugin.onEnable].
     */
    fun init(plugin: JavaPlugin) {
        logger = plugin.logger
        plugin.dataFolder.mkdirs()
        dataFile = File(plugin.dataFolder, "serverdata.json")
        serverData = load()
        plugin.logger.info("ServerDataManager initialised")
    }

    /**
     * Returns the server-wide [ServerData] instance.
     */
    fun get(): ServerData = serverData

    /**
     * Persists the current server data to disk.
     * Call this from [JavaPlugin.onDisable].
     */
    fun save() {
        runCatching { dataFile.writeText(gson.toJson(serverData.json)) }.onFailure { e ->
            logger.severe("Failed to save server data: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun load(): ServerData {
        val data = factory()
        if (dataFile.exists()) {
            runCatching {
                val root = JsonParser.parseString(dataFile.readText()).asJsonObject
                root.entrySet().forEach { (key, value) -> data.json.add(key, value) }
            }.onFailure { e ->
                logger.warning("Failed to load server data: [${e.javaClass.simpleName}] ${e.message}")
            }
        }
        return data
    }
}
