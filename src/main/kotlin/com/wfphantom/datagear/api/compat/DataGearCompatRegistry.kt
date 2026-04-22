package com.wfphantom.datagear.api.compat

import org.slf4j.LoggerFactory

/** Other mods register their [DataGearPlugin] implementations here. */
object DataGearCompatRegistry {

    private val logger = LoggerFactory.getLogger("datagear")
    private val plugins = mutableMapOf<String, DataGearPlugin>()

    fun register(plugin: DataGearPlugin) {
        if (plugins.containsKey(plugin.modId)) logger.warn("DataGear: Overwriting existing compat plugin for mod '${plugin.modId}'")
        plugins[plugin.modId] = plugin
        logger.info("DataGear: Registered compat plugin for '${plugin.modId}' (slots: ${plugin.getCustomSlotNames()})")
    }

    fun getPlugins(): Collection<DataGearPlugin> = plugins.values
}
