package me.weiwen.discussion

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.clip.placeholderapi.PlaceholderAPI
import me.weiwen.discussion.Discussion.Companion.plugin
import me.weiwen.discussion.database.*
import me.weiwen.discussion.hooks.DiscordSrvHook
import me.weiwen.discussion.replacements.EmojiManager
import me.weiwen.discussion.replacements.EmojiManager.emojify
import me.weiwen.discussion.replacements.itemify
import me.weiwen.discussion.replacements.linkify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.format.NamedTextColor


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
        val audience = channel.audience(player.location)
        audience.forEach {
            it.sendMessage(formattedMessage)
        }
        if (channel.distance != null && audience.all { it == player }) {
            player.sendMessage(miniMessage.deserialize(plugin.config.messages.noOneNearby))
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
        val emojisNode = Commands.literal("emojis")
            .requires { it.sender.hasPermission("discussion.command.emojis") }
            .executes { ctx ->
                ctx.source.sender.sendMessage(EmojiManager.listAsComponent())
                Command.SINGLE_SUCCESS
            }
            .build()

        val chatNode = Commands.literal("chat")
            .requires { it.sender.hasPermission("discussion.command.chat") && it.executor is Player }
            .then(
                Commands.literal("make")
                    .then(
                        Commands.argument("channel", StringArgumentType.string())
                            .then(
                                Commands.argument("color", StringArgumentType.string())
                                    .suggests { ctx, builder ->
                                        plugin.config.colors.forEach { builder.suggest(it.toString()) }
                                        builder.buildFuture()
                                    }
                                    .then(
                                        Commands.argument("password", StringArgumentType.string())
                                            .executes(::handleMakeChannel)
                                    )
                                    .executes(::handleMakeChannel)
                            )
                    )
            )
            .then(
                Commands.literal("leave")
                    .then(
                        Commands.argument("channel", StringArgumentType.string())
                            .suggests { ctx, builder ->
                                channels.keys.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes(::handleLeaveChannel)
                    )
            )
            .then(
                Commands.literal("invite")
                    .then(
                        Commands.argument("player", StringArgumentType.string())
                            .suggests { ctx, builder ->
                                plugin.server.onlinePlayers.forEach { builder.suggest(it.name) }
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("channel", StringArgumentType.string())
                                    .suggests { ctx, builder ->
                                        channels.keys.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .executes(::handleInviteChannel)
                            )
                    )
            )
            .then(
                Commands.literal("join")
                    .then(
                        Commands.argument("channel", StringArgumentType.string())
                            .suggests { ctx, builder ->
                                channels.values.map { it.name }.toSet().forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("password", StringArgumentType.string())
                                    .executes(::handleJoinChannel)
                            )
                            .executes(::handleJoinChannel)
                    )
            )
            .then(
                Commands.literal("info")
                    .then(
                        Commands.argument("channel", StringArgumentType.string())
                            .suggests { ctx, builder ->
                                channels.keys.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes(::handleChannelInfo)
                    )
                    .executes(::handleChannelInfo)
            )
            .then(
                Commands.literal("list")
                    .executes(::handleListChannels)
            )
            .build()

        val chNode = Commands.literal("ch")
            .requires { it.sender.hasPermission("discussion.command.chat") && it.executor is Player }
            .then(
                Commands.argument("channel", StringArgumentType.string())
                    .suggests { ctx, builder ->
                        channels.keys.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes(::handleSwitchChannel)
            )
            .build()

        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, {
            it.registrar().register(emojisNode)
            it.registrar().register(chatNode)
            it.registrar().register(chNode)
        })
    }

    private fun handleMakeChannel(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val alias = StringArgumentType.getString(ctx, "channel")
        val password = StringArgumentType.getString(ctx, "password")

        if (channels[alias.lowercase()] != null) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorChannelAlreadyExists,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
        }

        val channel = Channel(
            ctx.getArgument("color", NamedTextColor::class.java),
            password.ifEmpty { null },
            name = alias,
            players = mutableSetOf(player.uniqueId),
            owner = player.uniqueId
        )
        channels[alias.lowercase()] = channel
        saveChannels()

        val data = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS
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
        return Command.SINGLE_SUCCESS
    }

    private fun handleLeaveChannel(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val alias = StringArgumentType.getString(ctx, "channel")

        val channel = channels[alias.lowercase()]

        if (channel == null) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorChannelNotFound,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
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
            return Command.SINGLE_SUCCESS
        }

        channel.players.remove(player.uniqueId)
        if (channel.players.isEmpty()) {
            channels.remove(channel.name.lowercase())
            channel.alias?.let { channels.remove(it.lowercase()) }
            saveChannels()
        }

        val data = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS
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
        return Command.SINGLE_SUCCESS
    }

    private fun handleInviteChannel(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val thisData = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS

        val otherResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val other: Player = otherResolver.resolve(ctx.source)[0]
        val otherData = playerData[other.uniqueId] ?: return Command.SINGLE_SUCCESS

        val alias = StringArgumentType.getString(ctx, "channel")

        val channel = channels[alias.lowercase()]

        if (channel == null || !thisData.channels.contains(channel.name)) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorChannelNotFound,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
        }

        if (otherData.channels.contains(channel.name)) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorAlreadyJoined,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
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
        return Command.SINGLE_SUCCESS
    }

    private fun handleJoinChannel(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val alias = StringArgumentType.getString(ctx, "channel")

        val channel = channels[alias.lowercase()]

        if (channel == null) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorChannelNotFound,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
        }

        val data = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS
        if (data.channels.contains(channel.name)) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorAlreadyJoined,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
        }

        if (channel.password != null) {
            val password = StringArgumentType.getString(ctx, "password")
            if (password.isEmpty()) {
                player.sendMessage(
                    miniMessage.deserialize(
                        plugin.config.messages.errorChannelNeedsPassword,
                        Placeholder.component(
                            "channel",
                            Component.text(channel.name).color(channel.color)
                        )
                    )
                )
                return Command.SINGLE_SUCCESS
            } else if (channel.password != password) {
                player.sendMessage(
                    miniMessage.deserialize(
                        plugin.config.messages.errorWrongPassword,
                        Placeholder.component(
                            "channel",
                            Component.text(channel.name).color(channel.color)
                        )
                    )
                )
                return Command.SINGLE_SUCCESS
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
        return Command.SINGLE_SUCCESS
    }

    private fun handleChannelInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val data = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS
        val channel =
            channels[StringArgumentType.getString(ctx, "channel")] ?: return Command.SINGLE_SUCCESS

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
        return Command.SINGLE_SUCCESS
    }

    private fun handleListChannels(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player
        val data = playerData[player.uniqueId] ?: return Command.SINGLE_SUCCESS
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
        return Command.SINGLE_SUCCESS
    }

    private fun handleSwitchChannel(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.executor as Player

        val alias = StringArgumentType.getString(ctx, "channel")
        val channel = channels[alias.lowercase()]

        if (channel == null) {
            player.sendMessage(
                miniMessage.deserialize(
                    plugin.config.messages.errorChannelNotFound,
                    Placeholder.unparsed("channel", alias)
                )
            )
            return Command.SINGLE_SUCCESS
        }

        setChannel(player, channel)
        return Command.SINGLE_SUCCESS
    }

}