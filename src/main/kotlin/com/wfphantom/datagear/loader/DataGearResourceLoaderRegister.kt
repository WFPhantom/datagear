package com.wfphantom.datagear.loader

import com.wfphantom.datagear.DataGear
import com.wfphantom.datagear.loader.DataGearResourceLoader.applyAndRefresh
import com.wfphantom.datagear.loader.DataGearResourceLoader.reload
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

object DataGearResourceLoaderRegister {
    private val logger = DataGear.logger
    fun register() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.parse("datagear:resource_loader"), ResourceManagerReloadListener(::reload))
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            logger.info("DataGear: Server started, applying modifiers...")
            applyAndRefresh(server)
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, _ ->
            logger.info("DataGear: Datapack reload complete, applying modifiers...")
            applyAndRefresh(server)
        }
    }
}