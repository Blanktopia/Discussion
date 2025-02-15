package me.weiwen.discussion.hooks

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.minimessage.MiniMessage
import me.weiwen.discussion.Discussion.Companion.plugin
import org.bukkit.entity.Player

object DiscordSrvHook {
    private val discordSrv: DiscordSRV?
        get() =
            if (plugin.server.pluginManager.isPluginEnabled("DiscordSRV")) {
                DiscordSRV.getPlugin()
            } else {
                null
            }

    fun processChatMessage(player: Player, message: String, channel: String, cancelled: Boolean) {
        discordSrv?.processChatMessage(
            player,
            MiniMessage.miniMessage().deserialize(message),
            channel,
            cancelled,
        )
    }
}