package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.registration.PermissionRegistrar.registerAll
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin

/**
 * Automatically registers Bukkit [Permission] nodes for every
 * [PluginCommand] that declares a non-null [PluginCommand.permission].
 *
 * Call [registerAll] **after** [CommandRegistrar.registerAll] so that the
 * command list is fully populated. Each permission is registered with the
 * server's [org.bukkit.plugin.PluginManager], making it visible to
 * permission-management plugins and ensuring correct default values.
 *
 * Permissions default to [PermissionDefault.OP] (only operators have the
 * permission unless explicitly granted).
 */
object PermissionRegistrar {

    /**
     * Iterates over every registered command, and for each one that defines
     * a permission string, registers a corresponding [Permission] with the
     * server if it has not already been registered.
     */
    fun registerAll(plugin: JavaPlugin) {
        val commands = CommandRegistrar.getAllCommands()
        var count = 0

        for (info in commands) {
            val permString = info.command.permission ?: continue

            if (plugin.server.pluginManager.getPermission(permString) != null) {
                continue
            }

            val permission = Permission(
                permString,
                info.command.description,
                PermissionDefault.OP
            )
            plugin.server.pluginManager.addPermission(permission)
            plugin.logger.info("Registered permission: $permString")
            count++
        }

        plugin.logger.info("Registered $count permission(s)")
    }
}
