package net.trilleo.mc.plugins.tribingo.bingo.registry

import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.objectives.*
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Loads user-defined [BingoObjective] instances from `bingo_objectives.yml`
 * in the plugin's data folder and registers them with [BingoObjectiveRegistry].
 *
 * ### File format
 * ```yaml
 * objectives:
 *   - id: kill_creeper
 *     type: kill_entity
 *     difficulty: EASY
 *     name: "<yellow>Creeper Killer"
 *     description: "<gray>Kill a Creeper."
 *     entity_type: CREEPER
 *     count: 1
 * ```
 *
 * ### Supported types
 * | `type`             | Parameters                                                   |
 * |:-------------------|:-------------------------------------------------------------|
 * | `kill_entity`      | `entity_type` (EntityType name), `count`                     |
 * | `mine_block`       | `material` (Material name), `count`                          |
 * | `place_block`      | `material` (Material name), `count`                          |
 * | `craft_item`       | `material` (Material name, optional), `count`                |
 * | `fish_item`        | `count`, `count_all` (boolean, optional)                     |
 * | `eat_food`         | `material` (Material name, optional), `count`                |
 * | `enchant_item`     | `enchantment` (Enchantment key, optional), `count`           |
 * | `travel_distance`  | `blocks`                                                     |
 * | `breed_mob`        | `entity_type` (EntityType name, optional), `count`           |
 * | `tame_entity`      | `entity_type` (EntityType name, optional), `count`           |
 *
 * ### Custom types
 * Additional types can be registered at runtime before [load] is called:
 * ```kotlin
 * YamlObjectiveLoader.registerTypeHandler("my_type") { entry ->
 *     MyObjective(entry.str("id"), ...)
 * }
 * ```
 */
object YamlObjectiveLoader {

    private val mm = MiniMessage.miniMessage()

    /** Registered type-handler factories keyed by the YAML `type` string. */
    private val typeHandlers = mutableMapOf<String, (Map<String, Any>) -> BingoObjective>()

    init {
        registerBuiltinHandlers()
    }

    /**
     * Registers a custom type handler.
     *
     * @param type    the value of the `type` field in YAML that triggers this handler
     * @param handler factory that receives the raw YAML map and returns a [BingoObjective]
     */
    fun registerTypeHandler(type: String, handler: (Map<String, Any>) -> BingoObjective) {
        typeHandlers[type] = handler
    }

    /**
     * Saves the bundled default `bingo_objectives.yml` (if not already present),
     * parses it, creates objective instances via the registered type handlers,
     * and registers them all with [registry].
     *
     * @param plugin   the owning plugin (used for resource saving and logging)
     * @param registry the registry to populate
     */
    fun load(plugin: JavaPlugin, registry: BingoObjectiveRegistry) {
        plugin.saveResource("bingo_objectives.yml", false)

        val file = File(plugin.dataFolder, "bingo_objectives.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        @Suppress("UNCHECKED_CAST")
        val list = config.getList("objectives") as? List<*> ?: run {
            plugin.logger.warning("[YamlObjectiveLoader] 'objectives' key missing or not a list in bingo_objectives.yml")
            return
        }

        var count = 0
        for (raw in list) {
            @Suppress("UNCHECKED_CAST")
            val entry = raw as? Map<String, Any> ?: continue
            val type = entry.str("type")
            if (type.isBlank()) {
                plugin.logger.warning("[YamlObjectiveLoader] Entry missing 'type' field — skipping")
                continue
            }
            val handler = typeHandlers[type]
            if (handler == null) {
                plugin.logger.warning("[YamlObjectiveLoader] Unknown objective type '$type' — skipping")
                continue
            }
            runCatching {
                val objective = handler(entry)
                registry.register(objective)
                count++
            }.onFailure { e ->
                plugin.logger.warning(
                    "[YamlObjectiveLoader] Failed to create objective (id=${entry.str("id")}): " +
                            "[${e.javaClass.simpleName}] ${e.message}"
                )
            }
        }

        plugin.logger.info("[YamlObjectiveLoader] Loaded $count objective(s) from bingo_objectives.yml")
    }

    // ── Built-in type handlers ───────────────────────────────────────────

    private fun registerBuiltinHandlers() {

        typeHandlers["kill_entity"] = { e ->
            KillEntityObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                entityType = EntityType.valueOf(e.requireStr("entity_type").uppercase()),
                count = e.int("count", 1)
            )
        }

        typeHandlers["mine_block"] = { e ->
            MineBlockObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                material = Material.valueOf(e.requireStr("material").uppercase()),
                count = e.int("count", 1)
            )
        }

        typeHandlers["place_block"] = { e ->
            PlaceBlockObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                material = Material.valueOf(e.requireStr("material").uppercase()),
                count = e.int("count", 1)
            )
        }

        typeHandlers["craft_item"] = { e ->
            val mat = e.str("material").takeIf { it.isNotBlank() }
                ?.let { Material.valueOf(it.uppercase()) }
            CraftItemObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                material = mat,
                count = e.int("count", 1)
            )
        }

        typeHandlers["fish_item"] = { e ->
            FishItemObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                count = e.int("count", 1),
                countAll = e.bool("count_all", false)
            )
        }

        typeHandlers["eat_food"] = { e ->
            val mat = e.str("material").takeIf { it.isNotBlank() }
                ?.let { Material.valueOf(it.uppercase()) }
            EatFoodObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                material = mat,
                count = e.int("count", 1)
            )
        }

        typeHandlers["enchant_item"] = { e ->
            val enchantKey = e.str("enchantment").takeIf { it.isNotBlank() }
            val enchantment = if (enchantKey != null) {
                val key = org.bukkit.NamespacedKey.minecraft(enchantKey.lowercase())
                val result = org.bukkit.Registry.ENCHANTMENT.get(key)
                if (result == null) {
                    throw IllegalArgumentException("Unknown enchantment key '$enchantKey'")
                }
                result
            } else null
            EnchantItemObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                enchantment = enchantment,
                count = e.int("count", 1)
            )
        }

        typeHandlers["travel_distance"] = { e ->
            TravelDistanceObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                blocks = e.int("blocks", 100)
            )
        }

        typeHandlers["breed_mob"] = { e ->
            BreedMobObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                entityTypeName = e.str("entity_type").takeIf { it.isNotBlank() },
                count = e.int("count", 1)
            )
        }

        typeHandlers["tame_entity"] = { e ->
            TameEntityObjective(
                id = e.requireStr("id"),
                name = mm.deserialize(e.str("name", e.str("id"))),
                description = mm.deserialize(e.str("description")),
                difficulty = e.difficulty(),
                entityTypeName = e.str("entity_type").takeIf { it.isNotBlank() },
                count = e.int("count", 1)
            )
        }
    }

    // ── Map access helpers ────────────────────────────────────────────────

    private fun Map<String, Any>.str(key: String, default: String = ""): String =
        this[key]?.toString() ?: default

    private fun Map<String, Any>.requireStr(key: String): String =
        this[key]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Required field '$key' is missing or blank")

    private fun Map<String, Any>.int(key: String, default: Int = 0): Int =
        (this[key] as? Number)?.toInt() ?: default

    private fun Map<String, Any>.bool(key: String, default: Boolean = false): Boolean =
        (this[key] as? Boolean) ?: default

    private fun Map<String, Any>.difficulty(): Difficulty =
        runCatching { Difficulty.valueOf(str("difficulty", "EASY").uppercase()) }
            .getOrDefault(Difficulty.EASY)
}
