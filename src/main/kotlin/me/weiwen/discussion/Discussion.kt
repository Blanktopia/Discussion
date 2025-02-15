package me.weiwen.discussion

import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.paper.PaperCommandManager
import com.mineinabyss.idofront.platforms.IdofrontPlatforms
import me.weiwen.discussion.config.Config
import me.weiwen.discussion.config.parseConfig
import me.weiwen.discussion.replacements.EmojiManager
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.spigotmc.SpigotConfig.registerCommands
import java.net.http.WebSocket
import java.util.function.Function

class Discussion : JavaPlugin(), WebSocket.Listener {
    companion object {
        lateinit var plugin: Discussion
            private set
    }

    lateinit var config: Config

    override fun onLoad() {
        plugin = this
        IdofrontPlatforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        config = parseConfig(this)

        EmojiManager.onEnable()
        ChatManager.onEnable()
        server.pluginManager.registerEvents(ChatListener, this)

        registerCommands()

        logger.info("Discussion is enabled")
    }

    override fun onDisable() {
        ChatManager.onDisable()

        logger.info("Discussion is disabled")
    }

}
