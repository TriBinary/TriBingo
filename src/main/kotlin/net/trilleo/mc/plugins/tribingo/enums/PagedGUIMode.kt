package net.trilleo.mc.plugins.tribingo.enums

/**
 * Controls how items are supplied to a [net.trilleo.mc.plugins.tribingo.registration.PagedPluginGUI].
 *
 * - [LIST] – items are provided as a flat list via `getItems` and distributed
 *            automatically across pages.
 * - [SET]  – items are placed manually by page and slot via `getSetItems`,
 *            giving the developer full control over which item appears at each
 *            position.
 */
enum class PagedGUIMode {
    LIST,
    SET
}
