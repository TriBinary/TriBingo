package net.trilleo.mc.plugins.tribingo.utils

import com.google.gson.JsonArray
import net.trilleo.mc.plugins.tribingo.data.PlayerData
import net.trilleo.mc.plugins.tribingo.data.PlayerDataManager
import org.bukkit.entity.Player

/**
 * Singleton utility for managing per-player string tags backed by
 * [PlayerDataManager].
 *
 * Tags are stored as a JSON array under the key `"tags"` inside each
 * player's individual data file, so they are automatically loaded on join and
 * saved on quit without any extra setup.
 *
 * ### Quick-start
 *
 * ```kotlin
 * // Add a tag to a player
 * TagUtil.addTag(player, "vip")
 *
 * // Check whether a player has a tag
 * val isVip = TagUtil.hasTag(player, "vip")
 *
 * // Get all tags a player has
 * val tags = TagUtil.getTags(player)
 *
 * // Remove a specific tag
 * TagUtil.removeTag(player, "vip")
 *
 * // Remove all tags from a player
 * TagUtil.clearTags(player)
 * ```
 *
 * ### Persistence
 *
 * Tags are written directly into the player's [PlayerData] JSON on every
 * mutating operation. They are flushed to disk when the player quits or when
 * `PlayerDataManager.saveAll()` is called in `JavaPlugin.onDisable`. No
 * explicit save call is needed from your code.
 */
object TagUtil {

    private const val TAGS_KEY = "tags"

    /**
     * Adds [tag] to [player]'s set of tags.
     *
     * @param player the target player
     * @param tag    the tag to add (case-sensitive)
     * @return `true` if the tag was added; `false` if the player already had it
     */
    fun addTag(player: Player, tag: String): Boolean {
        val data = PlayerDataManager.get(player)
        val tags = readTags(data)
        if (!tags.add(tag)) return false
        writeTags(data, tags)
        return true
    }

    /**
     * Removes [tag] from [player]'s set of tags.
     *
     * @param player the target player
     * @param tag    the tag to remove (case-sensitive)
     * @return `true` if the tag was removed; `false` if the player did not have it
     */
    fun removeTag(player: Player, tag: String): Boolean {
        val data = PlayerDataManager.get(player)
        val tags = readTags(data)
        if (!tags.remove(tag)) return false
        writeTags(data, tags)
        return true
    }

    /**
     * Returns `true` if [player] currently has [tag].
     *
     * @param player the player to check
     * @param tag    the tag to look for (case-sensitive)
     */
    fun hasTag(player: Player, tag: String): Boolean =
        tag in readTags(PlayerDataManager.get(player))

    /**
     * Returns an immutable snapshot of all tags currently assigned to [player].
     *
     * @param player the player to query
     */
    fun getTags(player: Player): Set<String> =
        readTags(PlayerDataManager.get(player)).toSet()

    /**
     * Removes all tags from [player].
     *
     * @param player the player whose tags should be cleared
     */
    fun clearTags(player: Player) {
        val data = PlayerDataManager.get(player)
        writeTags(data, mutableSetOf())
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private fun readTags(data: PlayerData): MutableSet<String> {
        val array = data.getJsonArray(TAGS_KEY)
        return array.mapNotNullTo(mutableSetOf()) { element ->
            if (element.isJsonPrimitive) element.asString else null
        }
    }

    private fun writeTags(data: PlayerData, tags: Set<String>) {
        val array = JsonArray()
        tags.forEach { array.add(it) }
        data.set(TAGS_KEY, array)
    }
}
