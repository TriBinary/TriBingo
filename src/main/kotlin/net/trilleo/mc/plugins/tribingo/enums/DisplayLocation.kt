package net.trilleo.mc.plugins.tribingo.enums

/**
 * The location where countdown messages are displayed to the player.
 *
 * | Value        | Behaviour                                            |
 * |:-------------|:-----------------------------------------------------|
 * | [NONE]       | No message is displayed                              |
 * | [CHAT]       | Message is sent to the player's chat                 |
 * | [TITLE]      | Message is shown as a screen title                   |
 * | [BOSS_BAR]   | Message is shown in a boss bar that depletes over time |
 * | [ACTION_BAR] | Message is shown above the hotbar                    |
 */
enum class DisplayLocation {
    NONE,
    CHAT,
    TITLE,
    BOSS_BAR,
    ACTION_BAR
}
