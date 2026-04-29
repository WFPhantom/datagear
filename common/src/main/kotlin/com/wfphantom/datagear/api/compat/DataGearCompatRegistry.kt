package com.wfphantom.datagear.api.compat

import net.minecraft.core.component.DataComponentType
import org.slf4j.LoggerFactory

/** Other mods register their [DataGearPlugin] implementations here. */
object DataGearCompatRegistry {

    private val logger = LoggerFactory.getLogger("datagear")
    private val plugins = mutableMapOf<String, DataGearPlugin>()
    private val propertyHandlers = mutableMapOf<String, PropertyHandler>()
    private val propertyHandlersByType = mutableMapOf<DataComponentType<*>, MutableList<PropertyHandler>>()

    fun register(plugin: DataGearPlugin) {
        if (plugins.containsKey(plugin.modId)) logger.warn("DataGear: Overwriting existing compat plugin for mod '${plugin.modId}'")
        plugins[plugin.modId] = plugin
        val slots = plugin.customSlotNames
        val slotsInfo = if (slots.isEmpty()) "" else " (slots: $slots)"
        logger.info("DataGear: Registered compat plugin for '${plugin.modId}'$slotsInfo")
    }

    fun registerPropertyHandler(handler: PropertyHandler) {
        handler.getSupportedProperties().forEach { prop ->
            if (propertyHandlers.containsKey(prop)) logger.warn("DataGear: Overwriting handler for property '$prop'")
            propertyHandlers[prop] = handler
        }

        val type = handler.componentType
        propertyHandlersByType.getOrPut(type) { mutableListOf() }.add(handler)
    }

    fun getPropertyHandler(property: String): PropertyHandler? = propertyHandlers[property]

    fun getHandlersForType(type: DataComponentType<*>): List<PropertyHandler> = propertyHandlersByType[type] ?: emptyList()

    fun getPlugins(): Collection<DataGearPlugin> = plugins.values

    // distinct() because a single handler may be registered for multiple properties
    fun getPropertyHandlers(): Collection<PropertyHandler> = propertyHandlers.values.distinct()
}
