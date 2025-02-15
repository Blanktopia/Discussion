package me.weiwen.discussion.replacements

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.weiwen.discussion.Discussion.Companion.plugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.format.TextColor
import java.io.File
import java.util.*
import java.util.logging.Level
import java.util.regex.Pattern

object EmojiManager {
    private lateinit var rawEmojis: List<Emoji>
    private lateinit var emojis: SortedMap<String, String>
    private lateinit var names: List<String>
    private lateinit var replacementConfig: TextReplacementConfig

    fun onEnable() {
        loadEmojis()
    }

    private fun loadEmojis() {
        plugin.saveResource("emoji.json", true)
        val file = File(plugin.dataFolder, "emoji.json")

        rawEmojis = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(file.readText())
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, e.message)
            listOf()
        }

        emojis = sortedMapOf()
        for (emoji in rawEmojis) {
            if (emoji.unified.count { it == '-' } != 0) {
                continue
            }
            val unicode = emoji.unicode
            for (shortName in emoji.shortNames) {
                emojis[":$shortName:"] = unicode
            }
        }
        names = emojis.keys.sortedByDescending { it.length }

        replacementConfig = TextReplacementConfig.builder()
            .match(Pattern.compile("(:.*?:)"))
            .replacement { matchResult ->
                val emoji = emojis[matchResult.content()] ?: return@replacement matchResult
                Component.text(emoji)
                    .color(TextColor.color(0xffffff))
                    .hoverEvent(Component.text("${matchResult.content()}\nShift-Click to insert").asHoverEvent())
                    .insertion(matchResult.content())
            }
            .build()
    }

    fun listAsComponent(): Component {
        return rawEmojis
            .filter { emoji -> emoji.unified.count { it == '-' } == 0 }
            .mapNotNull { emoji ->
                val shortName = emoji.shortNames.firstOrNull() ?: return@mapNotNull null
                Component.text(emoji.unicode)
                    .color(TextColor.color(0xffffff))
                    .hoverEvent(Component.text(":$shortName:\nShift-Click to insert").asHoverEvent())
                    .insertion(":$shortName:")
            }.fold(Component.text("")) { acc, x -> acc.append(x) }
    }

    fun emojify(message: Component): Component {
        return message.replaceText(replacementConfig)
    }

    fun emojify(msg: String): String {
        var message = msg
        var replaced = StringBuilder()
        var previousPosition = 0

        // Go through all shortcuts
        for (key in names) {
            // If the message has the shortcut
            if (message.contains(key)) {
                // Find location in string of occurrences of shortcut (going forward)
                var i = -1
                while (message.indexOf(key, i + 1).also { i = it } != -1) {

                    // If character before shortcut is not an escape character
                    previousPosition =
                        if (i - 1 < 0 || message[i - 1] != '\\') { // Then replace shortcut with emoji
                            replaced.append(message.substring(previousPosition, i))
                                .append(emojis.get(key)) // Add previous text and emoji, but not anything after to prevent replacement issue later
                            i + key.length
                        } else { // Otherwise remove backslash as it's cancelling an emoji
                            replaced.append(message.substring(previousPosition, i - 1)).append(
                                message.substring(
                                    i,
                                    i + key.length
                                )
                            ) // Add previous text and remove backslash, exclude the matched key but not anything after
                            i + key.length
                        }
                    i++
                }
            }
            // Reset necessary variables for next shortcut
            replaced.append(message.substring(previousPosition)) // Add remaining text
            previousPosition = 0
            message = replaced.toString()
            replaced = StringBuilder()
        }

        return message
    }
}