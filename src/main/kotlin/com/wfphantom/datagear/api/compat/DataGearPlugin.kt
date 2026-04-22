package com.wfphantom.datagear.api.compat

import net.minecraft.world.item.ItemStack

/**
 * Compat plugin API for other mods to integrate with DataGear.
 *
 * Mods can implement this interface and register it via [DataGearCompatRegistry]
 * to handle custom equipment slots (e.g. Curios, Trinkets, Accessories, etc)
 * that aren't part of vanilla's EquipmentSlot enum.
 *
 * Note: For custom item properties (attributes, data components), DataGear
 * automatically discovers them from Minecraft's registries so no plugin needed.
 * (UNLESS YOURE DOING SOMETHING FUCKING WEIRD, DONT DO ANYTHING WEIRD)
 *
 * Example plugin:
 * <insert link to wiki here lmaoo>
 */
// @JvmDefaultWithCompatibility <- might need this for later
interface DataGearPlugin {
    /** The mod ID this plugin provides compatibility for */
    val modId: String

    /**
     * Returns the set of custom equipment slot names this plugin supports.
     * For example, a Curios compat plugin might return setOf("curios:ring", "curios:back").
     */
    fun getCustomSlotNames(): Set<String> = emptySet()

    /**
     * Applies a custom equipment slot to the item stack.
     * Called when the slot name is not a vanilla EquipmentSlot.
     * @return true if the slot was handled by this plugin
     */
    fun applyCustomSlot(stack: ItemStack, slotName: String): Boolean = false

    /**
     * Returns the current custom slot name for the item, if managed by this plugin.
     */
    fun getCustomSlot(stack: ItemStack): String? = null

    /**
     * Resolves a custom slot name to an EquipmentSlotGroup for attribute modifiers. (maybe?)
     * Return null if the slot name is not handled by this plugin.
     */
    fun resolveCustomSlotGroup(slotName: String): net.minecraft.world.entity.EquipmentSlotGroup? = null
}
