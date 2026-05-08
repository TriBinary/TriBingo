package net.trilleo.mc.plugins.tribingo.bingo

/**
 * Factory interface for [BingoObjective] subclasses that require
 * constructor-time parameters and therefore cannot be instantiated via a
 * no-arg constructor.
 *
 * Implement this interface on the **companion object** of the objective class.
 * [net.trilleo.mc.plugins.tribingo.bingo.registry.CodeObjectiveLoader] detects
 * the companion factory via Java reflection and calls [create] instead of
 * invoking a no-arg constructor.
 *
 * ### Example
 * ```kotlin
 * @CustomObjective
 * class KillWithDiamondSwordObjective(
 *     private val requiredMaterial: Material
 * ) : MultiEventBingoObjective(
 *     id          = "kill_zombie_diamond_sword",
 *     name        = Component.text("Diamond Slayer"),
 *     description = Component.text("Kill a zombie with a diamond sword."),
 *     difficulty  = Difficulty.MEDIUM
 * ) {
 *     companion object : BingoObjectiveFactory {
 *         override fun create() = KillWithDiamondSwordObjective(Material.DIAMOND_SWORD)
 *     }
 *     // ... @EventHandler methods
 * }
 * ```
 */
interface BingoObjectiveFactory {

    /**
     * Creates and returns a fully initialised [BingoObjective] instance.
     *
     * This method is called once per startup by
     * [net.trilleo.mc.plugins.tribingo.bingo.registry.CodeObjectiveLoader].
     * Construction-time parameters should be hardcoded here or read from
     * a shared configuration object.
     */
    fun create(): BingoObjective
}
