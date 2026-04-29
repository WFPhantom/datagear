package com.wfphantom.datagear.engine

import com.wfphantom.datagear.api.Operation
import com.wfphantom.datagear.api.compat.DataGearCompatRegistry
import com.wfphantom.datagear.api.compat.PropertyHandler
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.Tool
import net.minecraft.world.item.component.Weapon

/**
 * Default [PropertyHandler] implementations for vanilla components.
 */
object DefaultHandlers {
    class ToolHandler : PropertyHandler {
        override fun getComponentType() = DataComponents.TOOL
        override fun getSupportedProperties() = listOf("mining_speed", "damage_per_block")

        override fun getNumericValue(stack: ItemStack, property: String): Double? {
            val tool = stack.get(componentType) ?: return null
            return when (property) {
                "mining_speed" -> tool.defaultMiningSpeed().toDouble()
                "damage_per_block" -> tool.damagePerBlock().toDouble()
                else -> null
            }
        }

        override fun applyNumericModifier(stack: ItemStack, property: String, operation: Operation, value: Double): Boolean {
            val currentTool = stack.get(componentType) ?: return false
            val newTool = when (property) {
                "mining_speed" -> Tool(
                    currentTool.rules(),
                    ModifierEngine.computeValue(operation, currentTool.defaultMiningSpeed().toDouble(), value).toFloat(),
                    currentTool.damagePerBlock(),
                    currentTool.canDestroyBlocksInCreative
                )
                "damage_per_block" -> Tool(
                    currentTool.rules(),
                    currentTool.defaultMiningSpeed(),
                    ModifierEngine.computeValue(operation, currentTool.damagePerBlock().toDouble(), value).toInt(),
                    currentTool.canDestroyBlocksInCreative
                )
                else -> return false
            }
            stack.set(componentType, newTool)
            return true
        }
    }

    class WeaponHandler : PropertyHandler {
        override fun getComponentType() = DataComponents.WEAPON
        override fun getSupportedProperties() = listOf("item_damage_per_attack", "disable_blocking_for_seconds")

        override fun getNumericValue(stack: ItemStack, property: String): Double? {
            val weapon = stack.get(componentType) ?: return null
            return when (property) {
                "item_damage_per_attack" -> weapon.itemDamagePerAttack().toDouble()
                "disable_blocking_for_seconds" -> weapon.disableBlockingForSeconds().toDouble()
                else -> null
            }
        }

        override fun applyNumericModifier(stack: ItemStack, property: String, operation: Operation, value: Double): Boolean {
            val currentWeapon = stack.get(componentType) ?: return false
            val newWeapon = when (property) {
                "item_damage_per_attack" -> Weapon(
                    ModifierEngine.computeValue(operation, currentWeapon.itemDamagePerAttack().toDouble(), value).toInt(),
                    currentWeapon.disableBlockingForSeconds()
                )
                "disable_blocking_for_seconds" -> Weapon(
                    currentWeapon.itemDamagePerAttack(),
                    ModifierEngine.computeValue(operation, currentWeapon.disableBlockingForSeconds().toDouble(), value).toFloat()
                )
                else -> return false
            }
            stack.set(componentType, newWeapon)
            return true
        }
    }

    class EquippableHandler : PropertyHandler {
        override fun getComponentType() = DataComponents.EQUIPPABLE
        override fun getSupportedProperties() = listOf("equipment_slot")
        override fun getNumericValue(stack: ItemStack, property: String): Double? = null
        override fun formatValue(stack: ItemStack, property: String): Any? = stack.get(componentType)?.slot()?.name
        override fun applyNumericModifier(stack: ItemStack, property: String, operation: Operation, value: Double): Boolean = false
        override fun getPropertyType(): String = "String"
    }

    fun register() {
        DataGearCompatRegistry.registerPropertyHandler(ToolHandler())
        DataGearCompatRegistry.registerPropertyHandler(WeaponHandler())
        DataGearCompatRegistry.registerPropertyHandler(EquippableHandler())
    }
}