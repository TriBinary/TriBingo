package net.trilleo.mc.plugins.tribingo.registration

/**
 * Base class for all plugin tasks.
 *
 * Extend this class and place the subclass anywhere inside the
 * `net.trilleo.mc.plugins.trihunt.tasks` package (or any subpackage) to
 * have it automatically discovered, instantiated, and scheduled at startup.
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter (the plugin
 *   instance will be injected automatically).
 *
 * Example (repeating sync task – runs every 5 minutes after a 1-minute delay):
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.tasks
 *
 * class BroadcastTask : PluginTask(
 *     delay = 1200L,
 *     period = 6000L
 * ) {
 *     override fun run() {
 *         Bukkit.broadcast(Component.text("Hello from a scheduled task!"))
 *     }
 * }
 * ```
 *
 * Example (one-shot async task – runs once after a 5-second delay):
 * ```kotlin
 * package net.trilleo.mc.plugins.trihunt.tasks
 *
 * class CleanupTask : PluginTask(
 *     delay = 100L,
 *     async = true
 * ) {
 *     override fun run() {
 *         // perform async work here
 *     }
 * }
 * ```
 *
 * @param delay  delay in ticks before the task first runs (default: `0`)
 * @param period period in ticks between subsequent runs; use `-1` (or any
 *               negative value) to schedule the task as a one-shot task
 *               (default: `-1`)
 * @param async  when `true` the task is run off the main server thread
 *               (default: `false`)
 */
abstract class PluginTask(
    val delay: Long = 0L,
    val period: Long = -1L,
    val async: Boolean = false
) {

    /**
     * Called each time the task fires.
     *
     * For repeating tasks this is invoked once per [period] ticks; for
     * one-shot tasks it is invoked exactly once.
     */
    abstract fun run()
}
