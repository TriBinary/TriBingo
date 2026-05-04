package net.trilleo.mc.plugins.tribingo.registration

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers all concrete [Listener] implementations inside the `listeners`
 * package (and its subpackages) and registers them with Bukkit's
 * [org.bukkit.plugin.PluginManager].
 *
 * Each listener class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single [JavaPlugin] parameter.
 */
object ListenerRegistrar {

    private const val LISTENERS_PACKAGE = "net.trilleo.mc.plugins.trihunt.listeners"

    /**
     * Scans the listeners package, instantiates every [Listener] found,
     * and registers it for event handling.
     */
    fun registerAll(plugin: JavaPlugin) {
        val listenerClasses = PackageScanner.findClasses(
            plugin, LISTENERS_PACKAGE, Listener::class.java
        )

        for (listenerClass in listenerClasses) {
            try {
                val listener = instantiate(listenerClass, plugin)
                plugin.server.pluginManager.registerEvents(listener, plugin)
                plugin.logger.info("Registered listener: ${listenerClass.simpleName}")
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to register listener ${listenerClass.simpleName}: ${e.message}"
                )
            }
        }

        plugin.logger.info("Registered ${listenerClasses.size} listener(s)")
    }

    /**
     * Tries to create an instance of [clazz] using a constructor that accepts
     * a [JavaPlugin]; falls back to a no-arg constructor.
     */
    private fun instantiate(clazz: Class<out Listener>, plugin: JavaPlugin): Listener {
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
}
