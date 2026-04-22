package com.wfphantom

import com.wfphantom.datagear.command.DataGearCommand
import com.wfphantom.datagear.loader.DataGearResourceLoader
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

/* TODO: V2 TODO LIST */
// TODO: Potentially Apply effect - Armors should be able to just apply effects, everything else can maybe do on hit? Armors can maybe also do on being hit? same with shield? what about bow/trident?
// TODO: Potentially mixin equippable slots
// TODO: Load custom textures sounds and overlays
// TODO: Allow for adding custom items (/assets/datagear/obsidian)
// TODO: Add disabling modifiers (E.g no durability) "remove": ["max_damage", "attack_damage"]
// TODO: Lore
// TODO: Unchecked cast
// TODO: Test Plugin
// TODO: Create plugin for rendering the air bar
// TODO: Targeting by name could be fun lol
// TODO: Fix whatever the fuck is going on in /command (why is it called command when they're multiple??)
// TODO: Half the engine is probably redundant atp lmao
// TODO: Probably add a command for available modifiers idk man make your mod useful

object DataGear : ModInitializer {
    val logger: org.slf4j.Logger = LoggerFactory.getLogger("datagear")

    override fun onInitialize() {
        logger.info("Loading DataGear...")

        DataGearResourceLoader.register()
        DataGearCommand.register()

        logger.info("DataGear loaded successfully!")
    }
}
