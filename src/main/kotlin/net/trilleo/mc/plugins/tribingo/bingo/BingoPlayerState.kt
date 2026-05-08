package net.trilleo.mc.plugins.tribingo.bingo

import java.util.*

/**
 * Per-player mutable state for a single [BingoGame] session.
 *
 * Tracks which cells have been completed, stores intermediate progress
 * counters for objectives that require multiple steps (e.g. "kill 5 zombies"),
 * records which lines (rows/columns/diagonals) have been completed, and
 * accumulates the player's point total.
 *
 * ### Extended state for custom objectives
 * In addition to the integer [progressData] map, two additional maps are
 * available for code objectives that need richer intermediate state:
 *
 * - [stringData] — arbitrary string values keyed by `"objectiveId:fieldName"`.
 *   Access via [getString], [setString], [removeString].
 * - [stepData] — ordered sets of completed step tokens, keyed by objective ID.
 *   Access via [getSteps], [addStep], [hasStep], [clearSteps].
 *   Used by [SequentialBingoObjective] and any objective that must track which
 *   named actions have already occurred.
 *
 * @param uuid the unique identifier of the player this state belongs to
 */
class BingoPlayerState(val uuid: UUID) {

    /**
     * Set of completed [BingoCell.cellIndex] values for this player.
     *
     * Populated by [markCompleted] and cleared by [reset].
     */
    val completedCells: MutableSet<Int> = mutableSetOf()

    /**
     * Per-objective intermediate progress counters, keyed by
     * [BingoObjective.id].
     *
     * For example, a "kill 5 zombies" objective stores the current kill count
     * here. Cleared by [reset].
     */
    val progressData: MutableMap<String, Int> = mutableMapOf()

    /**
     * Arbitrary string values for code objectives, keyed by a compound key of
     * the form `"objectiveId:fieldName"`.
     *
     * Use [getString], [setString], and [removeString] rather than accessing
     * this map directly. Cleared by [reset].
     */
    val stringData: MutableMap<String, String> = mutableMapOf()

    /**
     * Ordered sets of completed step tokens for sequential or multi-step
     * objectives, keyed by [BingoObjective.id].
     *
     * Insertion order is preserved by [LinkedHashSet]. Use [getSteps],
     * [addStep], [hasStep], and [clearSteps] rather than accessing this map
     * directly. Cleared by [reset].
     */
    val stepData: MutableMap<String, MutableSet<String>> = mutableMapOf()

    /**
     * Set of line keys that have already been awarded their bonus points.
     *
     * Keys follow the pattern `"row_N"`, `"col_N"`, `"diag_main"`, or
     * `"diag_anti"`.  Used to avoid awarding the same line bonus twice.
     * Cleared by [reset].
     */
    val completedLines: MutableSet<String> = mutableSetOf()

    /**
     * The player's current point total for this game session.
     *
     * Incremented by [BingoManager.checkCompletion] when objectives and lines
     * are completed.  Reset to `0` by [reset].
     */
    var points: Int = 0

    // ── Completion helpers ───────────────────────────────────────────────

    /** Returns `true` when [cellIndex] has been marked complete. */
    fun isCompleted(cellIndex: Int): Boolean = cellIndex in completedCells

    /** Marks [cellIndex] as complete. */
    fun markCompleted(cellIndex: Int) {
        completedCells.add(cellIndex)
    }

    // ── Integer progress helpers ─────────────────────────────────────────

    /**
     * Returns the current progress counter for the objective identified by
     * [objectiveId], or `0` if no counter has been recorded yet.
     */
    fun getProgress(objectiveId: String): Int =
        progressData.getOrDefault(objectiveId, 0)

    /**
     * Sets the progress counter for [objectiveId] to [value].
     */
    fun setProgress(objectiveId: String, value: Int) {
        progressData[objectiveId] = value
    }

    // ── String data helpers ──────────────────────────────────────────────

    /**
     * Returns the string value stored under `"[objectiveId]:[field]"`, or
     * `null` if no value has been set.
     *
     * @param objectiveId the [BingoObjective.id] that owns this field
     * @param field       the field name within the objective's namespace
     */
    fun getString(objectiveId: String, field: String): String? =
        stringData["$objectiveId:$field"]

    /**
     * Stores [value] under the compound key `"[objectiveId]:[field]"`.
     *
     * @param objectiveId the [BingoObjective.id] that owns this field
     * @param field       the field name within the objective's namespace
     * @param value       the value to store
     */
    fun setString(objectiveId: String, field: String, value: String) {
        stringData["$objectiveId:$field"] = value
    }

    /**
     * Removes the string value stored under `"[objectiveId]:[field]"`, if any.
     *
     * @param objectiveId the [BingoObjective.id] that owns this field
     * @param field       the field name within the objective's namespace
     */
    fun removeString(objectiveId: String, field: String) {
        stringData.remove("$objectiveId:$field")
    }

    // ── Step data helpers ────────────────────────────────────────────────

    /**
     * Returns the live mutable set of completed step tokens for [objectiveId],
     * creating an empty [LinkedHashSet] on the first call.
     *
     * Insertion order is preserved, making this suitable for sequential
     * objectives that track which steps have been completed so far.
     *
     * @param objectiveId the [BingoObjective.id] that owns the step set
     */
    fun getSteps(objectiveId: String): MutableSet<String> =
        stepData.getOrPut(objectiveId) { LinkedHashSet() }

    /**
     * Adds [step] to the set of completed steps for [objectiveId].
     *
     * @param objectiveId the [BingoObjective.id] that owns the step set
     * @param step        the step token to record
     * @return `true` if [step] was not already present and was successfully added;
     *         `false` if [step] was already recorded (duplicate)
     */
    fun addStep(objectiveId: String, step: String): Boolean =
        getSteps(objectiveId).add(step)

    /**
     * Returns `true` when [step] has already been recorded for [objectiveId].
     *
     * @param objectiveId the [BingoObjective.id] that owns the step set
     * @param step        the step token to check
     */
    fun hasStep(objectiveId: String, step: String): Boolean =
        stepData[objectiveId]?.contains(step) ?: false

    /**
     * Removes all step tokens recorded for [objectiveId].
     *
     * @param objectiveId the [BingoObjective.id] whose steps should be cleared
     */
    fun clearSteps(objectiveId: String) {
        stepData.remove(objectiveId)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears all completion, progress, string, step, line-bonus, and points
     * data.
     *
     * Called by [BingoGame.reset] to wipe per-player state for a new game.
     */
    fun reset() {
        completedCells.clear()
        progressData.clear()
        stringData.clear()
        stepData.clear()
        completedLines.clear()
        points = 0
    }
}
