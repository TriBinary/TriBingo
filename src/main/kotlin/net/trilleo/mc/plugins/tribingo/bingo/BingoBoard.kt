package net.trilleo.mc.plugins.tribingo.bingo

/**
 * An N×N Bingo board (N in `3..6`) backed by a flat, row-major list of [BingoCell]s.
 *
 * The board is immutable after construction; swapping objectives requires
 * replacing the board via [BingoGame.refresh].
 *
 * ### Coordinate system
 * Cell `(row, col)` maps to `cells[row * size + col]`.
 *
 * @param size  side-length of the square board; must be in `3..6`
 * @param cells flat list of exactly `size × size` cells in row-major order
 */
class BingoBoard(
    val size: Int,
    val cells: List<BingoCell>
) {

    init {
        require(size in 3..6) {
            "Board size must be between 3 and 6, got $size"
        }
        require(cells.size == size * size) {
            "Expected ${size * size} cells for a ${size}x${size} board, got ${cells.size}"
        }
    }

    /**
     * Returns the [BingoCell] at the given [row] and [col].
     *
     * @param row zero-based row index (`0` until [size])
     * @param col zero-based column index (`0` until [size])
     */
    fun getCell(row: Int, col: Int): BingoCell {
        require(row in 0 until size) { "Row $row out of range [0, $size)" }
        require(col in 0 until size) { "Col $col out of range [0, $size)" }
        return cells[row * size + col]
    }

    /**
     * Returns `true` when [state] has at least one complete row, column,
     * or diagonal.
     *
     * @param state the per-player state to evaluate
     */
    fun isLineComplete(state: BingoPlayerState): Boolean {
        // Rows
        for (row in 0 until size) {
            if ((0 until size).all { col -> state.isCompleted(getCell(row, col).cellIndex) }) return true
        }
        // Columns
        for (col in 0 until size) {
            if ((0 until size).all { row -> state.isCompleted(getCell(row, col).cellIndex) }) return true
        }
        // Main diagonal (top-left → bottom-right)
        if ((0 until size).all { i -> state.isCompleted(getCell(i, i).cellIndex) }) return true
        // Anti-diagonal (top-right → bottom-left)
        if ((0 until size).all { i -> state.isCompleted(getCell(i, size - 1 - i).cellIndex) }) return true

        return false
    }

    /**
     * Returns `true` when every cell on the board has been completed by [state].
     *
     * @param state the per-player state to evaluate
     */
    fun isBoardFull(state: BingoPlayerState): Boolean =
        cells.all { state.isCompleted(it.cellIndex) }
}
