package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.event.Listener

/**
 * An event-driven [BingoObjective] that listens to **multiple** Bukkit event
 * types simultaneously.
 *
 * Use this base class when an objective requires `@EventHandler` methods for
 * more than one event type (e.g. tracking what weapon was used on one event,
 * then detecting the kill on another). For objectives that only listen to a
 * single event type, extend [EventBingoObjective] instead.
 *
 * Concrete subclasses add any number of `@EventHandler`-annotated methods.
 * The [BingoObjectiveRegistry][net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry]
 * automatically registers every `MultiEventBingoObjective` as a Bukkit event
 * listener because this class implements [Listener].
 *
 * ### Example — kill a zombie with a sword
 * ```kotlin
 * @CustomObjective
 * class KillZombieWithSwordObjective : MultiEventBingoObjective(
 *     id          = "kill_zombie_with_sword",
 *     name        = Component.text("Undead Swordsman"),
 *     description = Component.text("Kill a zombie using any sword."),
 *     difficulty  = Difficulty.MEDIUM
 * ) {
 *     // Track the last weapon used to damage each entity (by entity UUID)
 *     private val lastWeapon = mutableMapOf<UUID, Material?>()
 *
 *     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
 *     fun onDamage(event: EntityDamageByEntityEvent) {
 *         val player = event.damager as? Player ?: return
 *         val weapon = player.inventory.itemInMainHand.type
 *         if (weapon.name.endsWith("_SWORD")) {
 *             lastWeapon[event.entity.uniqueId] = weapon
 *         }
 *     }
 *
 *     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
 *     fun onDeath(event: EntityDeathEvent) {
 *         if (event.entity.type != EntityType.ZOMBIE) return
 *         val player = event.entity.killer ?: return
 *         if (lastWeapon[event.entity.uniqueId] == null) return
 *         val game = BingoManager.currentGame ?: return
 *         if (game.state != GameState.ACTIVE) return
 *         val state = game.getOrCreateState(player.uniqueId)
 *         state.setString(id, "usedSword", "true")
 *         BingoManager.checkCompletion(player, this)
 *         lastWeapon.remove(event.entity.uniqueId)
 *     }
 *
 *     override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
 *         state.getString(id, "usedSword") == "true"
 *
 *     override fun onReset(player: Player, state: BingoPlayerState) =
 *         state.removeString(id, "usedSword")
 * }
 * ```
 */
abstract class MultiEventBingoObjective(
    id: String,
    name: Component,
    description: Component,
    difficulty: Difficulty
) : BingoObjective(id, name, description, difficulty), Listener
