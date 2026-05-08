package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers all concrete [PluginCommand] subclasses inside the `commands`
 * package (and its subpackages) and registers them with the server.
 *
 * Commands are split into two categories based on [PluginCommand.isMainCommand]:
 *
 * * **Sub-commands** (`isMainCommand = false`, the default) – registered under
 *   the `/tribingo` parent command (alias `/tb`). A command with
 *   `name = "reload"` becomes `/tribingo reload`.
 * * **Main commands** (`isMainCommand = true`) – registered directly on the
 *   server [CommandMap] as standalone top-level commands.
 *
 * The `/tribingo` command provides tab-completion for all registered
 * sub-commands automatically.
 */
object CommandRegistrar {

    private const val COMMANDS_PACKAGE = "net.trilleo.mc.plugins.tribingo.commands"
    private const val ROOT_COMMAND = "tribingo"

    /** All registered sub-commands, keyed by their name (lower-case). */
    private val subCommands = mutableMapOf<String, PluginCommand>()

    /** Every registered command together with its category for the help display. */
    private val allCommands = mutableListOf<RegisteredCommandInfo>()

    /**
     * Holds metadata about a registered command used by the help system.
     *
     * @property command      the underlying [PluginCommand] instance
     * @property category     display-friendly category derived from the source subpackage
     * @property isSubCommand `true` when the command is a sub-command of `/tribingo`
     */
    data class RegisteredCommandInfo(
        val command: PluginCommand,
        val category: String,
        val isSubCommand: Boolean
    )

    /**
     * Returns an unmodifiable list of all registered commands and their metadata.
     */
    fun getAllCommands(): List<RegisteredCommandInfo> = allCommands.toList()

    /**
     * Returns all registered commands grouped by category. Commands within
     * each category are sorted alphabetically by name; categories themselves
     * are sorted alphabetically as well.
     */
    fun getCommandsByCategory(): Map<String, List<RegisteredCommandInfo>> {
        return allCommands
            .groupBy { it.category }
            .mapValues { (_, cmds) -> cmds.sortedBy { it.command.name.lowercase() } }
            .toSortedMap()
    }

    /**
     * Scans the commands package, instantiates every [PluginCommand] found,
     * and registers it either as a sub-command of `/tribingo` or as a
     * standalone main command.
     */
    fun registerAll(plugin: JavaPlugin) {
        subCommands.clear()
        allCommands.clear()

        val commandClasses = PackageScanner.findClasses(
            plugin, COMMANDS_PACKAGE, PluginCommand::class.java
        )
        val commandMap = getCommandMap()

        var subCount = 0
        var mainCount = 0

        for (commandClass in commandClasses) {
            try {
                val command = instantiate(commandClass, plugin)
                val category = extractCategory(commandClass)

                if (command.isMainCommand) {
                    val bukkitCommand = createBukkitCommand(command)
                    commandMap.register(plugin.name.lowercase(), bukkitCommand)
                    plugin.logger.info("Registered main command: /${command.name}")
                    mainCount++
                } else {
                    subCommands[command.name.lowercase()] = command
                    plugin.logger.info("Registered sub-command: /$ROOT_COMMAND ${command.name}")
                    subCount++
                }

                allCommands.add(RegisteredCommandInfo(command, category, !command.isMainCommand))
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to register command ${commandClass.simpleName}: ${e.message}"
                )
            }
        }

        val parentCommand = plugin.getCommand(ROOT_COMMAND)
        if (parentCommand == null) {
            plugin.logger.severe("Command '$ROOT_COMMAND' is missing from plugin.yml")
            return
        }

        parentCommand.setExecutor { sender, _, _, args ->
            executeParentCommand(sender, args)
        }

        parentCommand.tabCompleter = TabCompleter { sender, _, _, args ->
            tabCompleteParentCommand(sender, args)
        }

        plugin.logger.info(
            "Registered $subCount sub-command(s) and $mainCount main command(s)"
        )
    }

    /** Resolves the server [CommandMap] via reflection for broad compatibility. */
    private fun getCommandMap(): CommandMap {
        val server = Bukkit.getServer()
        val method = server.javaClass.getMethod("getCommandMap")
        return method.invoke(server) as CommandMap
    }

    /**
     * Tries to create an instance of [clazz] using a constructor that accepts
     * a [JavaPlugin]; falls back to a no-arg constructor.
     */
    private fun instantiate(clazz: Class<out PluginCommand>, plugin: JavaPlugin): PluginCommand {
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

    private fun executeParentCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) {
                sender.sendPrefixed("Usage: /$ROOT_COMMAND <subcommand>")
                sender.sendPrefixed(
                    "Available sub-commands: ${subCommands.keys.sorted().joinToString(", ")}"
                )
            } else {
                sender.sendMessage("Usage: /$ROOT_COMMAND <subcommand>")
                sender.sendMessage(
                    "Available sub-commands: ${subCommands.keys.sorted().joinToString(", ")}"
                )
            }
            return true
        }

        val subName = args[0].lowercase()
        val subCommand = subCommands[subName]
        if (subCommand == null) {
            if (sender is Player) {
                sender.sendPrefixed("Unknown sub-command: ${args[0]}")
                sender.sendPrefixed(
                    "Available sub-commands: ${subCommands.keys.sorted().joinToString(", ")}"
                )
            }
            sender.sendMessage("Unknown sub-command: ${args[0]}")
            sender.sendMessage(
                "Available sub-commands: ${subCommands.keys.sorted().joinToString(", ")}"
            )
            return true
        }

        subCommand.permission?.let { perm ->
            if (!sender.hasPermission(perm)) {
                if (sender is Player) {
                    sender.sendPrefixed("<red>You don't have permission to use this command!")
                } else {
                    sender.sendMessage("You do not have permission to use this command.")
                }
                return true
            }
        }

        val subArgs = args.drop(1).toTypedArray()
        return subCommand.execute(sender, subArgs)
    }

    private fun tabCompleteParentCommand(
        sender: CommandSender,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return subCommands.keys
                .filter { it.startsWith(args[0].lowercase()) }
                .sorted()
        }

        if (args.size >= 2) {
            val subName = args[0].lowercase()
            val subCommand = subCommands[subName] ?: return emptyList()
            val subArgs = args.drop(1).toTypedArray()
            return subCommand.tabComplete(sender, subArgs)
        }

        return emptyList()
    }

    /**
     * Derives a display-friendly category name from the command class's package.
     *
     * Commands directly inside the `commands` package are categorised as
     * **"General"**; commands in a subpackage use the first subpackage
     * segment, split on camelCase boundaries into title-cased words.
     */
    private fun extractCategory(clazz: Class<*>): String {
        val packageName = clazz.name.substringBeforeLast('.', "")
        val relative = packageName.removePrefix(COMMANDS_PACKAGE)
        return if (relative.isEmpty()) {
            "General"
        } else {
            val raw = relative.removePrefix(".").split('.').first()
            formatCamelCase(raw)
        }
    }

    /**
     * Converts a camelCase string into space-separated, title-cased words.
     */
    private fun formatCamelCase(input: String): String {
        return input
            .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    /** Wraps a [PluginCommand] in a Bukkit [Command] suitable for the command map. */
    private fun createBukkitCommand(command: PluginCommand): Command {
        return object : Command(
            command.name,
            command.description,
            command.usage,
            command.aliases
        ) {
            init {
                command.permission?.let { permission = it }
            }

            override fun execute(
                sender: CommandSender,
                commandLabel: String,
                args: Array<out String>
            ): Boolean = command.execute(sender, args)

            override fun tabComplete(
                sender: CommandSender,
                alias: String,
                args: Array<out String>
            ): List<String> = command.tabComplete(sender, args)
        }
    }
}
