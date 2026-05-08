package net.trilleo.mc.plugins.tribingo.registration

import org.bukkit.plugin.java.JavaPlugin

/**
 * Discovers all [PluginItem] subclasses (including Kotlin `object` singletons)
 * inside the `items` package (and its subpackages) and registers them in an
 * in-memory registry keyed by [PluginItem.id].
 *
 * Both Kotlin `object` declarations and regular classes are supported:
 *
 * * **Kotlin objects** — resolved via the compiler-generated `INSTANCE` field;
 *   no constructor call is needed.
 * * **Regular classes** — instantiated using a constructor that accepts a
 *   [JavaPlugin], or a no-arg constructor as a fallback.
 *
 * Items can also be referenced **directly by class/object name** in other code
 * (e.g. in recipe files) without going through the registry. The registry is
 * useful when you need to look up an item by its string ID at runtime.
 */
object ItemRegistrar {

    private const val ITEMS_PACKAGE = "net.trilleo.mc.plugins.tribingo.items"

    /** All registered items keyed by their [PluginItem.id]. */
    private val items = mutableMapOf<String, PluginItem>()

    /**
     * Scans the items package, resolves every [PluginItem] instance found,
     * and stores it in the registry.
     *
     * Duplicate IDs are logged as warnings and the second class is skipped.
     */
    fun registerAll(plugin: JavaPlugin) {
        items.clear()

        val itemClasses = PackageScanner.findClasses(plugin, ITEMS_PACKAGE, PluginItem::class.java)

        for (itemClass in itemClasses) {
            try {
                val item = resolveInstance(itemClass, plugin)
                if (items.containsKey(item.id)) {
                    plugin.logger.warning(
                        "Duplicate custom item ID '${item.id}' — skipping ${itemClass.simpleName}"
                    )
                    continue
                }
                items[item.id] = item
                plugin.logger.info("Registered custom item: ${item.id} (${itemClass.simpleName})")
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to register item ${itemClass.simpleName}: ${e.message}"
                )
            }
        }

        plugin.logger.info("Registered ${items.size} custom item(s)")
    }

    /**
     * Returns the [PluginItem] registered under [id], or `null` if no item
     * with that ID has been registered.
     *
     * @param id the unique item ID (as declared in [PluginItem.id])
     */
    fun get(id: String): PluginItem? = items[id]

    /**
     * Returns an unmodifiable snapshot of all registered items.
     */
    fun getAll(): Collection<PluginItem> = items.values.toList()

    /**
     * Resolves a [PluginItem] instance from [clazz].
     *
     * Priority order:
     * 1. Kotlin object `INSTANCE` field
     * 2. Constructor accepting a [JavaPlugin]
     * 3. No-arg constructor
     */
    private fun resolveInstance(clazz: Class<out PluginItem>, plugin: JavaPlugin): PluginItem {
        // Kotlin object singleton
        try {
            val field = clazz.getDeclaredField("INSTANCE")
            if (field.trySetAccessible()) {
                return field.get(null) as PluginItem
            }
        } catch (_: NoSuchFieldException) {
        }

        // Constructor accepting JavaPlugin
        try {
            return clazz.getDeclaredConstructor(JavaPlugin::class.java).newInstance(plugin)
        } catch (_: NoSuchMethodException) {
        }

        // No-arg constructor
        return try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (_: NoSuchMethodException) {
            throw IllegalArgumentException(
                "${clazz.simpleName} must be a Kotlin object, or declare either a no-arg constructor " +
                        "or a constructor accepting a single JavaPlugin parameter"
            )
        }
    }
}
