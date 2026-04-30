package com.wfphantom.datagear.api.compat;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * Compat plugin API for other mods to integrate with DataGear.
 * <p>
 * Mods can implement this interface and register it via {@link DataGearCompatRegistry}
 * to handle custom equipment slots (e.g. Curios, Trinkets, Accessories, etc.)
 * that aren't part of vanilla's EquipmentSlot enum.
 * <p>
 * Note: For custom item properties (attributes, data components), DataGear
 * automatically discovers them from Minecraft's registries so no plugin needed.
 * (UNLESS YOU'RE DOING SOMETHING FUCKING WEIRD, DON'T DO ANYTHING WEIRD)
 * <p>
 * @see <a href="https://github.com/WFPhantom/datagear/wiki">DataGear Wiki</a>
 */
public interface DataGearPlugin {

    /** @return the mod ID string this plugin provides compatibility for */
    String getModId();

    /** @return set of custom slot names (e.g. setOf("curios:ring")), empty by default */
    default Set<String> getCustomSlotNames() { return Set.of(); }

    /**
     * Applies a custom equipment slot to the item stack.
     * @return true if the slot was handled by this plugin
     */
    default boolean applyCustomSlot(ItemStack stack, String slotName) { return false; }

    /** @return the custom slot name for the given item stack, or null if not handled by this plugin */
    @Nullable
    default String getCustomSlot(ItemStack stack) { return null; }

    /** @return the EquipmentSlotGroup for the given slot name, or null if not handled by this plugin */
    @Nullable
    default EquipmentSlotGroup resolveCustomSlotGroup(String slotName) { return null; }
}