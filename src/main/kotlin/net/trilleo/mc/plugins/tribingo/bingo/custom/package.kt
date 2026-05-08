/**
 * Designated package for plugin-internal code objectives.
 *
 * Place any concrete [net.trilleo.mc.plugins.tribingo.bingo.BingoObjective]
 * subclasses here and annotate them with
 * [@CustomObjective][net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective].
 * [net.trilleo.mc.plugins.tribingo.bingo.registry.CodeObjectiveLoader] scans
 * this package automatically at startup, so no manual registration is needed.
 *
 * ### Quick-start checklist
 * 1. Choose the right base class:
 *    - [net.trilleo.mc.plugins.tribingo.bingo.BingoObjective] — snapshot-based
 *      (no events, completion checked on demand).
 *    - [net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective] — reacts to
 *      exactly one Bukkit event type.
 *    - [net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective] — reacts
 *      to multiple Bukkit event types.
 *    - [net.trilleo.mc.plugins.tribingo.bingo.SequentialBingoObjective] — tracks
 *      an ordered sequence of steps.
 * 2. Annotate the class with `@CustomObjective`.
 * 3. Provide either a no-arg constructor or a companion object implementing
 *    [net.trilleo.mc.plugins.tribingo.bingo.BingoObjectiveFactory].
 * 4. See `BINGO_GUIDE.md` → "Writing a Code Objective" for full documentation
 *    and worked examples.
 */
package net.trilleo.mc.plugins.tribingo.bingo.custom
