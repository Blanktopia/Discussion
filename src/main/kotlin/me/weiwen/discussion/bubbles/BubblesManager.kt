package me.weiwen.discussion.bubbles

import me.clip.placeholderapi.PlaceholderAPI
import me.weiwen.discussion.ChatManager.formatMessage
import me.weiwen.discussion.ChatManager.miniMessage
import me.weiwen.discussion.Discussion.Companion.plugin
import me.weiwen.discussion.database.Channel
import me.weiwen.discussion.replacements.EmojiManager.emojify
import me.weiwen.discussion.replacements.itemify
import me.weiwen.discussion.replacements.linkify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay

object BubblesManager {
    val bubbles: MutableMap<Player, ArrayDeque<TextDisplay>> = mutableMapOf()

    fun processChatMessage(player: Player, message: Component, channel: Channel, audience: Collection<Player>) {
        val formattedMessage = formatMessage(plugin.config.bubbles.format, player, message, channel)

        val location = player.location.add(0.0, 1.8 * (player.getAttribute(Attribute.SCALE)?.value ?: 1.0) + plugin.config.bubbles.offset, 0.0)
        val bubble = showBubble(location, formattedMessage, audience) ?: return

        val deque = bubbles.getOrPut(player) { ArrayDeque() }
        deque.forEach { it.teleport(it.location.add(0.0, plugin.config.bubbles.height, 0.0)) }
        deque.add(bubble)

        plugin.server.scheduler.runTaskLater(plugin, { ->
            bubbles[player]?.remove(bubble)
            if (bubble.isValid) {
                bubble.remove()
            }
        }, 10 * 20)
    }

    private fun showBubble(location: Location, component: Component, audience: Collection<Player>): TextDisplay? {
        val display: TextDisplay = location.world.spawnEntity(location, EntityType.TEXT_DISPLAY) as? TextDisplay ?: return null
        display.apply {
            isVisibleByDefault = false
            isPersistent = false
            billboard = Display.Billboard.CENTER
            brightness = Display.Brightness(15, 15)
            text(component)
            alignment = TextDisplay.TextAlignment.CENTER
        }
        for (player in audience) {
            player.showEntity(plugin, display)
        }
        return display
    }
}