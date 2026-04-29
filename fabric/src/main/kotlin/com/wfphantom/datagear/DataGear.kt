package com.wfphantom.datagear

import com.wfphantom.datagear.commands.DataGearCommandRegister
import com.wfphantom.datagear.engine.ModifierEngine
import com.wfphantom.datagear.loader.DataGearResourceLoaderRegister
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object DataGear : ModInitializer {
    private val logger = DataGearCommon.logger

    override fun onInitialize() {
        logger.info("Loading DataGear...")

        DataGearResourceLoaderRegister.register()
        DataGearCommandRegister.register()
        DataGearCommon.initialize()
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> ModifierEngine.applyPerInstanceModifiers(handler.player) }

        logger.info("DataGear loaded successfully!")
    }
}