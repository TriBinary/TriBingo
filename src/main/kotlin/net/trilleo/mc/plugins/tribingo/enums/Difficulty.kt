package net.trilleo.mc.plugins.tribingo.enums

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * The difficulty label assigned to a [net.trilleo.mc.plugins.tribingo.bingo.BingoObjective].
 *
 * Difficulties are purely informational at this stage. A future board-randomizer
 * may use them to control which objectives are selected for a given game.
 *
 * | Value    | Meaning                              |
 * |:---------|:-------------------------------------|
 * | [EASY]   | Quick or trivial to complete         |
 * | [MEDIUM] | Requires moderate effort             |
 * | [HARD]   | Challenging or time-consuming        |
 * | [INSANE] | Extremely rare or difficult to achieve |
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    INSANE;

    /**
     * Returns a coloured [Component] representation of this difficulty suitable
     * for use in item lore or chat messages.
     */
    fun displayName(): Component = when (this) {
        EASY   -> Component.text("Easy",   NamedTextColor.GREEN)
        MEDIUM -> Component.text("Medium", NamedTextColor.YELLOW)
        HARD   -> Component.text("Hard",   NamedTextColor.RED)
        INSANE -> Component.text("Insane", NamedTextColor.DARK_RED)
    }
}
