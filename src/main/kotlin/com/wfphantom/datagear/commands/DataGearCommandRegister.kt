package com.wfphantom.datagear.commands

import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.common.NeoForge.EVENT_BUS

object DataGearCommandRegister {
    fun register() {
        EVENT_BUS.addListener { event: RegisterCommandsEvent -> DataGearCommandHelper.registerCommands(event.dispatcher) }
    }
}