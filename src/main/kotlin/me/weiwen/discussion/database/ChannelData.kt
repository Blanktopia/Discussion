@file:UseSerializers(TextColorSerializer::class, UUIDSerializer::class)

package me.weiwen.discussion.database

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.weiwen.discussion.config.Config
import me.weiwen.discussion.serializers.TextColorSerializer
import me.weiwen.discussion.serializers.UUIDSerializer
import net.kyori.adventure.text.format.TextColor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

@Serializable
data class ChannelData(
    var channels: Map<String, Channel> = mapOf(
        "Global" to Channel(
            TextColor.color(0xffffff),
            alias = "g",
            format = "<channel-color><player><channel-color>: <message>",
        ),
        "Local" to Channel(
            TextColor.color(0x98a7f2),
            alias = "l",
            format = "<channel-color>[Local] <player><channel-color>: <reset><message>",
            distance = 100.0,
        ),
    ),
)

fun ChannelData.Companion.load(plugin: JavaPlugin): ChannelData {
    val file = File(plugin.dataFolder, "channels.yml")

    if (!file.exists()) {
        val data = ChannelData()
        file.createNewFile()
        file.writeText(Yaml().encodeToString(data))
        return data
    }

    return try {
        Yaml().decodeFromString(file.readText())
    } catch (e: Exception) {
        plugin.logger.log(Level.SEVERE, e.message)
        ChannelData()
    }
}

fun ChannelData.save(plugin: JavaPlugin) {
    val file = File(plugin.dataFolder, "channels.yml")

    if (!file.exists()) {
        plugin.dataFolder.mkdirs()
        file.createNewFile()
    }

    file.writeText(Yaml().encodeToString(this))
}
