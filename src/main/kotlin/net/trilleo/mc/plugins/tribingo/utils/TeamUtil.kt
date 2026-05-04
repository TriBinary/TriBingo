package net.trilleo.mc.plugins.tribingo.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tribingo.data.ServerDataManager
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil.addPlayer
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil.invalidateCache
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

/**
 * Represents a custom team managed by [TeamUtil].
 *
 * @param name        the unique, lowercase identifier for this team
 * @param displayName a MiniMessage string used as the human-readable team name
 * @param members     the mutable set of member UUIDs (managed by [TeamUtil])
 */
class Team(
    val name: String,
    var displayName: String,
    internal val members: MutableSet<UUID> = mutableSetOf()
) {

    /** Number of players currently in this team. */
    val memberCount: Int get() = members.size

    /**
     * Returns an immutable snapshot of all member UUIDs in this team.
     */
    fun getMembers(): Set<UUID> = members.toSet()

    /**
     * Returns all currently online members of this team as a list of [Player]s.
     */
    fun getOnlineMembers(): List<Player> = members.mapNotNull { Bukkit.getPlayer(it) }

    /**
     * Returns `true` if [player] is a member of this team.
     *
     * @param player the player to check
     */
    fun contains(player: Player): Boolean = player.uniqueId in members

    /**
     * Returns `true` if the player identified by [uuid] is a member of this team.
     *
     * @param uuid the UUID to look up
     */
    fun contains(uuid: UUID): Boolean = uuid in members

    /**
     * Sends [message] (parsed as MiniMessage) to all currently online members.
     *
     * @param message the MiniMessage string to broadcast
     */
    fun broadcast(message: String) {
        val component = MiniMessage.miniMessage().deserialize(message)
        getOnlineMembers().forEach { it.sendMessage(component) }
    }
}

/**
 * Singleton utility for managing custom teams with per-player membership
 * enforcement. Each player may belong to at most one team at a time — calling
 * [addPlayer] when a player is already in another team automatically removes
 * them from it first.
 *
 * Teams are persisted inside [ServerDataManager]'s server-wide JSON store
 * under the key `"teams"`, so they survive server restarts without any extra
 * setup.
 *
 * ### Quick-start
 *
 * ```kotlin
 * // Create a team
 * TeamUtil.createTeam("red", "<red>Red Team")
 *
 * // Add a player to a team (auto-removes from any previous team)
 * TeamUtil.addPlayer(player, "red")
 *
 * // Get the team a player belongs to
 * val team = TeamUtil.getPlayerTeam(player)
 *
 * // Broadcast to all online members of a team
 * TeamUtil.getTeam("red")?.broadcast("<yellow>Hello, team!")
 *
 * // Remove a player from their current team
 * TeamUtil.removePlayer(player)
 * ```
 *
 * ### Persistence
 *
 * All mutating operations call [ServerDataManager] immediately so the data
 * is ready to be flushed to disk by `ServerDataManager.save()` in
 * `JavaPlugin.onDisable`. No explicit `save` call is needed from your code.
 *
 * The in-memory cache is populated lazily on the first access after plugin
 * startup. If you modify the underlying server-data JSON externally, call
 * [invalidateCache] to force a reload on the next access.
 */
object TeamUtil {

    private const val TEAMS_KEY = "teams"

    private val teamCache = mutableMapOf<String, Team>()

    // Reverse index: member UUID -> team name for O(1) getPlayerTeam lookups
    private val memberIndex = mutableMapOf<UUID, String>()
    private var loaded = false

    // ── Team Management ─────────────────────────────────────────────────

    /**
     * Creates a new team with the given [name] and [displayName].
     *
     * @param name        unique identifier for the team (stored and matched
     *                    case-insensitively)
     * @param displayName MiniMessage string used as the human-readable name
     * @return `true` if the team was created; `false` if a team with that
     *         name already exists
     */
    fun createTeam(name: String, displayName: String): Boolean {
        ensureLoaded()
        val key = name.lowercase()
        if (teamCache.containsKey(key)) return false
        teamCache[key] = Team(name = key, displayName = displayName)
        persist()
        return true
    }

    /**
     * Deletes the team identified by [name], removing all of its members.
     *
     * @param name the team identifier (case-insensitive)
     * @return `true` if the team was deleted; `false` if it did not exist
     */
    fun deleteTeam(name: String): Boolean {
        ensureLoaded()
        val key = name.lowercase()
        val team = teamCache.remove(key) ?: return false
        team.members.forEach { memberIndex.remove(it) }
        persist()
        return true
    }

    /**
     * Deletes all existing teams, removing every member from every team.
     */
    fun deleteAll() {
        ensureLoaded()
        teamCache.clear()
        memberIndex.clear()
        persist()
    }

    /**
     * Updates the display name of the team identified by [name].
     *
     * @param name           the team identifier (case-insensitive)
     * @param newDisplayName the new MiniMessage display name
     * @return `true` if the team was updated; `false` if it did not exist
     */
    fun renameTeam(name: String, newDisplayName: String): Boolean {
        ensureLoaded()
        val team = teamCache[name.lowercase()] ?: return false
        team.displayName = newDisplayName
        persist()
        return true
    }

    /**
     * Returns the team identified by [name], or `null` if it does not exist.
     *
     * @param name the team identifier (case-insensitive)
     */
    fun getTeam(name: String): Team? {
        ensureLoaded()
        return teamCache[name.lowercase()]
    }

    /**
     * Returns a list of all currently existing teams.
     */
    fun getAllTeams(): List<Team> {
        ensureLoaded()
        return teamCache.values.toList()
    }

    /**
     * Returns `true` if a team with [name] exists.
     *
     * @param name the team identifier (case-insensitive)
     */
    fun hasTeam(name: String): Boolean {
        ensureLoaded()
        return teamCache.containsKey(name.lowercase())
    }

    // ── Player Membership ────────────────────────────────────────────────

    /**
     * Adds [player] to the team identified by [teamName].
     *
     * If the player is already in a different team they are removed from that
     * team first, so a player can never belong to more than one team.
     *
     * @param player   the player to add
     * @param teamName the target team identifier (case-insensitive)
     * @return `true` if the player was added to the team; `false` if the team
     *         does not exist or the player was already in that team
     */
    fun addPlayer(player: Player, teamName: String): Boolean {
        ensureLoaded()
        val team = teamCache[teamName.lowercase()] ?: return false
        if (team.contains(player)) return false
        // Remove from any current team before adding to the new one
        val current = teamCache[memberIndex[player.uniqueId]]
        current?.members?.remove(player.uniqueId)
        team.members.add(player.uniqueId)
        memberIndex[player.uniqueId] = team.name
        persist()
        return true
    }

    /**
     * Removes [player] from whichever team they currently belong to.
     *
     * @param player the player to remove
     * @return `true` if the player was removed from a team; `false` if they
     *         were not in any team
     */
    fun removePlayer(player: Player): Boolean {
        ensureLoaded()
        val team = getPlayerTeam(player) ?: return false
        team.members.remove(player.uniqueId)
        memberIndex.remove(player.uniqueId)
        persist()
        return true
    }

    /**
     * Returns the team that [player] currently belongs to, or `null` if they
     * are not in any team.
     *
     * @param player the player to look up
     */
    fun getPlayerTeam(player: Player): Team? {
        ensureLoaded()
        return teamCache[memberIndex[player.uniqueId]]
    }

    /**
     * Returns `true` if [player] is a member of the team identified by
     * [teamName].
     *
     * @param player   the player to check
     * @param teamName the team identifier (case-insensitive)
     */
    fun isInTeam(player: Player, teamName: String): Boolean {
        ensureLoaded()
        return teamCache[teamName.lowercase()]?.contains(player) ?: false
    }

    /**
     * Returns `true` if [playerA] and [playerB] are both members of the same
     * team. Returns `false` if either player is not in any team.
     *
     * @param playerA the first player
     * @param playerB the second player
     */
    fun areTeammates(playerA: Player, playerB: Player): Boolean {
        ensureLoaded()
        val teamName = memberIndex[playerA.uniqueId] ?: return false
        return teamName == memberIndex[playerB.uniqueId]
    }

    /**
     * Sends [message] (parsed as MiniMessage) to all currently online members
     * of every existing team.
     *
     * @param message the MiniMessage string to broadcast
     */
    fun broadcastAll(message: String) {
        ensureLoaded()
        teamCache.values.forEach { it.broadcast(message) }
    }

    // ── Cache ────────────────────────────────────────────────────────────

    /**
     * Clears the in-memory cache so that the next access reloads all team
     * data from [ServerDataManager].
     *
     * Only needed when the underlying server-data JSON has been modified
     * outside of [TeamUtil] (e.g. direct `ServerData` edits).
     */
    fun invalidateCache() {
        loaded = false
        teamCache.clear()
        memberIndex.clear()
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private fun ensureLoaded() {
        if (!loaded) {
            load()
            loaded = true
        }
    }

    private fun load() {
        teamCache.clear()
        memberIndex.clear()
        val teamsArray = ServerDataManager.get().getJsonArray(TEAMS_KEY)
        for (element in teamsArray) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val name = obj.get("name")?.asString?.lowercase() ?: continue
            val displayName = obj.get("displayName")?.asString ?: name
            val membersJson = if (obj.has("members") && obj.get("members").isJsonArray)
                obj.getAsJsonArray("members") else JsonArray()
            val members = mutableSetOf<UUID>()
            for (m in membersJson) {
                runCatching {
                    val uuid = UUID.fromString(m.asString)
                    members.add(uuid)
                    memberIndex[uuid] = name
                }
            }
            teamCache[name] = Team(name = name, displayName = displayName, members = members)
        }
    }

    private fun persist() {
        val teamsArray = JsonArray()
        for (team in teamCache.values) {
            val obj = JsonObject()
            obj.addProperty("name", team.name)
            obj.addProperty("displayName", team.displayName)
            val membersArray = JsonArray()
            team.members.forEach { membersArray.add(it.toString()) }
            obj.add("members", membersArray)
            teamsArray.add(obj)
        }
        ServerDataManager.get().set(TEAMS_KEY, teamsArray)
    }
}
