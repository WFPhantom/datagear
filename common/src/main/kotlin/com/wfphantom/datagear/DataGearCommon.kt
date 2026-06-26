package com.wfphantom.datagear

import com.wfphantom.datagear.engine.DefaultHandlers
import com.wfphantom.datagear.engine.ModifierEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/* TODO: Rewrite TODO LIST */
// TODO: Potentially Apply effect (or even just particles, without effects!) - Armors should be able to just apply effects, everything else can maybe do on hit? Armors can maybe also do on being hit? same with shield? what about bow/trident?
// TODO: Load custom textures, sounds, actions (holy shit fabric does not like actions), and overlays
// TODO: Allow for adding custom items (/assets/datagear/obsidian and /data/datagear/add/obsidian) (should be simple since we can add add components to anything, just textures and dynamically registering them in creative menu)
// TODO: Potentially mixin equippable slots
// TODO: Add disabling modifiers (E.g no durability) "remove": ["max_damage", "attack_damage"]
// TODO: Allow for jeb_ rainbow name (requires changes to color rendering)
// TODO: Test Plugin
// TODO: Create plugin for rendering the air bar
// TODO: Limitation of targeting by name: Items obtained after login (found in chests, crafted, etc.) won't get per-instance modifiers applied until the player renames them in an anvil or a /reload happens. There's no practical way around that without a tick-based scan, which I do not want to do because expensive.
// TODO: Wiki.
// TODO: "tooltip_show" attribute that would make a changed attribute show on the tooltip (this will be hell)
// TODO: clean commands
// TODO: Javadoc skull

object DataGearCommon {
    val logger: Logger = LoggerFactory.getLogger("datagear")

    fun initialize(
        registerResources: () -> Unit,
        registerCommands: () -> Unit,
        afterInit: () -> Unit = {}
    ) {
        logger.info("Loading DataGear...")
        registerResources()
        registerCommands()
        ModifierEngine.initializeCache()
        DefaultHandlers.register()
        afterInit()
        logger.info("DataGear loaded successfully!")
    }
}