package com.wfphantom.datagear

import com.wfphantom.datagear.commands.DataGearCommandRegister
import com.wfphantom.datagear.engine.ModifierEngine
import com.wfphantom.datagear.loader.DataGearResourceLoaderRegister
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object DataGear : ModInitializer {
    override fun onInitialize() {
        DataGearCommon.initialize(
            registerResources = { DataGearResourceLoaderRegister.register() },
            registerCommands = { DataGearCommandRegister.register() },
            afterInit = { ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> ModifierEngine.applyPerInstanceModifiers(handler.player) } }
        )
    }
}