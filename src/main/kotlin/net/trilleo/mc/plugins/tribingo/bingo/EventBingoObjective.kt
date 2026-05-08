package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.Listener

/**
 * An event-driven [BingoObjective] that also implements Bukkit's [Listener].
 *
 * Concrete subclasses add one or more `@EventHandler`-annotated methods that
 * extract the relevant [Player] and their [BingoPlayerState], update progress
 * counters, and—when the objective is satisfied—call
 * [BingoManager.checkCompletion].
 *
 * The [BingoObjectiveRegistry][net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry]
 * automatically registers every `EventBingoObjective` as a Bukkit listener
 * when `register` is called.
 *
 * ### Example
 * ```kotlin
 * class MyObjective : EventBingoObjective<PlayerMoveEvent>(
 *     id          = "walk_10_blocks",
 *     name        = Component.text("Walk 10 Blocks"),
 *     description = Component.text("Move 10 blocks."),
 *     difficulty  = Difficulty.EASY,
 *     eventClass  = PlayerMoveEvent::class.java
 * ) {
 *     @EventHandler
 *     fun onMove(event: PlayerMoveEvent) {
 *         if (!event.hasMovedBlock()) return
 *         val game = BingoManager.currentGame ?: return
 *         if (game.state != GameState.ACTIVE) return
 *         onEvent(event, event.player, game.getOrCreateState(event.player.uniqueId))
 *     }
 *     override fun onEvent(event: PlayerMoveEvent, player: Player, state: BingoPlayerState) { ... }
 *     override fun isCompletedBy(player: Player, state: BingoPlayerState) = state.getProgress(id) >= 10
 * }
 * ```
 *
 * @param T the Bukkit event type this objective listens to
 */
abstract class EventBingoObjective<T : Event>(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    /** The runtime class of the event this objective handles. */
    val eventClass: Class<T>
) : BingoObjective(id, name, description, difficulty), Listener {

    /**
     * Called from the concrete class's `@EventHandler` method once the
     * triggering [Player] and their [BingoPlayerState] have been resolved.
     *
     * Implementations should update progress counters and call
     * [BingoManager.checkCompletion] when the completion threshold is reached.
     *
     * @param event  the Bukkit event that triggered this call
     * @param player the player who caused the event
     * @param state  the player's per-game state
     */
    abstract fun onEvent(event: T, player: Player, state: BingoPlayerState)
}
