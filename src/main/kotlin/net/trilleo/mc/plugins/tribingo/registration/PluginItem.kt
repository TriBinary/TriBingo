package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.registration.PluginItem.Companion.ITEM_ID_KEY
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.persistence.PersistentDataType

/**
 * Base class for all custom plugin items.
 *
 * Extend this class (or declare a Kotlin `object`) and place it anywhere inside
 * the `net.trilleo.mc.plugins.trihunt.items` package (or a subpackage). The item is
 * automatically discovered by [ItemRegistrar] at startup.
 *
 * Every stack produced by [create] has the item's [id] embedded in its
 * [org.bukkit.persistence.PersistentDataContainer] under [ITEM_ID_KEY]. This
 * marker is used by [matches] and [asChoice] to identify the item in recipes
 * and inventory checks.
 *
 * Prefer declaring items as **Kotlin `object`s** (singletons) so they can be
 * referenced directly in recipe files:
 *
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.items
 *
 * import net.trilleo.mc.plugins.trihunt.registration.PluginItem
 * import net.trilleo.mc.plugins.trihunt.utils.itemStack
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * object ExcaliburItem : PluginItem("excalibur") {
 *     override fun buildItem(amount: Int): ItemStack = itemStack(Material.DIAMOND_SWORD) {
 *         amount(amount)
 *         name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
 *         lore("<gray>A legendary blade.")
 *     }
 * }
 * ```
 *
 * @param id a unique, lower-case string identifier for this item (e.g. `"excalibur"`).
 *           The ID is stored in the item's PDC and must not change once used in
 *           production data.
 */
abstract class PluginItem(val id: String) {

    companion object {
        /**
         * The PDC key stamped onto every stack created by [create].
         * The value stored under this key is the item's [id].
         *
         * Namespace `tribingo`, key `custom_item_id`.
         */
        @JvmField
        val ITEM_ID_KEY: NamespacedKey = NamespacedKey.fromString("tribingo:custom_item_id")!!
    }

    /**
     * Creates an [ItemStack] for this item with the given [amount].
     *
     * The item's [id] is automatically embedded in the stack's PDC under
     * [ITEM_ID_KEY] after [buildItem] returns.
     *
     * @param amount the number of items in the stack (1–64); defaults to `1`
     * @return the fully configured, ID-stamped [ItemStack]
     */
    fun create(amount: Int = 1): ItemStack {
        val stack = buildItem(amount)
        val meta = stack.itemMeta ?: return stack
        meta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, id)
        stack.itemMeta = meta
        return stack
    }

    /**
     * Override to define the item's material, name, lore, enchantments, etc.
     *
     * Use the [net.trilleo.mc.plugins.tribingo.utils.itemStack] DSL for concise item
     * creation. **Do not** add the custom-item PDC entry here — [create] does
     * that automatically after this method returns.
     *
     * @param amount the requested stack size
     * @return the configured [ItemStack] (without the custom-item PDC entry)
     */
    protected abstract fun buildItem(amount: Int): ItemStack

    /**
     * Returns `true` if [stack] was produced by this item — that is, its PDC
     * contains [id] under [ITEM_ID_KEY].
     *
     * @param stack the [ItemStack] to inspect
     */
    fun matches(stack: ItemStack): Boolean {
        val meta = stack.itemMeta ?: return false
        return meta.persistentDataContainer.get(ITEM_ID_KEY, PersistentDataType.STRING) == id
    }

    /**
     * Returns a [RecipeChoice.ExactChoice] that matches any stack produced by
     * this item. Use this when specifying this item as a recipe ingredient:
     *
     * ```kotlin
     * recipe.setIngredient('E', ExcaliburItem.asChoice())
     * ```
     *
     * Or use [PluginRecipe.customChoice] inside a recipe's [PluginRecipe.build]
     * method, which delegates to this function.
     */
    fun asChoice(): RecipeChoice.ExactChoice = RecipeChoice.ExactChoice(create(1))
}
