package net.trilleo.mc.plugins.tribingo.utils

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType

/**
 * Singleton utility for reading and writing values in
 * [PersistentDataContainer]s attached to [Entity], [org.bukkit.Chunk],
 * and [ItemStack] instances.
 *
 * Both [Entity] and [org.bukkit.Chunk] implement [PersistentDataHolder]
 * directly, so a single set of methods handles them. [ItemStack] requires
 * special handling because its PDC lives inside its [org.bukkit.inventory.meta.ItemMeta]
 * and the meta must be written back to the item after every mutation.
 *
 * ### Quick-start (Entity / Chunk)
 *
 * ```kotlin
 * import net.trilleo.mc.plugins.trihunt.utils.PDCUtil
 * import org.bukkit.NamespacedKey
 * import org.bukkit.persistence.PersistentDataType
 * import org.bukkit.plugin.java.JavaPlugin
 *
 * val key = NamespacedKey(plugin, "my_key")
 *
 * // Set
 * PDCUtil.set(entity, key, PersistentDataType.STRING, "hello")
 *
 * // Get (returns null when absent)
 * val value: String? = PDCUtil.get(entity, key, PersistentDataType.STRING)
 *
 * // Check presence
 * val exists: Boolean = PDCUtil.has(entity, key)
 *
 * // Remove
 * PDCUtil.remove(entity, key)
 *
 * // List all keys
 * val keys: Set<NamespacedKey> = PDCUtil.keys(entity)
 * ```
 *
 * ### Quick-start (ItemStack)
 *
 * ```kotlin
 * PDCUtil.set(item, key, PersistentDataType.INTEGER, 42)
 * val count: Int? = PDCUtil.get(item, key, PersistentDataType.INTEGER)
 * PDCUtil.remove(item, key)
 * ```
 *
 * ### Quick-start (ItemStack DSL)
 *
 * When building items with the [itemStack] DSL, use the `pdc` builder
 * method to attach PDC entries without leaving the builder block:
 *
 * ```kotlin
 * val key = NamespacedKey(plugin, "my_key")
 *
 * val item = itemStack(Material.DIAMOND) {
 *     name("<aqua>My Diamond")
 *     pdc(key, PersistentDataType.STRING, "custom_value")
 * }
 * ```
 */
object PDCUtil {

    // ── PersistentDataHolder (Entity, Chunk) ──────────────────────────────

    /**
     * Stores [value] under [key] in [holder]'s persistent data container.
     *
     * @param holder the [PersistentDataHolder] to write to (e.g. an [Entity] or [org.bukkit.Chunk])
     * @param key    the [NamespacedKey] identifying the entry
     * @param type   the [PersistentDataType] describing how the value is stored
     * @param value  the value to store
     */
    fun <P : Any, C : Any> set(
        holder: PersistentDataHolder,
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
        value: C
    ) {
        holder.persistentDataContainer.set(key, type, value)
    }

    /**
     * Returns the value stored under [key] in [holder]'s persistent data container,
     * or `null` if the key is absent or has a different type.
     *
     * @param holder the [PersistentDataHolder] to read from
     * @param key    the [NamespacedKey] to look up
     * @param type   the [PersistentDataType] expected for the stored value
     * @return the stored value, or `null` if not present
     */
    fun <P : Any, C : Any> get(holder: PersistentDataHolder, key: NamespacedKey, type: PersistentDataType<P, C>): C? =
        holder.persistentDataContainer.get(key, type)

    /**
     * Returns `true` if [holder]'s persistent data container contains an
     * entry for [key].
     *
     * @param holder the [PersistentDataHolder] to inspect
     * @param key    the [NamespacedKey] to check for
     */
    fun has(holder: PersistentDataHolder, key: NamespacedKey): Boolean =
        holder.persistentDataContainer.has(key)

    /**
     * Removes the entry identified by [key] from [holder]'s persistent data
     * container. Does nothing if the key is absent.
     *
     * @param holder the [PersistentDataHolder] to modify
     * @param key    the [NamespacedKey] of the entry to remove
     */
    fun remove(holder: PersistentDataHolder, key: NamespacedKey) {
        holder.persistentDataContainer.remove(key)
    }

    /**
     * Returns an immutable snapshot of all keys stored in [holder]'s
     * persistent data container.
     *
     * @param holder the [PersistentDataHolder] to inspect
     * @return a [Set] of every [NamespacedKey] currently present
     */
    fun keys(holder: PersistentDataHolder): Set<NamespacedKey> =
        holder.persistentDataContainer.keys.toSet()

    // ── ItemStack ─────────────────────────────────────────────────────────

    /**
     * Stores [value] under [key] in [item]'s persistent data container.
     *
     * The [ItemStack]'s meta is updated in place after the write.
     *
     * @param item  the [ItemStack] to write to
     * @param key   the [NamespacedKey] identifying the entry
     * @param type  the [PersistentDataType] describing how the value is stored
     * @param value the value to store
     */
    fun <P : Any, C : Any> set(item: ItemStack, key: NamespacedKey, type: PersistentDataType<P, C>, value: C) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(key, type, value)
        item.itemMeta = meta
    }

    /**
     * Returns the value stored under [key] in [item]'s persistent data
     * container, or `null` if the key is absent, the item has no meta, or
     * the stored type does not match.
     *
     * @param item the [ItemStack] to read from
     * @param key  the [NamespacedKey] to look up
     * @param type the [PersistentDataType] expected for the stored value
     * @return the stored value, or `null` if not present
     */
    fun <P : Any, C : Any> get(item: ItemStack, key: NamespacedKey, type: PersistentDataType<P, C>): C? =
        item.itemMeta?.persistentDataContainer?.get(key, type)

    /**
     * Returns `true` if [item]'s persistent data container contains an
     * entry for [key].
     *
     * @param item the [ItemStack] to inspect
     * @param key  the [NamespacedKey] to check for
     */
    fun has(item: ItemStack, key: NamespacedKey): Boolean =
        item.itemMeta?.persistentDataContainer?.has(key) ?: false

    /**
     * Removes the entry identified by [key] from [item]'s persistent data
     * container. Does nothing if the key is absent or the item has no meta.
     *
     * The [ItemStack]'s meta is updated in place after the removal.
     *
     * @param item the [ItemStack] to modify
     * @param key  the [NamespacedKey] of the entry to remove
     */
    fun remove(item: ItemStack, key: NamespacedKey) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.remove(key)
        item.itemMeta = meta
    }

    /**
     * Returns an immutable snapshot of all keys stored in [item]'s
     * persistent data container, or an empty set if the item has no meta.
     *
     * @param item the [ItemStack] to inspect
     * @return a [Set] of every [NamespacedKey] currently present
     */
    fun keys(item: ItemStack): Set<NamespacedKey> =
        item.itemMeta?.persistentDataContainer?.keys?.toSet() ?: emptySet()
}
