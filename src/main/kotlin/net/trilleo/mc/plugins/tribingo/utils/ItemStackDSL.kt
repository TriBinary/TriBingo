package net.trilleo.mc.plugins.tribingo.utils

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * DSL builder for creating [ItemStack] instances in a concise, readable way.
 *
 * All text (display name and lore lines) is parsed through
 * [MiniMessage](https://docs.advntr.dev/minimessage/index.html), so you can
 * use MiniMessage tags such as `<bold>`, `<red>`, `<gradient>`, etc.
 *
 * Use the top-level [itemStack] function as the entry point:
 * ```kotlin
 * val sword = itemStack(Material.DIAMOND_SWORD) {
 *     name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
 *     lore("<gray>A legendary blade", "<gray>Damage: <red>+20")
 *     enchant(Enchantment.SHARPNESS, 5)
 *     unbreakable(true)
 *     flag(ItemFlag.HIDE_ENCHANTS)
 * }
 * ```
 *
 * Use [pdc] to attach [PDCUtil]-managed persistent data to the item while
 * still inside the builder block:
 * ```kotlin
 * val key = NamespacedKey(plugin, "my_key")
 * val item = itemStack(Material.DIAMOND) {
 *     name("<aqua>My Diamond")
 *     pdc(key, PersistentDataType.STRING, "custom_value")
 * }
 * ```
 *
 * For advanced use-cases not covered by the builder API, use the [meta]
 * escape hatch to modify the [ItemMeta] directly:
 * ```kotlin
 * val head = itemStack(Material.PLAYER_HEAD) {
 *     name("<yellow>Custom Head")
 *     meta {
 *         // 'this' is the ItemMeta — call any Paper API method
 *         (this as org.bukkit.inventory.meta.SkullMeta)
 *             .owningPlayer = org.bukkit.Bukkit.getOfflinePlayer("Notch")
 *     }
 * }
 * ```
 */
class ItemStackBuilder(@PublishedApi internal val material: Material) {

    private val miniMessage = MiniMessage.miniMessage()

    @PublishedApi
    internal var itemAmount: Int = 1

    @PublishedApi
    internal var displayName: String? = null

    @PublishedApi
    internal var loreLines: List<String>? = null

    @PublishedApi
    internal var enchantments: MutableMap<Enchantment, Int> = mutableMapOf()

    @PublishedApi
    internal var isUnbreakable: Boolean = false

    @PublishedApi
    internal var isHideTooltip: Boolean = false

    @PublishedApi
    internal var itemFlags: MutableList<ItemFlag> = mutableListOf()

    @PublishedApi
    internal var modelData: Int? = null

    @PublishedApi
    internal var metaBlock: (ItemMeta.() -> Unit)? = null

    @PublishedApi
    internal val pdcOperations: MutableList<(PersistentDataContainer) -> Unit> = mutableListOf()

    /**
     * Sets the display name of the item.
     * The string is parsed with MiniMessage.
     *
     * @param name the display name (supports MiniMessage tags)
     */
    fun name(name: String) {
        this.displayName = name
    }

    /**
     * Sets the lore lines of the item.
     * Each line is parsed with MiniMessage independently.
     *
     * @param lines one or more lore lines (each supports MiniMessage tags)
     */
    fun lore(vararg lines: String) {
        this.loreLines = lines.toList()
    }

    /**
     * Adds an enchantment to the item.
     *
     * @param enchantment the enchantment to apply
     * @param level       the enchantment level
     */
    fun enchant(enchantment: Enchantment, level: Int) {
        this.enchantments[enchantment] = level
    }

    /**
     * Sets whether the item is unbreakable.
     *
     * @param value `true` to make the item unbreakable
     */
    fun unbreakable(value: Boolean) {
        this.isUnbreakable = value
    }

    /**
     * Sets whether to show the tooltip.
     *
     * @param value `true` to hide the tooltip
     */
    fun hideTooltip(value: Boolean) {
        this.isHideTooltip = value
    }

    /**
     * Sets the stack size of the item.
     *
     * @param amount the number of items in the stack (1–64)
     */
    fun amount(amount: Int) {
        this.itemAmount = amount
    }

    /**
     * Adds one or more [ItemFlag]s to the item.
     *
     * @param flags the flags to add (e.g. [ItemFlag.HIDE_ENCHANTS])
     */
    fun flag(vararg flags: ItemFlag) {
        this.itemFlags.addAll(flags)
    }

    /**
     * Sets the custom model data value for the item.
     *
     * @param data the custom model data integer
     */
    fun customModelData(data: Int) {
        this.modelData = data
    }

    /**
     * Attaches a persistent data entry to the item using [PDCUtil].
     *
     * PDC entries are applied **before** the [meta] escape-hatch block, so
     * the [meta] block can still override them if needed.
     *
     * @param key   the [NamespacedKey] identifying the entry
     * @param type  the [PersistentDataType] describing how the value is stored
     * @param value the value to store
     */
    fun <P, C : Any> pdc(key: NamespacedKey, type: PersistentDataType<P, C>, value: C) {
        pdcOperations.add { container -> container.set(key, type, value) }
    }

    /**
     * Escape hatch for direct [ItemMeta] manipulation.
     *
     * The receiver block is applied to the item's meta **after** all other
     * builder properties have been set, so any changes made here take
     * precedence.
     *
     * @param block a lambda with [ItemMeta] as the receiver
     */
    fun meta(block: ItemMeta.() -> Unit) {
        this.metaBlock = block
    }

    /**
     * Builds and returns the configured [ItemStack].
     *
     * @return the fully constructed item stack
     */
    fun build(): ItemStack {
        val item = ItemStack(material, itemAmount)
        val meta = item.itemMeta ?: return item

        displayName?.let { meta.displayName(miniMessage.deserialize("<reset><i:false>$it")) }
        loreLines?.let { lines -> meta.lore(lines.map { miniMessage.deserialize("<reset><i:false>$it") }) }
        enchantments.forEach { (enchant, level) -> meta.addEnchant(enchant, level, true) }
        meta.isUnbreakable = isUnbreakable
        meta.isHideTooltip = isHideTooltip
        if (itemFlags.isNotEmpty()) meta.addItemFlags(*itemFlags.toTypedArray())
        modelData?.let { meta.setCustomModelData(it) }
        pdcOperations.forEach { it(meta.persistentDataContainer) }
        metaBlock?.invoke(meta)

        item.itemMeta = meta
        return item
    }
}

/**
 * Creates an [ItemStack] of the given [material] using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val item = itemStack(Material.GOLDEN_APPLE) {
 *     name("<gold>Enchanted Apple")
 *     amount(3)
 * }
 * ```
 *
 * @param material the material type for the item
 * @param block    the builder configuration block
 * @return the fully constructed [ItemStack]
 */
fun itemStack(material: Material, block: ItemStackBuilder.() -> Unit): ItemStack {
    return ItemStackBuilder(material).apply(block).build()
}
