package me.weiwen.discussion

import io.papermc.paper.event.player.AsyncChatEvent
import me.weiwen.discussion.ChatManager.channels
import me.weiwen.discussion.ChatManager.playerData
import me.weiwen.discussion.Discussion.Companion.plugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent

object ChatListener : Listener {
    @EventHandler
    fun onJoin(event: AsyncPlayerPreLoginEvent) {
        ChatManager.loadPlayer(event.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        ChatManager.saveAndRemovePlayer(event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player

        val split = event.message.split(' ', ignoreCase = false, limit = 2)
        val alias = (split.getOrNull(0) ?: return).drop(1).lowercase()
        val channel = channels[alias.lowercase()]

        if (channel == null || playerData[player.uniqueId]?.channels?.contains(channel.name) == false) {
            return
        }

        event.isCancelled = true

        val message = split.getOrNull(1)
        if (message != null) {
            ChatManager.broadcastMessage(player, message, channel, event)
        } else {
            ChatManager.setChannel(player, channel)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val data = playerData[player.uniqueId] ?: return

        event.isCancelled = true

        var message = ChatManager.miniMessage.serialize(event.originalMessage())

        val channel = if (message.startsWith(plugin.config.alternateChannelPrefix)) {
            message = message.drop(1)
            val alternate = data.alternate
            if (alternate == null) {
                player.sendMessage(
                    ChatManager.miniMessage.deserialize(
                        plugin.config.messages.errorNoAlternateChannel,
                    )
                )
                return
            }
            channels[alternate.lowercase()]
        } else {
            channels[data.channel.lowercase()]
        } ?: return

        ChatManager.broadcastMessage(player, message, channel, event)
    }
}