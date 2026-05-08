package net.trilleo.mc.plugins.tribingo.bingo.registry

import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjectiveFactory
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.registration.PackageScanner
import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers and registers [BingoObjective] subclasses that are annotated with
 * [@CustomObjective][CustomObjective] from one or more designated packages.
 *
 * This loader is the code-objective counterpart to [YamlObjectiveLoader]:
 * instead of parsing `bingo_objectives.yml`, it scans the plugin JAR for
 * concrete classes and instantiates them automatically.
 *
 * ### Lifecycle
 * Call [load] **after** [BingoObjectiveRegistry.init] and **before**
 * [YamlObjectiveLoader.load] so that code-defined objectives are registered
 * first and YAML IDs cannot silently shadow code-defined IDs:
 *
 * ```kotlin
 * BingoObjectiveRegistry.init(plugin)
 * CodeObjectiveLoader.load(plugin, BingoObjectiveRegistry,
 *     "net.trilleo.mc.plugins.tribingo.bingo.custom")
 * YamlObjectiveLoader.load(plugin, BingoObjectiveRegistry)
 * BingoManager.init(plugin)
 * ```
 *
 * ### Instantiation strategies
 * For each discovered class (in order of preference):
 *
 * 1. **Companion factory** — if the class has a `companion object` that
 *    implements [BingoObjectiveFactory], [BingoObjectiveFactory.create] is
 *    called. Use this for objectives that require constructor-time parameters.
 * 2. **No-arg constructor** — if no factory companion is found, the loader
 *    invokes the zero-argument constructor. Use this for objectives whose
 *    parameters are hardcoded in the class body.
 *
 * If neither strategy succeeds, the class is skipped and a warning is logged.
 */
object CodeObjectiveLoader {

    /**
     * Scans [packageNames] for concrete [BingoObjective] subclasses that carry
     * the [@CustomObjective][CustomObjective] annotation, instantiates each
     * one, and registers it with [registry].
     *
     * @param plugin       the owning plugin (used for JAR scanning and logging)
     * @param registry     the registry to populate
     * @param packageNames one or more package names to scan; defaults to
     *                     `net.trilleo.mc.plugins.tribingo.bingo.custom`
     */
    fun load(
        plugin: JavaPlugin,
        registry: BingoObjectiveRegistry,
        vararg packageNames: String = arrayOf("net.trilleo.mc.plugins.tribingo.bingo.custom")
    ) {
        var count = 0

        for (packageName in packageNames) {
            val classes = PackageScanner.findClasses(
                plugin, packageName, BingoObjective::class.java
            )

            for (clazz in classes) {
                if (!clazz.isAnnotationPresent(CustomObjective::class.java)) continue

                val objective = tryInstantiate(clazz, plugin) ?: continue
                registry.register(objective)
                count++
            }
        }

        plugin.logger.info("[CodeObjectiveLoader] Loaded $count code objective(s)")
    }

    // ── Instantiation ──────────────────────────────────────────────────────

    /**
     * Attempts to create an instance of [clazz] using:
     * 1. A companion object that implements [BingoObjectiveFactory].
     * 2. A public no-arg constructor.
     *
     * Returns `null` and logs a warning when neither strategy succeeds.
     */
    private fun tryInstantiate(
        clazz: Class<out BingoObjective>,
        plugin: JavaPlugin
    ): BingoObjective? {
        // Strategy 1: companion factory
        tryCompanionFactory(clazz)?.let { return it }

        // Strategy 2: no-arg constructor
        return runCatching {
            clazz.getDeclaredConstructor().newInstance()
        }.onFailure {
            plugin.logger.warning(
                "[CodeObjectiveLoader] Cannot instantiate ${clazz.simpleName}: " +
                        "no no-arg constructor and no companion BingoObjectiveFactory found. " +
                        "Add a no-arg constructor or implement BingoObjectiveFactory on the companion object."
            )
        }.getOrNull()
    }

    /**
     * Checks whether [clazz] has a `companion object` that implements
     * [BingoObjectiveFactory] via Java reflection (no `kotlin-reflect` required).
     *
     * In Kotlin, a companion object is compiled to a static field named
     * `"Companion"` on the enclosing class. If that field's value implements
     * [BingoObjectiveFactory], its [BingoObjectiveFactory.create] method is
     * called and the resulting objective is returned.
     *
     * `setAccessible(true)` is called on the field because the `Companion`
     * field's modifier may be package-private in some compilation contexts.
     * This is safe for classes in the plugin's own JAR.
     *
     * @return the newly created objective, or `null` if no factory companion exists
     */
    private fun tryCompanionFactory(clazz: Class<out BingoObjective>): BingoObjective? {
        return runCatching {
            val companionField = clazz.getDeclaredField("Companion")
            companionField.setAccessible(true)
            val companion = companionField.get(null)
            (companion as? BingoObjectiveFactory)?.create()
        }.getOrNull()
    }
}
