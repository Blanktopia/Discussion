package me.weiwen.discussion

import me.weiwen.discussion.config.Config
import me.weiwen.discussion.config.parseConfig
import me.weiwen.discussion.replacements.EmojiManager
import org.bukkit.plugin.java.JavaPlugin
import java.net.http.WebSocket

class Discussion : JavaPlugin(), WebSocket.Listener {
    companion object {
        lateinit var plugin: Discussion
            private set
    }

    lateinit var config: Config

    override fun onLoad() {
        plugin = this
    }

    override fun onEnable() {
        config = parseConfig(this)

        EmojiManager.onEnable()
        ChatManager.onEnable()
        server.pluginManager.registerEvents(ChatListener, this)

        logger.info("Discussion is enabled")
    }

    override fun onDisable() {
        ChatManager.onDisable()

        logger.info("Discussion is disabled")
    }

}
