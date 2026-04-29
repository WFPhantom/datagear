package com.wfphantom.datagear.api.compat;

import com.wfphantom.datagear.api.Operation;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Interface for handling complex data components that store multiple named values.
 * <p>
 * Implement this interface to teach DataGear how to read and modify subproperties
 * of compound components that can't be handled by the dynamic component system.
 * <p>
 * Register implementations via {@link DataGearCompatRegistry#registerPropertyHandler}.
 */
public interface PropertyHandler {
    /**
     * The component type this handler manages.
     */
    DataComponentType<?> getComponentType();

    /**
     * Returns a list of subproperty names supported by this handler.
     */
    List<String> getSupportedProperties();

    /**
     * Gets a numeric value for a subproperty from the stack.
     * Returns null if the property is not supported or not present.
     */
    @Nullable
    Double getNumericValue(ItemStack stack, String property);

    /**
     * Applies a numeric modification to a subproperty on the stack.
     * @return true if handled
     */
    boolean applyNumericModifier(ItemStack stack, String property, Operation operation, double value);

    /**
     * Formats the value for tooltip/inspection display.
     * Defaults to getNumericValue.
     */
    @Nullable
    default Object formatValue(ItemStack stack, String property) {
        return getNumericValue(stack, property);
    }

    /**
     * Returns the logical type of the properties handled by this handler.
     * Defaults to "Double".
     */
    default String getPropertyType() {
        return "Double";
    }
}