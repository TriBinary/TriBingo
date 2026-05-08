package net.trilleo.mc.plugins.tribingo.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * A typed wrapper around the plugin's `config.yml`.
 *
 * On construction the default configuration is saved (if the file does not
 * yet exist) and the current values are loaded into memory. Call [reload]
 * to re-read the file at runtime without restarting the server.
 *
 * @param plugin the owning plugin instance
 */
class PluginConfig(private val plugin: JavaPlugin) {

    /** The currently loaded Bukkit [FileConfiguration]. */
    private var config: FileConfiguration

    init {
        plugin.saveDefaultConfig()
        config = plugin.config
    }

    /**
     * Re-reads `config.yml` from disk, picking up any changes made since
     * the last load.
     */
    fun reload() {
        plugin.reloadConfig()
        plugin.config.options().copyDefaults(true)
        plugin.saveConfig()
        config = plugin.config
    }

    // ── Plugin Properties ────────────────────────────────────────────────

    /**
     * The prefix shown before plugin messages. Taken from the `message-prefix`
     * key in `config.yml`. Supports plain text and MiniMessage formatting.
     */
    val messagePrefix: String
        get() = getString("message-prefix", "[TriBingo]")

    // ── Bingo Properties ─────────────────────────────────────────────────

    /**
     * Default side-length for a new Bingo board (3–6).
     * Taken from `bingo.default-board-size` in `config.yml`.
     */
    val boardDefaultSize: Int
        get() = getInt("bingo.default-board-size", 4).coerceIn(3, 6)

    /**
     * Win condition mode. When `true` (default), the first player to complete
     * a full row, column, or diagonal wins. When `false`, a player must complete
     * the entire board.
     *
     * Taken from `bingo.win-condition` in `config.yml`. Set to `LINE` for line
     * wins, or `FULL_BOARD` for a full-board win.
     */
    val winConditionLine: Boolean
        get() = getString("bingo.win-condition", "LINE").uppercase() == "LINE"

    /**
     * When `true`, a server-wide announcement is sent whenever any player
     * completes a bingo cell. Taken from `bingo.announce-completions`.
     */
    val announceCompletions: Boolean
        get() = getBoolean("bingo.announce-completions", true)

    // ── Typed Getters ───────────────────────────────────────────────────

    /**
     * Returns the [String] value at [path], or [default] when the key is
     * absent or not a string.
     */
    fun getString(path: String, default: String = ""): String =
        config.getString(path, default) ?: default

    /**
     * Returns the [Int] value at [path], or [default] when the key is
     * absent or not an integer.
     */
    fun getInt(path: String, default: Int = 0): Int =
        config.getInt(path, default)

    /**
     * Returns the [Double] value at [path], or [default] when the key is
     * absent or not a double.
     */
    fun getDouble(path: String, default: Double = 0.0): Double =
        config.getDouble(path, default)

    /**
     * Returns the [Boolean] value at [path], or [default] when the key is
     * absent or not a boolean.
     */
    fun getBoolean(path: String, default: Boolean = false): Boolean =
        config.getBoolean(path, default)

    /**
     * Returns the [List] of [String] values at [path], or an empty list
     * when the key is absent.
     */
    fun getStringList(path: String): List<String> =
        config.getStringList(path)

    /**
     * Returns `true` when [path] exists in the loaded configuration.
     */
    fun contains(path: String): Boolean =
        config.contains(path)
}
