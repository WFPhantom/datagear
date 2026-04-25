package com.wfphantom.datagear.commands

import com.mojang.brigadier.context.CommandContext
import com.wfphantom.datagear.commands.DataGearCommandHelper.buildConditionHoverText
import com.wfphantom.datagear.engine.ModifierEngine
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

object InspectCommand {

    fun inspectHeldItem(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val stack = player.mainHandItem

        if (stack.isEmpty) {
            ctx.source.sendFailure(Component.literal("You must be holding an item!"))
            return 0
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val defaultStack = stack.item.defaultInstance
        val props = ModifierEngine.getItemProperties(defaultStack, stack)

        val itemTags = BuiltInRegistries.ITEM.get(itemId)
            .orElse(null)?.tags()
            ?.map { it.location().toString() }
            ?.sorted()
            ?.toList() ?: emptyList()

        ctx.source.sendSuccess({
            Component.literal(stack.hoverName.string)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
                .append(Component.literal(" ($itemId)")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withBold(false)))
        }, false)

        if (itemTags.isNotEmpty()) {
            ctx.source.sendSuccess({
                Component.literal("Tags: ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal(itemTags.joinToString(", "))
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
            }, false)
        }

        ctx.source.sendSuccess({
            Component.literal("Properties")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
        }, false)

        for ((key, value) in props) {
            ctx.source.sendSuccess({
                Component.literal("  $key ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                    .append(Component.literal("» ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.literal(value.toString())
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
            }, false)
        }

        val activeModifiers = ModifierEngine.getModifiers().filter { ModifierEngine.modifierTargetsItem(it, itemId) }
        if (activeModifiers.isNotEmpty()) {
            ctx.source.sendSuccess({
                Component.literal("Modifiers")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
            }, false)

            for (mod in activeModifiers) {
                val conditionsMet = mod.conditions == null || ModifierEngine.evaluateCondition(mod.conditions, stack)
                val allMods = mod.modifiers.entries.map { "${it.key}=${it.value}" } +
                        mod.stringModifiers.entries.map { "${it.key}=${it.value}" } +
                        mod.booleanModifiers.entries.map { "${it.key}=${it.value}" } +
                        mod.listModifiers.entries.map { "${it.key}=${it.value}" }
                val slotSuffix = if (mod.slot != null) " [${mod.slot}]" else ""

                val message = Component.literal("  ${mod.id} ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                    .append(Component.literal("(${mod.operation.name.lowercase()}: {${allMods.joinToString(", ")}}$slotSuffix) ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))

                if (conditionsMet) message.append(Component.literal("✔").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)))
                else {
                    val reasons = ModifierEngine.getConditionFailureReasons(mod.conditions, stack)
                    val hoverText = buildConditionHoverText(reasons)
                    message.append(
                        Component.literal("✘").withStyle(
                            Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withHoverEvent(HoverEvent.ShowText(hoverText))
                        )
                    )
                }
                ctx.source.sendSuccess({ message }, false)
            }
        }
        return 1
    }
}