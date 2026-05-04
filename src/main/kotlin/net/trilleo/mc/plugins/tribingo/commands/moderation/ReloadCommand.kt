package net.trilleo.mc.plugins.tribingo.commands.moderation

import net.trilleo.mc.plugins.tribingo.Main
import net.trilleo.mc.plugins.tribingo.registration.PluginCommand
import net.trilleo.mc.plugins.tribingo.utils.MessageUtil
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * Reloads the plugin configuration from disk.
 *
 * Registered as `/tribingo reload` and requires the
 * `tribingo.reload` permission.
 */
class ReloadCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "reload",
    description = "Reload the plugin configuration",
    permission = "tribingo.reload"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val main = plugin as? Main
        if (main == null) {
            if (sender is Player) {
                sender.sendPrefixed("<red>Error: Plugin instance type mismatch. Unable to reload configuration.")
            } else {
                sender.sendMessage("Error: Plugin instance type mismatch. Unable to reload configuration.")
            }
            return true
        }
        main.pluginConfig.reload()
        MessageUtil.init(main.pluginConfig.messagePrefix)
        if (sender is Player) {
            sender.sendPrefixed("Configuration reloaded!")
        } else {
            sender.sendMessage("Configuration reloaded!")
        }
        return true
    }
}