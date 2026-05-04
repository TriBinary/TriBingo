package com.example.exampleplugin.registration

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Scans the plugin JAR (or class directory) for classes within a given package
 * and its subpackages that extend or implement a specified parent type.
 */
object PackageScanner {

    /**
     * Finds all concrete classes in [packageName] (and subpackages) that are
     * assignable to [parentClass].
     */
    fun <T> findClasses(
        plugin: JavaPlugin,
        packageName: String,
        parentClass: Class<T>
    ): List<Class<out T>> {
        val classLoader = plugin.javaClass.classLoader
        val packagePath = packageName.replace('.', '/')
        val codeSource = plugin.javaClass.protectionDomain.codeSource
            ?: return emptyList()
        val location = File(codeSource.location.toURI())

        return if (location.isDirectory) {
            scanDirectory(location, packagePath, classLoader, parentClass, plugin)
        } else {
            scanJar(location, packagePath, classLoader, parentClass, plugin)
        }
    }

    private fun <T> scanJar(
        jarFile: File,
        packagePath: String,
        classLoader: ClassLoader,
        parentClass: Class<T>,
        plugin: JavaPlugin
    ): List<Class<out T>> {
        val classes = mutableListOf<Class<out T>>()

        JarFile(jarFile).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("$packagePath/") && it.name.endsWith(".class") }
                .forEach { entry ->
                    val className = entry.name
                        .removeSuffix(".class")
                        .replace('/', '.')

                    loadAndFilter(className, classLoader, parentClass, plugin)?.let {
                        classes.add(it)
                    }
                }
        }

        return classes
    }

    private fun <T> scanDirectory(
        directory: File,
        packagePath: String,
        classLoader: ClassLoader,
        parentClass: Class<T>,
        plugin: JavaPlugin
    ): List<Class<out T>> {
        val classes = mutableListOf<Class<out T>>()
        val packageDir = File(directory, packagePath)

        if (!packageDir.exists()) return classes

        packageDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .forEach { file ->
                val className = file.relativeTo(directory).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')

                loadAndFilter(className, classLoader, parentClass, plugin)?.let {
                    classes.add(it)
                }
            }

        return classes
    }

    private fun <T> loadAndFilter(
        className: String,
        classLoader: ClassLoader,
        parentClass: Class<T>,
        plugin: JavaPlugin
    ): Class<out T>? {
        return try {
            val clazz = classLoader.loadClass(className)
            if (parentClass.isAssignableFrom(clazz)
                && !clazz.isInterface
                && !Modifier.isAbstract(clazz.modifiers)
            ) {
                @Suppress("UNCHECKED_CAST")
                clazz as Class<out T>
            } else null
        } catch (e: ClassNotFoundException) {
            plugin.logger.warning("Class not found: $className (missing dependency?)")
            null
        } catch (e: NoClassDefFoundError) {
            plugin.logger.warning("Class definition error for $className (incompatible class format?)")
            null
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load class $className: [${e.javaClass.simpleName}] ${e.message}")
            null
        }
    }
}
