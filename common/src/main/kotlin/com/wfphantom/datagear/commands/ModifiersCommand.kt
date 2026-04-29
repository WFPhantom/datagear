package com.wfphantom.datagear.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.wfphantom.datagear.engine.ModifierEngine
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.concurrent.CompletableFuture

object ModifiersCommand {

    fun listModifiers(ctx: CommandContext<CommandSourceStack>): Int {
        val namespaceFilter = try { StringArgumentType.getString(ctx, "namespace") } catch (_: Exception) { null }
        val filteredModifiers = ModifierEngine.getModifiers().let { all -> if (namespaceFilter != null) all.filter { it.id.namespace == namespaceFilter } else all }

        if (filteredModifiers.isEmpty()) {
            ctx.source.sendFailure(Component.literal(
                if (namespaceFilter != null) "No active modifiers found for namespace: $namespaceFilter"
                else "No active modifiers loaded."
            ))
            return 0
        }

        ctx.source.sendSuccess({
            Component.literal(if (namespaceFilter != null) "Modifiers: $namespaceFilter (${filteredModifiers.size})" else "Modifiers (${filteredModifiers.size})")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
        }, false)

        for (mod in filteredModifiers.sortedBy { it.id.toString() }) {
            ctx.source.sendSuccess({
                Component.literal("  ${mod.id} ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal("» ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.literal(mod.targetDisplay)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                    .append(Component.literal(" [${mod.operation.name.lowercase()}]")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)))
            }, false)
        }
        return 1
    }

    fun listAvailableProperties(ctx: CommandContext<CommandSourceStack>): Int {
        val namespaceFilter = try { StringArgumentType.getString(ctx, "namespace") } catch (_: Exception) { null }
        val filteredProps = ModifierEngine.getAvailableProperties().let { all ->
            if (namespaceFilter != null) all.filter { (key, _) ->
                if (key.contains(":")) key.startsWith("$namespaceFilter:") else namespaceFilter == "minecraft" }
            else all
        }

        if (filteredProps.isEmpty()) {
            ctx.source.sendFailure(Component.literal(
                if (namespaceFilter != null) "No modifiable properties found for namespace: $namespaceFilter"
                else "No properties found."
            ))
            return 0
        }

        ctx.source.sendSuccess({
            Component.literal(if (namespaceFilter != null) "Properties: $namespaceFilter (${filteredProps.size})" else "Properties (${filteredProps.size})")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
        }, false)

        for ((prop, type) in filteredProps) {
            ctx.source.sendSuccess({
                Component.literal("  $prop ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal("($type)")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            }, false)
        }
        return 1
    }

    fun suggestModifierNamespaces(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val input = builder.remaining.lowercase()
        ModifierEngine.getModifiers().map { it.id.namespace }.distinct().sorted()
            .filter { it.lowercase().contains(input) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    fun suggestNamespaces(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val input = builder.remaining.lowercase()
        ModifierEngine.getAvailableProperties().keys
            .map { if (it.contains(":")) it.substringBefore(":") else "minecraft" }
            .distinct().sorted()
            .filter { it.lowercase().contains(input) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}