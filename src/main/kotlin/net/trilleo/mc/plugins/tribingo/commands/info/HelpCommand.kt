package net.trilleo.mc.plugins.tribingo.commands.info

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.registration.CommandRegistrar
import net.trilleo.mc.plugins.tribingo.registration.PluginCommand
import org.bukkit.command.CommandSender

class HelpCommand : PluginCommand(
    name = "help",
    description = "Show all available commands"
) {
    companion object {
        // Matches the total width of the header: "========= TriBingo Commands ========="
        private const val HEADER_WIDTH = 37
    }

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val categorized = CommandRegistrar.getCommandsByCategory()

        // Header
        sender.sendMessage(
            Component.text("=========", NamedTextColor.GOLD)
                .append(
                    Component.text(" TriBingo Commands ", NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                )
                .append(Component.text("=========", NamedTextColor.GOLD))
        )

        for ((category, commands) in categorized) {
            // Category header
            sender.sendMessage(
                Component.text("» ", NamedTextColor.GOLD)
                    .append(
                        Component.text(category, NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD)
                    )
            )

            // Commands in this category, sorted alphabetically
            for (info in commands) {
                val commandText = if (info.isSubCommand) {
                    "/tribingo ${info.command.name}"
                } else {
                    "/${info.command.name}"
                }
                sender.sendMessage(
                    Component.text("  $commandText", NamedTextColor.GREEN)
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(info.command.description, NamedTextColor.GRAY))
                )
            }
        }

        // Footer
        sender.sendMessage(Component.text("=".repeat(HEADER_WIDTH), NamedTextColor.GOLD))

        return true
    }
}