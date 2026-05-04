package net.trilleo.mc.plugins.tribingo.registration

import net.trilleo.mc.plugins.tribingo.registration.TaskRegistrar.registerAll
import net.trilleo.mc.plugins.tribingo.registration.TaskRegistrar.unregisterAll
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/**
 * Discovers all concrete [PluginTask] subclasses inside the `tasks`
 * package (and its subpackages) and schedules them with the Bukkit scheduler.
 *
 * Each task is scheduled according to its [PluginTask.delay], [PluginTask.period],
 * and [PluginTask.async] properties:
 *
 * * **Repeating sync** (`async = false`, `period >= 0`) — scheduled with
 *   `BukkitScheduler.runTaskTimer`
 * * **Repeating async** (`async = true`, `period >= 0`) — scheduled with
 *   `BukkitScheduler.runTaskTimerAsynchronously`
 * * **One-shot sync** (`async = false`, `period < 0`) — scheduled with
 *   `BukkitScheduler.runTaskLater`
 * * **One-shot async** (`async = true`, `period < 0`) — scheduled with
 *   `BukkitScheduler.runTaskLaterAsynchronously`
 *
 * All scheduled tasks are tracked internally and cancelled together when
 * [unregisterAll] is called.
 */
object TaskRegistrar {

    private const val TASKS_PACKAGE = "net.trilleo.mc.plugins.trihunt.tasks"

    /** Every [BukkitTask] that has been scheduled by this registrar. */
    private val scheduledTasks = mutableListOf<BukkitTask>()

    /**
     * Scans the tasks package, instantiates every [PluginTask] found,
     * and schedules it with the Bukkit scheduler.
     */
    fun registerAll(plugin: JavaPlugin) {
        unregisterAll()

        val taskClasses = PackageScanner.findClasses(
            plugin, TASKS_PACKAGE, PluginTask::class.java
        )

        val scheduler = plugin.server.scheduler

        for (taskClass in taskClasses) {
            try {
                val task = instantiate(taskClass, plugin)
                val runnable = Runnable { task.run() }

                val bukkitTask = if (task.period >= 0) {
                    if (task.async) {
                        scheduler.runTaskTimerAsynchronously(plugin, runnable, task.delay, task.period)
                    } else {
                        scheduler.runTaskTimer(plugin, runnable, task.delay, task.period)
                    }
                } else {
                    if (task.async) {
                        scheduler.runTaskLaterAsynchronously(plugin, runnable, task.delay)
                    } else {
                        scheduler.runTaskLater(plugin, runnable, task.delay)
                    }
                }

                scheduledTasks.add(bukkitTask)
                plugin.logger.info("Scheduled task: ${taskClass.simpleName}")
            } catch (e: Exception) {
                plugin.logger.severe(
                    "Failed to schedule task ${taskClass.simpleName}: ${e.message}"
                )
            }
        }

        plugin.logger.info("Scheduled ${scheduledTasks.size} task(s)")
    }

    /**
     * Cancels all tasks that were scheduled by [registerAll].
     *
     * Should be called from [org.bukkit.plugin.java.JavaPlugin.onDisable].
     */
    fun unregisterAll() {
        scheduledTasks.forEach { it.cancel() }
        scheduledTasks.clear()
    }

    /**
     * Tries to create an instance of [clazz] using a constructor that accepts
     * a [JavaPlugin]; falls back to a no-arg constructor.
     */
    private fun instantiate(clazz: Class<out PluginTask>, plugin: JavaPlugin): PluginTask {
        return try {
            clazz.getDeclaredConstructor(JavaPlugin::class.java).newInstance(plugin)
        } catch (_: NoSuchMethodException) {
            try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (_: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "${clazz.simpleName} must declare either a no-arg constructor " +
                            "or a constructor accepting a single JavaPlugin parameter"
                )
            }
        }
    }
}
