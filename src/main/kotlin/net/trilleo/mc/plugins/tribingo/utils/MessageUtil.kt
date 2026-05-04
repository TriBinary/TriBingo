package net.trilleo.mc.plugins.tribingo.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tribingo.utils.MessageUtil.init
import org.bukkit.entity.Player

/**
 * Utility for sending prefix-decorated messages to players.
 *
 * Call [init] once during plugin startup (and again after a config reload) to
 * load the configured prefix. The prefix string may be plain text or a
 * MiniMessage-formatted string such as `"<gray>[<gold>TriBingo<gray>]"`.
 *
 * ### Usage
 *
 * ```kotlin
 * import net.trilleo.mc.plugins.trihunt.utils.sendPrefixed
 *
 * // Plain text or MiniMessage string
 * player.sendPrefixed("Hello!")
 * player.sendPrefixed("<green>Operation successful!")
 *
 * // Adventure Component
 * player.sendPrefixed(Component.text("Hello!", NamedTextColor.GREEN))
 * ```
 */
object MessageUtil {

    private val mm = MiniMessage.miniMessage()
    private var prefixComponent: Component = Component.empty()

    /**
     * Loads the prefix from [prefixString].
     *
     * [prefixString] may be a plain text string (e.g. `"[TriBingo]"`) or
     * a MiniMessage-formatted string (e.g. `"<gray>[<gold>TriBingo<gray>]"`).
     * Call this method during plugin startup and again whenever the configuration
     * is reloaded.
     *
     * @param prefixString the prefix text to display before every message
     */
    fun init(prefixString: String) {
        prefixComponent = mm.deserialize(prefixString)
    }

    /**
     * Sends [message] to [player] with the configured prefix prepended.
     *
     * [message] may be a plain text string or a MiniMessage-formatted string.
     *
     * @param player  the recipient
     * @param message the message string (plain text or MiniMessage-formatted)
     */
    fun sendPrefixed(player: Player, message: String) {
        player.sendMessage(build(mm.deserialize(message)))
    }

    /**
     * Sends [message] to [player] with the configured prefix prepended.
     *
     * @param player  the recipient
     * @param message the [Component] to send
     */
    fun sendPrefixed(player: Player, message: Component) {
        player.sendMessage(build(message))
    }

    private fun build(message: Component): Component =
        Component.text()
            .append(prefixComponent)
            .append(Component.space())
            .append(message)
            .build()
}

/**
 * Sends [message] to this player with the plugin prefix prepended.
 *
 * [message] may be a plain text string or a MiniMessage-formatted string.
 *
 * @receiver the target player
 * @param message the message string (plain text or MiniMessage-formatted)
 */
fun Player.sendPrefixed(message: String) = MessageUtil.sendPrefixed(this, message)

/**
 * Sends [message] to this player with the plugin prefix prepended.
 *
 * @receiver the target player
 * @param message the [Component] to send
 */
fun Player.sendPrefixed(message: Component) = MessageUtil.sendPrefixed(this, message)
