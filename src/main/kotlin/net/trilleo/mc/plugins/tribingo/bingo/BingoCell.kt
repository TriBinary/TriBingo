package net.trilleo.mc.plugins.tribingo.bingo

/**
 * An immutable value type representing a single cell on a [BingoBoard].
 *
 * Completion state is **not** stored here; it lives in the per-player
 * [BingoPlayerState] keyed by [cellIndex].
 *
 * @param cellIndex zero-based flat index within the board (row-major order);
 *                  used as the key in [BingoPlayerState.completedCells]
 * @param objective the objective that must be fulfilled to mark this cell
 */
data class BingoCell(
    val cellIndex: Int,
    val objective: BingoObjective
)
