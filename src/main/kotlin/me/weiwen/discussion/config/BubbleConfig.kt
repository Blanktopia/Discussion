package me.weiwen.discussion.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BubbleConfig(
    val enabled: Boolean = true,

    @SerialName("format")
    var format: String = "<channel-color><message></channel-color>",

    @SerialName("offset")
    val offset: Double = 0.1,

    @SerialName("height")
    val height: Double = 0.25,
)