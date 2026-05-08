# TriBingo - Bingo System Guide

This guide documents every aspect of the Bingo system: its architecture, game lifecycle, objective model,
persistence layer, configuration, commands, and GUI. It is intended for developers who want to understand,
extend, or maintain the system.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Initialisation Order](#initialisation-order)
3. [Game Lifecycle](#game-lifecycle)
4. [Core Classes](#core-classes)
   - [BingoManager](#bingomanager)
   - [BingoGame](#bingogame)
   - [BingoBoard](#bingoboard)
   - [BingoCell](#bingocell)
   - [BingoPlayerState](#bingoplayerstate)
5. [Objectives](#objectives)
   - [BingoObjective](#bingoobjective)
   - [EventBingoObjective](#eventbingoobjective)
   - [Built-in Objective Types](#built-in-objective-types)
6. [Registry & Loading](#registry--loading)
   - [BingoObjectiveRegistry](#bingoobjectiveregistry)
   - [YamlObjectiveLoader](#yamlobjectiveloader)
   - [bingo_objectives.yml Format](#bingo_objectivesyml-format)
7. [Persistence](#persistence)
   - [BingoServerData](#bingoserverdata)
8. [Configuration](#configuration)
9. [Commands](#commands)
10. [Board GUI](#board-gui)
11. [Writing a Custom Objective](#writing-a-custom-objective)

---

## Architecture Overview

The Bingo system is organised into four layers:

| Layer         | Key Classes                                                         | Responsibility                                   |
|:--------------|:--------------------------------------------------------------------|:-------------------------------------------------|
| **Facade**    | `BingoManager`                                                      | Single entry-point for all gameplay operations   |
| **Game**      | `BingoGame`, `BingoBoard`, `BingoCell`, `BingoPlayerState`          | In-memory game state and win-condition logic     |
| **Objective** | `BingoObjective`, `EventBingoObjective`, built-in implementations   | Definition and completion logic for each cell    |
| **Data**      | `BingoObjectiveRegistry`, `YamlObjectiveLoader`, `BingoServerData`  | Loading, registering, and persisting game data   |

A `BingoBoardGUI` and a `BingoCommand` provide the player-facing interfaces.

```
                          ┌───────────────────────────────────────────────┐
                          │               BingoManager (singleton)        │
                          │  newGame · startGame · stopGame · resetGame   │
                          │  refreshBoard · setBoardSize · checkCompletion │
                          └──────────────────┬────────────────────────────┘
                                             │ owns
                                    ┌────────▼─────────┐
                                    │    BingoGame      │
                                    │  state · board    │
                                    │  playerStates     │
                                    └──┬─────────────┬──┘
                                       │             │
                              ┌────────▼──┐   ┌──────▼─────────────┐
                              │ BingoBoard│   │  BingoPlayerState  │
                              │  size     │   │  completedCells    │
                              │  cells[]  │   │  progressData      │
                              └────┬──────┘   └────────────────────┘
                                   │ N cells
                              ┌────▼──────┐
                              │ BingoCell │
                              │  index    │
                              │  objective│
                              └────┬──────┘
                                   │
                         ┌─────────▼──────────┐
                         │   BingoObjective   │
                         │  (abstract)        │
                         └─────────┬──────────┘
                                   │ extends
                        ┌──────────▼────────────┐
                        │ EventBingoObjective<T>│  (also implements Listener)
                        └───────────────────────┘
```

---

## Initialisation Order

The following sequence must be followed exactly in `Main.onEnable`:

```kotlin
// 1. Set the BingoServerData factory BEFORE ServerDataManager.init
ServerDataManager.setFactory { BingoServerData() }
ServerDataManager.init(plugin)

// 2. Register everything else (commands, listeners, GUIs, tasks) ...

// 3. Initialise the Bingo system LAST, after all other systems are ready
BingoObjectiveRegistry.init(plugin)          // stores plugin reference for listener registration
YamlObjectiveLoader.load(plugin, BingoObjectiveRegistry)  // parses bingo_objectives.yml
BingoManager.init(plugin)                    // rehydrates or creates a default game
```

And in `Main.onDisable`:

```kotlin
// Save bingo state BEFORE ServerDataManager.save
BingoManager.save()
ServerDataManager.save()
```

---

## Game Lifecycle

A game moves through three states defined by `GameState`:

```
INACTIVE ──start()──► ACTIVE ──end()──► ENDED
   ▲                                       │
   └──────────────── reset() ─────────────┘
```

| State      | Meaning                                                    |
|:-----------|:-----------------------------------------------------------|
| `INACTIVE` | Game exists but has not been started; objectives cannot be completed |
| `ACTIVE`   | Game is running; objectives can be completed; win checks are performed |
| `ENDED`    | Game has finished (winner found or stopped manually); call `reset()` to start fresh |

`refresh()` can only be called while `INACTIVE`. `reset()` can be called from any state.

---

## Core Classes

### BingoManager

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

The singleton facade for the entire Bingo system. All gameplay operations flow through this object.

| Method                                    | Description                                                                       |
|:------------------------------------------|:----------------------------------------------------------------------------------|
| `init(plugin)`                            | Stores the plugin reference and rehydrates or creates a default game on startup   |
| `save()`                                  | Serialises the current game into `BingoServerData` for disk persistence           |
| `newGame(size: Int): BingoGame`           | Creates a new game with randomly selected objectives; size must be `3..6`         |
| `startGame()`                             | Transitions the current game from `INACTIVE` → `ACTIVE`                          |
| `stopGame()`                              | Ends the current `ACTIVE` game without a winner                                   |
| `resetGame()`                             | Resets all player progress and returns the game to `INACTIVE`                    |
| `refreshBoard()`                          | Picks a new random set of objectives (game must be `INACTIVE`)                   |
| `setBoardSize(size: Int)`                 | Changes the board size; refreshes if the same size and `INACTIVE`, otherwise creates a new game |
| `checkCompletion(player, objective)`      | Called by event objectives to mark a cell complete and check the win condition    |
| `isGameActive(): Boolean`                 | Returns `true` if the current game is in `ACTIVE` state                          |
| `currentGame: BingoGame?`                 | The currently active (or most-recently-created) game; `null` if none exists      |

**`checkCompletion` flow:**

1. Verifies a game is `ACTIVE` and the cell has not yet been completed for the player.
2. Marks the cell complete in `BingoPlayerState`.
3. Optionally broadcasts a server-wide completion announcement (`bingo.announce-completions`).
4. Refreshes the player's open `BingoBoardGUI` in-place.
5. Checks the configured win condition (`LINE` or `FULL_BOARD`) and calls `BingoGame.end(player)` if met.

---

### BingoGame

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

The state machine for a single game session. Create via `BingoManager.newGame`; do not instantiate directly.

| Method / Property                              | Description                                                                      |
|:-----------------------------------------------|:---------------------------------------------------------------------------------|
| `board: BingoBoard`                            | The current board; replaced by `refresh()`                                       |
| `state: GameState`                             | Current lifecycle state                                                          |
| `playerStates: Map<UUID, BingoPlayerState>`    | Read-only snapshot of all player states created this session                     |
| `start()`                                      | `INACTIVE` → `ACTIVE`; broadcasts start message to all online players            |
| `end(winner: Player?)`                         | `ACTIVE` → `ENDED`; broadcasts winner (or "game ended") message                  |
| `reset()`                                      | Any state → `INACTIVE`; calls `onReset` on each objective for online players, then clears all states |
| `refresh(objectives)`                          | Rebuilds the board from a new random selection (must be `INACTIVE`)              |
| `getOrCreateState(uuid): BingoPlayerState`     | Returns the player's state, creating a fresh one if it does not exist            |

---

### BingoBoard

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

An N×N grid (N in `3..6`) backed by a flat, row-major list of `BingoCell` objects. Immutable after construction.

**Coordinate system:** cell `(row, col)` maps to `cells[row * size + col]`.

| Method / Property                               | Description                                                         |
|:------------------------------------------------|:--------------------------------------------------------------------|
| `size: Int`                                     | Side-length of the square board                                     |
| `cells: List<BingoCell>`                        | All cells in row-major order (`size × size` entries)               |
| `getCell(row, col): BingoCell`                  | Returns the cell at zero-based `(row, col)`                         |
| `isLineComplete(state): Boolean`                | `true` if the player has completed any row, column, or diagonal     |
| `isBoardFull(state): Boolean`                   | `true` if the player has completed every cell                       |

Win-condition check performed by `BingoManager.checkCompletion`:

- `LINE` mode → `isLineComplete(state)`
- `FULL_BOARD` mode → `isBoardFull(state)`

---

### BingoCell

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Immutable value type representing a single cell on the board. Completion state lives in `BingoPlayerState`.

| Property       | Type              | Description                                                 |
|:---------------|:------------------|:------------------------------------------------------------|
| `cellIndex`    | `Int`             | Zero-based flat index (row-major); key in `BingoPlayerState.completedCells` |
| `objective`    | `BingoObjective`  | The objective that must be fulfilled to complete this cell  |

---

### BingoPlayerState

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Per-player mutable state for a single game session. Keyed by player `UUID`.

| Property / Method                          | Description                                                                   |
|:-------------------------------------------|:------------------------------------------------------------------------------|
| `uuid: UUID`                               | The player this state belongs to                                              |
| `completedCells: MutableSet<Int>`          | Set of completed `cellIndex` values                                           |
| `progressData: MutableMap<String, Int>`    | Per-objective progress counters, keyed by `BingoObjective.id`                 |
| `isCompleted(cellIndex): Boolean`          | Returns `true` when the cell has been marked complete                         |
| `markCompleted(cellIndex)`                 | Adds `cellIndex` to `completedCells`                                          |
| `getProgress(objectiveId): Int`            | Returns the current progress counter (0 if absent)                            |
| `setProgress(objectiveId, value)`          | Sets the progress counter for the given objective ID                          |
| `reset()`                                  | Clears all completion and progress data                                       |

---

## Objectives

### BingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Abstract base class for every bingo objective. Extend this class when the objective does not rely on a
Bukkit event (e.g. a snapshot check). For event-driven objectives, extend `EventBingoObjective` instead.

**Constructor parameters:**

| Parameter     | Type          | Description                                                |
|:--------------|:--------------|:-----------------------------------------------------------|
| `id`          | `String`      | Unique identifier; used for persistence and YAML lookups  |
| `name`        | `Component`   | Display name shown in GUIs and completion announcements    |
| `description` | `Component`   | Flavour text shown when a player clicks the cell in the GUI |
| `difficulty`  | `Difficulty`  | `EASY`, `MEDIUM`, `HARD`, or `INSANE`                      |

**Methods to implement / override:**

| Method                                           | Required | Description                                                                             |
|:-------------------------------------------------|:---------|:----------------------------------------------------------------------------------------|
| `isCompletedBy(player, state): Boolean`          | Yes      | Returns `true` when the objective has been fulfilled                                    |
| `onReset(player, state)`                         | No       | Called on board reset; override to clear intermediate counters in `progressData`        |
| `displayItem(player, completed): ItemStack`      | No       | Inventory GUI representation; default uses coloured stained-glass / lime concrete blocks |

**Default `displayItem` colour mapping:**

| State       | Material               |
|:------------|:-----------------------|
| Completed   | `LIME_CONCRETE`        |
| `EASY`      | `GREEN_STAINED_GLASS`  |
| `MEDIUM`    | `YELLOW_STAINED_GLASS` |
| `HARD`      | `RED_STAINED_GLASS`    |
| `INSANE`    | `PURPLE_STAINED_GLASS` |

---

### EventBingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Abstract subclass of `BingoObjective` that also implements Bukkit's `Listener`. Use this for any objective
that tracks a Bukkit event (entity deaths, block breaks, crafting, etc.).

**Constructor parameters** (in addition to those inherited from `BingoObjective`):

| Parameter    | Type       | Description                                     |
|:-------------|:-----------|:------------------------------------------------|
| `eventClass` | `Class<T>` | The Bukkit event class this objective listens to |

`BingoObjectiveRegistry.register` automatically calls `registerEvents` for every `EventBingoObjective`, so
no manual listener registration is needed.

**Abstract method:**

| Method                                                   | Description                                                              |
|:---------------------------------------------------------|:-------------------------------------------------------------------------|
| `onEvent(event: T, player: Player, state: BingoPlayerState)` | Called from the concrete `@EventHandler` after the player and state have been resolved |

**Typical implementation pattern:**

```kotlin
class MyObjective : EventBingoObjective<SomeBukkitEvent>(
    id          = "my_objective",
    name        = Component.text("My Objective"),
    description = Component.text("Do the thing."),
    difficulty  = Difficulty.MEDIUM,
    eventClass  = SomeBukkitEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSomeEvent(event: SomeBukkitEvent) {
        val player = /* extract player from event */ ?: return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, player, game.getOrCreateState(player.uniqueId))
    }

    override fun onEvent(event: SomeBukkitEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= REQUIRED_COUNT) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState) =
        state.getProgress(id) >= REQUIRED_COUNT

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}
```

---

### Built-in Objective Types

All built-in implementations live in `net.trilleo.mc.plugins.tribingo.bingo.objectives`.

| Class                   | YAML `type`       | Bukkit Event              | Tracked field(s)                                     |
|:------------------------|:------------------|:--------------------------|:-----------------------------------------------------|
| `KillEntityObjective`   | `kill_entity`     | `EntityDeathEvent`        | Kills of a specific `EntityType`                     |
| `MineBlockObjective`    | `mine_block`      | `BlockBreakEvent`         | Blocks of a specific `Material` mined                |
| `PlaceBlockObjective`   | `place_block`     | `BlockPlaceEvent`         | Blocks of a specific `Material` placed               |
| `CraftItemObjective`    | `craft_item`      | `CraftItemEvent`          | Craft operations (optionally filtered by `Material`) |
| `FishItemObjective`     | `fish_item`       | `PlayerFishEvent`         | Fish caught (or all rod catches if `countAll = true`)|
| `EatFoodObjective`      | `eat_food`        | `PlayerItemConsumeEvent`  | Food items consumed (optionally filtered by `Material`) |
| `EnchantItemObjective`  | `enchant_item`    | `EnchantItemEvent`        | Enchanting operations (optionally filtered by enchantment) |
| `TravelDistanceObjective` | `travel_distance` | `PlayerMoveEvent`       | Block-boundary crossings (in any direction)          |
| `BreedMobObjective`     | `breed_mob`       | `EntityBreedEvent`        | Breeding events triggered by the player (optionally filtered by entity type) |
| `TameEntityObjective`   | `tame_entity`     | `EntityTameEvent`         | Taming events (optionally filtered by entity type)   |

All built-in objectives listen at `EventPriority.MONITOR` with `ignoreCancelled = true`.

---

## Registry & Loading

### BingoObjectiveRegistry

**Package:** `net.trilleo.mc.plugins.tribingo.bingo.registry`

In-memory registry of all available `BingoObjective` instances. Objectives are stored in insertion order.

| Method                                          | Description                                                                             |
|:------------------------------------------------|:----------------------------------------------------------------------------------------|
| `init(plugin)`                                  | Stores the plugin reference; must be called before `register`                           |
| `register(objective)`                           | Adds the objective to the registry; auto-registers `EventBingoObjective` as a listener; duplicate IDs are skipped with a warning |
| `unregister(id)`                                | Removes an objective by ID (listener remains active but is a no-op while the game is inactive) |
| `getAll(): List<BingoObjective>`                | Returns all registered objectives in insertion order                                    |
| `getByDifficulty(difficulty): List<BingoObjective>` | Returns objectives filtered by `Difficulty`                                         |
| `get(id): BingoObjective?`                      | Returns the objective with the given ID, or `null`                                      |
| `clear()`                                       | Removes all objectives; intended for tests or full plugin reloads                       |

---

### YamlObjectiveLoader

**Package:** `net.trilleo.mc.plugins.tribingo.bingo.registry`

Loads user-defined objectives from `bingo_objectives.yml` in the plugin's data folder and registers them
with `BingoObjectiveRegistry`.

| Method                                            | Description                                                                           |
|:--------------------------------------------------|:--------------------------------------------------------------------------------------|
| `load(plugin, registry)`                          | Saves the default `bingo_objectives.yml` if absent, parses it, and registers all objectives |
| `registerTypeHandler(type, handler)`              | Adds a custom type handler; must be called **before** `load`                          |

**Custom type handler example:**

```kotlin
YamlObjectiveLoader.registerTypeHandler("my_type") { entry ->
    MyObjective(
        id          = entry["id"].toString(),
        name        = MiniMessage.miniMessage().deserialize(entry["name"].toString()),
        description = MiniMessage.miniMessage().deserialize(entry["description"].toString()),
        difficulty  = Difficulty.valueOf((entry["difficulty"] as? String ?: "EASY").uppercase()),
        myParam     = entry["my_param"].toString()
    )
}
```

---

### bingo_objectives.yml Format

Located at `<server>/plugins/TriBingo/bingo_objectives.yml`. The file is created from the bundled default
on the first run and is **not** overwritten on subsequent starts.

**Top-level structure:**

```yaml
objectives:
  - id: <string>
    type: <string>
    difficulty: EASY | MEDIUM | HARD | INSANE
    name: "<MiniMessage string>"
    description: "<MiniMessage string>"
    # type-specific parameters ...
```

**Parameters per type:**

| `type`            | Required parameters              | Optional parameters                              |
|:------------------|:---------------------------------|:-------------------------------------------------|
| `kill_entity`     | `entity_type`, `count`           | —                                                |
| `mine_block`      | `material`, `count`              | —                                                |
| `place_block`     | `material`, `count`              | —                                                |
| `craft_item`      | `count`                          | `material` (omit to match any crafted item)      |
| `fish_item`       | `count`                          | `count_all: true` (count fish + reeled entities) |
| `eat_food`        | `count`                          | `material` (omit to match any food)              |
| `enchant_item`    | `count`                          | `enchantment` (Minecraft key, e.g. `sharpness`)  |
| `travel_distance` | `blocks`                         | —                                                |
| `breed_mob`       | `count`                          | `entity_type` (omit to match any breedable mob)  |
| `tame_entity`     | `count`                          | `entity_type` (omit to match any tameable entity)|

**Field details:**

- `id` — Unique string. **Do not change after a game has been persisted** — IDs are stored in `serverdata.json` and used to rehydrate the board.
- `name` / `description` — Parsed with [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Tags like `<red>`, `<bold>`, `<gradient:gold:yellow>` are fully supported.
- `entity_type` — Must match a valid `org.bukkit.entity.EntityType` name (e.g. `CREEPER`, `ZOMBIE`).
- `material` — Must match a valid `org.bukkit.Material` name (e.g. `IRON_ORE`, `BREAD`).
- `enchantment` — Must be a valid Minecraft namespaced key without the `minecraft:` prefix (e.g. `sharpness`, `mending`).

**Example entry:**

```yaml
objectives:
  - id: kill_creeper
    type: kill_entity
    difficulty: EASY
    name: "<green>Creeper Killer"
    description: "<gray>Kill a Creeper."
    entity_type: CREEPER
    count: 1
```

---

## Persistence

### BingoServerData

**Package:** `net.trilleo.mc.plugins.tribingo.data`

Extends `ServerData` to persist the active game to `serverdata.json` via `ServerDataManager`.

| Property / Method                          | JSON key                  | Type          | Description                                         |
|:-------------------------------------------|:--------------------------|:--------------|:----------------------------------------------------|
| `boardSize`                                | `bingo_board_size`        | `Int`         | Side-length of the persisted board; `0` = no game   |
| `gameStateName`                            | `bingo_game_state`        | `String`      | Serialised `GameState` name                         |
| `boardLayout`                              | `bingo_board_layout`      | `JsonArray`   | Ordered list of objective IDs (size × size entries) |
| `savePlayerStates(states)`                 | `bingo_player_states`     | `JsonObject`  | Serialises all player states                        |
| `loadPlayerStates(): Map<UUID, Pair<...>>` | `bingo_player_states`     | `JsonObject`  | Deserialises previously saved player states         |
| `clearGameData()`                          | —                         | —             | Removes all bingo keys from the backing JSON        |

**Player-state JSON structure** (stored per UUID):

```json
{
  "c": [0, 3, 7],       // completed cell indices
  "p": {                // progress counters keyed by objective id
    "kill_creeper": 1,
    "mine_dirt": 14
  }
}
```

**Rehydration:** On `BingoManager.init`, the system reads `boardSize` and `boardLayout`, looks up each
objective ID in the registry, reconstructs the `BingoBoard` and `BingoGame`, then loads player states.
If any objective ID is missing from the registry, rehydration is aborted and a warning is logged.

---

## Configuration

All bingo settings live in `config.yml` under the `bingo` section. Reload at runtime with `/tb reload`.

| YAML key                       | Config property                    | Type      | Default  | Description                                                             |
|:-------------------------------|:-----------------------------------|:----------|:---------|:------------------------------------------------------------------------|
| `bingo.default-board-size`     | `PluginConfig.boardDefaultSize`    | `Int`     | `4`      | Side-length used when no persisted game is found on startup (clamped `3..6`) |
| `bingo.win-condition`          | `PluginConfig.winConditionLine`    | `String`  | `LINE`   | `LINE`: first full row/column/diagonal wins. `FULL_BOARD`: all cells must be completed |
| `bingo.announce-completions`   | `PluginConfig.announceCompletions` | `Boolean` | `true`   | Broadcasts a message to all players when any player completes a cell    |

**Example `config.yml` section:**

```yaml
bingo:
  default-board-size: 4
  win-condition: LINE
  announce-completions: true
```

---

## Commands

All Bingo commands are sub-commands of `/tribingo` (alias `/tb`), dispatched through `BingoCommand`.

### `/tb bingo <sub-command>`

| Sub-command      | Permission              | Who can use | Description                                                              |
|:-----------------|:------------------------|:------------|:-------------------------------------------------------------------------|
| `board`          | *(none)*                | Players     | Opens the `BingoBoardGUI` for the sender                                 |
| `start`          | `tribingo.bingo.manage` | Any sender  | Starts the current game (must be `INACTIVE`)                             |
| `stop`           | `tribingo.bingo.manage` | Any sender  | Ends the current game without a winner (must be `ACTIVE`)                |
| `reset`          | `tribingo.bingo.manage` | Any sender  | Resets all player progress; transitions game back to `INACTIVE`          |
| `refresh`        | `tribingo.bingo.manage` | Any sender  | Picks new random objectives for the current board (must be `INACTIVE`)   |
| `size <3-6>`     | `tribingo.bingo.manage` | Any sender  | Sets the board size; refreshes if same size and `INACTIVE`, creates new game otherwise |
| `status`         | *(none)*                | Any sender  | Shows board size, game state, number of objectives, and active players   |

Tab-completion is supported: typing `/tb bingo ` shows all sub-commands; typing `/tb bingo size ` shows `3 4 5 6`.

---

## Board GUI

**Class:** `BingoBoardGUI` — `net.trilleo.mc.plugins.tribingo.guis`
**GUI ID:** `bingo_board`

Opened with `/tb bingo board` (players only) or via `GUIManager.open(player, "bingo_board")`.

### Layout

The board is centred within a 6-row (54-slot) double-chest inventory. All unoccupied slots are filled with
dark glass panes.

```
vertPad  = (6 - N) / 2    // rows above the board
horizPad = (9 - N) / 2    // columns to the left of the board
slot(row, col) = (vertPad + row) * 9 + (horizPad + col)
```

For example, a 5×5 board (`vertPad = 0`, `horizPad = 2`) occupies slots 2–6, 11–15, 20–24, 29–33, 38–42.

### Cell Appearance

Each cell is rendered by `BingoObjective.displayItem(player, completed)`:

- **Uncompleted:** coloured stained-glass block whose colour reflects difficulty (green/yellow/red/purple).
- **Completed:** lime concrete block.
- Lore shows: difficulty, description, progress counter (for count-based objectives), and completion status.

### Interaction

Clicking a cell sends the objective's name, description, and completion status to the player in chat.
All inventory clicks are cancelled to prevent item theft.

### Live Updates

`BingoBoardGUI.refreshFor(player)` repopulates the inventory in-place. It is called automatically by
`BingoManager.checkCompletion` whenever a player completes a cell.

---

## Writing a Custom Objective

### Step 1 — Create the class

Place the class anywhere in the project (it does **not** need to be in a specific package for auto-registration;
objectives are registered explicitly via `BingoObjectiveRegistry.register`).

```kotlin
import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerBedEnterEvent

class SleepObjective : EventBingoObjective<PlayerBedEnterEvent>(
    id          = "sleep_in_bed",
    name        = Component.text("Good Night"),
    description = Component.text("Sleep in a bed."),
    difficulty  = Difficulty.EASY,
    eventClass  = PlayerBedEnterEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
        val game = BingoManager.currentGame ?: return
        if (game.state != GameState.ACTIVE) return
        onEvent(event, event.player, game.getOrCreateState(event.player.uniqueId))
    }

    override fun onEvent(event: PlayerBedEnterEvent, player: Player, state: BingoPlayerState) {
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.isCompleted(
            BingoManager.currentGame?.board?.cells?.find { it.objective.id == id }?.cellIndex ?: return false
        )
}
```

### Step 2 — Register it

Call `BingoObjectiveRegistry.register` **after** `BingoObjectiveRegistry.init` and **before**
`YamlObjectiveLoader.load` (or anywhere before `BingoManager.init`) so it is available for board generation:

```kotlin
BingoObjectiveRegistry.init(plugin)
BingoObjectiveRegistry.register(SleepObjective())   // ← register your custom objective
YamlObjectiveLoader.load(plugin, BingoObjectiveRegistry)
BingoManager.init(plugin)
```

### Step 3 — (Optional) Add a YAML type handler

If you want server operators to configure multiple variants of your objective from `bingo_objectives.yml`,
register a type handler **before** `YamlObjectiveLoader.load`:

```kotlin
YamlObjectiveLoader.registerTypeHandler("sleep_in_bed") { entry ->
    SleepObjective(
        id          = entry["id"].toString(),
        name        = MiniMessage.miniMessage().deserialize(entry["name"]?.toString() ?: "Sleep"),
        description = MiniMessage.miniMessage().deserialize(entry["description"]?.toString() ?: "Sleep in a bed."),
        difficulty  = Difficulty.valueOf((entry["difficulty"] as? String ?: "EASY").uppercase())
    )
}
```

---
