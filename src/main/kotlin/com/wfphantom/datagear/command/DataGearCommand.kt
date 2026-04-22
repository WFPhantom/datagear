package com.wfphantom.datagear.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.wfphantom.datagear.engine.ModifierEngine
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey

// This is nightmare, you have been warned
object DataGearCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ -> registerCommands(dispatcher) })
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("datagear")
                .then(Commands.literal("tags").executes { ctx -> listTags(ctx) }
                    .then(Commands.argument("tag", StringArgumentType.string()).suggests { _, builder -> suggestTags(builder) }.executes { ctx -> inspectTag(ctx) })
                )
                .then(Commands.literal("modifiers").executes { ctx -> listModifiers(ctx) })
                .then(Commands.literal("help").executes { ctx -> showHelp(ctx) })
                .executes { ctx -> inspectHeldItem(ctx) }
        )
    }

    /**
     * /datagear - Shows data for the currently held item
     */
    private fun inspectHeldItem(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val stack = player.mainHandItem

        if (stack.isEmpty) {
            ctx.source.sendFailure(Component.literal("§cYou must be holding an item!"))
            return 0
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        // Use the item's current default instance to reflect applied modifiers,
        // but also pass the held stack for runtime only modded components
        val defaultStack = stack.item.defaultInstance
        val props = ModifierEngine.getItemProperties(defaultStack, stack)

        val itemTags = BuiltInRegistries.ITEM.get(itemId)
            .orElse(null)?.tags()
            ?.map { it.location().toString() }
            ?.sorted()
            ?.toList() ?: emptyList()

        ctx.source.sendSuccess({ Component.literal("§6§l=== §f${stack.hoverName.string} §6§l===") }, false)

        ctx.source.sendSuccess({ Component.literal("§7Item ID: §f$itemId") }, false)

        if (itemTags.isNotEmpty()) ctx.source.sendSuccess({ Component.literal("§7Item Tags: §f${itemTags.joinToString(", ")}") }, false)

        ctx.source.sendSuccess({ Component.literal("§e--- Properties ---") }, false)
        for ((key, value) in props)  ctx.source.sendSuccess({ Component.literal("  §7$key: §f$value") }, false)
        // Show active modifiers affecting this item
        val activeModifiers = ModifierEngine.getModifiers().filter { modifier -> ModifierEngine.modifierTargetsItem(modifier, itemId)
        }

        if (activeModifiers.isNotEmpty()) {
            ctx.source.sendSuccess({ Component.literal("§e--- Active Modifiers ---") }, false)
            for (mod in activeModifiers) {
                val conditionsMet = mod.conditions == null || ModifierEngine.evaluateCondition(mod.conditions, stack)
                val allMods = mod.modifiers.map { (k, v) -> "$k=$v" } + mod.stringModifiers.map { (k, v) -> "$k=$v" } + mod.booleanModifiers.map { (k, v) -> "$k=$v" }
                val slotSuffix = if (mod.slot != null) " [${mod.slot}]" else ""
                val modInfo = "  §7${mod.id} §8(${mod.operation.name.lowercase()}: {${allMods.joinToString(", ")}}$slotSuffix) "
                val message = Component.literal(modInfo)
                if (conditionsMet) message.append(Component.literal("§a✔"))
                else {
                    val reasons = ModifierEngine.getConditionFailureReasons(mod.conditions, stack)
                    val hoverText = buildConditionHoverText(reasons)
                    val crossMark = Component.literal("§c✘").withStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowText(hoverText)))
                    message.append(crossMark)
                }
                ctx.source.sendSuccess({ message }, false)
            }
        }
        return 1
    }

    /**
     * /datagear tags - Lists all registered DataGear tags
     */
    private fun listTags(ctx: CommandContext<CommandSourceStack>): Int {
        val tags = BuiltInRegistries.ITEM.getTags().filter { it.key().location().namespace == "datagear" }.toList()

        ctx.source.sendSuccess({ Component.literal("§6§l=== DataGear Tags (${tags.size}) ===") }, false)

        for (namedSet in tags.sortedBy { it.key().location().toString() }) {
            val tagStr = namedSet.key().location().toString()
            val count = namedSet.count()
            ctx.source.sendSuccess({ Component.literal("  §e#$tagStr §7($count items)") }, false)
        }
        return 1
    }

    /**
     * /datagear tags <tag> - Shows items in a specific tag
     */
    private fun inspectTag(ctx: CommandContext<CommandSourceStack>): Int {
        val tagName = StringArgumentType.getString(ctx, "tag")
        val tagId = Identifier.tryParse(tagName)

        if (tagId == null) {
            ctx.source.sendFailure(Component.literal("§cInvalid tag identifier: $tagName"))
            return 0
        }

        val tagKey = TagKey.create(Registries.ITEM, tagId)
        val items = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey).map { BuiltInRegistries.ITEM.getKey(it.value()) }.toList()

        if (items.isEmpty()) {
            ctx.source.sendFailure(Component.literal("§cTag #$tagId not found or is empty."))
            return 0
        }

        ctx.source.sendSuccess({ Component.literal("§6§l=== #$tagId (${items.size} items) ===") }, false)
        for (item in items.sortedBy { it.toString() }) {
            ctx.source.sendSuccess({ Component.literal("  §f$item") }, false)
        }
        return 1
    }

    /**
     * /datagear modifiers - Lists all loaded modifiers
     */
    private fun listModifiers(ctx: CommandContext<CommandSourceStack>): Int {
        val modifiers = ModifierEngine.getModifiers()

        ctx.source.sendSuccess({ Component.literal("§6§l=== DataGear Modifiers (${modifiers.size}) ===") }, false)

        for (mod in modifiers) {
            ctx.source.sendSuccess({ Component.literal("  §e${mod.id} §7-> §f${mod.targetDisplay} §8[${mod.operation.name.lowercase()}]") }, false)
            val allMods = mod.modifiers.map { (k, v) -> k to v.toString() } +
                mod.stringModifiers.map { (k, v) -> k to v } +
                mod.booleanModifiers.map { (k, v) -> k to v.toString() }
            for ((prop, value) in allMods) {
                ctx.source.sendSuccess({ Component.literal("    §7$prop: §f$value") }, false)
            }
        }
        return 1
    }

    private fun suggestTags(builder: SuggestionsBuilder): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val input = builder.remaining.lowercase().removePrefix("\"")
        BuiltInRegistries.ITEM.getTags().forEach { namedSet ->
            val tagStr = namedSet.key().location().toString()
            if (tagStr.lowercase().contains(input)) builder.suggest("\"$tagStr\"")
        }
        return builder.buildFuture()
    }

    private fun buildConditionHoverText(reasons: List<String>): Component {
        val lines = mutableListOf<String>()
        lines.add("§cConditions not met:")
        for (reason in reasons) lines.add("§7- $reason")
        return Component.literal(lines.joinToString("\n"))
    }

    /**
     * /datagear help
     */
    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val lines = listOf(
            "§e/datagear §7- Inspect held item data & modifiers",
            "§e/datagear tags §7- List all DataGear tags",
            "§e/datagear tags <tag> §7- Show items in a tag",
            "§e/datagear modifiers §7- List all loaded modifiers",
            "",
            "§7Datapack structure:",
            "§f  data/<ns>/datagear/modify/<file>.json §7- Modify items",
        )
        for (line in lines) ctx.source.sendSuccess({ Component.literal(line) }, false)
        return 1
    }
}
