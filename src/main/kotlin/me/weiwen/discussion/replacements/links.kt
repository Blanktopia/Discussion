package me.weiwen.discussion.replacements

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration

fun linkify(message: Component): Component {
    return message.replaceText { builder ->
        builder
            .match("(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
            .replacement { matchResult, _ ->
                Component.text(matchResult.group())
                    .clickEvent(ClickEvent.openUrl(matchResult.group()))
                    .hoverEvent(Component.text("Open URL: ${matchResult.group()}").asHoverEvent())
                    .decorate(TextDecoration.UNDERLINED)
            }
    }
}
