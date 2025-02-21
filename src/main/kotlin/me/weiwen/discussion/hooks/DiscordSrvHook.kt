package me.weiwen.discussion.hooks

import github.scarsz.discordsrv.DiscordSRV
import me.weiwen.discussion.Discussion.Companion.plugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.minimessage.MiniMessage as DiscordSRVMiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.Event

object DiscordSrvHook {
    private val discordSrv: DiscordSRV?
        get() =
            if (plugin.server.pluginManager.isPluginEnabled("DiscordSRV")) {
                DiscordSRV.getPlugin()
            } else {
                null
            }

    fun processChatMessage(player: Player, message: Component, channel: String, cancelled: Boolean, event: Event) {
        discordSrv?.processChatMessage(
            player,
            DiscordSRVMiniMessage.miniMessage().deserialize(MiniMessage.miniMessage().serialize(message)),
            channel,
            cancelled,
            event,
        )
    }
}