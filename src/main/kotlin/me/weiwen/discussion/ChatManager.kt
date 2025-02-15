package me.weiwen.discussion

import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.bukkit.parsers.PlayerArgument
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.minecraft.extras.TextColorArgument
import cloud.commandframework.paper.PaperCommandManager
import me.clip.placeholderapi.PlaceholderAPI
import me.weiwen.discussion.Discussion.Companion.plugin
import me.weiwen.discussion.database.*
import me.weiwen.discussion.hooks.DiscordSrvHook
import me.weiwen.discussion.replacements.EmojiManager
import me.weiwen.discussion.replacements.EmojiManager.emojify
import me.weiwen.discussion.replacements.itemify
import me.weiwen.discussion.replacements.linkify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.awt.SystemColor.text
import java.util.*
import java.util.function.Function

object ChatManager {
    lateinit var channelData: ChannelData
    val channels: MutableMap<String, Channel> = mutableMapOf()
    val playerData: MutableMap<UUID, PlayerData> = mutableMapOf()
    val miniMessage: MiniMessage by lazy { MiniMessage.builder().strict(true).build() }

    fun onEnable() {
        channelData = ChannelData.load(plugin)
        registerCommands()
        initializeChannels()
        plugin.server.onlinePlayers.forEach { loadPlayer(it.uniqueId) }
    }

    fun onDisable() {
        plugin.server.onlinePlayers.forEach { savePlayer(it.uniqueId) }
    }

    fun broadcastMessage(player: Player, message: String, channel: Channel) {
        val formattedMessage = formatMessage(player, Component.text(message), channel)
        channel.audience(player.location).forEach {
            it.sendMessage(formattedMessage)
        }
        DiscordSrvHook.processChatMessage(player, message, channel.name, false)
        plugin.logger.info(LegacyComponentSerializer.legacySection().serialize(formattedMessage))
    }

    fun setChannel(player: Player, channel: Channel) {
        val data = playerData[player.uniqueId] ?: return

        val oldChannel = channels[data.channel.lowercase()]
        if (oldChannel != channel) {
            data.alternate = data.channel
            data.channel = channel.name
        }

        val alternate = data.alternate?.let { channels[it.lowercase()] }
        player.sendMessage(
            miniMessage.deserialize(
                plugin.config.messages.channelSet,
                Placeholder.component("channel", Component.text(channel.name).color(channel.color)),
                Placeholder.component(
                    "alternate",
                    if (alternate != null) Component.text(alternate.name)
                        .color(alternate.color) else Component.text("None")
                )
            )
        )
    }


    private fun formatMessage(player: Player, msg: Component, channel: Channel): Component {
        val message = emojify(itemify(linkify(msg), player))

        var format = channel.format
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = PlaceholderAPI.setPlaceholders(player, format)
        }

        return miniMessage.deserialize(
            format,
            Placeholder.unparsed("channel", channel.name),
            Placeholder.unparsed("alias", channel.alias ?: channel.name),
            Placeholder.component("player", player.displayName()),
            Placeholder.component("message", message),
            TagResolver.resolver("channel-color", Tag.styling(channel.color))
        )
    }

    fun loadPlayer(uuid: UUID) {
        playerData[uuid] = PlayerData.load(plugin, uuid)
        playerData[uuid]?.channels?.forEach {
            val channel = channels[it.lowercase()] ?: return@forEach
            channel.players.add(uuid)
        }
    }

    private fun savePlayer(uuid: UUID) {
        playerData[uuid]?.save(plugin, uuid)
    }

    fun saveAndRemovePlayer(uuid: UUID) {
        savePlayer(uuid)
        playerData[uuid]?.channels?.forEach {
            val channel = channels[it.lowercase()] ?: return@forEach
            channel.players.remove(uuid)
        }
        playerData.remove(uuid)
    }

    private fun saveChannels() {
        channelData.channels = channels.values.toSet().associateBy { it.name }
        channelData.save(plugin)
    }

    private fun initializeChannels() {
        val defaultChannels = plugin.config.defaultChannels

        for ((name, channel) in channelData.channels.entries) {
            channel.name = name
            if (defaultChannels.contains(name)) {
                channel.players = plugin.server.onlinePlayers.map { it.uniqueId }.toMutableSet()
            }
            channels[name.lowercase()] = channel
            channel.alias?.let { channels[it.lowercase()] = channel }
        }
    }

    private fun registerCommands() {
        val manager = PaperCommandManager(
            plugin,
            CommandExecutionCoordinator.simpleCoordinator(),
            Function.identity(),
            Function.identity(),
        )

        try {
            manager.registerBrigadier()
            if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                manager.registerAsynchronousCompletions()
            }
            plugin.logger.info("Registered commands.")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to initialize Brigadier support: " + e.message)
        }

        manager.command(manager.commandBuilder("emojis")
            .permission("discussion.command.emojis")
            .handler { ctx ->
                ctx.sender.sendMessage(EmojiManager.listAsComponent())
            })

        manager.commandBuilder("chat", "ch")
            .permission("discussion.command.chat")
            .senderType(Player::class.java).let { builder ->
                manager.command(builder.literal("make")
                    .argument(StringArgument.of("channel"))
                    .argument(TextColorArgument.of("color"))
                    .argument(StringArgument.optional("password"))
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val alias = ctx.get<String>("channel")
                        val password = ctx.getOptional<String>("password")

                        if (channels[alias.lowercase()] != null) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorChannelAlreadyExists,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        val channel = Channel(
                            ctx.get("color"),
                            if (password.isPresent) password.get() else null,
                            name = alias,
                            players = mutableSetOf(player.uniqueId),
                            owner = player.uniqueId
                        )
                        channels[alias.lowercase()] = channel
                        saveChannels()

                        val data = playerData[player.uniqueId] ?: return@handler
                        data.channels.add(channel.name)
                        setChannel(player, channel)

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.channelJoined,
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                )
                            )
                        )
                    })

                manager.command(builder.literal("leave")
                    .argument(StringArgument.of("channel"))
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val alias = ctx.get<String>("channel")

                        val channel = channels[alias.lowercase()]

                        if (channel == null) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorChannelNotFound,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        if (channel.name in plugin.config.defaultChannels) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorCannotLeaveChannel,
                                    Placeholder.component(
                                        "channel",
                                        Component.text(channel.name).color(channel.color)
                                    )
                                )
                            )
                            return@handler
                        }

                        channel.players.remove(player.uniqueId)
                        if (channel.players.isEmpty()) {
                            channels.remove(channel.name.lowercase())
                            channel.alias?.let { channels.remove(it.lowercase()) }
                            saveChannels()
                        }

                        val data = playerData[player.uniqueId] ?: return@handler
                        data.channels.remove(channel.name)
                        if (data.channel == channel.name) {
                            data.channel = data.channels.first()
                        }

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.channelLeft,
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                )
                            )
                        )
                    })

                manager.command(builder.literal("invite")
                    .argument(PlayerArgument.of("player"))
                    .argument(StringArgument.of("channel"))
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val thisData = playerData[player.uniqueId] ?: return@handler

                        val other = ctx.get<Player>("player")
                        val otherData = playerData[other.uniqueId] ?: return@handler

                        val alias = ctx.get<String>("channel")

                        val channel = channels[alias.lowercase()]

                        if (channel == null || !thisData.channels.contains(channel.name)) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorChannelNotFound,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        if (otherData.channels.contains(channel.name)) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorAlreadyJoined,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        channel.players.add(player.uniqueId)

                        thisData.channels.add(channel.name)
                        setChannel(player, channel)

                        other.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.inviteReceived,
                                Placeholder.component("player", player.displayName()),
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                ),
                                Placeholder.unparsed("password", channel.password ?: ""),
                            )
                        )

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.inviteSent,
                                Placeholder.component("player", other.displayName()),
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                )
                            )
                        )
                    })

                manager.command(builder.literal("join")
                    .argument(StringArgument.of("channel"))
                    .argument(StringArgument.optional("password"))
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val alias = ctx.get<String>("channel")

                        val channel = channels[alias.lowercase()]

                        if (channel == null) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorChannelNotFound,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        val data = playerData[player.uniqueId] ?: return@handler
                        if (data.channels.contains(channel.name)) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorAlreadyJoined,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        if (channel.password != null) {
                            val password = ctx.getOptional<String>("password")
                            if (password.isEmpty) {
                                player.sendMessage(
                                    miniMessage.deserialize(
                                        plugin.config.messages.errorChannelNeedsPassword,
                                        Placeholder.component(
                                            "channel",
                                            Component.text(channel.name).color(channel.color)
                                        )
                                    )
                                )
                                return@handler
                            } else if (channel.password != password.get()) {
                                player.sendMessage(
                                    miniMessage.deserialize(
                                        plugin.config.messages.errorWrongPassword,
                                        Placeholder.component(
                                            "channel",
                                            Component.text(channel.name).color(channel.color)
                                        )
                                    )
                                )
                                return@handler
                            }
                        }

                        channel.players.add(player.uniqueId)

                        data.channels.add(channel.name)
                        setChannel(player, channel)

                        val message = miniMessage.deserialize(
                            plugin.config.messages.playerJoinedChannel,
                            Placeholder.component("player", player.displayName()),
                            Placeholder.component("channel", Component.text(channel.name).color(channel.color))
                        )
                        channel.players.forEach {
                            plugin.server.getPlayer(it)?.sendMessage(message)
                        }

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.channelJoined,
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                )
                            )
                        )
                    })

                manager.command(builder.literal("info")
                    .argument(StringArgument.optional("channel"))
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val data = playerData[player.uniqueId] ?: return@handler
                        val channel =
                            channels[ctx.getOptional<String>("channel").orElse(data.channel)] ?: return@handler

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.channelInfo,
                                Placeholder.component(
                                    "channel",
                                    Component.text(channel.name).color(channel.color)
                                ),
                                Placeholder.parsed("players", channel.players.joinToString(", ") {
                                    plugin.server.getPlayer(it)?.let {
                                        miniMessage.serialize(it.displayName())
                                    } ?: "Unknown"
                                })
                            )
                        )
                    })

                manager.command(builder.literal("list")
                    .handler { ctx ->
                        val player = ctx.sender as Player
                        val data = playerData[player.uniqueId] ?: return@handler
                        val channels =
                            data.channels.joinToString(", ") {
                                val channel = channels[it.lowercase()] ?: return@joinToString "Unknown"
                                when (it) {
                                    data.channel ->
                                        "<underlined>" +
                                                "<hover:show_text:'<gold>Alias: <white>${channel.alias}'>" +
                                                "<click:run_command:/${channel.alias ?: channel.name}>" +
                                                "<color:${channel.color.asHexString()}>" +
                                                channel.name +
                                                "</color:${channel.color.asHexString()}>" +
                                                "</click></hover></underlined>"
                                    data.alternate ->
                                        "<hover:show_text:'<gold>Alias: <white>${channel.alias}'>" +
                                                "<click:run_command:/${channel.alias ?: channel.name}>" +
                                                "<color:${channel.color.asHexString()}>" +
                                                channel.name +
                                                "</color:${channel.color.asHexString()}>" +
                                                "</click></hover>"
                                    else ->
                                        "<hover:show_text:'<gold>Alias: <white>${channel.alias}'>" +
                                                "<click:run_command:/${channel.alias ?: channel.name}>" +
                                                "<color:${channel.color.asHexString()}>" +
                                                channel.name +
                                                "</color:${channel.color.asHexString()}>" +
                                                "</click></hover>"
                                }
                            }

                        player.sendMessage(
                            miniMessage.deserialize(
                                plugin.config.messages.listChannels,
                                Placeholder.parsed("channels", channels),
                            )
                        )
                    })

                manager.command(builder.argument(StringArgument.of("channel"))
                    .handler { ctx ->
                        val player = ctx.sender as Player

                        val alias = ctx.get<String>("channel")
                        val channel = channels[alias.lowercase()]

                        if (channel == null) {
                            player.sendMessage(
                                miniMessage.deserialize(
                                    plugin.config.messages.errorChannelNotFound,
                                    Placeholder.unparsed("channel", alias)
                                )
                            )
                            return@handler
                        }

                        setChannel(player, channel)
                    })
            }
    }
}