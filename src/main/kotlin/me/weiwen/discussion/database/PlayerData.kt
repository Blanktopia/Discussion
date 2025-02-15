@file:UseSerializers(TextColorSerializer::class, UUIDSerializer::class)

package me.weiwen.discussion.database

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import me.weiwen.discussion.Discussion.Companion.plugin
import me.weiwen.discussion.serializers.TextColorSerializer
import me.weiwen.discussion.serializers.UUIDSerializer
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import java.util.logging.Level

@Serializable
data class PlayerData(
    @SerialName("0")
    var channel: String = plugin.config.defaultChannels[0],
    @SerialName("1")
    var alternate: String? = plugin.config.defaultChannels[1],
    @SerialName("c")
    val channels: MutableList<String> = plugin.config.defaultChannels.toMutableList(),
)

fun PlayerData.Companion.load(plugin: JavaPlugin, uuid: UUID): PlayerData {
    val file = File(plugin.dataFolder, "players/$uuid.cbor")

    if (!file.exists()) {
        return PlayerData()
    }

    return try {
        Cbor.decodeFromByteArray(file.readBytes())
    } catch (e: Exception) {
        plugin.logger.log(Level.SEVERE, e.message)
        PlayerData()
    }
}

fun PlayerData.save(plugin: JavaPlugin, uuid: UUID) {
    val file = File(plugin.dataFolder, "players/$uuid.cbor")

    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }

    file.writeBytes(Cbor.encodeToByteArray(this))
}
