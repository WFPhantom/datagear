package com.wfphantom.datagear.engine

import com.wfphantom.datagear.api.Condition
import com.wfphantom.datagear.api.GearModifier
import com.wfphantom.datagear.api.LogicalCondition
import com.wfphantom.datagear.api.Operation
import com.wfphantom.datagear.api.compat.DataGearCompatRegistry
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.Tool
import net.minecraft.world.item.component.Weapon
import net.minecraft.world.item.equipment.Equippable
import com.wfphantom.DataGear
import net.minecraft.util.Unit as McUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Core engine that applies [GearModifier]s to items.
 * Modifiers are applied on datapack reload and affect the default components of items.
 */
object ModifierEngine {

    private val logger = DataGear.logger
    private val modifiers = mutableListOf<GearModifier>()

    // Map of attribute name strings to their Holder references
    private val ATTRIBUTE_MAP: Map<String, Holder<Attribute>> = mapOf(
        "armor" to Attributes.ARMOR,
        "armor_toughness" to Attributes.ARMOR_TOUGHNESS,
        "attack_damage" to Attributes.ATTACK_DAMAGE,
        "attack_speed" to Attributes.ATTACK_SPEED,
        "attack_knockback" to Attributes.ATTACK_KNOCKBACK,
        "knockback_resistance" to Attributes.KNOCKBACK_RESISTANCE,
        "movement_speed" to Attributes.MOVEMENT_SPEED,
        "max_health" to Attributes.MAX_HEALTH,
        "luck" to Attributes.LUCK,
        "block_break_speed" to Attributes.BLOCK_BREAK_SPEED,
        "block_interaction_range" to Attributes.BLOCK_INTERACTION_RANGE,
        "entity_interaction_range" to Attributes.ENTITY_INTERACTION_RANGE,
        "fall_damage_multiplier" to Attributes.FALL_DAMAGE_MULTIPLIER,
        "gravity" to Attributes.GRAVITY,
        "jump_strength" to Attributes.JUMP_STRENGTH,
        "safe_fall_distance" to Attributes.SAFE_FALL_DISTANCE,
        "scale" to Attributes.SCALE,
        "mining_efficiency" to Attributes.MINING_EFFICIENCY
    )

    // Base values that Minecraft adds to ADD_VALUE modifiers for tooltip display
    private val ATTRIBUTE_PLAYER_BASE: Map<String, Double> = mapOf(
        "attack_damage" to 1.0,
        "attack_speed" to 4.0,
        "movement_speed" to 0.1
    )

    fun clear() {
        modifiers.clear()
    }

    fun addAll(newModifiers: List<GearModifier>) {
        modifiers.addAll(newModifiers)
    }

    fun getModifiers(): List<GearModifier> = modifiers.toList()

    fun modifierTargetsItem(modifier: GearModifier, itemId: Identifier): Boolean {
        val matchesTarget = modifier.targets.any { target -> targetMatchesItem(target, itemId) }
        if (!matchesTarget) return false
        val excluded = modifier.exclude.any { target -> targetMatchesItem(target, itemId) }
        return !excluded
    }

    private fun targetMatchesItem(target: String, itemId: Identifier): Boolean {
        if (!target.startsWith("#")) return Identifier.tryParse(target) == itemId
        val tagId = Identifier.tryParse(target.substring(1)) ?: return false
        val holder = BuiltInRegistries.ITEM.get(itemId).orElse(null) ?: return false
        val tagKey = TagKey.create(Registries.ITEM, tagId)
        return holder.`is`(tagKey)
    }

     //Called after datapack reload.
    fun applyAll() {
        val sorted = modifiers.sortedBy { it.priority }
        var applied = 0
        for (modifier in sorted) {
            val targetItems = resolveTargetItems(modifier)
            for (item in targetItems) if (applyModifierToItem(modifier, item)) applied++
        }
        logger.info("DataGear: Applied $applied modifier(s) from ${modifiers.size} rule(s)")
    }

    private fun resolveTargetItems(modifier: GearModifier): List<Item> {
        val included = modifier.targets.flatMap { resolveTarget(it) }.distinct()
        if (modifier.exclude.isEmpty()) return included
        val excluded = modifier.exclude.flatMap { resolveTarget(it) }.toSet()
        return included.filter { it !in excluded }
    }

    private fun resolveTarget(target: String): List<Item> {
        if (!target.startsWith("#")) {
            val itemId = Identifier.tryParse(target) ?: return emptyList()
            val holder = BuiltInRegistries.ITEM.get(itemId).orElse(null) ?: return emptyList()
            return listOf(holder.value())
        }

        val tagId = Identifier.tryParse(target.substring(1)) ?: return emptyList()
        val tagKey = TagKey.create(Registries.ITEM, tagId)
        return BuiltInRegistries.ITEM.getTagOrEmpty(tagKey).map { it.value() }
    }

    /**
     * Applies a single modifier to a single item.
     * Modifications are written back to the item's built-in registry holder so they persist.
     * Returns true if any modification was made.
     */
    private fun applyModifierToItem(modifier: GearModifier, item: Item): Boolean {
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        val holder = BuiltInRegistries.ITEM.get(itemKey).orElse(null) ?: return false
        if (!holder.isBound || !holder.areComponentsBound()) {
            logger.debug("DataGear: Skipping item {} - components not bound yet", BuiltInRegistries.ITEM.getKey(item))
            return false
        }

        val stack = try {
            item.defaultInstance
        } catch (_: NullPointerException) {
            logger.debug("DataGear: Skipping item {} - components not bound yet", BuiltInRegistries.ITEM.getKey(item))
            return false
        }
        if (stack.isEmpty) return false

        if (modifier.conditions != null && !evaluateCondition(modifier.conditions, stack)) return false

        var modified = false
        val modifiedComponents = mutableSetOf<DataComponentType<*>>()

        for ((property, strValue) in modifier.stringModifiers) {
            when (property) {
                "equipment_slot" -> {
                    if (modifier.operation == Operation.MULTIPLY || modifier.operation == Operation.ADD) {
                        if (modifier.operation == Operation.MULTIPLY) logger.warn("DataGear: 'multiply' operation is not supported for equipment_slot in ${modifier.id}")
                        else logger.warn("DataGear: 'add' operation is not supported for equipment_slot (items can only have one slot), treating as 'set' in ${modifier.id}")
                    }
                    val slot = try {
                        EquipmentSlot.byName(strValue.lowercase())
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (slot != null) {
                        applyEquipmentSlot(stack, slot)
                        modifiedComponents.add(DataComponents.EQUIPPABLE)
                        modified = true
                    }
                    else {
                        // Try loading compat plugins here
                        val handled = DataGearCompatRegistry.getPlugins().any { plugin -> plugin.applyCustomSlot(stack, strValue) }
                        if (handled) modified = true
                        else logger.warn("DataGear: Unknown equipment slot '$strValue' in ${modifier.id}")
                    }
                }
                else -> logger.warn("DataGear: Unknown string modifier property '$property' in ${modifier.id}")
            }
        }

        val removedComponents = mutableSetOf<DataComponentType<*>>()
        for ((property, boolValue) in modifier.booleanModifiers) {
            val component = applyBooleanModifier(stack, property, boolValue, modifier)
            if (component != null) {
                if (boolValue) modifiedComponents.add(component)
                else removedComponents.add(component)
                modified = true
            }
        }

        for ((property, value) in modifier.modifiers) {
            val componentModified = applyNumericModifier(stack, modifier, property, value)
            if (componentModified != null) {
                modifiedComponents.add(componentModified)
                modified = true
            }
        }

        // Write only the modified components back to the item's registry holder
        if (modified) {
            val builder = DataComponentMap.builder()
            builder.addAll(item.components())
            for (componentType in modifiedComponents) {
                val componentValue = stack.get(componentType as DataComponentType<Any>) ?: continue
                builder.set(componentType, componentValue)
            }
            // Remove components that were set to false
            // For removed components, we need to rebuild without them
            val finalMap = builder.build()
            if (removedComponents.isNotEmpty()) {
                val filteredBuilder = DataComponentMap.builder()
                for (typed in finalMap) if (typed.type() !in removedComponents) filteredBuilder.set(typed.type() as DataComponentType<Any>, typed.value())
                holder.bindComponents(filteredBuilder.build())
            } else holder.bindComponents(finalMap)
        }
        return modified
    }

    private fun applyBooleanModifier(
        stack: ItemStack,
        property: String,
        value: Boolean,
        modifier: GearModifier
    ): DataComponentType<*>? {
        return when (property) {
            "unbreakable" -> {
                if (value) stack.set(DataComponents.UNBREAKABLE, McUnit.INSTANCE)
                else stack.remove(DataComponents.UNBREAKABLE)
                DataComponents.UNBREAKABLE
            }
            "glider" -> {
                if (value) stack.set(DataComponents.GLIDER, McUnit.INSTANCE)
                else stack.remove(DataComponents.GLIDER)
                DataComponents.GLIDER
            }
            "enchantment_glint_override" -> {
                if (value) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                else stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
                DataComponents.ENCHANTMENT_GLINT_OVERRIDE
            }
            "intangible_projectile" -> {
                if (value) stack.set(DataComponents.INTANGIBLE_PROJECTILE, McUnit.INSTANCE)
                else stack.remove(DataComponents.INTANGIBLE_PROJECTILE)
                DataComponents.INTANGIBLE_PROJECTILE
            }
            else -> {
                logger.warn("DataGear: Unknown boolean modifier property '$property' in ${modifier.id}")
                null
            }
        }
    }

    private fun applyNumericModifier(
        stack: ItemStack,
        modifier: GearModifier,
        property: String,
        value: Double
    ): DataComponentType<*>? {
        val knownAttr = ATTRIBUTE_MAP[property]
        if (knownAttr != null) {
            applyAttributeModifier(stack, modifier, knownAttr, value, resolveSlotGroup(modifier.slot))
            return DataComponents.ATTRIBUTE_MODIFIERS
        }

        return when (property) {
            "max_damage", "durability" -> {
                applyIntComponentModifier(stack, DataComponents.MAX_DAMAGE, modifier.operation, value)
                DataComponents.MAX_DAMAGE
            }
            "max_stack_size" -> {
                applyIntComponentModifier(stack, DataComponents.MAX_STACK_SIZE, modifier.operation, value)
                DataComponents.MAX_STACK_SIZE
            }
            "repair_cost" -> {
                applyIntComponentModifier(stack, DataComponents.REPAIR_COST, modifier.operation, value)
                DataComponents.REPAIR_COST
            }
            "mining_speed", "default_mining_speed" -> {
                applyToolMiningSpeed(stack, modifier.operation, value)
                DataComponents.TOOL
            }
            "damage_per_block" -> {
                applyToolDamagePerBlock(stack, modifier.operation, value)
                DataComponents.TOOL
            }
            "item_damage_per_attack" -> {
                applyWeaponDamagePerAttack(stack, modifier.operation, value)
                DataComponents.WEAPON
            }
            "disable_blocking_for_seconds" -> {
                applyWeaponBlockingDisable(stack, modifier.operation, value)
                DataComponents.WEAPON
            }
            else -> applyDynamicModifier(stack, modifier, property, value)
        }
    }

    /**
     * Tries to apply a modifier via dynamic attribute or component lookup. :pray:
     * Returns the DataComponentType, which was modified, or null.
     */
    private fun applyDynamicModifier(
        stack: ItemStack,
        modifier: GearModifier,
        property: String,
        value: Double
    ): DataComponentType<*>? {
        val attrId = Identifier.tryParse(property)
        if (attrId == null) {
            logger.warn("DataGear: Invalid property identifier '$property' in ${modifier.id}")
            return null
        }

        val dynamicAttrHolder = BuiltInRegistries.ATTRIBUTE.get(attrId).orElse(null)
        if (dynamicAttrHolder != null) {
            applyAttributeModifier(stack, modifier, dynamicAttrHolder, value, resolveSlotGroup(modifier.slot))
            return DataComponents.ATTRIBUTE_MODIFIERS
        }

        val dynamicComponent = BuiltInRegistries.DATA_COMPONENT_TYPE.get(attrId).map { it.value() }.orElse(null)
        if (dynamicComponent != null) {
            if (applyDynamicComponentModifier(stack, dynamicComponent, modifier.operation, value)) return dynamicComponent

            logger.warn("DataGear: Component '$property' exists but its value is not numeric in ${modifier.id}")
            return null
        }
        logger.warn("DataGear: Unknown modifier property '$property' in ${modifier.id}")
        return null
    }

    fun evaluateCondition(condition: LogicalCondition, stack: ItemStack): Boolean {
        return when (condition) {
            is LogicalCondition.Single -> testSingleCondition(condition.condition, stack)
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
            is LogicalCondition.Single -> {
                val cond = condition.condition
                val numVal = getPropertyValue(cond.property, stack)
                val strVal = getStringPropertyValue(cond.property, stack)
                val currentDisplay = numVal?.toString() ?: strVal ?: "not present"
                val expected = mutableListOf<String>()
                if (cond.min != null) expected.add("min ${cond.min}")
                if (cond.max != null) expected.add("max ${cond.max}")
                if (cond.equals != null) expected.add("equals ${cond.equals}")
                if (!testSingleCondition(cond, stack)) reasons.add("${cond.property}: $currentDisplay (requires ${expected.joinToString(", ")})")
            }
            is LogicalCondition.And -> condition.conditions.forEach { collectFailureReasons(it, stack, reasons) }
            is LogicalCondition.Or -> {
                if (!evaluateCondition(condition, stack)) {
                    reasons.add("None of the OR conditions met")
                    condition.conditions.forEach { collectFailureReasons(it, stack, reasons) }
                }
            }
            is LogicalCondition.Not -> if (!evaluateCondition(condition, stack)) reasons.add("NOT condition failed (inner condition is true)")
        }
    }

    // Tests a single property condition against the item stack.
    private fun testSingleCondition(condition: Condition, stack: ItemStack): Boolean {
        // Try numeric value first
        val numericValue = getPropertyValue(condition.property, stack)
        if (numericValue != null) return condition.test(numericValue)

        // Try string value
        val stringValue = getStringPropertyValue(condition.property, stack)
        if (stringValue != null) return condition.testString(stringValue)

        return false
    }

    private fun getStringPropertyValue(property: String, stack: ItemStack): String? {
        return when (property) {
            "equipment_slot" -> stack.get(DataComponents.EQUIPPABLE)?.slot()?.name?.lowercase() ?: DataGearCompatRegistry.getPlugins().firstNotNullOfOrNull { it.getCustomSlot(stack) }
            else -> null
        }
    }

    fun getPropertyValue(property: String, stack: ItemStack): Double? {
        val knownAttr = ATTRIBUTE_MAP[property]
        if (knownAttr != null) return getAttributeBaseValue(stack, knownAttr)

        return when (property) {
            "max_damage", "durability" -> stack.get(DataComponents.MAX_DAMAGE)?.toDouble()
            "max_stack_size" -> stack.get(DataComponents.MAX_STACK_SIZE)?.toDouble()
            "repair_cost" -> stack.get(DataComponents.REPAIR_COST)?.toDouble()
            "mining_speed", "default_mining_speed" -> stack.get(DataComponents.TOOL)?.defaultMiningSpeed()?.toDouble()
            "damage_per_block" -> stack.get(DataComponents.TOOL)?.damagePerBlock()?.toDouble()
            "item_damage_per_attack" -> stack.get(DataComponents.WEAPON)?.itemDamagePerAttack()?.toDouble()
            "disable_blocking_for_seconds" -> stack.get(DataComponents.WEAPON)?.disableBlockingForSeconds()?.toDouble()
            else -> getDynamicPropertyValue(property, stack)
        }
    }

    // Tries to get a property value via dynamic attribute or component lookup. :pray:
    private fun getDynamicPropertyValue(property: String, stack: ItemStack): Double? {
        val attrId = Identifier.tryParse(property) ?: return null

        val dynamicAttrHolder = BuiltInRegistries.ATTRIBUTE.get(attrId).orElse(null)
        if (dynamicAttrHolder != null) return getAttributeBaseValue(stack, dynamicAttrHolder)

        val dynamicComponent = BuiltInRegistries.DATA_COMPONENT_TYPE.get(attrId).map { it.value() }.orElse(null)
        if (dynamicComponent != null) return getDynamicComponentValue(stack, dynamicComponent)

        return null
    }

    private fun getAttributeBaseValue(stack: ItemStack, attribute: Holder<Attribute>): Double? {
        val attrMods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: return null
        var total = 0.0
        var found = false
        for (entry in attrMods.modifiers()) {
            if (entry.attribute() == attribute) {
                total += entry.modifier().amount()
                found = true
            }
        }
        return if (found) total else null
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

    private fun formatDouble(value: Double): String {
        val bd = BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    private fun computeValue(operation: Operation, current: Double, value: Double): Double = when (operation) {
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
            val newAmount = computeValue(modifier.operation, 0.0, value)
            val attrKey = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()) ?: Identifier.parse("datagear:unknown")
            val targetSlot = slotGroup ?: EquipmentSlotGroup.ANY
            val slotSuffix = if (slotGroup != null) "/${slotGroup.serializedName}" else ""
            val modId = Identifier.parse("datagear:${modifier.id.path}/${attrKey.path}$slotSuffix")
            val attrMod = AttributeModifier(modId, newAmount, AttributeModifier.Operation.ADD_VALUE)
            entries.add(ItemAttributeModifiers.Entry(attribute, attrMod, targetSlot, ItemAttributeModifiers.Display.attributeModifiers()))
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers(entries))
    }

    private fun applyIntComponentModifier(
        stack: ItemStack,
        component: DataComponentType<Int>,
        operation: Operation,
        value: Double
    ) {
        val current = stack.get(component) ?: return
        val newValue = computeValue(operation, current.toDouble(), value).toInt().coerceAtLeast(0)
        stack.set(component, newValue)
    }

    private fun applyToolMiningSpeed(stack: ItemStack, operation: Operation, value: Double) {
        val tool = stack.get(DataComponents.TOOL) ?: return
        val newSpeed = computeValue(operation, tool.defaultMiningSpeed().toDouble(), value).toFloat()
        stack.set(DataComponents.TOOL, Tool(tool.rules(), newSpeed, tool.damagePerBlock(), tool.canDestroyBlocksInCreative()))
    }

    private fun applyToolDamagePerBlock(stack: ItemStack, operation: Operation, value: Double) {
        val tool = stack.get(DataComponents.TOOL) ?: return
        val newDmg = computeValue(operation, tool.damagePerBlock().toDouble(), value).toInt().coerceAtLeast(0)
        stack.set(DataComponents.TOOL, Tool(tool.rules(), tool.defaultMiningSpeed(), newDmg, tool.canDestroyBlocksInCreative()))
    }

    private fun applyWeaponDamagePerAttack(stack: ItemStack, operation: Operation, value: Double) {
        val weapon = stack.get(DataComponents.WEAPON) ?: return
        val newDmg = computeValue(operation, weapon.itemDamagePerAttack().toDouble(), value).toInt().coerceAtLeast(0)
        stack.set(DataComponents.WEAPON, Weapon(newDmg, weapon.disableBlockingForSeconds()))
    }

    private fun applyWeaponBlockingDisable(stack: ItemStack, operation: Operation, value: Double) {
        val weapon = stack.get(DataComponents.WEAPON) ?: return
        val newVal = computeValue(operation, weapon.disableBlockingForSeconds().toDouble(), value).toFloat().coerceAtLeast(0f)
        stack.set(DataComponents.WEAPON, Weapon(weapon.itemDamagePerAttack(), newVal))
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

        if (operation == Operation.SET || operation == Operation.ADD) {
            val types: List<Pair<String, () -> Unit>> = listOf(
                "Int" to { stack.set(componentType as DataComponentType<Int>, value.toInt()) },
                "Float" to { stack.set(componentType as DataComponentType<Float>, value.toFloat()) },
                "Double" to { stack.set(componentType as DataComponentType<Double>, value) }
            )
            for ((_, setter) in types) {
                try {
                    setter()
                    if (stack.has(componentType)) return true
                } catch (_: Exception) {
                    // Try next type LMAOO
                }
            }
            logger.warn("DataGear: Failed to set component '$componentType' to value $value - component type may not accept numeric values")
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
        val knownAttrHolders = ATTRIBUTE_MAP.values.toSet()
        if (attrMods != null) {
            val allAttributes = linkedMapOf<String, Holder<Attribute>>()
            for ((name, attr) in ATTRIBUTE_MAP) if (attrMods.modifiers().any { it.attribute() == attr }) allAttributes[name] = attr
            // Discover modded attributes not in ATTRIBUTE_MAP :pray:
            for (entry in attrMods.modifiers()) {
                if (entry.attribute() !in knownAttrHolders) {
                    val attrKey = BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value())
                    if (attrKey != null) allAttributes.putIfAbsent(attrKey.toString(), entry.attribute())
                }
            }

            for ((name, attr) in allAttributes) {
                val entriesBySlot = mutableMapOf<EquipmentSlotGroup, Double>()
                for (entry in attrMods.modifiers()) {
                    if (entry.attribute() == attr) {
                        entriesBySlot[entry.slot()] = (entriesBySlot[entry.slot()] ?: 0.0) + entry.modifier().amount()
                    }
                }
                if (entriesBySlot.isEmpty()) continue

                if (entriesBySlot.size == 1) {
                    val (slot, total) = entriesBySlot.entries.first()
                    var displayTotal = total
                    if (attrMods.modifiers().any { it.attribute() == attr && it.modifier().operation() == AttributeModifier.Operation.ADD_VALUE }) {
                        val base = ATTRIBUTE_PLAYER_BASE[name]
                        if (base != null) displayTotal += base
                    }
                    val slotSuffix = if (slot != EquipmentSlotGroup.ANY) " [${slot.serializedName}]" else ""
                    props[name] = "${formatDouble(displayTotal)}$slotSuffix"
                } else {
                    for ((slot, total) in entriesBySlot) {
                        var displayTotal = total
                        if (attrMods.modifiers().any { it.attribute() == attr && it.slot() == slot && it.modifier().operation() == AttributeModifier.Operation.ADD_VALUE }) {
                            val base = ATTRIBUTE_PLAYER_BASE[name]
                            if (base != null) displayTotal += base
                        }
                        val slotSuffix = if (slot != EquipmentSlotGroup.ANY) " [${slot.serializedName}]" else ""
                        props["$name$slotSuffix"] = formatDouble(displayTotal)
                    }
                }
            }
        }

        stack.get(DataComponents.MAX_DAMAGE)?.let { props["max_damage"] = it }
        stack.get(DataComponents.MAX_STACK_SIZE)?.let { props["max_stack_size"] = it }
        val repairCost = stack.get(DataComponents.REPAIR_COST)
        if (repairCost != null && (repairCost > 0 || stack.has(DataComponents.MAX_DAMAGE))) props["repair_cost"] = repairCost
        stack.get(DataComponents.TOOL)?.let { tool ->
            props["default_mining_speed"] = tool.defaultMiningSpeed()
            props["damage_per_block"] = tool.damagePerBlock()
        }
        stack.get(DataComponents.WEAPON)?.let { weapon ->
            props["item_damage_per_attack"] = weapon.itemDamagePerAttack()
            props["disable_blocking_for_seconds"] = weapon.disableBlockingForSeconds()
        }
        stack.get(DataComponents.EQUIPPABLE)?.let { equip ->
            props["equipment_slot"] = equip.slot().name
        }
        stack.get(DataComponents.ENCHANTABLE)?.let { props["enchantability"] = it }

        if (stack.has(DataComponents.UNBREAKABLE)) props["unbreakable"] = true
        if (stack.has(DataComponents.GLIDER)) props["glider"] = true
        if (stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) props["enchantment_glint_override"] = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
        if (stack.has(DataComponents.INTANGIBLE_PROJECTILE)) props["intangible_projectile"] = true

        // Discover modded data components with numeric values :pray:
        val knownComponents = setOf(
            DataComponents.MAX_DAMAGE, DataComponents.MAX_STACK_SIZE, DataComponents.REPAIR_COST,
            DataComponents.TOOL, DataComponents.WEAPON, DataComponents.EQUIPPABLE,
            DataComponents.ENCHANTABLE, DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.DAMAGE, DataComponents.UNBREAKABLE, DataComponents.ENCHANTMENTS,
            DataComponents.STORED_ENCHANTMENTS, DataComponents.TOOLTIP_DISPLAY,
            DataComponents.ITEM_NAME, DataComponents.CUSTOM_NAME, DataComponents.LORE,
            DataComponents.RARITY, DataComponents.CUSTOM_MODEL_DATA, DataComponents.REPAIRABLE,
            DataComponents.GLIDER, DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
            DataComponents.INTANGIBLE_PROJECTILE
        )
        // Check both prototype and held stack for modded components (runtime-only components
        // only exist on the actual held item, not the prototype) :pray:
        val discoveredTypes = mutableSetOf<DataComponentType<*>>()
        for (source in listOfNotNull(stack, heldStack)) {
            for (typedComponent in source.components) {
                val componentType = typedComponent.type()
                if (componentType in knownComponents || componentType in discoveredTypes) continue
                discoveredTypes.add(componentType)
                val componentId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(componentType) ?: continue
                val numericValue = getDynamicComponentValue(source, componentType)
                if (numericValue != null) {
                    props[componentId.toString()] = formatDouble(numericValue)
                } else {
                    val value = source.get(componentType)
                    if (value != null && componentId.namespace != "minecraft") props[componentId.toString()] = value.toString()
                }
            }
        }
        return props
    }
}
