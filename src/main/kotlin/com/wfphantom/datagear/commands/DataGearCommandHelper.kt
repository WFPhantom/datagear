package com.wfphantom.datagear.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.wfphantom.datagear.commands.InspectCommand.inspectHeldItem
import com.wfphantom.datagear.commands.ModifiersCommand.listAvailableProperties
import com.wfphantom.datagear.commands.ModifiersCommand.listModifiers
import com.wfphantom.datagear.commands.ModifiersCommand.suggestModifierNamespaces
import com.wfphantom.datagear.commands.ModifiersCommand.suggestNamespaces
import com.wfphantom.datagear.commands.TagsCommand.inspectTag
import com.wfphantom.datagear.commands.TagsCommand.listTags
import com.wfphantom.datagear.commands.TagsCommand.suggestTags
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.net.URI

object DataGearCommandHelper {

    fun register() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ -> registerCommands(dispatcher) })
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = buildCommand()
        dispatcher.register(command)
        dispatcher.register(
            Commands.literal("datagear")
                .requires { it.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR) }
                .redirect(dispatcher.register(command))
                .executes { ctx -> inspectHeldItem(ctx) }
        )
    }

    private fun buildCommand() = Commands.literal("dg")
        .requires { it.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR) }
        .then(Commands.literal("tags").executes { ctx -> listTags(ctx) }
            .then(Commands.argument("tag", StringArgumentType.string()).suggests { _, builder -> suggestTags(builder) }.executes { ctx -> inspectTag(ctx) })
        )
        .then(Commands.literal("modifiers").executes { ctx -> listModifiers(ctx) }
            .then(Commands.argument("namespace", StringArgumentType.string()).suggests { _, builder -> suggestModifierNamespaces(builder) }.executes { ctx -> listModifiers(ctx) })
            .then(Commands.literal("list").executes { ctx -> listAvailableProperties(ctx) }
                .then(Commands.argument("namespace", StringArgumentType.string()).suggests { _, builder -> suggestNamespaces(builder) }.executes { ctx -> listAvailableProperties(ctx) })
            )
        )
        .then(Commands.literal("help").executes { ctx -> showHelp(ctx) })
        .executes { ctx -> inspectHeldItem(ctx) }

    fun buildConditionHoverText(reasons: List<String>): Component {
        val root = Component.literal("Conditions not met:\n").withStyle(Style.EMPTY.withColor(ChatFormatting.RED))
        for (reason in reasons) root.append(Component.literal("- $reason\n").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
        return root
    }

    fun showHelp(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val commands = listOf(
            "/dg" to "Inspect held item data and modifiers",
            "/dg tags" to "List all DataGear tags",
            "/dg tags <tag>" to "Show items in a tag",
            "/dg modifiers [ns]" to "List loaded modifiers",
            "/dg modifiers list [ns]" to "List modifiable properties",
        )
        ctx.source.sendSuccess({
            Component.literal("DataGear Help")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
        }, false)
        for ((cmd, desc) in commands) {
            ctx.source.sendSuccess({
                Component.literal("  $cmd ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal("» ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.literal(desc)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
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