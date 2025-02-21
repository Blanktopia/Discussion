@file:UseSerializers(TextColorSerializer::class, UUIDSerializer::class)

package me.weiwen.discussion.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.*
import me.weiwen.discussion.serializers.TextColorSerializer
import me.weiwen.discussion.serializers.UUIDSerializer
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

const val CONFIG_VERSION = "1.0.0"

@Serializable
data class Config(
    @SerialName("config-version")
    var configVersion: String = "1.0.0",

    @SerialName("alternate-channel-prefix")
    var alternateChannelPrefix: String = "#",

    @SerialName("default-channel-format")
    var defaultChannelFormat: String = "<channel-color>[<channel>] <player>: <message></channel-color>",

    @SerialName("default-channels")
    var defaultChannels: List<String> = listOf("Global", "Local"),

    var colors: List<TextColor> = listOf(),

    val messages: Messages = Messages(),

    val bubbles: BubbleConfig = BubbleConfig(),
)

fun parseConfig(plugin: JavaPlugin): Config {
    val file = File(plugin.dataFolder, "config.yml")

    if (!file.exists()) {
        plugin.logger.log(Level.INFO, "Config file not found, creating default")
        plugin.dataFolder.mkdirs()
        file.createNewFile()
        file.writeText(Yaml().encodeToString(Config()))
    }

    val config = try {
        Yaml().decodeFromString(file.readText())
    } catch (e: Exception) {
        plugin.logger.log(Level.SEVERE, e.message)
        Config()
    }

    if (config.configVersion != CONFIG_VERSION) {
        config.configVersion = CONFIG_VERSION
        plugin.logger.log(Level.INFO, "Updating config")
        file.writeText(Yaml().encodeToString(plugin.config))
    }

    return config
}
