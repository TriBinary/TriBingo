package net.trilleo.mc.plugins.tribingo.enums

/**
 * Controls how empty slots in a [net.trilleo.mc.plugins.tribingo.registration.PluginGUI] are filled when the inventory
 * is first opened.
 *
 * - [NONE]  – no filler items are placed; the inventory is left as-is.
 * - [LIGHT] – every slot is pre-filled with a **white stained glass pane**
 *             before [net.trilleo.mc.plugins.tribingo.registration.PluginGUI.setup] is called.  Override individual slots
 *             inside `setup` to replace the filler with real items.
 * - [DARK]  – same as [LIGHT] but uses a **black stained glass pane**.
 */
enum class FillMode {
    NONE,
    LIGHT,
    DARK
}
