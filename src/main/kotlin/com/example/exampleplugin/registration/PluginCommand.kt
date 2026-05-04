package com.example.exampleplugin.registration

import org.bukkit.command.CommandSender

/**
 * Base class for all plugin commands.
 *
 * Extend this class and place the subclass anywhere inside the
 * `com.example.exampleplugin.commands` package (or any subpackage) to
 * have it automatically discovered and registered at startup.
 *
 * By default every command is registered as a **sub-command** of `/exampleplugin`
 * (alias `/ep`). For example, a command with `name = "reload"` becomes
 * `/exampleplugin reload`. Set [isMainCommand] to `true` to register the command
 * as a standalone top-level command instead.
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter (the plugin
 *   instance will be injected automatically).
 *
 * Example (sub-command – registered as `/exampleplugin start`):
 * ```kotlin
 * package com.example.exampleplugin.commands.game
 *
 * class StartCommand : PluginCommand(
 *     name = "start",
 *     description = "Start the game",
 *     usage = "/exampleplugin start",
 *     permission = "exampleplugin.start"
 * ) {
 *     override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
 *         sender.sendMessage("The game has begun!")
 *         return true
 *     }
 * }
 * ```
 *
 * Example (main command – registered as `/globaltool`):
 * ```kotlin
 * package com.example.exampleplugin.commands
 *
 * class GlobalToolCommand : PluginCommand(
 *     name = "globaltool",
 *     description = "A standalone command",
 *     isMainCommand = true
 * ) {
 *     override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
 *         sender.sendMessage("Hello from /globaltool!")
 *         return true
 *     }
 * }
 * ```
 */
abstract class PluginCommand(
    val name: String,
    val description: String = "",
    val usage: String = "/<command>",
    val aliases: List<String> = emptyList(),
    val permission: String? = null,
    val isMainCommand: Boolean = false
) {

    /**
     * Called when the command is executed.
     *
     * @param sender the entity that issued the command
     * @param args   the arguments passed after the command name
     * @return `true` if the command was handled successfully
     */
    abstract fun execute(sender: CommandSender, args: Array<out String>): Boolean

    /**
     * Called when tab-completion is requested for this command.
     *
     * @param sender the entity requesting completions
     * @param args   the arguments typed so far
     * @return a list of possible completions, or an empty list
     */
    open fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = emptyList()
}
