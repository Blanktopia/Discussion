package me.weiwen.discussion.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Messages(
    @SerialName("channel-set")
    val channelSet: String = "<gold>Channel set: <channel> (#: <alternate>).</gold>",

    @SerialName("channel-joined")
    val channelJoined: String = "<gold>Channel joined: <channel>.</gold>",

    @SerialName("channel-left")
    val channelLeft: String = "<gold>Channel left: <channel>.</gold>",

    @SerialName("list-channels")
    val listChannels: String = "<gold>Channels: <channels>.</gold>",

    @SerialName("invite-received")
    val inviteReceived: String = "<click:run_command:/chat join <channel> <password>><gold><player> invited you to join chat: <channel>. Click here to join.</gold></click>",

    @SerialName("invite-sent")
    val inviteSent: String = "<gold>Invited <player> to join chat: <channel>.</gold>",

    @SerialName("player-joined-channel")
    val playerJoinedChannel: String = "<gold><player> joined channel: <channel>.</gold>",

    @SerialName("channel-info")
    val channelInfo: String = "<gold>Channel: <channel>\nOnline players: <players></gold>",

    @SerialName("error-channel-needs-password")
    val errorChannelNeedsPassword: String = "<red><channel> needs a password to join.</red>",

    @SerialName("error-channel-not-found")
    val errorChannelNotFound: String = "<red>No such channel: <channel>.</red>",

    @SerialName("error-wrong-password")
    val errorWrongPassword: String = "<red>Wrong password.</red>",

    @SerialName("error-cannot-leave-channel")
    val errorCannotLeaveChannel: String = "<red>Cannot leave channel: <channel>.</red>",

    @SerialName("error-channel-already-exists")
    val errorChannelAlreadyExists: String = "<red>This channel already exists.</red>",

    @SerialName("error-no-alternate-channel")
    val errorNoAlternateChannel: String = "<red>You have no alternate channels.</red>",

    @SerialName("error-already-joined")
    val errorAlreadyJoined: String = "<red>You are already in channel: <channel>.</red>",

    @SerialName("error-player-already-joined")
    val errorPlayerAlreadyJoined: String = "<red>Player already in channel: <channel>.</red>",

    @SerialName("no-one-nearby")
    val noOneNearby: String = "<gray>No one is nearby to hear you.</gray>",
)