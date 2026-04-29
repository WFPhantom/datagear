package com.wfphantom.datagear.commands

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.net.URI

object HelpCommand {
    fun showHelp(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val commands = listOf(
            "/dg" to "Inspect held item data and modifiers",
            "/dg tags" to "List all DataGear tags",
            "/dg tags <tag>" to "Show items in a tag",
            "/dg modifiers [ns]" to "List loaded modifiers",
            "/dg modifiers list [ns]" to "List modifiable properties",
        )
        ctx.source.sendSuccess({
            Component.literal("DataGear Help").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)) }, false)
        for ((cmd, desc) in commands) {
            ctx.source.sendSuccess({
                Component.literal("  $cmd ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal("» ").withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.literal(desc).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            }, false)
        }
        ctx.source.sendSuccess({
            Component.literal("  Wiki: ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                .append(
                    Component.literal("https://github.com/WFPhantom/datagear/wiki")
                        .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(ClickEvent.OpenUrl(URI("https://github.com/WFPhantom/datagear/wiki")))
                        )
                )
        }, false)
        return 1
    }
}