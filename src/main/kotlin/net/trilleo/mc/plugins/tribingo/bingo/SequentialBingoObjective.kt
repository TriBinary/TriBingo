package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.entity.Player
import org.bukkit.event.Listener

/**
 * A helper base class for objectives that must be completed by performing a
 * fixed sequence of steps **in order**.
 *
 * Concrete subclasses declare the ordered list of step tokens in [steps] and
 * add `@EventHandler` methods that call [advanceStep] with the appropriate
 * token. The objective is complete when all steps have been performed in the
 * correct sequence.
 *
 * ### Example — craft a crafting table, place it, then craft something on it
 * ```kotlin
 * @CustomObjective
 * class CraftPlaceCraftObjective : SequentialBingoObjective(
 *     id          = "craft_place_craft",
 *     name        = Component.text("Crafty Crafter"),
 *     description = Component.text("Craft a table, place it, craft something on it."),
 *     difficulty  = Difficulty.MEDIUM,
 *     steps       = listOf("crafted_table", "placed_table", "crafted_on_table")
 * ) {
 *     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
 *     fun onCraft(event: CraftItemEvent) {
 *         val player = event.whoClicked as? Player ?: return
 *         val game = BingoManager.currentGame ?: return
 *         if (game.state != GameState.ACTIVE) return
 *         val state = game.getOrCreateState(player.uniqueId)
 *         val result = event.recipe.result.type
 *         when {
 *             result == Material.CRAFTING_TABLE && !hasStep(state, "crafted_table") ->
 *                 advanceStep(state, "crafted_table")
 *             hasStep(state, "placed_table") ->
 *                 if (advanceStep(state, "crafted_on_table"))
 *                     BingoManager.checkCompletion(player, this)
 *         }
 *     }
 *
 *     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
 *     fun onPlace(event: BlockPlaceEvent) {
 *         if (event.blockPlaced.type != Material.CRAFTING_TABLE) return
 *         val game = BingoManager.currentGame ?: return
 *         if (game.state != GameState.ACTIVE) return
 *         val state = game.getOrCreateState(event.player.uniqueId)
 *         advanceStep(state, "placed_table")
 *     }
 * }
 * ```
 *
 * ### How [advanceStep] works
 * The helper compares [step] against `steps[completedStepCount]` — the
 * expected **next** step. If the token matches, it is recorded in
 * [BingoPlayerState.stepData] and the method returns `true` when that was the
 * last required step. Out-of-order or duplicate steps are silently ignored.
 *
 * @param steps ordered list of step token strings that must be completed in sequence
 */
abstract class SequentialBingoObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty,
    val steps: List<String>
) : BingoObjective(id, name, description, difficulty), Listener {

    /**
     * Attempts to advance the objective's step sequence for [state].
     *
     * The method checks whether [step] is the **next expected** step (based on
     * how many steps have already been completed). If so, it records the step
     * and returns `true` when the sequence is now fully complete, or `false`
     * when more steps remain. Returns `false` immediately if [step] is not the
     * expected next step or if all steps are already complete.
     *
     * Call [BingoManager.checkCompletion] when this method returns `true`.
     *
     * @param state the player's per-game state
     * @param step  the token identifying which step was just performed
     * @return `true` if [step] was the final required step and the objective is now complete
     */
    protected fun advanceStep(state: BingoPlayerState, step: String): Boolean {
        val done = state.getSteps(id)
        if (done.size >= steps.size) return false        // sequence already finished
        if (steps[done.size] != step) return false       // wrong step for this position
        state.addStep(id, step)
        // done is a live reference to the underlying mutable set; size reflects the addition
        return done.size == steps.size
    }

    /**
     * Returns `true` when [state] has completed the given [step] token in a
     * previous call to [advanceStep].
     *
     * Useful for guarding event handlers that must only fire after an earlier
     * step is already recorded (e.g. "only count crafts after the table has
     * been placed").
     *
     * @param state the player's per-game state
     * @param step  the token to check
     */
    protected fun hasStep(state: BingoPlayerState, step: String): Boolean =
        state.hasStep(id, step)

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).size >= steps.size

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)
}
