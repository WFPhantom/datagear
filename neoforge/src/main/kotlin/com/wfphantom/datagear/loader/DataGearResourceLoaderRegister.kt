package com.wfphantom.datagear.loader

import com.wfphantom.datagear.DataGearCommon
import com.wfphantom.datagear.engine.ModifierEngine
import com.wfphantom.datagear.loader.DataGearResourceLoader.applyAndRefresh
import com.wfphantom.datagear.loader.DataGearResourceLoader.reload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.neoforged.neoforge.common.NeoForge.EVENT_BUS
import net.neoforged.neoforge.event.AddServerReloadListenersEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent

object DataGearResourceLoaderRegister {
    private val logger = DataGearCommon.logger
    fun register() {
        EVENT_BUS.addListener { event: AddServerReloadListenersEvent -> event.addListener(Identifier.parse("datagear:resource_loader"), ResourceManagerReloadListener(::reload)) }
        EVENT_BUS.addListener { event: ServerStartedEvent ->
            logger.info("DataGear: Server started, applying modifiers...")
            applyAndRefresh(event.server)
        }
        EVENT_BUS.addListener { event: PlayerLoggedInEvent ->
            val player = event.entity
            if (player is ServerPlayer) ModifierEngine.applyPerInstanceModifiers(player)
        }
    }
}