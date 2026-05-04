package net.trilleo.mc.plugins.tribingo.utils

import net.trilleo.mc.plugins.tribingo.utils.GameRuleUtil.toggle
import org.bukkit.GameRule
import org.bukkit.World

/**
 * Singleton utility for reading and writing Minecraft game rules on a [World].
 *
 * Covers all game rules of every type (`Boolean` and `Int`). For boolean rules,
 * [toggle] flips the current value without needing to specify `true` or `false`
 * explicitly.
 *
 * ### Quick-start
 *
 * ```kotlin
 * // Read a rule
 * val keepInventory = GameRuleUtil.get(world, GameRule.KEEP_INVENTORY)
 *
 * // Write a rule
 * GameRuleUtil.set(world, GameRule.KEEP_INVENTORY, true)
 * GameRuleUtil.set(world, GameRule.RANDOM_TICK_SPEED, 3)
 *
 * // Toggle a boolean rule (no true/false needed)
 * GameRuleUtil.toggle(world, GameRule.DO_DAYLIGHT_CYCLE)
 * ```
 */
object GameRuleUtil {

    /**
     * Returns the current value of [rule] in [world], or `null` if the rule
     * has no value set.
     *
     * @param world the world to read from
     * @param rule  the game rule to query
     */
    fun <T : Any> get(world: World, rule: GameRule<T>): T? =
        world.getGameRuleValue(rule)

    /**
     * Sets [rule] in [world] to [value].
     *
     * @param world the world to update
     * @param rule  the game rule to change
     * @param value the new value
     * @return `true` if the value was applied successfully; `false` if the
     *         rule is not recognized by this world
     */
    fun <T : Any> set(world: World, rule: GameRule<T>, value: T): Boolean =
        world.setGameRule(rule, value)

    /**
     * Flips the current value of the boolean [rule] in [world] to its logical
     * inverse, without requiring the caller to know the current state.
     *
     * @param world the world to update
     * @param rule  the boolean game rule to toggle
     * @return the **new** value after toggling, or `null` if the current value
     *         could not be read
     */
    fun toggle(world: World, rule: GameRule<Boolean>): Boolean? {
        val newValue = !(world.getGameRuleValue(rule) ?: return null)
        world.setGameRule(rule, newValue)
        return newValue
    }
}
