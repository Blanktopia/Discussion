package me.weiwen.discussion.hooks

import github.scarsz.discordsrv.DiscordSRV
import me.weiwen.discussion.Discussion.Companion.plugin
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

    fun processChatMessage(player: Player, message: String, channel: String, cancelled: Boolean, event: Event) {
        discordSrv?.processChatMessage(
            player,
            message,
            channel,
            cancelled,
            event,
        )
    }
}