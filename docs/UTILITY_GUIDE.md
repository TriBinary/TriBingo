# ExamplePlugin - Utility Guide

This guide covers the utility helpers provided in `com.example.exampleplugin.utils`. Each utility is designed to
reduce boilerplate and provide commonly needed functionality out of the box.

| Utility         | Description                                                        |
|:----------------|:-------------------------------------------------------------------|
| `itemStack`     | DSL builder for creating `ItemStack` instances concisely           |
| `CountdownUtil` | Per-player countdown with configurable display and sound           |
| `TeamUtil`      | Custom team management with server-data persistence                |
| `TagUtil`       | Per-player string tag management with player-data persistence      |
| `MessageUtil`   | Prefix-decorated message sender for players                        |
| `PDCUtil`       | Persistent data container helpers for Entity, Chunk, and ItemStack |
| `GameRuleUtil`  | Convenient get, set, and toggle helpers for Minecraft game rules   |

---

## ItemStack Builder DSL

Building `ItemStack` instances with custom names, lore, enchantments, and flags normally requires verbose
boilerplate. The `itemStack` DSL in `com.example.exampleplugin.utils` lets you create fully configured items in a
single expression. All text is parsed through
[MiniMessage](https://docs.advntr.dev/minimessage/index.html), so rich formatting tags like `<bold>`, `<red>`,
and `<gradient>` work out of the box.

### Before (vanilla API)

```kotlin
val item = ItemStack(Material.DIAMOND_SWORD)
val meta = item.itemMeta
meta.displayName(MiniMessage.miniMessage().deserialize("<bold><gradient:gold:yellow>Excalibur</gradient></bold>"))
meta.lore(
    listOf(
        MiniMessage.miniMessage().deserialize("<gray>A legendary blade"),
        MiniMessage.miniMessage().deserialize("<gray>Damage: <red>+20")
    )
)
meta.addEnchant(Enchantment.SHARPNESS, 5, true)
meta.isUnbreakable = true
meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
item.itemMeta = meta
```

### After (using the DSL)

```kotlin
import com.example.exampleplugin.utils.itemStack

val item = itemStack(Material.DIAMOND_SWORD) {
    name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
    lore("<gray>A legendary blade", "<gray>Damage: <red>+20")
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable(true)
    flag(ItemFlag.HIDE_ENCHANTS)
}
```

### Builder Methods

| Method            | Signature                                        | Description                                     |
|:------------------|:-------------------------------------------------|:------------------------------------------------|
| `name`            | `name(String)`                                   | Set the display name (MiniMessage)              |
| `lore`            | `lore(vararg String)`                            | Set lore lines (each parsed with MiniMessage)   |
| `enchant`         | `enchant(Enchantment, Int)`                      | Add an enchantment at the given level           |
| `unbreakable`     | `unbreakable(Boolean)`                           | Make the item unbreakable                       |
| `hideTooltip`     | `hideTooltip(Boolean)`                           | Hide the tooltip                                |
| `amount`          | `amount(Int)`                                    | Set the stack size                              |
| `flag`            | `flag(vararg ItemFlag)`                          | Add one or more item flags                      |
| `customModelData` | `customModelData(Int)`                           | Set the custom model data value                 |
| `pdc`             | `pdc(NamespacedKey, PersistentDataType<P,C>, C)` | Store a PDC entry on the item (via PDCUtil)     |
| `meta`            | `meta(ItemMeta.() -> Unit)`                      | Escape hatch for direct `ItemMeta` manipulation |

### Escape Hatch Example

For advanced use-cases not covered by the builder methods, the `meta` block gives you direct access to the
`ItemMeta`. Any changes made inside `meta` are applied **after** all other builder properties, so they take
precedence:

```kotlin
import com.example.exampleplugin.utils.itemStack

val head = itemStack(Material.PLAYER_HEAD) {
    name("<yellow>Custom Head")
    meta {
        // 'this' is the ItemMeta — cast and use any Paper API method
        (this as org.bukkit.inventory.meta.SkullMeta)
            .owningPlayer = org.bukkit.Bukkit.getOfflinePlayer("Notch")
    }
}
```

---

## CountdownUtil

`CountdownUtil` runs a per-player countdown and displays the progress through a configurable
`DisplayLocation` (see the [Developer Guide](DEVELOPER_GUIDE.md#displaylocation) for all values). It schedules a
repeating sync task that ticks every second, shows an
optional message on each tick, and fires an optional finish message and callback when the countdown reaches zero.

### Usage

```kotlin
import com.example.exampleplugin.utils.CountdownUtil
import com.example.exampleplugin.enums.DisplayLocation
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 10,
    displayLocation = DisplayLocation.ACTION_BAR,
    message = "<yellow>Starting in <bold>{seconds}</bold> (<gray>{time}</gray>)",
    finishMessage = "<green>Go!",
    sound = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f),
    finishSound = Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1f, 1f),
    onFinish = { p -> p.sendMessage("Started!") }
)
```

### Parameters

| Parameter         | Type               | Required | Default              | Description                                                          |
|:------------------|:-------------------|:---------|:---------------------|:---------------------------------------------------------------------|
| `plugin`          | `JavaPlugin`       | Yes      | —                    | The owning plugin, used to schedule the internal task                |
| `player`          | `Player`           | Yes      | —                    | The player to target                                                 |
| `seconds`         | `Int`              | Yes      | —                    | Total number of seconds to count down from (must be > 0)             |
| `displayLocation` | `DisplayLocation`  | Yes      | —                    | Where messages are shown; use `DisplayLocation.NONE` to suppress all |
| `message`         | `String?`          | No       | `null`               | MiniMessage string shown on every tick; omit to skip per-tick output |
| `finishMessage`   | `String?`          | No       | `null`               | MiniMessage string shown when the countdown ends; omit to skip       |
| `bossBarColor`    | `BossBar.Color`    | No       | `BossBar.Color.BLUE` | Boss bar colour; only used when `displayLocation` is `BOSS_BAR`      |
| `sound`           | `Sound?`           | No       | `null`               | Sound played on every tick; pass `null` for silence                  |
| `finishSound`     | `Sound?`           | No       | `null`               | Sound played when the countdown ends; pass `null` for silence        |
| `onFinish`        | `(Player) -> Unit` | Yes      | —                    | Callback invoked with the player when the countdown reaches zero     |

### Message Placeholders

Both `message` and `finishMessage` support the following placeholders:

| Placeholder | Output example |
|:------------|:---------------|
| `{seconds}` | `5s`           |
| `{time}`    | `1h 2m 3s`     |

Either or both placeholders may be omitted from the message string.

### Example (Chat Countdown)

```kotlin
import com.example.exampleplugin.utils.CountdownUtil
import com.example.exampleplugin.enums.DisplayLocation

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 5,
    displayLocation = DisplayLocation.CHAT,
    message = "<gray>Game starts in <yellow>{seconds}",
    finishMessage = "<green><bold>Game started!",
    onFinish = { p -> p.sendMessage("Good luck!") }
)
```

### Example (Boss Bar Countdown)

```kotlin
import com.example.exampleplugin.utils.CountdownUtil
import com.example.exampleplugin.enums.DisplayLocation
import net.kyori.adventure.bossbar.BossBar

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 30,
    displayLocation = DisplayLocation.BOSS_BAR,
    bossBarColor = BossBar.Color.RED,
    message = "<red>Time remaining: {time}",
    finishMessage = "<green>Time's up!",
    onFinish = { p -> p.sendMessage("Round over!") }
)
```

---

## TeamUtil

`TeamUtil` manages custom teams with persistent, server-wide state backed by
[`ServerDataManager`](DEVELOPER_GUIDE.md#server-data). Each player may belong
to **at most one team** at a time — calling `addPlayer` when a player is already
in another team removes them from that team first. All changes are written into
the server-data JSON immediately, so they are flushed to disk when
`ServerDataManager.save()` is called in `JavaPlugin.onDisable`.

### Usage

```kotlin
import com.example.exampleplugin.utils.TeamUtil

// Create a team (returns false if the name is already taken)
TeamUtil.createTeam("red", "<red>Red Team")
TeamUtil.createTeam("blue", "<blue>Blue Team")

// Add a player — automatically removes them from any previous team
TeamUtil.addPlayer(player, "red")

// Find which team a player is in
val team = TeamUtil.getPlayerTeam(player)

// Broadcast a MiniMessage string to all online members of a team
team?.broadcast("<yellow>Get ready to fight!")

// Check if two players are on the same team
val sameTeam = TeamUtil.areTeammates(playerA, playerB)

// Broadcast to all online members across every team
TeamUtil.broadcastAll("<green>The game is starting!")

// Remove a player from their team
TeamUtil.removePlayer(player)

// Delete a team entirely (members are removed along with it)
TeamUtil.deleteTeam("red")

// Delete all teams at once
TeamUtil.deleteAll()
```

### Team Management Methods

| Method                             | Return       | Description                                                          |
|:-----------------------------------|:-------------|:---------------------------------------------------------------------|
| `createTeam(name, displayName)`    | `Boolean`    | Creates a team; returns `false` if the name is already taken         |
| `deleteTeam(name)`                 | `Boolean`    | Deletes a team and all its members; returns `false` if not found     |
| `deleteAll()`                      | `Unit`       | Deletes every team, clearing all members                             |
| `renameTeam(name, newDisplayName)` | `Boolean`    | Updates the display name; returns `false` if the team does not exist |
| `getTeam(name)`                    | `Team?`      | Returns the team, or `null` if it does not exist                     |
| `getAllTeams()`                    | `List<Team>` | Returns every existing team                                          |
| `hasTeam(name)`                    | `Boolean`    | Returns `true` if the team exists                                    |

All name lookups are **case-insensitive**. The stored key is always lowercase.

### Player Membership Methods

| Method                           | Return    | Description                                                                                                               |
|:---------------------------------|:----------|:--------------------------------------------------------------------------------------------------------------------------|
| `addPlayer(player, teamName)`    | `Boolean` | Adds the player to a team, auto-removing from any current team; returns `false` if team not found or player already in it |
| `removePlayer(player)`           | `Boolean` | Removes the player from their current team; returns `false` if not in any team                                            |
| `getPlayerTeam(player)`          | `Team?`   | Returns the player's team, or `null`                                                                                      |
| `isInTeam(player, teamName)`     | `Boolean` | Returns `true` if the player is a member of the named team                                                                |
| `areTeammates(playerA, playerB)` | `Boolean` | Returns `true` if both players are in the same team; `false` if either is not in any team                                 |
| `broadcastAll(message)`          | `Unit`    | Sends a MiniMessage string to all online members across every team                                                        |

### Team Instance Methods

Once you have a `Team` reference (from `getTeam` or `getPlayerTeam`), you can
use these methods directly on it:

| Method               | Return              | Description                                        |
|:---------------------|:--------------------|:---------------------------------------------------|
| `getMembers()`       | `Set<UUID>`         | Immutable snapshot of all member UUIDs             |
| `getOnlineMembers()` | `List<Player>`      | All online members as `Player` instances           |
| `contains(player)`   | `Boolean`           | Whether the player is in the team                  |
| `contains(uuid)`     | `Boolean`           | Whether the UUID belongs to a team member          |
| `broadcast(message)` | `Unit`              | Sends a MiniMessage string to all online members   |
| `memberCount`        | `Int` (property)    | Total number of members (online and offline)       |
| `displayName`        | `String` (property) | MiniMessage display name; mutable via `renameTeam` |

### Cache

`TeamUtil` loads team data lazily on the first access after plugin startup. If
you modify the underlying `ServerData` JSON directly (outside of `TeamUtil`),
call `TeamUtil.invalidateCache()` to force a fresh load on the next access.

### Example (Game Setup)

```kotlin
import com.example.exampleplugin.utils.TeamUtil
import org.bukkit.entity.Player

fun setupGame(players: List<Player>) {
    TeamUtil.createTeam("red", "<red><bold>Red")
    TeamUtil.createTeam("blue", "<blue><bold>Blue")

    players.forEachIndexed { index, player ->
        val teamName = if (index % 2 == 0) "red" else "blue"
        TeamUtil.addPlayer(player, teamName)
    }

    TeamUtil.getTeam("red")?.broadcast("<red>You are on the Red Team!")
    TeamUtil.getTeam("blue")?.broadcast("<blue>You are on the Blue Team!")
}

fun endGame() {
    TeamUtil.broadcastAll("<green>The game has ended. Thanks for playing!")
    TeamUtil.deleteAll()
}
```

---

## TagUtil

`TagUtil` manages an arbitrary set of string tags for each player. Tag data is
stored inside each player's individual [`PlayerDataManager`](DEVELOPER_GUIDE.md#player-data)
file under the key `"tags"`, so tags are automatically loaded when the player
joins and saved when they quit — no explicit setup is required.

### Usage

```kotlin
import com.example.exampleplugin.utils.TagUtil

// Add a tag (returns false if the player already has it)
TagUtil.addTag(player, "vip")

// Check if a player has a tag
val isVip = TagUtil.hasTag(player, "vip")

// Get all tags assigned to a player
val tags = TagUtil.getTags(player)

// Remove a specific tag (returns false if the player did not have it)
TagUtil.removeTag(player, "vip")

// Remove all tags from a player
TagUtil.clearTags(player)
```

### Methods

| Method                   | Return        | Description                                                      |
|:-------------------------|:--------------|:-----------------------------------------------------------------|
| `addTag(player, tag)`    | `Boolean`     | Adds the tag; returns `false` if the player already has it       |
| `removeTag(player, tag)` | `Boolean`     | Removes the tag; returns `false` if the player did not have it   |
| `hasTag(player, tag)`    | `Boolean`     | Returns `true` if the player currently has the tag               |
| `getTags(player)`        | `Set<String>` | Returns an immutable snapshot of all tags assigned to the player |
| `clearTags(player)`      | `Unit`        | Removes all tags from the player                                 |

Tags are **case-sensitive** — `"VIP"` and `"vip"` are treated as distinct values.

### Persistence

Tags are written into the player's `PlayerData` JSON on every mutating call.
They are flushed to disk when the player quits or when `PlayerDataManager.saveAll()`
is called during `JavaPlugin.onDisable`. No extra save call is needed.

### Example (Permission Gate)

```kotlin
import com.example.exampleplugin.utils.TagUtil
import org.bukkit.entity.Player

fun onEnterVipArea(player: Player) {
    if (!TagUtil.hasTag(player, "vip")) {
        player.sendMessage("<red>You need VIP access to enter this area.")
        return
    }
    player.sendMessage("<gold>Welcome to the VIP area!")
}
```

---

## MessageUtil

`MessageUtil` sends prefix-decorated messages to players. The prefix is
read from `config.yml` under the `message-prefix` key and supports both plain text and
[MiniMessage](https://docs.advntr.dev/minimessage/index.html) formatting. The plugin
initialises `MessageUtil` automatically at startup and after every `/exampleplugin reload`,
so no manual setup is required in your own commands or listeners.

### Configuration

```yaml
# config.yml

# Plain text
message-prefix: "[ExamplePlugin]"

# MiniMessage (rich formatting)
message-prefix: "<gray>[<gold>ExamplePlugin<gray>]"
```

### Usage

```kotlin
import com.example.exampleplugin.utils.sendPrefixed
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

// Plain text
player.sendPrefixed("Hello!")

// MiniMessage
player.sendPrefixed("<green>Operation successful!")
player.sendPrefixed("<red>Something went wrong.")

// Adventure Component
player.sendPrefixed(Component.text("Hello!", NamedTextColor.GREEN))
```

### Methods

| Method / Extension                                 | Description                                                                                  |
|:---------------------------------------------------|:---------------------------------------------------------------------------------------------|
| `MessageUtil.init(prefixString)`                   | Loads the prefix (plain text or MiniMessage). Called automatically at startup and on reload. |
| `MessageUtil.sendPrefixed(player, msg: String)`    | Sends a plain-text or MiniMessage string with the prefix prepended.                          |
| `MessageUtil.sendPrefixed(player, msg: Component)` | Sends an Adventure `Component` with the prefix prepended.                                    |
| `Player.sendPrefixed(msg: String)`                 | Extension shorthand for `MessageUtil.sendPrefixed(this, msg)`.                               |
| `Player.sendPrefixed(msg: Component)`              | Extension shorthand for `MessageUtil.sendPrefixed(this, msg)`.                               |

### Example (Listener)

```kotlin
import com.example.exampleplugin.utils.sendPrefixed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class JoinListener : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.player.sendPrefixed("<green>Welcome to the server!")
    }
}
```

---

## PDCUtil

`PDCUtil` provides a concise API for reading and writing values in
[PersistentDataContainer](https://jd.papermc.io/paper/1.21/org/bukkit/persistence/PersistentDataContainer.html)s
attached to `Entity`, `Chunk`, and `ItemStack` instances.

Both `Entity` and `Chunk` implement `PersistentDataHolder` directly, so a single
set of methods covers both. `ItemStack` requires special handling because its PDC
lives inside its `ItemMeta`; `PDCUtil` takes care of reading and writing the meta
automatically.

All `PDCUtil` operations for `ItemStack` that mutate data call `item.itemMeta = meta`
immediately so the item is always consistent after the call.

### Usage (Entity / Chunk)

```kotlin
import com.example.exampleplugin.utils.PDCUtil
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "my_key")

// Set a value
PDCUtil.set(entity, key, PersistentDataType.STRING, "hello")

// Get a value (returns null when absent or wrong type)
val value: String? = PDCUtil.get(entity, key, PersistentDataType.STRING)

// Check if a key is present
val exists: Boolean = PDCUtil.has(entity, key)

// Remove a key
PDCUtil.remove(entity, key)

// List all keys in the container
val keys: Set<NamespacedKey> = PDCUtil.keys(entity)
```

The same four methods work identically for `Chunk`:

```kotlin
PDCUtil.set(chunk, key, PersistentDataType.INTEGER, 42)
val count: Int? = PDCUtil.get(chunk, key, PersistentDataType.INTEGER)
PDCUtil.has(chunk, key)
PDCUtil.remove(chunk, key)
PDCUtil.keys(chunk)
```

### Usage (ItemStack)

```kotlin
import com.example.exampleplugin.utils.PDCUtil
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "damage_bonus")

PDCUtil.set(item, key, PersistentDataType.INTEGER, 10)
val bonus: Int? = PDCUtil.get(item, key, PersistentDataType.INTEGER)
PDCUtil.has(item, key)
PDCUtil.remove(item, key)
PDCUtil.keys(item)
```

### Methods (PersistentDataHolder — Entity, Chunk)

| Method                          | Return               | Description                                                      |
|:--------------------------------|:---------------------|:-----------------------------------------------------------------|
| `set(holder, key, type, value)` | `Unit`               | Stores `value` under `key` in the holder's PDC                   |
| `get(holder, key, type)`        | `C?`                 | Returns the stored value, or `null` if absent or type-mismatched |
| `has(holder, key)`              | `Boolean`            | Returns `true` if the key exists in the holder's PDC             |
| `remove(holder, key)`           | `Unit`               | Removes the key from the holder's PDC; no-op if absent           |
| `keys(holder)`                  | `Set<NamespacedKey>` | Returns an immutable snapshot of all keys in the holder's PDC    |

### Methods (ItemStack)

| Method                        | Return               | Description                                                                |
|:------------------------------|:---------------------|:---------------------------------------------------------------------------|
| `set(item, key, type, value)` | `Unit`               | Stores `value` in the item's PDC; writes meta back to the item             |
| `get(item, key, type)`        | `C?`                 | Returns the stored value, or `null` if absent, type-mismatched, or no meta |
| `has(item, key)`              | `Boolean`            | Returns `true` if the key exists in the item's PDC                         |
| `remove(item, key)`           | `Unit`               | Removes the key from the item's PDC; no-op if absent or no meta            |
| `keys(item)`                  | `Set<NamespacedKey>` | Returns an immutable snapshot of all keys in the item's PDC                |

### Usage (ItemStack DSL)

PDC entries can be set directly inside the `itemStack` builder block using the
`pdc` method, without needing a separate `PDCUtil.set` call after the item is
built. PDC entries are applied **before** the `meta` escape-hatch block, so the
`meta` block can still override them if needed.

```kotlin
import com.example.exampleplugin.utils.itemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "damage_bonus")

val item = itemStack(Material.DIAMOND_SWORD) {
    name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
    lore("<gray>A legendary blade")
    enchant(Enchantment.SHARPNESS, 5)
    pdc(key, PersistentDataType.INTEGER, 20)
}
```

### Example (Custom Item Identity)

A common use-case is marking items with a unique identifier so you can
distinguish plugin items from regular ones:

```kotlin
import com.example.exampleplugin.utils.PDCUtil
import com.example.exampleplugin.utils.itemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

val itemIdKey = NamespacedKey(plugin, "item_id")

// Create a marked item using the DSL
val specialSword = itemStack(Material.DIAMOND_SWORD) {
    name("<red>Soul Blade")
    pdc(itemIdKey, PersistentDataType.STRING, "soul_blade")
}

// Check if an item in hand is the special sword
fun isSpecialSword(player: Player): Boolean {
    val held = player.inventory.itemInMainHand
    return PDCUtil.get(held, itemIdKey, PersistentDataType.STRING) == "soul_blade"
}
```

---

## GameRuleUtil

`GameRuleUtil` provides convenient helpers for reading, writing, and toggling
Minecraft game rules on any [World](https://jd.papermc.io/paper/1.21/org/bukkit/World.html).
All game rules — both `Boolean` rules (e.g. `KEEP_INVENTORY`, `DO_DAYLIGHT_CYCLE`) and
`Int` rules (e.g. `RANDOM_TICK_SPEED`) — are supported through a single generic
API. The [toggle] method is a specialisation for boolean rules that flips the
current value without requiring the caller to check it first.

### Usage

```kotlin
import com.example.exampleplugin.utils.GameRuleUtil
import org.bukkit.GameRule

// Read a rule
val keepInventory: Boolean? = GameRuleUtil.get(world, GameRule.KEEP_INVENTORY)
val tickSpeed: Int? = GameRuleUtil.get(world, GameRule.RANDOM_TICK_SPEED)

// Write a rule
GameRuleUtil.set(world, GameRule.KEEP_INVENTORY, true)
GameRuleUtil.set(world, GameRule.RANDOM_TICK_SPEED, 3)

// Toggle a boolean rule (no true/false needed)
val newValue: Boolean? = GameRuleUtil.toggle(world, GameRule.DO_DAYLIGHT_CYCLE)
```

### Methods

| Method   | Signature                                | Return     | Description                                                                                                             |
|:---------|:-----------------------------------------|:-----------|:------------------------------------------------------------------------------------------------------------------------|
| `get`    | `get(world, rule: GameRule<T>)`          | `T?`       | Returns the current value of the rule, or `null` if not set                                                             |
| `set`    | `set(world, rule: GameRule<T>, value)`   | `Boolean`  | Sets the rule to `value`; returns `false` if the rule is unrecognized                                                   |
| `toggle` | `toggle(world, rule: GameRule<Boolean>)` | `Boolean?` | Flips a boolean rule to its opposite value; returns the **new** value, or `null` if the current value could not be read |

### Example (Cycle Day and Weather)

```kotlin
import com.example.exampleplugin.utils.GameRuleUtil
import org.bukkit.GameRule

// Pause the day/night cycle and weather during a mini-game
fun freezeWorld(world: org.bukkit.World) {
    GameRuleUtil.set(world, GameRule.DO_DAYLIGHT_CYCLE, false)
    GameRuleUtil.set(world, GameRule.DO_WEATHER_CYCLE, false)
}

// Restore them when the game ends
fun unfreezeWorld(world: org.bukkit.World) {
    GameRuleUtil.set(world, GameRule.DO_DAYLIGHT_CYCLE, true)
    GameRuleUtil.set(world, GameRule.DO_WEATHER_CYCLE, true)
}
```

### Example (Toggle)

```kotlin
import com.example.exampleplugin.utils.GameRuleUtil
import org.bukkit.GameRule

// Toggle keep-inventory on command
fun onToggleKeepInventory(world: org.bukkit.World) {
    val newState = GameRuleUtil.toggle(world, GameRule.KEEP_INVENTORY)
    println("keep-inventory is now $newState")
}
```

---
