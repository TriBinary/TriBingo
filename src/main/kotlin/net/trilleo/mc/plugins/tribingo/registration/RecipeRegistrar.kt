package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.registration.RecipeRegistrar.registerAll
import net.trilleo.mc.plugins.tribingo.registration.RecipeRegistrar.unregisterAll
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers all concrete [PluginRecipe] subclasses inside the `recipes` package
 * (and its subpackages), builds each recipe, and registers it with the server.
 *
 * All Minecraft crafting containers are supported — crafting table (shaped /
 * shapeless), furnace, blast furnace, smoker, campfire, stonecutter, and
 * smithing table — depending on the [org.bukkit.inventory.Recipe] subtype
 * returned by [PluginRecipe.build].
 *
 * Recipes registered by [registerAll] are tracked internally by their
 * [org.bukkit.NamespacedKey] and removed cleanly by [unregisterAll], which
 * prevents stale recipes from persisting across plugin reloads.
 */
object RecipeRegistrar {

    private const val RECIPES_PACKAGE = "net.trilleo.mc.plugins.trihunt.recipes"

    /** [NamespacedKey]s of every recipe successfully registered by [registerAll]. */
    private val registeredKeys = mutableListOf<NamespacedKey>()

    /**
     * Scans the recipes package, instantiates every [PluginRecipe] found,
     * builds it, and registers it with the Bukkit recipe system.
     *
     * Any previously registered recipes are removed first so that this method
     * is safe to call multiple times (e.g. on plugin reload).
     */
    fun registerAll(plugin: JavaPlugin) {
        unregisterAll()

        val recipeClasses = PackageScanner.findClasses(plugin, RECIPES_PACKAGE, PluginRecipe::class.java)

        for (recipeClass in recipeClasses) {
            try {
                val pluginRecipe = instantiate(recipeClass, plugin)
                val recipe = pluginRecipe.build(plugin)
                Bukkit.addRecipe(recipe)

                if (recipe is Keyed) {
                    registeredKeys.add(recipe.key)
                    plugin.logger.info("Registered recipe: ${recipe.key}")
                } else {
                    plugin.logger.warning(
                        "Recipe ${recipeClass.simpleName} is not Keyed; it will not be tracked for removal"
                    )
                }
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to register recipe ${recipeClass.simpleName}: ${e.message}"
                )
            }
        }

        plugin.logger.info("Registered ${registeredKeys.size} recipe(s)")
    }

    /**
     * Removes all recipes registered by [registerAll] from the server.
     *
     * Should be called from [JavaPlugin.onDisable] to prevent stale recipes
     * from persisting across plugin reloads or server restarts.
     */
    fun unregisterAll() {
        registeredKeys.forEach { key -> Bukkit.removeRecipe(key) }
        registeredKeys.clear()
    }

    /**
     * Tries to create an instance of [clazz] using a constructor that accepts
     * a [JavaPlugin]; falls back to a no-arg constructor.
     */
    private fun instantiate(clazz: Class<out PluginRecipe>, plugin: JavaPlugin): PluginRecipe {
        return try {
            clazz.getDeclaredConstructor(JavaPlugin::class.java).newInstance(plugin)
        } catch (_: NoSuchMethodException) {
            try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (_: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "${clazz.simpleName} must declare either a no-arg constructor " +
                            "or a constructor accepting a single JavaPlugin parameter"
                )
            }
        }
    }
}
