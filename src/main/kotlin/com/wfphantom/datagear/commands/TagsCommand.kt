package com.wfphantom.datagear.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import java.util.concurrent.CompletableFuture

object TagsCommand {

    fun listTags(ctx: CommandContext<CommandSourceStack>): Int {
        val tags = BuiltInRegistries.ITEM.getTags().toList()
            .filter { it.key().location().namespace == "datagear" }
            .sortedBy { it.key().location().toString() }

        ctx.source.sendSuccess({
            Component.literal("DataGear Tags (${tags.size})")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
        }, false)

        for (namedSet in tags) {
            val tagStr = namedSet.key().location().toString()
            ctx.source.sendSuccess({
                Component.literal("  #$tagStr ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal("(${namedSet.count()} items)")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            }, false)
        }
        return 1
    }

    fun inspectTag(ctx: CommandContext<CommandSourceStack>): Int {
        val tagName = StringArgumentType.getString(ctx, "tag")
        val tagId = Identifier.tryParse(tagName)

        if (tagId == null) {
            ctx.source.sendFailure(Component.literal("Invalid tag identifier: $tagName"))
            return 0
        }

        val tagKey = TagKey.create(Registries.ITEM, tagId)
        val items = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)
            .map { BuiltInRegistries.ITEM.getKey(it.value()) }
            .sortedBy { it.toString() }
            .toList()

        if (items.isEmpty()) {
            ctx.source.sendFailure(Component.literal("Tag #$tagId not found or is empty."))
            return 0
        }

        ctx.source.sendSuccess({
            Component.literal("#$tagId ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
                .append(Component.literal("(${items.size} items)")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withBold(false)))
        }, false)

        for (item in items) {
            ctx.source.sendSuccess({
                Component.literal("  $item")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
            }, false)
        }
        return 1
    }

    fun suggestTags(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val input = builder.remaining.lowercase().removePrefix("\"")
        BuiltInRegistries.ITEM.getTags().forEach { namedSet ->
            val tagStr = namedSet.key().location().toString()
            if (tagStr.lowercase().contains(input)) builder.suggest("\"$tagStr\"")
        }
        return builder.buildFuture()
    }
}