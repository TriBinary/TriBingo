package net.trilleo.mc.plugins.tribingo.enums

/**
 * Represents the lifecycle state of a [net.trilleo.mc.plugins.tribingo.bingo.BingoGame].
 *
 * Valid transitions:
 * ```
 * INACTIVE ──start()──► ACTIVE ──end()──► ENDED
 *    ▲                                       │
 *    └───────────────reset()─────────────────┘
 * ```
 *
 * | Value      | Meaning                                              |
 * |:-----------|:-----------------------------------------------------|
 * | [INACTIVE] | Game exists but has not been started yet             |
 * | [ACTIVE]   | Game is running; objectives can be completed         |
 * | [ENDED]    | Game has finished; call `reset()` to start fresh     |
 */
enum class GameState {
    INACTIVE,
    ACTIVE,
    ENDED
}
