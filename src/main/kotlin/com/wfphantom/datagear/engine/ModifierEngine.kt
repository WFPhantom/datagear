package com.wfphantom.datagear.engine

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import com.wfphantom.datagear.DataGear
import com.wfphantom.datagear.api.GearModifier
import com.wfphantom.datagear.api.LogicalCondition
import com.wfphantom.datagear.api.Operation
import com.wfphantom.datagear.api.compat.DataGearCompatRegistry
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.util.Unit as McUnit

/**
 * Core engine that applies [GearModifier]s to items.
 * Modifiers are applied on datapack reload and affect the default components of items.
 */
object ModifierEngine {

    private val logger = DataGear.logger
    private val prototypeModifiers = mutableListOf<GearModifier>()
    private val perInstanceModifiers = mutableListOf<GearModifier>()
    private val modifiedItems = mutableSetOf<Item>()
    private val componentNumericTypeHints = mutableMapOf<DataComponentType<*>, ComponentMetadata>()
    private val targetCache = mutableMapOf<Identifier, List<Item>>()

    private enum class LogicalType {
        INT, FLOAT, DOUBLE, LONG, BOOLEAN, UNIT, STRING, LIST, UNKNOWN
    }

    private data class ComponentMetadata(
        val logicalType: LogicalType,
        val typeName: String
    )

    // Base values that Minecraft adds to ADD_VALUE modifiers for tooltip display
    private val ATTRIBUTE_PLAYER_BASE: Map<String, Double> = mapOf(
        "attack_damage" to 1.0,
        "attack_speed" to 4.0,
        "movement_speed" to 0.1
    )

    fun getAvailableProperties(): Map<String, String> {
        val props = mutableMapOf<String, String>()

        // Dynamic lookup for all attributes
        BuiltInRegistries.ATTRIBUTE.keySet().forEach { id ->
            if (id.namespace == "minecraft") props[id.path] = "Attribute (Double)"
            else props[id.toString()] = "Attribute (Double)"
        }

        componentNumericTypeHints.forEach { (type, metadata) ->
            val id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) ?: return@forEach
            if (id.namespace == "minecraft") props[id.path] = metadata.typeName
            else props[id.toString()] = metadata.typeName
        }

        DataGearCompatRegistry.getPropertyHandlers().forEach { handler -> handler.getSupportedProperties().forEach { prop -> props[prop] = handler.propertyType } }

        // Hardcoded (fix later)
        val aliases = mapOf(
            "equipment_slot" to "String", // Alias for Equippable.slot
            "item_name" to "String",      // Alias for DataComponents.ITEM_NAME
            "custom_name" to "String",    // Alias for DataComponents.CUSTOM_NAME
            "lore" to "List"              // Alias for DataComponents.LORE
        )
        props.putAll(aliases)
        return props.toSortedMap()
    }

    fun clear() {
        prototypeModifiers.clear()
        perInstanceModifiers.clear()
        modifiedItems.clear()
        targetCache.clear()
    }

    fun getModifiers(): List<GearModifier> = prototypeModifiers + perInstanceModifiers

    fun addAll(newModifiers: List<GearModifier>) {
        for (modifier in newModifiers) {
            if (modifier.conditions?.hasPerInstanceCondition() == true) perInstanceModifiers.add(modifier)
            else prototypeModifiers.add(modifier)
        }
        perInstanceModifiers.sortBy { it.priority }
        prototypeModifiers.sortBy { it.priority }
    }

    fun isItemModified(item: Item): Boolean = modifiedItems.contains(item)
    
    fun hasPerInstanceModifiers(stack: ItemStack): Boolean {
        if (perInstanceModifiers.isEmpty()) return false
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        return perInstanceModifiers.any { modifierTargetsItem(it, itemId) }
    }

    /**
     * Initializes a cache of numeric type hints for data components.
     * This is used as a heuristic when applying numeric modifiers to items that don't have the component yet.
     */
    fun initializeCache() {
        componentNumericTypeHints.clear()
        BuiltInRegistries.DATA_COMPONENT_TYPE.forEach { type ->
            val codecStr = try { type.codec()?.toString()?.lowercase() ?: "" } catch (_: Exception) { "" }
            
            val logicalType = when {
                codecStr.contains("integer") || codecStr.contains("int") -> LogicalType.INT
                codecStr.contains("float") -> LogicalType.FLOAT
                codecStr.contains("double") -> LogicalType.DOUBLE
                codecStr.contains("long") -> LogicalType.LONG
                codecStr.contains("boolean") -> LogicalType.BOOLEAN
                codecStr.contains("unit") -> LogicalType.UNIT
                type.codec() == null && !type.isTransient -> LogicalType.UNIT
                codecStr.contains("string") || codecStr.contains("component") || codecStr.contains("mutablecomponent") -> LogicalType.STRING
                codecStr.contains("list") || codecStr.contains("itemlore") -> LogicalType.LIST
                else -> LogicalType.UNKNOWN
            }

            val typeName = when (logicalType) {
                LogicalType.INT -> "Int"
                LogicalType.FLOAT -> "Float"
                LogicalType.DOUBLE -> "Double"
                LogicalType.LONG -> "Long"
                LogicalType.BOOLEAN, LogicalType.UNIT -> "Boolean"
                LogicalType.STRING -> "String"
                LogicalType.LIST -> "List"
                else -> "Component"
            }
            componentNumericTypeHints[type] = ComponentMetadata(logicalType, typeName)
        }
        logger.info("DataGear: Cached numeric hints for ${componentNumericTypeHints.size} components")
    }

    fun modifierTargetsItem(modifier: GearModifier, itemId: Identifier): Boolean {
        val matchesTarget = modifier.targets.any { target -> targetMatchesItem(target, itemId) }
        val excluded = modifier.exclude.any { target -> targetMatchesItem(target, itemId) }
        return matchesTarget && !excluded
    }

    private fun targetMatchesItem(target: String, itemId: Identifier): Boolean {
        if (!target.startsWith("#")) return Identifier.tryParse(target) == itemId
        val tagId = Identifier.tryParse(target.substring(1)) ?: return false
        val holder = BuiltInRegistries.ITEM.get(itemId).orElse(null) ?: return false
        val tagKey = TagKey.create(Registries.ITEM, tagId)
        return holder.`is`(tagKey)
    }

    fun applyAll(server: MinecraftServer? = null) {
        modifiedItems.clear()
        var applied = 0

        for (modifier in prototypeModifiers) {
            val excludedItems =
                if (modifier.exclude.isNotEmpty()) modifier.exclude.flatMap { resolveTarget(it) }.toSet()
                else emptySet()
            val targetItems = resolveTargetItems(modifier, excludedItems)

            for (item in targetItems) {
                if (applyModifierToItem(modifier, item)) {
                    applied++
                    modifiedItems.add(item)
                }
            }
        }
        
        // Per-instance modifiers apply to online players
        val players = server?.playerList?.players ?: emptyList()
        for (player in players) applyPerInstanceModifiers(player)

        logger.info("DataGear: Applied $applied prototype modifier(s) from ${prototypeModifiers.size} rule(s)")
        if (perInstanceModifiers.isNotEmpty()) logger.info("DataGear: Loaded ${perInstanceModifiers.size} per-instance rule(s)")
    }

    fun applyPerInstanceModifiers(player: ServerPlayer) {
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            applyPerInstanceModifiers(stack)
        }
    }

    fun applyPerInstanceModifiers(stack: ItemStack) {
        if (stack.isEmpty) return
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val sorted = perInstanceModifiers
        
        if (sorted.none { modifierTargetsItem(it, itemId) }) return

        // Cache prototype to avoid multiple ItemStack creations
        val proto = stack.item.defaultInstance

        // Reset to prototype before reapplying to prevent stacking
        stack.applyComponents(proto.components)

        // remove any per-instance modifiers that aren't present on the prototype
        for (modifier in sorted) {
            if (!modifierTargetsItem(modifier, itemId)) continue
            for (property in modifier.booleanModifiers.keys) {
                val id = Identifier.tryParse(property) ?: continue
                val type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null) ?: continue
                if (!proto.has(type)) stack.remove(type)
            }
            if (modifier.stringModifiers.containsKey("equipment_slot")) {
                if (!proto.has(DataComponents.EQUIPPABLE)) stack.remove(DataComponents.EQUIPPABLE)
            }
        }
        applyPerInstanceModifiersNoReset(stack)
    }

    fun applyPerInstanceModifiersNoReset(stack: ItemStack) {
        if (stack.isEmpty) return
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val sorted = perInstanceModifiers
        
        for (modifier in sorted) {
            if (!modifierTargetsItem(modifier, itemId)) continue
            applyModifierToStack(stack, modifier)
        }
    }

    private fun applyModifierToStack(stack: ItemStack, modifier: GearModifier): Boolean {
        return applyModifierToStack(stack, modifier, isPrototype = false)
    }

    private fun resolveTargetItems(modifier: GearModifier, excludedItems: Set<Item>): Set<Item> {
        val included = modifier.targets.flatMap { resolveTarget(it) }.toSet()
        if (excludedItems.isEmpty()) return included
        return included.filter { it !in excludedItems }.toSet()
    }

    private fun resolveTarget(target: String): List<Item> {
        val id = Identifier.tryParse(if (target.startsWith("#")) target.substring(1) else target) ?: return emptyList()
        targetCache[id]?.let { return it }

        val items =
            if (!target.startsWith("#")) {
                val holder = BuiltInRegistries.ITEM.get(id).orElse(null) ?: return emptyList()
                listOf(holder.value())
            } else {
                val tagKey = TagKey.create(Registries.ITEM, id)
                BuiltInRegistries.ITEM.getTagOrEmpty(tagKey).map { it.value() }
            }
        targetCache[id] = items
        return items
    }

    /**
     * Modifications are written back to the item's built-in registry holder so they persist.
     * Returns true if any modification was made.
     */
    private fun applyModifierToItem(modifier: GearModifier, item: Item): Boolean {
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        val holder = BuiltInRegistries.ITEM.get(itemKey).orElse(null) ?: return false
        if (!holder.isBound || !holder.areComponentsBound()) {
            logger.warn("DataGear: Skipping $itemKey - holder not bound")
            return false
        }

        val stack = try { item.defaultInstance } catch (_: NullPointerException) { return false }
        if (stack.isEmpty) return false

        val modified = applyModifierToStack(stack, modifier, isPrototype = true)

        @Suppress("UNCHECKED_CAST")
        if (modified) {
            val builder = DataComponentMap.builder()
            builder.addAll(item.components())
            for (typed in stack.components) builder.set(typed.type() as DataComponentType<Any>, typed.value())

            holder.bindComponents(builder.build())
        }
        return modified
    }

    private fun applyListModifier(stack: ItemStack, property: String, listValue: List<String>, modifier: GearModifier): Boolean {
        if (property != "item_name" && property != "custom_name" && property != "lore") return false
        
        val components = listValue.map { text ->
            try {
                val json = JsonParser.parseString(text)
                ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow { e -> RuntimeException(e) }
            } catch (_: Exception) { Component.literal(text) }
        }

        return when (property) {
            "item_name" -> {
                if (components.isNotEmpty()) { stack.set(DataComponents.ITEM_NAME, components.first()); true }
                else false
            }
            "custom_name" -> {
                if (components.isNotEmpty()) { stack.set(DataComponents.CUSTOM_NAME, components.first()); true }
                else false
            }
            "lore" -> {
                val currentLore = stack.get(DataComponents.LORE)
                val newLore = when (modifier.operation) {
                    Operation.SET -> ItemLore(components)
                    Operation.ADD -> ItemLore((currentLore?.lines() ?: emptyList()) + components)
                    Operation.MULTIPLY -> currentLore
                }
                if (newLore != null) { stack.set(DataComponents.LORE, newLore); true }
                else false
            }
            else -> false
        }
    }

    private fun applyModifierToStack(stack: ItemStack, modifier: GearModifier, isPrototype: Boolean = false): Boolean {
        if (stack.isEmpty) return false
        if (modifier.conditions != null && !evaluateCondition(modifier.conditions, stack)) return false

        var modified = false
        
        for ((property, value) in modifier.stringModifiers) {
            if (property == "equipment_slot") {
                val slot = try { EquipmentSlot.byName(value.lowercase()) } catch (_: Exception) { null }
                if (slot != null) {
                    applyEquipmentSlot(stack, slot)
                    modified = true
                } else if (isPrototype) {
                    val handled = DataGearCompatRegistry.getPlugins().any { it.applyCustomSlot(stack, value) }
                    if (handled) modified = true
                    else logger.warn("DataGear: Unknown equipment slot '$value' in ${modifier.id}")
                }
            }
        }

        val slotGroup = resolveSlotGroup(modifier.slot)
        for ((property, value) in modifier.modifiers) {
            val id = Identifier.tryParse(property) ?: continue

            val attrHolder = BuiltInRegistries.ATTRIBUTE.get(id).orElse(null)
            if (attrHolder != null) {
                applyAttributeModifier(stack, modifier, attrHolder, value, slotGroup)
                modified = true
                continue
            }

            val handler = DataGearCompatRegistry.getPropertyHandler(property)
            if (handler != null) {
                if (handler.applyNumericModifier(stack, property, modifier.operation, value)) modified = true

                continue
            }

            val componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null)
            if (componentType != null) {
                if (applyDynamicComponentModifier(stack, componentType, modifier.operation, value)) modified = true
                else logger.warn("DataGear: Component '$property' exists but its value is not numeric or could not be applied in ${modifier.id}")

                continue
            }
            logger.warn("DataGear: Unknown modifier property '$property' in ${modifier.id}")
        }

        for ((property, value) in modifier.booleanModifiers) if (applyBooleanModifier(stack, property, value, modifier) != null) modified = true

        for ((property, listValue) in modifier.listModifiers) if (applyListModifier(stack, property, listValue, modifier)) modified = true
        
        return modified
    }

    private fun applyBooleanModifier(
        stack: ItemStack,
        property: String,
        value: Boolean,
        modifier: GearModifier
    ): DataComponentType<*>? {
        val id = Identifier.tryParse(property) ?: return null
        val componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null) ?: return null

        val existing = stack.get(componentType)
        @Suppress("UNCHECKED_CAST")
        return when {
            existing is McUnit || (existing == null && componentType.codec() == null) -> {
                if (value) stack.set(componentType as DataComponentType<McUnit>, McUnit.INSTANCE)
                else stack.remove(componentType)
                componentType
            }
            existing is Boolean -> {
                stack.set(componentType as DataComponentType<Boolean>, value)
                componentType
            }
            existing == null -> {
                val protoValue = stack.item.defaultInstance.get(componentType)
                when (protoValue) {
                    is McUnit -> {
                        if (value) stack.set(componentType as DataComponentType<McUnit>, McUnit.INSTANCE)
                        else stack.remove(componentType)
                        componentType
                    }

                    is Boolean -> {
                        stack.set(componentType as DataComponentType<Boolean>, value)
                        componentType
                    }

                    else -> {
                        when (componentNumericTypeHints[componentType]?.logicalType) {
                            LogicalType.UNIT -> {
                                if (value) stack.set(componentType as DataComponentType<McUnit>, McUnit.INSTANCE)
                                else stack.remove(componentType)
                                componentType
                            }

                            LogicalType.BOOLEAN -> {
                                stack.set(componentType as DataComponentType<Boolean>, value)
                                componentType
                            }

                            else -> {
                                logger.warn("DataGear: Cannot determine type for boolean component '$property' in ${modifier.id}")
                                null
                            }
                        }
                    }
                }
            }
            else -> {
                logger.warn("DataGear: Component '$property' exists but is not a boolean or unit flag in ${modifier.id}")
                null
            }
        }
    }


    fun evaluateCondition(condition: LogicalCondition, stack: ItemStack): Boolean {
        return when (condition) {
            is LogicalCondition.Single -> condition.condition.test(stack)
            is LogicalCondition.And -> condition.conditions.all { evaluateCondition(it, stack) }
            is LogicalCondition.Or -> condition.conditions.any { evaluateCondition(it, stack) }
            is LogicalCondition.Not -> !evaluateCondition(condition.condition, stack)
        }
    }

    fun getConditionFailureReasons(condition: LogicalCondition, stack: ItemStack): List<String> {
        val reasons = mutableListOf<String>()
        collectFailureReasons(condition, stack, reasons)
        return reasons
    }

    private fun collectFailureReasons(condition: LogicalCondition, stack: ItemStack, reasons: MutableList<String>) {
        when (condition) {
            is LogicalCondition.Single -> condition.condition.getFailureReason(stack)?.let { reasons.add(it) }
            is LogicalCondition.And -> condition.conditions.forEach { collectFailureReasons(it, stack, reasons) }
            is LogicalCondition.Or -> if (!evaluateCondition(condition, stack)) {
                reasons.add("None of the OR conditions were met")
                condition.conditions.forEach { collectFailureReasons(it, stack, reasons) }
            }
            is LogicalCondition.Not -> if (!evaluateCondition(condition, stack)) reasons.add("NOT condition failed")
        }
    }

    fun getBooleanPropertyValue(property: String, stack: ItemStack): Boolean? {
        val id = Identifier.tryParse(property) ?: return null
        val type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null) ?: return null

        val existing = stack.get(type)
        if (existing is McUnit) return true
        if (existing is Boolean) return existing
        
        if (existing == null) {
            val proto = stack.item.defaultInstance.get(type)
            if (proto is McUnit || (proto == null && type.codec() == null)) return stack.has(type)
            if (proto is Boolean) return proto
        }
        return null
    }

    fun getStringPropertyValue(property: String, stack: ItemStack): String? {
        if (property == "equipment_slot") return stack.get(DataComponents.EQUIPPABLE)?.slot()?.name?.lowercase() ?: DataGearCompatRegistry.getPlugins().firstNotNullOfOrNull { it.getCustomSlot(stack) }
        if (property == "item_name") return stack.get(DataComponents.ITEM_NAME)?.string
        if (property == "custom_name") return stack.get(DataComponents.CUSTOM_NAME)?.string

        val id = Identifier.tryParse(property) ?: return null
        val type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null) ?: return null

        return stack.get(type)?.toString()
    }

    fun getPropertyValue(property: String, stack: ItemStack): Double? {
        val id = Identifier.tryParse(property) ?: return null

        val attrHolder = BuiltInRegistries.ATTRIBUTE.get(id).orElse(null)
        if (attrHolder != null) return getAttributeBaseValue(stack, attrHolder)

        val handler = DataGearCompatRegistry.getPropertyHandler(property)
        if (handler != null) return handler.getNumericValue(stack, property)

        val componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).map { it.value() }.orElse(null)
        if (componentType != null) return getDynamicComponentValue(stack, componentType)

        return null
    }

    private fun getAttributeBaseValue(stack: ItemStack, attribute: Holder<Attribute>): Double? {
        val attrMods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: return null

        var found = false
        var value = 0.0
        var pendingMultipliedBase = 0.0
        var pendingMultipliedTotal = 1.0
        
        for (entry in attrMods.modifiers()) {
            if (entry.attribute() != attribute) continue
            found = true
            when (entry.modifier().operation()) {
                AttributeModifier.Operation.ADD_VALUE -> value += entry.modifier().amount()
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE -> pendingMultipliedBase += entry.modifier().amount()
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> pendingMultipliedTotal *= (1.0 + entry.modifier().amount())
            }
        }
        
        value += value * pendingMultipliedBase
        value *= pendingMultipliedTotal
        
        return if (found) value else null
    }

    private fun resolveSlotGroup(slotName: String?): EquipmentSlotGroup? {
        if (slotName == null) return null
        try {
            val slot = EquipmentSlot.byName(slotName.lowercase())
            return EquipmentSlotGroup.bySlot(slot)
        } catch (_: IllegalArgumentException) { }
        EquipmentSlotGroup.entries.find { it.serializedName == slotName.lowercase() }?.let { return it }
        // Try compat plugins for modded slots
        for (plugin in DataGearCompatRegistry.getPlugins()) plugin.resolveCustomSlotGroup(slotName)?.let { return it }

        return null
    }

    fun computeValue(operation: Operation, current: Double, value: Double): Double = when (operation) {
        Operation.ADD -> current + value
        Operation.SET -> value
        Operation.MULTIPLY -> current * value
    }

    private fun applyAttributeModifier(
        stack: ItemStack,
        modifier: GearModifier,
        attribute: Holder<Attribute>,
        value: Double,
        slotGroup: EquipmentSlotGroup? = null
    ) {
        val current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        val entries = current.modifiers().toMutableList()
        var found = false

        for (i in entries.indices) {
            val entry = entries[i]
            val matchesAttr = entry.attribute() == attribute
            val matchesSlot = slotGroup == null || entry.slot() == slotGroup
            if (matchesAttr && matchesSlot) {
                found = true
                val newAmount = computeValue(modifier.operation, entry.modifier().amount(), value)
                val newMod = AttributeModifier(entry.modifier().id(), newAmount, entry.modifier().operation())
                entries[i] = ItemAttributeModifiers.Entry(attribute, newMod, entry.slot(), entry.display())
            }
        }

        if (!found) {
            val base = if (modifier.operation == Operation.MULTIPLY) 1.0 else 0.0
            val newAmount = computeValue(modifier.operation, base, value)
            val attrKey = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()) ?: Identifier.parse("datagear:unknown")
            val targetSlot = slotGroup ?: EquipmentSlotGroup.ANY
            val slotSuffix = if (slotGroup != null) "/${slotGroup.serializedName}" else ""
            val modId = Identifier.parse("datagear:${modifier.id.path}/${attrKey.path}$slotSuffix")
            val attrMod = AttributeModifier(modId, newAmount, AttributeModifier.Operation.ADD_VALUE)
            entries.add(ItemAttributeModifiers.Entry(attribute, attrMod, targetSlot, ItemAttributeModifiers.Display.attributeModifiers()))
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers(entries))
    }

    private fun applyEquipmentSlot(stack: ItemStack, slot: EquipmentSlot) {
        val current = stack.get(DataComponents.EQUIPPABLE)
        val newEquippable = if (current != null) {
            Equippable(
                slot,
                current.equipSound(),
                current.assetId(),
                current.cameraOverlay(),
                current.allowedEntities(),
                current.dispensable(),
                current.swappable(),
                current.damageOnHurt(),
                current.equipOnInteract(),
                current.canBeSheared(),
                current.shearingSound()
            )
        } else Equippable.builder(slot).build()
        stack.set(DataComponents.EQUIPPABLE, newEquippable)
    }

    /**
     * Attempts to apply a modifier to a dynamically looked-up data component.
     * Supports components whose values are Int, Float, Double, or Long. :pray:
     */
    private fun applyDynamicComponentModifier(
        stack: ItemStack,
        componentType: DataComponentType<*>,
        operation: Operation,
        value: Double
    ): Boolean {
        val current = stack.get(componentType)

        @Suppress("UNCHECKED_CAST")
        if (current != null) {
            return when (current) {
                is Int -> {
                    stack.set(componentType as DataComponentType<Int>, computeValue(operation, current.toDouble(), value).toInt())
                    true
                }
                is Float -> {
                    stack.set(componentType as DataComponentType<Float>, computeValue(operation, current.toDouble(), value).toFloat())
                    true
                }
                is Double -> {
                    stack.set(componentType as DataComponentType<Double>, computeValue(operation, current, value))
                    true
                }
                is Long -> {
                    stack.set(componentType as DataComponentType<Long>, computeValue(operation, current.toDouble(), value).toLong())
                    true
                }
                else -> false
            }
        }

        @Suppress("UNCHECKED_CAST")
        if (operation == Operation.SET || operation == Operation.ADD) {
            val metadata = componentNumericTypeHints[componentType]
            return when (metadata?.logicalType) {
                LogicalType.INT -> {
                    stack.set(componentType as DataComponentType<Int>, value.toInt())
                    true
                }
                LogicalType.FLOAT -> {
                    stack.set(componentType as DataComponentType<Float>, value.toFloat())
                    true
                }
                LogicalType.DOUBLE -> {
                    stack.set(componentType as DataComponentType<Double>, value)
                    true
                }
                LogicalType.LONG -> {
                    stack.set(componentType as DataComponentType<Long>, value.toLong())
                    true
                }
                else -> {
                    // Fallback to codec inspection if not in cache or unknown
                    val codecStr = try { componentType.codec()?.toString()?.lowercase() ?: "" } catch (_: Exception) { "" }
                    
                    when {
                        codecStr.contains("integer") || codecStr.contains("int") -> {
                            try { stack.set(componentType as DataComponentType<Int>, value.toInt()); true } catch (_: Exception) { false }
                        }
                        codecStr.contains("float") -> {
                            try { stack.set(componentType as DataComponentType<Float>, value.toFloat()); true } catch (_: Exception) { false }
                        }
                        codecStr.contains("double") -> {
                            try { stack.set(componentType as DataComponentType<Double>, value); true } catch (_: Exception) { false }
                        }
                        codecStr.contains("long") -> {
                            try { stack.set(componentType as DataComponentType<Long>, value.toLong()); true } catch (_: Exception) { false }
                        }
                        else -> {
                            // me: https://www.youtube.com/watch?v=mH3qUgJWLYU
                            var success = false
                            try { stack.set(componentType as DataComponentType<Int>, value.toInt()); success = true } catch (_: Exception) {}
                            if (!success) try { stack.set(componentType as DataComponentType<Float>, value.toFloat()); success = true } catch (_: Exception) {}
                            
                            if (!success) {
                                logger.warn("DataGear: Failed to set component '$componentType' to value $value - component type may not accept numeric values")
                                false
                            } else true
                        }
                    }
                }
            }
        }
        return false
    }

    // :pray:
    private fun getDynamicComponentValue(
        stack: ItemStack,
        componentType: DataComponentType<*>
    ): Double? {
        return when (val current = stack.get(componentType)) {
            is Int -> current.toDouble()
            is Float -> current.toDouble()
            is Double -> current
            is Long -> current.toDouble()
            else -> null
        }
    }

    fun getItemProperties(stack: ItemStack, heldStack: ItemStack? = null): Map<String, Any?> {
        val props = linkedMapOf<String, Any?>()

        val attrMods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)
        if (attrMods != null) {
            val attributeGroups = attrMods.modifiers().groupBy { Pair(it.attribute(), it.slot()) }
            
            for ((group, entries) in attributeGroups) {
                val (attr, slot) = group
                var value = 0.0
                var pendingMultipliedBase = 0.0
                var pendingMultipliedTotal = 1.0
                
                for (entry in entries) {
                    when (entry.modifier().operation()) {
                        AttributeModifier.Operation.ADD_VALUE -> value += entry.modifier().amount()
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE -> pendingMultipliedBase += entry.modifier().amount()
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> pendingMultipliedTotal *= (1.0 + entry.modifier().amount())
                    }
                }
                
                value += value * pendingMultipliedBase
                value *= pendingMultipliedTotal
                
                val attrId = BuiltInRegistries.ATTRIBUTE.getKey(attr.value())
                val prettyName = attrId?.path ?: "unknown"
                
                var displayTotal = value
                if (entries.any { it.modifier().operation() == AttributeModifier.Operation.ADD_VALUE }) {
                    val base = ATTRIBUTE_PLAYER_BASE[prettyName]
                    if (base != null) displayTotal += base
                }
                
                val slotSuffix = if (slot != EquipmentSlotGroup.ANY) " [${slot.serializedName}]" else ""
                props["$prettyName$slotSuffix"] = displayTotal.toString()
            }
        }

        // Dynamic component discovery
        val allSources = listOfNotNull(stack, heldStack)
        val seenTypes = mutableSetOf<DataComponentType<*>>()
        
        // Components that are already handled
        val excludedTypes = setOf(
            DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.DAMAGE
        )
        
        for (source in allSources) {
            for (typed in source.components) {
                val type = typed.type()
                if (type in excludedTypes || type in seenTypes) continue
                val id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) ?: continue

                var handledByHandler = false
                val handlers = DataGearCompatRegistry.getHandlersForType(type)
                if (handlers.isNotEmpty()) {
                    for (handler in handlers) {
                        for (prop in handler.getSupportedProperties()) {
                            val formatted = handler.formatValue(source, prop)
                            if (formatted != null) props[prop] = formatted
                        }
                    }
                    handledByHandler = true
                }

                if (handledByHandler) {
                    seenTypes.add(type)
                    continue
                }

                val value: Any? = when (type) {
                    DataComponents.REPAIR_COST -> {
                        val cost = source.get(DataComponents.REPAIR_COST)
                        if (cost != null && (cost > 0 || source.has(DataComponents.MAX_DAMAGE))) cost else null
                    }
                    DataComponents.LORE -> {
                        val lore = source.get(DataComponents.LORE)
                        if (lore != null && lore.lines().isNotEmpty()) props["lore"] = lore.lines().map { it.string }
                        null
                    }
                    else -> {
                        val numericValue = getDynamicComponentValue(source, type)
                        numericValue
                            ?: if (id.namespace != "minecraft") typed.value()
                            else if (type.codec() == null && !type.isTransient) true // boolean flag
                            else null
                    }
                }
                if (value != null) props[id.toString()] = value
                seenTypes.add(type)
            }
        }
        return props
    }
}
