package net.trilleo.mc.plugins.tribingo.registration

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.plugin.java.JavaPlugin

/**
 * Base class for all plugin recipes.
 *
 * Extend this class and place the subclass anywhere inside the
 * `net.trilleo.mc.plugins.trihunt.recipes` package (or any subpackage) to have it
 * automatically discovered and registered at startup by [RecipeRegistrar].
 *
 * All Minecraft crafting containers are supported depending on the
 * [org.bukkit.inventory.Recipe] subtype returned by [build]:
 *
 * | Container         | Recipe type                     |
 * |:------------------|:--------------------------------|
 * | Crafting table / player 2×2 | `ShapedRecipe` / `ShapelessRecipe`    |
 * | Furnace           | `FurnaceRecipe`                 |
 * | Blast furnace     | `BlastingRecipe`                |
 * | Smoker            | `SmokingRecipe`                 |
 * | Campfire          | `CampfireRecipe`                |
 * | Stonecutter       | `StonecuttingRecipe`            |
 * | Smithing table    | `SmithingTransformRecipe`       |
 *
 * Both vanilla items and plugin custom items (defined in the `items` package)
 * can be used as ingredients via the [vanillaChoice] and [customChoice] helpers.
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter.
 *
 * Example (shaped crafting recipe using both vanilla and custom ingredients):
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.recipes
 *
 * import net.trilleo.mc.plugins.trihunt.items.ExampleItem
 * import net.trilleo.mc.plugins.trihunt.registration.PluginRecipe
 * import org.bukkit.Material
 * import org.bukkit.NamespacedKey
 * import org.bukkit.inventory.Recipe
 * import org.bukkit.inventory.ShapedRecipe
 * import org.bukkit.plugin.java.JavaPlugin
 *
 * class ExampleRecipe : PluginRecipe("example_recipe") {
 *     override fun build(plugin: JavaPlugin): Recipe {
 *         val recipe = ShapedRecipe(namespacedKey(plugin), ExampleItem.create())
 *         recipe.shape("GGG", "GNG", "GGG")
 *         recipe.setIngredient('G', vanillaChoice(Material.GOLD_INGOT))
 *         recipe.setIngredient('N', vanillaChoice(Material.NETHER_STAR))
 *         return recipe
 *     }
 * }
 * ```
 *
 * @param key the unique name used to build this recipe's [NamespacedKey] via
 *            [namespacedKey]. Must be lower-case alphanumeric with underscores
 *            (e.g. `"excalibur_recipe"`).
 */
abstract class PluginRecipe(val key: String) {

    /**
     * Build and return the Bukkit [Recipe] to register.
     *
     * Use [namespacedKey] to create the recipe's [NamespacedKey], and use
     * [vanillaChoice] / [customChoice] to create [RecipeChoice] instances for
     * vanilla and custom-item ingredients.
     *
     * @param plugin the plugin instance, needed for [NamespacedKey] creation
     * @return the fully configured [Recipe]
     */
    abstract fun build(plugin: JavaPlugin): Recipe

    /**
     * Returns a [NamespacedKey] scoped to [plugin] with name [key].
     *
     * Use this inside [build] to set the recipe's key:
     * ```kotlin
     * val recipe = ShapedRecipe(namespacedKey(plugin), result)
     * ```
     */
    protected fun namespacedKey(plugin: JavaPlugin): NamespacedKey = NamespacedKey(plugin, key)

    /**
     * Returns a [RecipeChoice.MaterialChoice] that matches any [org.bukkit.inventory.ItemStack]
     * made of [material]. Use this for vanilla ingredient slots.
     *
     * @param material the vanilla [Material] to match
     */
    protected fun vanillaChoice(material: Material): RecipeChoice.MaterialChoice =
        RecipeChoice.MaterialChoice(material)

    /**
     * Returns a [RecipeChoice.ExactChoice] that matches only stacks created by
     * [item] (identified by the custom-item PDC marker). Use this when a
     * recipe ingredient must be a specific plugin custom item.
     *
     * Delegates to [PluginItem.asChoice].
     *
     * @param item the [PluginItem] whose stacks should be matched
     */
    protected fun customChoice(item: PluginItem): RecipeChoice.ExactChoice = item.asChoice()
}
