package com.wfphantom.datagear.loader

import com.google.gson.JsonParser
import com.wfphantom.datagear.DataGearCommon
import com.wfphantom.datagear.api.GearModifier
import com.wfphantom.datagear.engine.ModifierEngine
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Loads DataGear modifier JSONs from datapacks.
 *
 * Directory:
 * - `data/<namespace>/datagear/modify/<file>.json`
 */
object DataGearResourceLoader {

    private val logger = DataGearCommon.logger
    private const val MODIFY_DIR = "datagear/modify"

    fun applyAndRefresh(server: MinecraftServer) {
        ModifierEngine.applyAll(server)
        refreshPlayerInventories(server)
    }

    fun reload(manager: ResourceManager) {
        logger.info("DataGear: Clearing ${ModifierEngine.getModifiers().size} modifier(s)...")
        ModifierEngine.clear()
        loadModifiers(manager)
    }

    /**
     * Refreshes all online players' inventory stacks so tooltips reflect updated item prototypes.
     * Existing ItemStacks hold a snapshot of the item's components from creation time,
     * so after modifying the prototype via bindComponents() we need to rebuild them. (please don't break lmao)
     */
    private fun refreshPlayerInventories(server: MinecraftServer) {
        var totalRefreshed = 0
        for (player in server.playerList.players) {
            val inventory = player.inventory
            for (i in 0 until inventory.containerSize) {
                val stack = inventory.getItem(i)
                if (stack.isEmpty) continue
                
                val isPrototypeModified = ModifierEngine.isItemModified(stack.item)
                val hasPerInstanceMatch = ModifierEngine.hasPerInstanceModifiers(stack)

                if (!isPrototypeModified && !hasPerInstanceMatch) continue

                // Create a fresh stack from the (now-modified) item prototype
                val fresh = stack.item.defaultInstance
                // Copy over the count
                fresh.count = stack.count
                // Re-apply any per-instance patches (enchantments, custom name, damage, etc.)
                fresh.applyComponents(stack.componentsPatch)

                // Re-apply per-instance modifiers
                if (hasPerInstanceMatch) ModifierEngine.applyPerInstanceModifiers(fresh)
                
                inventory.setItem(i, fresh)
                if (isPrototypeModified) totalRefreshed++
            }
            // Sync inventory to client
            player.inventoryMenu.broadcastChanges()
        }
        logger.info("DataGear: Refreshed $totalRefreshed item(s) across ${server.playerList.players.size} player(s)")
    }

    private fun loadModifiers(manager: ResourceManager) {
        val modifyResources = manager.listResources(MODIFY_DIR) { it.path.endsWith(".json") }
        val loadedModifiers = mutableListOf<GearModifier>()
        for ((id, resource) in modifyResources) {
            try {
                val json = resource.openAsReader().use { reader -> JsonParser.parseReader(reader).asJsonObject }
                val modifierPath = id.path.removePrefix("$MODIFY_DIR/").removeSuffix(".json")
                val modifierId = Identifier.parse("${id.namespace}:$modifierPath")
                val modifier = GearModifier.fromJson(modifierId, json)
                loadedModifiers.add(modifier)
            } catch (e: Exception) {
                logger.error("DataGear: Failed to load modifier $id", e)
            }
        }
        ModifierEngine.addAll(loadedModifiers)
        logger.info("DataGear: Loaded ${loadedModifiers.size} modifier(s)")
    }
}
