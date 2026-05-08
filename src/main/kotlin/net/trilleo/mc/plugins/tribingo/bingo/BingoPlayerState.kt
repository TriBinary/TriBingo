package net.trilleo.mc.plugins.tribingo.bingo

import java.util.UUID

/**
 * Per-player mutable state for a single [BingoGame] session.
 *
 * Tracks which cells have been completed and stores intermediate progress
 * counters for objectives that require multiple steps (e.g. "kill 5 zombies").
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

    // ── Completion helpers ───────────────────────────────────────────────

    /** Returns `true` when [cellIndex] has been marked complete. */
    fun isCompleted(cellIndex: Int): Boolean = cellIndex in completedCells

    /** Marks [cellIndex] as complete. */
    fun markCompleted(cellIndex: Int) {
        completedCells.add(cellIndex)
    }

    // ── Progress helpers ─────────────────────────────────────────────────

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

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears all completion and progress data.
     *
     * Called by [BingoGame.reset] to wipe per-player state for a new game.
     */
    fun reset() {
        completedCells.clear()
        progressData.clear()
    }
}
