@file:UseSerializers(TextColorSerializer::class, UUIDSerializer::class)

package me.weiwen.discussion.database

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import me.weiwen.discussion.Discussion
import me.weiwen.discussion.serializers.TextColorSerializer
import me.weiwen.discussion.serializers.UUIDSerializer
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

@Serializable
data class Channel(
    val color: TextColor,
    val password: String? = null,
    val owner: UUID? = null,
    val distance: Double? = null,
    var alias: String? = null,
    var format: String = Discussion.plugin.config.defaultChannelFormat,

    @Transient
    var name: String = "",

    @Transient
    var players: MutableSet<UUID> = mutableSetOf(),
)

fun Channel.audience(location: Location): Collection<Player> =
    if (distance != null) {
        val distanceSquared = distance * distance
        Bukkit.getServer().onlinePlayers.filter { it.world == location.world && it.location.distanceSquared(location) < distanceSquared }
    } else players.mapNotNull { Bukkit.getServer().getPlayer(it) }

