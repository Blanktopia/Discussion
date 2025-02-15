package me.weiwen.discussion.replacements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Emoji(
    val unified: String,
    @SerialName("short_names")
    val shortNames: List<String>,
    val category: String,
) {
    val unicode: String
        get() = String(
            unified.split('-').map { it.toInt(16) }.toIntArray(),
            offset = 0,
            length = unified.count { it == '-' } + 1)
}
