package net.trilleo.mc.plugins.tribingo.bingo.annotation

import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.registry.CodeObjectiveLoader

/**
 * Marks a concrete [BingoObjective] subclass for automatic discovery and
 * registration by [CodeObjectiveLoader].
 *
 * Any class annotated with `@CustomObjective` that is placed in a package
 * scanned by [CodeObjectiveLoader.load] (default:
 * `net.trilleo.mc.plugins.tribingo.bingo.custom`) will be instantiated and
 * registered with [net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry]
 * at startup — no manual `register` call is needed.
 *
 * ### Construction
 * [CodeObjectiveLoader] instantiates the objective using one of two strategies
 * (in order):
 *
 * 1. **Companion factory** — if the class has a `companion object` that
 *    implements [net.trilleo.mc.plugins.tribingo.bingo.BingoObjectiveFactory],
 *    [BingoObjectiveFactory.create][net.trilleo.mc.plugins.tribingo.bingo.BingoObjectiveFactory.create]
 *    is called to produce the instance.
 * 2. **No-arg constructor** — if no companion factory is present, the loader
 *    calls the zero-argument constructor.
 *
 * If neither strategy succeeds, the class is skipped with a warning logged to
 * the console.
 *
 * ### Minimal example (no-arg constructor)
 * ```kotlin
 * @CustomObjective
 * class SleepObjective : EventBingoObjective<PlayerBedEnterEvent>(
 *     id          = "sleep_in_bed",
 *     name        = Component.text("Good Night"),
 *     description = Component.text("Sleep in a bed."),
 *     difficulty  = Difficulty.EASY,
 *     eventClass  = PlayerBedEnterEvent::class.java
 * ) { ... }
 * ```
 *
 * ### Parameterised example (companion factory)
 * ```kotlin
 * @CustomObjective
 * class KillWithDiamondSwordObjective(
 *     private val requiredMaterial: Material
 * ) : MultiEventBingoObjective(...) {
 *
 *     companion object : BingoObjectiveFactory {
 *         override fun create() = KillWithDiamondSwordObjective(Material.DIAMOND_SWORD)
 *     }
 *     ...
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomObjective
