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
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object DataGearCommandHelper {

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
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
        .then(Commands.literal("help").executes { ctx -> HelpCommand.showHelp(ctx) })
        .executes { ctx -> inspectHeldItem(ctx) }

    fun buildConditionHoverText(reasons: List<String>): Component {
        val root = Component.literal("Conditions not met:\n").withStyle(Style.EMPTY.withColor(ChatFormatting.RED))
        for (reason in reasons) root.append(Component.literal("- $reason\n").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
        return root
    }
}