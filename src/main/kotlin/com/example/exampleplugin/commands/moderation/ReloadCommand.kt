package com.example.exampleplugin.commands.moderation

import com.example.exampleplugin.Main
import com.example.exampleplugin.registration.PluginCommand
import com.example.exampleplugin.utils.MessageUtil
import com.example.exampleplugin.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * Reloads the plugin configuration from disk.
 *
 * Registered as `/exampleplugin reload` and requires the
 * `exampleplugin.reload` permission.
 */
class ReloadCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "reload",
    description = "Reload the plugin configuration",
    permission = "exampleplugin.reload"
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