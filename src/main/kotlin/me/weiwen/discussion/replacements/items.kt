package me.weiwen.discussion.replacements

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

fun itemify(message: Component, player: Player): Component {
    return message.replaceText { builder ->
        builder.match(":item:")
            .replacement { _ ->
                val item = player.inventory.itemInMainHand
                val displayName = if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
                    item.itemMeta.displayName()!!
                } else {
                    Component.translatable(item.type.translationKey())
                }
                if (item.amount <= 1) {
                    Component.text("[")
                        .append(displayName)
                        .append(Component.text("]"))
                        .hoverEvent(item.asHoverEvent())
                } else {
                    Component.text("[")
                        .append(displayName)
                        .append(Component.text(" x ${item.amount}"))
                        .append(Component.text("]"))
                        .hoverEvent(item.asHoverEvent())
                }
            }
    }
}
