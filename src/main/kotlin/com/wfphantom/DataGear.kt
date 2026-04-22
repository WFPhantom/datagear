package com.wfphantom

import com.wfphantom.datagear.command.DataGearCommand
import com.wfphantom.datagear.loader.DataGearResourceLoader
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

// TODO: Potentially Apply effect - Armors should be able to just apply effects, everything else can maybe do on hit? Armors can maybe also do on being hit? same with shield? what about bow/trident?
// TODO: Test Plugin
// TODO: Potentially mixin equippable slots
// TODO: Rely less on datagear tags and make use of common or already existing minecraft tags
// TODO: Load custom textures and overlays
// TODO: Allow for adding custom items (/assets/datagear/obsidian)

object DataGear : ModInitializer {
    private val logger = LoggerFactory.getLogger("datagear")

    override fun onInitialize() {
        logger.info("Loading DataGear...")

        DataGearResourceLoader.register()
        DataGearCommand.register()

        logger.info("DataGear loaded successfully!")
    }
}
