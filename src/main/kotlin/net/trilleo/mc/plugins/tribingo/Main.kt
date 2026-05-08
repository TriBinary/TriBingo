package net.trilleo.mc.plugins.tribingo

import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry
import net.trilleo.mc.plugins.tribingo.bingo.registry.CodeObjectiveLoader
import net.trilleo.mc.plugins.tribingo.bingo.registry.YamlObjectiveLoader
import net.trilleo.mc.plugins.tribingo.config.PluginConfig
import net.trilleo.mc.plugins.tribingo.data.BingoServerData
import net.trilleo.mc.plugins.tribingo.data.PlayerDataManager
import net.trilleo.mc.plugins.tribingo.data.ServerDataManager
import net.trilleo.mc.plugins.tribingo.registration.*
import net.trilleo.mc.plugins.tribingo.utils.MessageUtil
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    /** Typed configuration wrapper – available after [onEnable]. */
    lateinit var pluginConfig: PluginConfig
        private set

    override fun onEnable() {
        // Load configuration
        logger.info("Loading configuration...")
        pluginConfig = PluginConfig(this)
        MessageUtil.init(pluginConfig.messagePrefix)

        // Initialise data managers (BingoServerData factory must be set first)
        logger.info("Initialising data managers...")
        ServerDataManager.setFactory { BingoServerData() }
        ServerDataManager.init(this)
        PlayerDataManager.init(this)

        // Register custom items and recipes
        logger.info("Registering custom items...")
        ItemRegistrar.registerAll(this)
        logger.info("Registering recipes...")
        RecipeRegistrar.registerAll(this)

        // Register commands, listeners, GUIs and tasks
        logger.info("Registering commands...")
        CommandRegistrar.registerAll(this)
        logger.info("Registering permissions...")
        PermissionRegistrar.registerAll(this)
        logger.info("Registering listeners...")
        ListenerRegistrar.registerAll(this)
        logger.info("Registering GUIs...")
        GUIManager.registerAll(this)
        logger.info("Registering tasks...")
        TaskRegistrar.registerAll(this)

        // Initialise Bingo system
        logger.info("Initialising Bingo system...")
        BingoObjectiveRegistry.init(this)
        CodeObjectiveLoader.load(
            this, BingoObjectiveRegistry,
            "net.trilleo.mc.plugins.tribingo.bingo.custom"
        )
        YamlObjectiveLoader.load(this, BingoObjectiveRegistry)
        BingoManager.init(this)

        logger.info("Plugin enabled!")
    }

    override fun onDisable() {
        // Cancel all scheduled tasks
        TaskRegistrar.unregisterAll()

        // Remove all registered recipes
        RecipeRegistrar.unregisterAll()

        // Persist data for any players still online, bingo state, and server-wide data
        PlayerDataManager.saveAll()
        BingoManager.save()
        ServerDataManager.save()

        logger.info("Plugin disabled!")
    }
}
